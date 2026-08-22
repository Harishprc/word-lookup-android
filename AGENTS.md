# AGENTS.md

This file provides guidance to Codex (Codex.ai/code) when working with code in this repository.

## What this is

A clean-room Kotlin clone of `com.harish.wordlookup` v0.1.0 — an on-device "select a word → get an AI
lookup card" tool — rebuilt from the shipped `WordLookup-apk.zip` (kept in this folder as the reference
artifact; never modify it). The original Android source was never on this machine; every file under
`app/src/` was reconstructed from the APK (binary AXML, `resources.arsc`, Room DDL, dex strings) and from
a jadx decompile at `reference/jadx/sources/` (full app package, `@Metadata` intact so original Kotlin
signatures and the exact Gemini prompt were recoverable, not guessed).

Two rounds of deliberate departure from the shipped app since the initial clone:
- **Swedish added as a 26th language**, closing a gap where the Android app (25 languages) lagged the
  desktop's list (26).
- **A hardening/latency/accuracy pass** (see "Hardening, latency, and accuracy work" below):
  script-validation retry ladder, a faster default model, single-flight + in-memory cache, a real
  debounce, connection warm-up, and fixes for several ways the app could crash or silently misbehave.

v0.1.0 byte-parity (manifest, dependencies, accessibility config, Room DDL/DAO SQL) is still verified on
every build — see "Verifying a change" — but the Gemini prompt and generated SQL now *intentionally* differ
from the original; parity was the goal for the initial clone, not for this app going forward.

