package com.harish.wordlookup.ui

/**
 * SETTINGS is the permissions/account hub reached from Home's "Settings" nav
 * card. EDIT_SETUP is the pre-existing language/API-key editor (the
 * SetupScreen composable) - a different destination, now reached from a
 * button *inside* SETTINGS rather than directly from Home.
 */
enum class Screen { HOME, REGISTER, SETTINGS, EDIT_SETUP }
