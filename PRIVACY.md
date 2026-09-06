# Privacy Policy for Word Lookup

Last updated: 2026-08-28.

Word Lookup (Android) looks up words you select, on your device. This page states plainly what data it touches, what leaves your phone, and what stays on it.

## What leaves your device

**Selected text, on every lookup.** When you look up a word, the app sends the text you selected plus your chosen target-language name to Google's Gemini API (`generativelanguage.googleapis.com`), under **your own** API key. See [`GeminiProvider.kt:138-161`](app/src/main/java/com/harish/wordlookup/data/GeminiProvider.kt:138) for the exact request. This is the only way the app produces a result.

**Google's free-tier terms.** If you're using the default free Gemini API tier, Google's own terms mark free-tier content as usable to improve their models. That's Google's policy, not this app's; stated here so it's not buried in a comment. If that matters to you, Gemini's paid tier has different data-use terms; see [aistudio.google.com](https://aistudio.google.com) for current terms.

## What stays on your device

- **Your API key**, stored in an encrypted preferences file (AndroidX Security `EncryptedSharedPreferences`, AES-256), see [`ApiKeyStore.kt`](app/src/main/java/com/harish/wordlookup/data/ApiKeyStore.kt). It's never transmitted anywhere except as the auth header on your own Gemini requests.
  - **One honest edge case**: on some devices, Android's encrypted-storage layer can fail after a backup-restore onto different hardware. If that happens, the app falls back to a plain (unencrypted) preferences file rather than breaking entirely (see [`ApiKeyStore.kt:17-24`](app/src/main/java/com/harish/wordlookup/data/ApiKeyStore.kt:17)). This is a rare fallback path, not the normal case, but you should know it exists.
- **Your lookup history**, cached locally on-device so repeat lookups are instant and don't touch the API again.
- **Your settings** (target language, trigger mode).

## Optional permissions

**Accessibility service (off by default, opt-in).** Only needed for the instant, no-tap lookup trigger. The app works fully through the text-selection menu without it. If you turn it on, the service can see the text you select system-wide (that's how the instant trigger works); it does not read, store, or transmit anything else on your screen. See the in-app consent screen shown before this permission is ever requested, and Android's own Accessibility settings page for the same disclosure.

**Display over other apps.** Needed to show the lookup card as a floating window over whatever you're reading.

## What this app does not do

- No telemetry, analytics, or crash reporting of any kind.
- No data is sent to Word Lookup's author, or to any server other than Google's Gemini API.

## Third parties

- **Google Gemini API**: receives selected text + target language per lookup. See [Google's Gemini API terms](https://ai.google.dev/gemini-api/terms) for their current data-handling policy.

## Deleting your data

Uninstalling the app removes your local lookup history, settings, and stored key together. Anything already sent to Google as part of a completed lookup is governed by Google's own retention policy, not this app.

## Changes to this policy

This file is versioned in the repository alongside the code. Check the commit history for what changed and when.