**Companion desktop app**: `H:\Codex Project Files\Mouse automation project 1` (Python, `kannada_lookup`
package). This Android app was built alongside it and syncs with it through one private GitHub Gist. Its
`README.md:162` names this Android app. Reach for that repo for the *reasoning* behind shared behavior
(the Gemini prompt's script-validation rules, the sync merge order) — this repo has the same behavior but
sparer comments, since v0.1.0 predates several things the desktop later grew (see "Known drift" below).

## Commands

```bash
source tools/env.sh          # Bash tool: puts JDK 17 / SDK / Gradle / jadx on PATH for this session
# or, PowerShell:  . .\tools\env.ps1

./gradlew :app:assembleRelease           # build (signed with the debug key, like the original)
./gradlew :app:testReleaseUnitTest       # 52 JVM unit tests, all offline (no device, no network, no Room)
adb install -r app/build/outputs/apk/release/app-release.apk

python tools/apk_inventory.py <apk> -o out.json      # extract manifest/deps/DDL/SQL/prompt/languages
python tools/apk_inventory.py --diff a.json b.json   # diff two inventories — the acceptance test
```

The toolchain lives in `C:\tools` (JDK 17.0.20, `android-sdk` with platform-35 + build-tools 35.0.0,
Gradle 8.9, jadx 1.5.1) — none of it is on `PATH` by default, hence `tools/env.sh`/`env.ps1`.

**No device has been attached to any session so far.** Everything through inventory diff and 47 unit tests
is verified; the actual `adb install` + on-device walkthrough has not happened yet. The user's test device
is a Samsung S24 (Android 14+, One UI). Before trusting any of this on that device, at minimum: onboarding
→ both trigger paths → card (check the English example now renders) → register → toggle the app off from
inside the app *and* from the tile, confirming both actually stop the menu-triggered path too → drag-select
across a sentence and confirm one lookup fires, not a burst → revoke "Display over other apps" and confirm
a Toast instead of a dead accessibility service → leave it running with a GitHub token set and confirm sync
actually completes (watch for `Result.failure()` in `adb shell dumpsys jobscheduler` or a WorkManager log
if it doesn't, and check that a bad/expired token stops retrying instead of looping forever).

## Architecture

Package layout mirrors the recovered class list exactly:

```
app/src/main/java/com/harish/wordlookup/
├── WordLookupApp.kt          Application: owns Settings/ApiKeyStore/LookupRepository singletons,
│                             plus enabledState/targetLanguageState (fast-path read-only hints)
├── data/                     Settings (DataStore), ApiKeyStore (EncryptedSharedPreferences,
│                             falls back to plaintext prefs if Keystore is corrupt), Language(s)
│                             (26, incl. Swedish), LookupResult, TextTruncation,
│                             TranslationProvider (interface) + GeminiProvider (impl),
│                             ScriptValidator, LookupRepository, NetworkModule
├── data/cache/                Room: LookupEntity/LookupDao/LookupDatabase, table `lookups`
├── data/sync/                 SyncEntry/SyncPayload, GistSyncClient, SyncMerger, SyncWorker
├── service/                   ProcessTextActivity, SelectionAccessibilityService,
│                             SelectionExtractor, LookupTileService, LookupViewModel
└── ui/                        MainActivity+HomeScreen, SetupScreen, RegisterScreen, LookupCard,
                              OverlayHost+OverlayLifecycleOwner, Permissions, MainViewModel
```

**Three trigger paths, one repository.** `MainActivity` (launcher), `ProcessTextActivity`
(`ACTION_PROCESS_TEXT` from the text-selection menu — transparent activity, tap-anywhere-to-dismiss), and
`SelectionAccessibilityService` (watches `TYPE_VIEW_TEXT_SELECTION_CHANGED`, no tap needed, genuinely
debounced - see below) all funnel into `data.LookupRepository.lookup(text, language)`: in-memory LRU hit →
instant; Room hit → fast; miss → call the provider (single-flighted) → persist to both → return.
`data.TriggerMode` (INSTANT / MENU_ONLY / BOTH) controls which paths are live.

**`LookupRepository` is three layers, each skipping the cost of the next**: a 256-entry in-memory LRU, then
Room, then `TranslationProvider` (an interface `GeminiProvider` implements - the modularity seam that
replaced a fake-HTTP-layer test dependency with a plain fake in `LookupRepositoryTest`). Concurrent
identical lookups (a drag re-selecting the same word, or both trigger paths firing near-simultaneously)
share one in-flight fetch via a mutex-guarded `Deferred` map rather than issuing two API calls -
`LookupRepository.singleFlight`. `WordLookupApp.targetLanguageState` exists purely as a
zero-suspension advisory hint for the LRU's fast path (`peekMemory`); a stale read there only costs a
missed optimization, never wrong data - the real lookup always re-reads the authoritative DataStore value.

**Accuracy: `ScriptValidator`** rejects a translation written in the wrong alphabet before it ever reaches
the cache (which has no expiry - one bad entry would be served forever). Ported from the desktop's
`SCRIPT_RANGES`/`uses_expected_script` (`kannada_lookup/languages.py:60-152`), including the "own script
present AND no foreign letter" rule that catches mixed-script replies a naive "contains any own character"
check would miss. `GeminiProvider.lookup` runs a retry-then-escalate ladder on a script failure: same model
with the mistake named explicitly → escalate once to `DEFAULT_FALLBACK_MODEL` → fail loudly rather than
cache something unreadable. Because the default `generationConfig` is `temperature = 0` (greedy, both the
fastest setting and what keeps the cache coherent), a retry that resent the identical prompt would just get
the identical bad reply back - every retry step appends a different corrective instruction instead.

**Latency**: default model is `gemini-flash-lite-latest` (the desktop measured 1.4-3.1s vs 4.4-10.6s+ for
plain `flash`, against the same API), `generationConfig` now sets `temperature`/`topP`/`maxOutputTokens`,
and the prompt caps field lengths (fewer output tokens ≈ less generation time - the single biggest lever
after model choice). The network call itself is suspend-cancellable (`Call.await()` via
`suspendCancellableCoroutine`, not the blocking `execute()`), so a superseded lookup's coroutine
cancellation genuinely aborts its socket instead of burning quota on an answer nobody will see.
`SelectionAccessibilityService` debounces on a real quiet window (250ms, cancel-and-reschedule per event) -
dragging a selection handle used to fire one API call per intermediate substring; now it fires once, after
the selection stops changing. `GeminiProvider.warmUp()` fires on the first event of a new selection burst
so the DNS+TLS handshake overlaps the debounce wait instead of sitting on the critical path;
`data.NetworkModule` gives `GeminiProvider` and `GistSyncClient` one shared `ConnectionPool`.

**`OverlayHost` is the trickiest piece.** A `WindowManager`-attached view has no Activity behind it, so
`OverlayLifecycleOwner` supplies lifecycle/viewmodel-store/saved-state by hand. Window flags
(`TYPE_APPLICATION_OVERLAY`, `FLAG_NOT_FOCUSABLE|FLAG_NOT_TOUCH_MODAL|FLAG_LAYOUT_IN_SCREEN`), gravity
(`TOP|START`), positioning (anchor.left, anchor.bottom + 12dp, clamped to screen), and the 6s auto-dismiss
were all recovered from jadx byte-exact — don't change them without re-checking
`reference/jadx/sources/com/harish/wordlookup/ui/OverlayHost.java`.

**`SyncWorker.doWork()` is reconstructed, not decompiled 1:1** — jadx flagged its bytecode as
"decompiled incorrectly" (a heavily-mangled coroutine state machine). It's rebuilt from the disclosed
local variable names (`app`, `dao`, `client`, `local`, `gistId`, `merged`) plus the desktop's documented
sync design. `SyncMerger`'s comparator chain, `SyncEntry`'s wire shape, and `GistSyncClient`'s HTTP calls
*are* byte-exact from jadx — only the worker's top-level control flow is a reasoned reconstruction.
Correctness there rests on the round-trip test, not a diff.

## Hardening, latency, and accuracy work (round 2)

A failure-mode audit of the whole codebase (not just the recorded bugs), a modularization evaluation, and a
judged response to an external latency-optimization guide written for the desktop app (its recommendations
don't all transfer - see the reasoning below). Everything here is grounded in file:line or in the desktop's
measured numbers, not generic Android advice.

**Modularization, evaluated and judged:** multi-module Gradle and a DI framework (Hilt/Koin) were both
rejected - at 2,000-ish LOC and six singletons built in eight lines of `WordLookupApp.onCreate`, either
would cost more than it returns. What was adopted instead: interface seams exactly where behavior varies or
needs faking - `TranslationProvider` (lets `LookupRepository` be tested with a plain fake instead of a fake
HTTP layer) and the already-existing `SelectionExtractor` pattern, joined by `ScriptValidator`. **Descoped:**
an `AppContainer` consolidating `WordLookupApp`'s four `lateinit var`s was recommended in the plan but not
implemented - by the time the functional work above was done and fully green, it would have been a pure
refactor with real regression risk for no behavior change. Worth doing before this grows further, not worth
risking a stable, tested build for right now. Said here explicitly rather than silently dropped.

**Crash hardening**: `ui/OverlayHost.kt` - `addView` is wrapped; a revoked `SYSTEM_ALERT_WINDOW` or an OEM
refusing `TYPE_APPLICATION_OVERLAY` now shows a Toast instead of crashing
`SelectionAccessibilityService` (which Android would then permanently disable). `data/ApiKeyStore.kt` -
`EncryptedSharedPreferences.create` failure (a real, field-reported AndroidX Keystore-corruption mode,
typically after a cross-device restore) now falls back to a plaintext prefs file instead of bricking every
key read forever. `GeminiProvider`/`GistSyncClient` - `response.body!!` replaced with checked reads.

**Sync**: `data/sync/SyncWorker.kt` - `enqueue()` (called after every successful lookup) now uses
`ExistingWorkPolicy.KEEP`, not `REPLACE`. REPLACE restarted the 15-minute delay on every call, so anyone
looking things up more often than every 15 minutes never synced at all - the "Not synced yet." state visible
throughout the original screen recording. `enqueueNow()` (the "Sync now" button) still REPLACEs, correctly.
`GistSyncClient.GistSyncException` now carries a `statusCode`; `SyncWorker` classifies 401/403 (bad/revoked
token) as `Result.failure()` instead of an invisible infinite `Result.retry()` loop.

**Stale state, the tile's real root cause**: `WordLookupApp.enabledState` is seeded `Eagerly` with a
hardcoded `true` while the real value loads from DataStore asynchronously. `LookupTileService.onClick` used
to compute `next = !enabledState.value` from that possibly-stale snapshot - on a cold start this could
compute `!true = false` while the stored value was *already* `false`, writing a no-op, which is why the tile
could look like it "does nothing." Fixed by calling `Settings.toggleEnabled()` (an atomic DataStore
read-modify-write) instead of computing a value from a snapshot; the tile's own visible state still updates
via the existing `onStartListening` collector once the real write lands. `ProcessTextActivity` now decides
from a suspend read of the real value (`settings.enabled.first()`) for the same reason - it was the other
half of "the tile changes nothing," since it never checked the enabled flag at all before this fix.

## Round 3 — crash on the second instant popup, and the tile's real job

Both reported from the device, both root-caused rather than guessed at.

**The instant popup worked exactly once per process, then the app crashed** ("Something went wrong with
Word Lookup … this app has a bug"), after which only the selection menu still worked. `OverlayHost` held a
single `OverlayLifecycleOwner` for its whole life, but `dismiss()` drives that owner to `DESTROYED`, which
`LifecycleRegistry` treats as terminal - the next `show()` called `start()` on it and threw. One
`OverlayHost` serves the entire accessibility service, so card #2 killed the process the service lives in.
Fixed by making the owner per-attachment (created in `ensureViewAttached`, dropped in `dismiss()`), so every
card gets a fresh, legal lifecycle. `position()`'s `updateViewLayout` is now also wrapped - it runs from a
`post{}` that can land after a dismiss has already detached the view, which throws for the same
crash-the-process result.

**`TriggerMode` was never read by anything.** The Instant / Menu-only / Both radio buttons wrote to
DataStore and no trigger path ever consulted it - inherited from v0.1.0 and faithfully cloned. So the
instant overlay could not be turned off at all, and the tile (which toggled the *master* `enabled` flag
instead) was the only switch, an all-or-nothing one. Now `SelectionAccessibilityService` checks
`instantEnabledState` and `ProcessTextActivity` checks for `INSTANT`-only, so the setting does what it says.

**The tile now switches the instant popup, not the whole app** — which is what a one-tap toggle is
actually for. `Settings.toggleInstant()` flips between `BOTH` and `MENU_ONLY`, so turning instant off
leaves "Word Lookup" in the selection menu rather than disabling the app. Tile label stays the app name
(the manifest and `Permissions.requestAddTile` share `@string/tile_label`, and it has to be findable in the
QS picker); the subtitle reads "Instant on"/"Instant off". `TriggerModeToggleTest` covers the decision
table - including toggling from `INSTANT`, the state where a naive "write the opposite of the snapshot"
toggle would no-op.

## Bugs — fixed, and one won't-fix

All five were verified against a real screen recording of the shipped app (94s, both trigger paths) plus
direct testing on a Samsung S24. Fixed under the same v1 label per standing instruction —
`versionName` stays `0.1.0`; these are not a "v2".

1. **Fixed — no retry on a malformed model reply.** `GeminiProvider.parseModelJson` and the
   empty-translation branch now throw `MalformedReplyException` (a `LookupFailedException` subclass, so
   every existing `catch (LookupFailedException)` still works). `lookup()` catches it and retries
   `executeOnce` exactly once, mirroring the desktop's `MalformedReply`. Covered by
   `GeminiProviderTest` using a fully faked `OkHttpClient` (an `Interceptor`, no real network) so the
   retry count is asserted directly.
2. **Fixed — synonyms repeating the meaning.** `synonymsField(obj, meaning, headword)` now drops entries
   that case-insensitively match `meaning` or the headword, plus de-dupes case-insensitively. Fixes the
   recorded case: `autodidactic` / meaning "self-taught" / synonyms "self-taught, independent, untutored".
3. **Fixed — `exampleEn` never rendered.** Added to `LookupCard.ResultContent` (after synonyms, before the
   divider) and to `RegisterScreen.RegisterRow`. No schema/wire change — the field was always stored.
4. **Fixed — Quick Settings tile did nothing.** Three real bugs, not one - see "Hardening, latency, and
   accuracy work" above for the stale-state root cause (the actual "first tap appears to do nothing" bug)
   and its fix. The other two: `ProcessTextActivity` (the menu-driven trigger) never checked the enabled
   state at all — inherited from the original — so toggling the tile off only ever silenced the
   accessibility trigger while the menu path kept working, making the tile look completely inert to anyone
   using the menu (which the recording shows heavily); it now shows a "Word Lookup is off" message instead
   of running the lookup when off. And `LookupTileService.syncTile` called `Tile.setSubtitle`
   unconditionally — that API is 29+ (confirmed against `android-sdk/platforms/android-35/data/api-versions.xml`)
   while `minSdk` is 24, so a device on API 24–28 would `NoSuchMethodError` there and the tile would die
   silently; now guarded by `Build.VERSION.SDK_INT >= 29` (not the S24's symptom, but a real latent bug at
   `minSdk`, fixed regardless). Also fixed: `LookupTileService.scope` was never cancelled in `onDestroy`.
5. **Fixed — selecting one word sometimes translated the whole paragraph.** Root cause in
   `SelectionAccessibilityService`: when an accessibility event carries no selection indices (`-1`,
   routine in WebViews and reader apps), the old code returned the *entire node's text* as the "selection".
   Extracted into `service/SelectionExtractor.kt` (framework-type-free, so it's actually unit-testable —
   `AccessibilityEvent` can't be constructed in a JVM test, which is exactly why this had no coverage
   before) with a strict ladder: explicit event range → node's own `textSelectionStart/End` → otherwise
   only accept something short enough to plausibly be a hand-made selection (≤80 chars/≤8 words), else
   `null`. See `SelectionExtractorTest` for the regression case.
6. **Won't fix — "Word Lookup" hidden behind the PDF/browser selection-toolbar overflow.** Android decides
   `ACTION_PROCESS_TEXT` menu ordering and overflow; an app has no control over its own position in it.

## Round 4 — capture outside the browser, and the language picker

Both reported from the S24.

**Text capture only worked in browsers.** Two independent causes, both fixed:
- `SelectionExtractor` read the selection range out of `event.text` only. WebViews (so every browser)
  populate that; many native views leave it empty and fill the *source node's* text instead, so the range
  had nothing to index into and the extractor returned null. It now tries `event.text` first and falls back
  to the node's text for the same range. `SelectionExtractorTest` covers the empty/blank-event-text case.
- `res/xml/accessibility_service_config.xml` carried v0.1.0's flags verbatim
  (`flagRetrieveInteractiveWindows` alone). Views not marked important-for-accessibility are excluded from
  events unless `flagIncludeNotImportantViews` is set - which excludes a great deal of ordinary native-app
  text. Now `flagRetrieveInteractiveWindows|flagIncludeNotImportantViews` (`0x42`).

**Changing the language froze or did nothing.** Also two causes:
- The `OutlinedTextField` inside `ExposedDropdownMenuBox` had no `Modifier.menuAnchor(...)`, which is what
  attaches the field to the menu. Without it Material3 has nothing to anchor against and the tap does
  nothing at all. Now `MenuAnchorType.PrimaryNotEditable`.
- `MainViewModel.hasGeminiKey()`/`hasGithubPat()` were plain functions called *directly from composition*,
  so every recomposition of the setup screen did an `EncryptedSharedPreferences` read - Keystore + Tink +
  disk - on the main thread. They are `StateFlow`s now, read once on `Dispatchers.IO`; writes moved to
  `saveKeys(...)`, also off the main thread.

## Known drift vs. the desktop app

- v0.1.0 has **no `synonymsNative` column/field** — that's a desktop-only v4 schema addition
  (`kannada_lookup/store.py:53`). Confirmed from jadx: `GistSyncClient`'s shared `Json` instance sets
  `ignoreUnknownKeys = true`, so a payload from a newer desktop client **round-trips safely** — the extra
  field is silently dropped, not an error. (This resolves what the binary-only analysis upstream of this
  clone could not determine.)
- The desktop's `sync.py` docstring claims it's "currently the only client of the format" — false, this
  app is the second client. Don't trust that docstring as current.

## Verifying a change

`tools/apk_inventory.py` is the acceptance test for "does this still match v0.1.0": it extracts manifest,
dependency versions, Room DDL, DAO SQL, the Gemini prompt, accessibility config, and the language list from
any APK. Build, then:

```bash
python tools/apk_inventory.py app/build/outputs/apk/release/app-release.apk -o reference/inventory-ours.json
python tools/apk_inventory.py --diff reference/inventory-original.json reference/inventory-ours.json
```

As of round 2, v0.1.0 parity is no longer the goal for the app's *behavior* (see "What this is"), only for
the platform surface - so the diff now covers fewer categories on purpose:

**Must still be clean, always** - a change here is a genuine regression, no exceptions: `manifest`,
`dependencies`. These describe what the app declares to the OS.

**`accessibility_config` - one intentional change.** `accessibilityFlags` is `0x42`, not v0.1.0's `0x40`:
`flagIncludeNotImportantViews` was added on top of `flagRetrieveInteractiveWindows`. Without it the service
only ever saw selections in browsers - views not marked important-for-accessibility are excluded by default,
and that describes a lot of ordinary text in native apps. Any *other* change to this category is still a
regression.

**Expected to differ, and why:**
- `dex.app_classes` — internal Compose-generated names (source was rewritten idiomatically, never decompiled
  line-for-line), plus every class round 2 added (`ScriptValidator`, `TranslationProvider`, `NetworkModule`,
  `MalformedReplyException`, `SelectionExtractor`, `AppContainer`-adjacent state on `WordLookupApp`).
- `dex.languages` — `+Swedish`, intentional since round 1.
- `dex.prompt_fragments` — the prompt now has field length caps, a `generationConfig` block, and two
  distinct retry-correction suffixes. None of this existed in v0.1.0; it's the accuracy/latency work itself.
- `dex.sql` — **the tool under-reports this one.** `apk_inventory.py`'s SQL regex only matches
  `SELECT|INSERT|UPDATE|DELETE|CREATE TABLE|DROP TABLE`, not `CREATE INDEX`, so the new `createdAt` index
  (Room schema v2, see `data/cache/LookupDatabase.kt`) doesn't show up as a diff line even though it's a
  real, intentional schema change - verify it via `app/schemas/.../2.json` instead, not the diff.
- `ui_text` — a mix of kotlinx-coroutines/serialization library strings that shift with unrelated code-path
  changes (harmless, not worth chasing), plus real new strings ("Word Lookup is off", the overlay-permission
  Toast text) that are expected.

`tools/jadx_digest.py <file.java>` strips jadx boilerplate (Intrinsics null-checks, data-class
copy/equals/hashCode, `@Metadata` blobs) from a decompiled file so what's left is closer to the original
Kotlin — use it before reading anything under `reference/jadx/sources/` raw.

## Working rules for this directory

- `WordLookup-apk.zip` is read-only reference evidence — never repack or re-sign it.
- `reference/` (extracted APK, jadx output, inventory JSON, frame captures) is regenerable from the zip;
  don't hand-edit it, regenerate it.
- Claims about original v0.1.0 behavior must cite where they came from — the inventory diff, a specific
  `reference/jadx/sources/` file, or the screen recording — not "what a word-lookup app would probably do."
- New source code goes in `app/src/main/java/com/harish/wordlookup/`, matching the existing package layout.
