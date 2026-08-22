# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

A clean-room Kotlin clone of `com.harish.wordlookup` v0.1.0 — an on-device "select a word → get an AI
lookup card" tool — rebuilt from the shipped `WordLookup-apk.zip` (kept in this folder as the reference
artifact; never modify it). The original Android source was never on this machine; every file under
`app/src/` was reconstructed from the APK (binary AXML, `resources.arsc`, Room DDL, dex strings) and from
a jadx decompile at `reference/jadx/sources/` (full app package, `@Metadata` intact so original Kotlin
signatures and the exact Gemini prompt were recoverable, not guessed).

Three rounds of deliberate departure from the shipped app since the initial clone:
- **Swedish added as a 26th language**, closing a gap where the Android app (25 languages) lagged the
  desktop's list (26).
- **A hardening/latency/accuracy pass** (see "Hardening, latency, and accuracy work" below):
  script-validation retry ladder, a faster default model, single-flight + in-memory cache, a real
  debounce, connection warm-up, and fixes for several ways the app could crash or silently misbehave.
- **The GitHub-Gist sync feature removed entirely** (see "Sync removal" below): `data/sync/` and its
  `githubPat`/gist plumbing are gone. This app is a self-contained on-device cache now, not a client of
  the desktop's shared-gist format.

v0.1.0 byte-parity (manifest, dependencies, accessibility config, Room DDL/DAO SQL) is still verified on
every build — see "Verifying a change" — but the Gemini prompt and generated SQL now *intentionally* differ
from the original, and manifest/dependencies carry the one intentional removal documented there; parity was
the goal for the initial clone, not for this app going forward.

