package com.harish.wordlookup.ui

/**
 * SETTINGS is the permissions/account hub reached from Home's "Settings" nav
 * card. EDIT_SETUP is the pre-existing language/API-key editor (the
 * SetupScreen composable) - a different destination, now reached from a
 * button *inside* SETTINGS rather than directly from Home.
 *
 * REVIEW (round 8) is reached only from REGISTER's bottom-docked quiz bar,
 * never from Home - see CLAUDE.md's "Round 8" section for why the quiz
 * lives with the list it quizzes from. Back from REVIEW returns to
 * REGISTER, not HOME, since that is where it was entered from.
 *
 * ACCESSIBILITY_CONSENT is reached only from SETTINGS' "Accessibility
 * service" row, and its back target is always SETTINGS - never HOME -
 * same idiom as REVIEW above.
 *
 * DIGEST (round 10) is reached from Home's "This week" nav card or the
 * Friday digest notification; back always returns to HOME.
 *
 * LANGUAGE (round 10) is reached only from SETTINGS' "Target language" row;
 * back always returns to SETTINGS. A separate full-screen glyph grid from
 * the dropdown still used inside EDIT_SETUP's onboarding/edit flow - see
 * CLAUDE.md's "Round 10" section for why that dropdown was left alone.
 *
 * TRY_DEMO (round 10) is reached only from Home's "Try a lookup" nav card;
 * back always returns to HOME. Entirely local/hardcoded data - not a real
 * lookup, no Gemini call.
 *
 * WIDGET_PREVIEW (round 10) is reached only from SETTINGS' "Preview widgets
 * and tile" row; back always returns to SETTINGS. A static illustration, not
 * a real AppWidgetProvider.
 */
enum class Screen {
    HOME, REGISTER, SETTINGS, EDIT_SETUP, REVIEW, ACCESSIBILITY_CONSENT,
    DIGEST, LANGUAGE, TRY_DEMO, WIDGET_PREVIEW,
}