**Companion desktop app**: `H:\Claude Project Files\Mouse automation project 1` (Python, `kannada_lookup`
package). This Android app was built alongside it and originally synced with it through one private GitHub
Gist; that sync path is now removed from this app (see "Sync removal" below) — the two are independent
again. Its `README.md:162` names this Android app. Reach for that repo for the *reasoning* behind other
shared behavior (the Gemini prompt's script-validation rules) — this repo has similar behavior but sparer
comments, since v0.1.0 predates several things the desktop later grew (see "Known drift" below).

## Commands

```bash
source tools/env.sh          # Bash tool: puts JDK 17 / SDK / Gradle / jadx on PATH for this session
# or, PowerShell:  . .\tools\env.ps1

./gradlew :app:assembleRelease           # build (signed with the debug key, like the original)
./gradlew :app:testReleaseUnitTest       # 55 JVM unit tests, all offline (no device, no network, no Room)
adb install -r app/build/outputs/apk/release/app-release.apk

python tools/apk_inventory.py <apk> -o out.json      # extract manifest/deps/DDL/SQL/prompt/languages
python tools/apk_inventory.py --diff a.json b.json   # diff two inventories — the acceptance test
python tools/gen_lang_icons.py                       # regenerate the 26 per-language launcher icons (round 6)
```

The toolchain lives in `C:\tools` (JDK 17.0.20, `android-sdk` with platform-35 + build-tools 35.0.0,
Gradle 8.9, jadx 1.5.1) — none of it is on `PATH` by default, hence `tools/env.sh`/`env.ps1`.
`gen_lang_icons.py` additionally needs `fontTools` (`pip install fonttools`) and the specific Windows
font paths hardcoded at its top (`Nirmala.ttc`, `arial.ttf`, `malgun.ttf`, `LeelaUIb.ttf`) — it was
written and run on the machine that authored round 6, not verified on any other machine.

**No full on-device walkthrough has happened in any session so far** - screenshots from the user's
own phone (round 7) are the first real-device data point this repo has had, and they already
surfaced three real bugs (see "Round 7" below: an oversized search field, three Settings buttons
that never got round 7's shadow-tile treatment, and an over-large, off-center launcher glyph) that
nothing short of an actual screen caught. Everything through inventory diff and 55 unit tests is
verified by build alone; a full `adb install` + walkthrough of every flow below still has not
happened. The user's test device is a Samsung S24 (Android 14+, One UI). Before trusting any of
this on that device, at minimum: onboarding
→ both trigger paths → card (check the English example now renders) → register → toggle the app off from
inside the app *and* from the tile, confirming both actually stop the menu-triggered path too → drag-select
across a sentence and confirm one lookup fires, not a burst → revoke "Display over other apps" and confirm
a Toast instead of a dead accessibility service. **Round 6 adds one more must-check, the highest-risk item
in this repo's history: switch the target language and confirm the launcher icon actually updates** (and
that One UI doesn't drop the home-screen shortcut in the process) — this has never been observed outside a
`pathData` raster proof on the build machine, never on a real launcher.

**Round 7 adds three more must-checks.** First: **install the round-7 APK over an existing install that
already has saved words**, not a fresh install — that's the only way to actually exercise the 3→4 Room
migration path rather than `createSql`, and the only way to catch a migration bug that would otherwise
silently wipe a real user's register. Second: **tap "Pause now" on a real banking app that currently
refuses to run**, to find out whether `disableSelf()` actually satisfies it — this was reasoned from how
banking-app fraud SDKs generally work, never confirmed against a specific bank on this or any device.
Third, from the same round's screenshot-driven follow-up (see "Round 7" below): **do a clean
uninstall-then-reinstall, not `adb install -r`**, before judging the launcher-icon fix — the
screenshot that prompted it showed a violet background the current build's assets cannot produce
(the per-language background is ink, `#FF16181C`, and every LAUNCHER-category component is a
per-language alias; nothing points at the base app's old violet `ic_launcher_background.xml` any
more), which points at either a stale build on the phone or launcher icon caching (Samsung One UI
is known for both) rather than a code bug - a clean install rules out both at once, a same-package
reinstall might not.

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
`data.NetworkModule` holds `GeminiProvider`'s shared `ConnectionPool`.

**`OverlayHost` is the trickiest piece.** A `WindowManager`-attached view has no Activity behind it, so
`OverlayLifecycleOwner` supplies lifecycle/viewmodel-store/saved-state by hand. Window flags
(`TYPE_APPLICATION_OVERLAY`, `FLAG_NOT_FOCUSABLE|FLAG_NOT_TOUCH_MODAL|FLAG_LAYOUT_IN_SCREEN`), gravity
(`TOP|START`), positioning (anchor.left, anchor.bottom + 12dp, clamped to screen), and the 6s auto-dismiss
were all recovered from jadx byte-exact — don't change them without re-checking
`reference/jadx/sources/com/harish/wordlookup/ui/OverlayHost.java`.

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
key read forever. `GeminiProvider` - `response.body!!` replaced with a checked read.

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
- `MainViewModel.hasGeminiKey()` was a plain function called *directly from composition*, so every
  recomposition of the setup screen did an `EncryptedSharedPreferences` read - Keystore + Tink + disk - on
  the main thread. It's a `StateFlow` now, read once on `Dispatchers.IO`; the write moved to
  `saveGeminiKey(...)`, also off the main thread.

## Design system (round 5)

`DESIGN.md` documents the app's color/typography/spacing/component tokens, sharing its palette with
the desktop app's own DESIGN.md (`primary` #14171C, `tertiary`/brand #7150F0, Inter + Noto Sans
Kannada). `app/src/main/java/com/harish/wordlookup/ui/theme/` (`Color.kt`, `Type.kt`, `Theme.kt`) wires
those tokens into a real Compose `ColorScheme`/`Typography` - the app previously ran on stock Material3
defaults (a generic purple, not the brand's #7150F0) despite `LookupCard`'s own colors already being
byte-exact from v0.1.0. `MainActivity` now wraps its content in `WordLookupTheme` instead of a bare
`MaterialTheme`.

Verify with `npx -p @google/design.md@0.4.0 designmd lint DESIGN.md` (the `design.md` bin name collides
with npx's own binary-name inference from the package name containing a dot, so the `-p ... designmd`
form is what actually works - plain `npx --yes @google/design.md lint DESIGN.md` hangs). Current result:
0 errors, 7 warnings - all `orphaned-tokens` on the `*-dark` color set, which is intentional and
documented in DESIGN.md's Colors section ("reserved for a future system-theme pass; not yet wired into
the Compose theme"). Two real `contrast-ratio` findings from an earlier lint pass (`tertiary` text on a
light fill measuring 4.11-4.45:1, under WCAG AA's 4.5:1) were fixed by using `tertiary-hover` - the same
accent hue, one shade darker, already in the palette as the pressed-state color - for text-on-light
contexts (`button-secondary`, `chip-status-on`); the corresponding `Theme.kt` roles
(`onPrimaryContainer`/`onSecondaryContainer`) and `MainActivity`'s `OutlinedButton` call sites
(`secondaryButtonColors()`) were updated to match, not just the markdown.

## Sync removal (round 5)

The GitHub-Gist sync feature — this app's client half of the desktop's shared-cache format — is gone,
per explicit instruction. Deleted outright: `data/sync/` (`SyncEntry`, `SyncPayload`, `GistSyncClient`,
`SyncMerger`, `SyncWorker`) and its test (`SyncMergerTest`). Pruned from surviving files rather than left
as dead code:
- `ApiKeyStore` — `githubPat`/`gistId` properties and their prefs keys.
- `Settings` — `lastSyncAt` and `setLastSyncAt()`.
- `MainViewModel` — `hasGithubPat`, `lastSyncAt`, and the GitHub-PAT half of `saveKeys(...)` (now
  `saveGeminiKey(key: String)`).
- `MainActivity`/`SetupScreen` — the "Sync" `SectionCard`, the GitHub-token input field and its help text,
  and the `hasGithubPat`/`githubPat` parameters threaded through both.
- `LookupDao`/`LookupEntity` — the soft-delete/tombstone machinery (`deleted`, `updatedAt`,
  `allIncludingDeleted()`, `upsertAll()`) existed solely so a local delete could survive as a marker until
  it propagated to the shared gist. With nothing left to reconcile against, `delete()` is a real
  `DELETE FROM lookups` again. Room schema bumped to version 3
  (`fallbackToDestructiveMigration`, so no migration path needed — see "Database" below; round 7
  is the first version bump that actually needed one, see "Round 7").
- `AndroidManifest.xml` — `RECEIVE_BOOT_COMPLETED`, `WAKE_LOCK`, `FOREGROUND_SERVICE` dropped; nothing in
  the app uses them once `WorkManager` (the only thing that needed periodic-work-survives-reboot) is gone.
- `app/build.gradle.kts` / `gradle/libs.versions.toml` — `androidx.work:work-runtime-ktx` dependency
  removed, confirmed unused by grep before removal.
- `WordLookupApp.onCreate()` — a dead `createNotificationChannel()` call creating a `"word_lookup_sync"` /
  "Background sync" channel that nothing posted to once `SyncWorker` (the only thing that would have)
  was deleted; found during the security/consistency pass, not the initial sweep.

`kotlinx-serialization-json` stays: `GeminiProvider` uses it independently to parse the model's JSON reply,
unrelated to the sync wire format.

## Round 6 — monochrome repalette, the register as the lookup card, language-reactive icons

Three requests from the same session, executed as one pass since they touch the same files.

**Repalette, black/grey/white with one signal accent.** Round 5's violet redesign was rejected
outright — the user asked for a Dieter Rams direction instead. `ui/theme/Color.kt`/`Theme.kt`
replaced entirely: cool-neutral greys (chosen to sit correctly under the lookup card's own
unchanged `#EDF2FB` gradient), plus exactly one signal accent (`#C4440B` light / `#E86A2A` dark)
reserved strictly for on/active state — the enabled switch's track, a granted permission's status
dot. Nothing else reads it. Both a light and a dark `ColorScheme` are wired into `WordLookupTheme`
via `isSystemInDarkTheme()` — round 5 only ever built the light one; the dark tokens documented in
DESIGN.md sat unused until now. Rounded corners were **kept** (16/12/8/4dp, unchanged) — restraint
went into color and elevation, not shape. `SectionCard`/`NavigationCard`/register entries all
switched from `tonalElevation` to an explicit 1dp hairline (`Surface(border = ...)`) — structure is
drawn, never simulated. `DESIGN.md` was rewritten to match and re-lints at 0 errors, 0 warnings
(down from the prior 7 orphaned-token warnings — the dark tokens are real components now, not
just declared). The companion desktop app's own DESIGN.md/palette is untouched; see "Known drift"
below for why that's now a bigger, deliberate gap than before.

**The word register renders each saved lookup as the popup card, not a second layout.**
`LookupCard.kt`'s `ResultContent` was extracted into `internal fun LookupResultBody(result)` —
the popup (`LookupCard`) and the register (`RegisterScreen.RegisterEntryCard`) both call it inside
their own wrapper (320dp fixed vs. `fillMaxWidth()`), so a saved word and a live lookup are
genuinely the same rendering, not two components that happen to look similar. One real trap here:
`OverlayHost` never wraps `LookupCard` in a `MaterialTheme`, so `LookupResultBody`'s
`MaterialTheme.typography.*` calls resolve against Compose's *stock* M3 type scale on the popup
path — but the register renders inside `WordLookupTheme`. Without correcting for that, the "same"
card would render at two different sizes. `RegisterEntryCard` wraps its copy in
`MaterialTheme(typography = androidx.compose.material3.Typography())` specifically to match.
`LookupCard.kt` itself, and `res/values/colors.xml`, are still completely unmodified — same
constraint as round 5, still holds.

**Icons follow the selected language.** All three - the in-app brand mark, the Quick Settings
tile, and the launcher icon - now show the active language's own script glyph
(`data.Languages.ALL[i].glyph`) on an ink ground, switching live when the language changes.
- **In-app mark** (`ui/components/BrandMark.kt`): trivial, just draws the glyph as `Text`.
- **Tile** (`service/LookupTileService.kt`): renders the glyph onto a `Bitmap` via `Canvas`/`Paint`
  at `syncTile`-time, now collecting `WordLookupApp.targetLanguageState` alongside
  `instantEnabledState`. No new assets, no manifest change.
- **Launcher icon** (the invasive one, chosen deliberately by the user after being told the cost):
  `tools/gen_lang_icons.py` extracts real vector outlines from installed fonts via `fontTools`
  (`SVGPathPen` + `TransformPen`, centered and y-flipped into a 108×108 adaptive-icon viewport) and
  writes 26 `res/drawable/ic_lang_<code>.xml` + 26 `res/mipmap-anydpi-v26/ic_launcher_<code>.xml`,
  plus one shared `ic_launcher_lang_background.xml` (ink). **The original, v0.1.0-recovered
  `ic_launcher_background.xml`/`ic_launcher_foreground.xml` are untouched** — same "recovered
  byte-exact, don't touch" discipline as `Permissions.kt`/`OverlayHost.kt`; the per-language assets
  are purely additive. `AndroidManifest.xml` gained 26 `<activity-alias>` entries
  (`.ui.LauncherAlias<Code>`, e.g. `LauncherAliasKn`), each `targetActivity=".ui.MainActivity"`
  with its own icon; only the alias matching `Settings.targetLanguage`'s default (Kannada) ships
  `enabled="true"`, the rest `false`. `MainActivity`'s own `<activity>` intent-filter dropped its
  `LAUNCHER` category (kept `MAIN`, now inert) since the aliases are the real entry points.
  `data/LauncherIcon.kt` (owned by `WordLookupApp.launcherIcon`, called from
  `MainViewModel.setLanguage`) calls `PackageManager.setComponentEnabledSetting` to flip which
  alias is enabled — **the new one is always enabled before any other is disabled**; the reverse
  order risks a window where zero LAUNCHER components are enabled, which would drop the app off the
  home screen with no way back in.
  - **Indonesian's glyph has no launcher icon.** Its display glyph is Balinese `ᬅ` (U+1B05) —
    every font on the machine that generated these assets that claims cmap coverage for that
    codepoint renders an empty box (verified by rendering, not just checking `cmap` — a cmap entry
    existing is not proof of real outline data). The launcher icon for `id` falls back to the Latin
    text "ID" instead. The tile and in-app mark are unaffected: they render live on-device, where
    Android's own font fallback stack does carry real Balinese support.
  - **This is the one deliberate exception to "`manifest` must stay clean"** — see "Verifying a
    change" below, updated accordingly.

## Round 7 — native synonyms, the settings hub, shadow-based tiles, pausing for banking apps

Four requests from one session: port a further-iterated design pass into real Kotlin, add
native-language synonyms to the lookup card, split Home's permissions/account clutter into its own
screen, and give the user a real way to satisfy banking apps that refuse to run alongside this app.

**`synonymsNative` — the first real Room migration in this app's history.** `LookupResult` and
`LookupEntity` both gained `synonymsNative: String = ""` (appended last, so the positional
`LookupEntity.toResult()` call didn't silently misassign a field). Room schema bumped 3 → 4 with an
actual `Migration(3, 4)` (`ALTER TABLE lookups ADD COLUMN synonymsNative TEXT NOT NULL DEFAULT ''`)
registered via `addMigrations(...)` ahead of the existing `fallbackToDestructiveMigration()`
backstop — every prior version bump in this app relied solely on the destructive fallback, which
was an honest tradeoff while there was no installed base with real data (see round 5's note above);
there now is, and a destructive migration on this bump would have silently wiped the user's saved
word register. `GeminiProvider`'s prompt gained a `synonyms_native` field (2–3 target-language
synonyms for the translation, not the English synonyms translated word-for-word — the prompt says
so explicitly). `synonymsField(obj, meaning, headword, key)` — previously hardcoded to the JSON key
`"synonyms"` — took a `key` param (defaulted last, so all six existing test call sites kept
compiling unchanged) so the same de-dup/parse logic serves both fields. `GENERATION_MAX_OUTPUT_TOKENS`
rose 200 → 320: non-Latin scripts cost more tokens per character than English, and a second
native-script field pushed replies close enough to the old cap to risk truncating the JSON mid-object
— the single most likely regression risk in this round, called out explicitly rather than
discovered later. `backfillEnglishFields` deliberately does **not** cover `synonymsNative` — it's a
target-language field, same treatment as `translation`/`exampleNative`: only the script-check-passing
attempt's own value counts, so it can come back blank after a script retry rather than carrying over
a wrong-script attempt's value. `synonymsNative` also is not `ScriptValidator`-checked, matching the
existing precedent for `exampleNative` (also native-script, also unchecked — only `translation`
gates the retry ladder). Rendered in both card hosts (`LookupCard.LookupResultBody`) as a bracketed
suffix after the translation, e.g. "ಕ್ಷಣಿಕ (ನಶ್ವರ, ಕ್ಷಣಭಂಗುರ)" — the user confirmed this belongs on the
popup too, not just the register, resolving what round 6 had left as an open question. English
`synonyms` also picked up brackets, in both hosts, for visual consistency with the new native line.

**The settings hub.** `Screen.SETTINGS` used to mean "the language/API-key editor" (opens
`SetupScreen`). That destination is now `Screen.EDIT_SETUP`; `Screen.SETTINGS` is a new screen
(`ui/SettingsScreen.kt`) holding the Access card, the banking-pause card, and the
language/API-key/App-Info/tile-add actions — everything that used to sit loose on Home, all at
once. Home keeps only the header, the summary card, "How it opens," and two navigation cards
("Word register", "Settings"). The `overlayGranted`/`accessibilityGranted` permission-recheck
`DisposableEffect` moved from `HomeScreen` up to `AppRoot`, so it keeps running regardless of which
screen is on top — needed for the pause action below to be reflected the moment the user returns to
the app, not only while Home happens to be composed.

**Pause for banking.** Confirmed root cause, not guessed: banking-app fraud SDKs refuse to run when
they detect an enabled `AccessibilityService` and/or `SYSTEM_ALERT_WINDOW`, since both are exactly
what an overlay-attack trojan needs and the SDK can't distinguish intent from permission flags. The
app's existing toggles (master switch, the Quick Settings tile) never helped with this — both only
flip in-app flags `SelectionAccessibilityService` reads on its own; the service stays listed in
`Settings.Secure.enabled_accessibility_services` regardless, which is what a bank app actually
checks. `SelectionAccessibilityService` gained a companion `instance` reference (set in
`onServiceConnected`, cleared in `onDestroy`/`onUnbind`) and `fun pause(): Boolean` that calls the
real `AccessibilityService.disableSelf()` — this genuinely deregisters the service with the OS.
Android does not let an app re-enable its own accessibility service; the Settings screen's copy
says so directly rather than implying the pause is a simple toggle, and points at
`Permissions.accessibilitySettingsIntent()` (unchanged, pre-existing) for the manual re-enable.
`disableSelf()` does not touch `SYSTEM_ALERT_WINDOW` — a bank that blocks on overlay permission
alone will still refuse; not promised as a fix for every bank, only the accessibility half of it.

**Design port (shadow-based tiles, pure white, gradient/glow).** `background` (light) went
`#F2F3F5` → `#FFFFFF` — background and surface are now the same value in light mode, which is fine
because tile structure no longer depends on their contrast. `SectionCard`, `NavigationCard`, and
the register's entry card all dropped their `BorderStroke` in favour of `Surface(shadowElevation =
...)`; `OutlinedTextField` became `TextField` with a `sunken` fill and transparent focus/unfocus
indicators (`ui/components/FilledFieldColors.kt`, shared by the three text fields in the app) —
`outline`/`outline-strong` are narrowed to row-dividers inside a tile and the segmented control's
selected-key edge, not a tile's own boundary any more. `SummaryCard` and `BrandMark` both gained an
ink-to-near-black gradient plus a matching glow shadow, replacing their flat fill. Home's tile stack
moved from per-tile margins to `Arrangement.spacedBy(16.dp)`, so the gap between tiles is
mathematically even instead of depending on CSS-style margin collapsing between differently-padded
neighbors. Every em dash in user-facing copy was replaced with a colon or a period. The API-key
helper text's `aistudio.google.com` mention is now a real tap target (`LinkAnnotation.Url`,
Compose 1.7) opening `https://aistudio.google.com/apikey` directly.

**Screenshot-driven follow-up, same round.** Three real bugs, found only because the user finally
sent actual phone screenshots - none of them showed up in a build or a test.
- **Register search field was oversized.** `RegisterScreen.kt`'s `TextField` used
  `Modifier.heightIn(min = 48.dp)` - a floor, not a cap - so M3's filled `TextField` rendered at its
  own larger natural height, tall enough for the placeholder to visibly wrap to two lines. Fixed to
  a fixed `Modifier.height(52.dp)`, which actually constrains the internal decoration box instead of
  just flooring it.
- **Settings' bottom three actions never got round 7's border→shadow treatment.** They were still
  `OutlinedButton`, visibly inconsistent with the Access/Pause-for-banking cards directly above them
  on the same screen. New `SettingsActionTile` private composable in `SettingsScreen.kt` - same
  `shadowElevation = 4.dp` `SectionCard` uses - replaces all three.
- **Launcher glyph read too large and off-center.** `tools/gen_lang_icons.py`'s `TARGET_SPAN` (the
  glyph's longest dimension, out of the 108-unit viewport) was `60` - technically inside Android's
  ~66/108 adaptive-icon safe circle, but close enough to it that the generator's bbox-centroid
  centering (correct in general, but not the same as true optical centering for an asymmetric glyph)
  became visible. Lowered to `44`; all 26 icons regenerated. The centering *method* didn't change -
  shrinking the span is what makes the residual error imperceptible.
- **Font: bundled Poppins, not literal "Google Sans."** The user asked for Google Sans specifically;
  it's Google's proprietary internal typeface (Product Sans), never published to Google Fonts, not
  licensed for third-party bundling - not something to source from an unofficial copy. Poppins (SIL
  OFL, fetched from Google Fonts' own repo) is the legitimate substitute the user chose instead: real
  `.ttf` files at `app/src/main/res/font/poppins_{regular,medium,semibold}.ttf`, license text at
  `THIRD_PARTY_LICENSES.md`, wired as `Poppins` in `ui/theme/Type.kt` and applied to every style in
  `WordLookupTypography` except `BodyNative` - Poppins has no Kannada/Devanagari/CJK glyphs, so
  native-script text is left on the system default, where Android's own per-character font fallback
  already substitutes the right script font (same mechanism the file's own comment already documents
  for the existing Inter/Noto split - only the Latin family actually changed).

## Known drift vs. the desktop app

- v0.1.0 (and this app, before this round) had **no `synonymsNative` column/field** — that's a
  desktop-only v4 schema addition (`kannada_lookup/store.py:53`). This app no longer syncs with the
  desktop at all, so the question of whether that field round-trips is now moot rather than resolved.
- The desktop's `sync.py` docstring claims it's "currently the only client of the format" — that was
  already false while this app synced with it, and is trivially true again now that this app doesn't.
  Historical note only; don't design around it.
- **Round 6: the visual palette itself now diverges.** This app is monochrome (black/grey/white +
  one signal accent); the desktop keeps its own violet DESIGN.md unchanged. Before round 6 the two
  apps' DESIGN.md docs described the same palette by design ("shares its palette and typography...
  so the two feel like one product seen through two windows"); that line was removed from this
  app's DESIGN.md because it stopped being true. This was an explicit user request, not drift from
  neglect — don't "fix" the two back into sync without being asked to.

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

**Must still be clean, always** - a change here is a genuine regression, no exceptions, beyond the
documented intentional changes below in `manifest`, `dependencies`. These describe what the app
declares to the OS.

**`accessibility_config` - one intentional change.** `accessibilityFlags` is `0x42`, not v0.1.0's `0x40`:
`flagIncludeNotImportantViews` was added on top of `flagRetrieveInteractiveWindows`. Without it the service
only ever saw selections in browsers - views not marked important-for-accessibility are excluded by default,
and that describes a lot of ordinary text in native apps. Any *other* change to this category is still a
regression.

**`manifest` - intentional changes, round 5.** `RECEIVE_BOOT_COMPLETED`, `WAKE_LOCK`, `FOREGROUND_SERVICE`
are gone (sync removal, see "Sync removal" above) - nothing in the app needs boot-survival or wake locks
once `WorkManager` is gone. Three new `<application>` attributes were added as part of the same round's
security pass: `android:networkSecurityConfig` (`res/xml/network_security_config.xml` - disables cleartext
HTTP explicitly rather than relying on minSdk 24's pre-API-28 cleartext-by-default), and
`android:fullBackupContent`/`android:dataExtractionRules` (`res/xml/backup_rules.xml` +
`data_extraction_rules.xml` - exclude `ApiKeyStore`'s two SharedPreferences files from Auto Backup, closing
a real gap where the Keystore-corruption plaintext-fallback path could otherwise upload the Gemini key in
cleartext to the user's cloud backup).

**`manifest` - intentional changes, round 6.** 26 `<activity-alias>` components were added (one per
language, `.ui.LauncherAlias<Code>`), and `MainActivity`'s own intent-filter lost its `LAUNCHER`
category (kept `MAIN`, now inert) since the aliases are the real launch points now. See "Round 6" above
for the full design and why the app never has zero enabled LAUNCHER components. This is the first round
where `manifest` intentionally isn't 100% parity-clean beyond the two-attribute-set exceptions above -
diff it expecting these 26 additions, not zero.

Any *other* permission, component, or attribute change beyond the three documented rounds above (5's
security attributes, 6's launcher aliases) is still a regression.

**`dependencies` - one intentional change.** `androidx.work:work-runtime-ktx` is gone as of round 5, for
the same reason - taking `androidx.lifecycle:lifecycle-livedata`/`lifecycle-livedata-core-ktx` down with it
transitively (confirmed via `--diff`; nothing in this app used LiveData directly, WorkManager pulled it in).
Any *other* dependency change is still a regression.

**Expected to differ, and why:**
- `dex.app_classes` — internal Compose-generated names (source was rewritten idiomatically, never decompiled
  line-for-line), plus every class round 2 added (`ScriptValidator`, `TranslationProvider`, `NetworkModule`,
  `MalformedReplyException`, `SelectionExtractor`, `AppContainer`-adjacent state on `WordLookupApp`), minus
  every `data.sync.*` class round 5 removed. Round 6 adds `data.LauncherIcon` and the `ui.components.*`
  package (`BrandMark`, `SectionCard`, `SegmentedControl`, `PermissionStatusRow`, `NavigationCard`,
  `OnboardingProgress`), and removes `ui.components.StatusChip` (round 5's status pill has no monochrome
  equivalent — a granted permission is now a status dot, not a filled chip). Round 7 adds
  `ui.SettingsScreen`, `ui.components.FilledFieldColors`, and
  `data.cache.LookupDatabaseKt$MIGRATION_3_4$1`.
- `dex.languages` — `+Swedish`, intentional since round 1.
- `dex.prompt_fragments` — the prompt now has field length caps, a `generationConfig` block, and two
  distinct retry-correction suffixes. None of this existed in v0.1.0; it's the accuracy/latency work itself.
  Round 7 adds the `synonyms_native` field and extends the script-correction retry suffix to name it
  alongside `example_native`.
- `dex.sql` — **the tool under-reports index/alter changes, but not column changes.** `apk_inventory.py`'s
  SQL regex only matches `SELECT|INSERT|UPDATE|DELETE|CREATE TABLE|DROP TABLE`, not `CREATE INDEX` or
  `ALTER TABLE`, so the new `createdAt` index (Room schema v2) doesn't show up as a diff line even though
  it's a real, intentional schema change - verify it via `app/schemas/.../2.json` instead, not the diff.
  Schema v3 (round 5) dropped the `deleted`/`updatedAt` columns the sync tombstone pattern needed - check
  `app/schemas/.../3.json` for that one instead. **Schema v4 (round 7, `synonymsNative`) is the exception
  that *does* show up** - Room's compiled `CREATE TABLE`/`INSERT OR REPLACE` string constants both changed
  shape (one new column, one new `?` placeholder), and those are exactly what the regex matches; the
  `ALTER TABLE` migration statement itself still only surfaces under `ui_text`, not `dex.sql`, for the
  same under-matching reason. Current highest schema version: 4 (`app/schemas/.../4.json`).
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
