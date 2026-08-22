package com.harish.wordlookup.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "settings")

/** Plaintext app preferences. Secrets live in [ApiKeyStore], not here. */
class Settings(private val context: Context) {

    private object Keys {
        val TARGET_LANGUAGE = stringPreferencesKey("target_language")
        val ENABLED = booleanPreferencesKey("enabled")
        val TRIGGER_MODE = stringPreferencesKey("trigger_mode")
        val ONBOARDING_DONE = booleanPreferencesKey("onboarding_done")
    }

    val targetLanguage = context.dataStore.data.map { it[Keys.TARGET_LANGUAGE] ?: Languages.DEFAULT.name }
    val enabled = context.dataStore.data.map { it[Keys.ENABLED] ?: true }
    val triggerMode = context.dataStore.data.map { prefs ->
        prefs[Keys.TRIGGER_MODE]?.let { raw -> runCatching { TriggerMode.valueOf(raw) }.getOrNull() } ?: TriggerMode.BOTH
    }
    val onboardingDone = context.dataStore.data.map { it[Keys.ONBOARDING_DONE] ?: false }

    /** The no-tap overlay trigger, which is what the Quick Settings tile switches. */
    val instantEnabled = triggerMode.map { it != TriggerMode.MENU_ONLY }

    suspend fun setTargetLanguage(name: String) {
        context.dataStore.edit { it[Keys.TARGET_LANGUAGE] = name }
    }

    suspend fun setEnabled(value: Boolean) {
        context.dataStore.edit { it[Keys.ENABLED] = value }
    }

    suspend fun toggleEnabled() {
        context.dataStore.edit { it[Keys.ENABLED] = !(it[Keys.ENABLED] ?: true) }
    }

    suspend fun setTriggerMode(mode: TriggerMode) {
        context.dataStore.edit { it[Keys.TRIGGER_MODE] = mode.name }
    }

    /**
     * Flips just the instant (no-tap overlay) trigger, leaving the
     * selection-menu entry alone - turning instant off gives MENU_ONLY rather
     * than disabling the app, so "Word Lookup" stays available in the text
     * selection menu. Read-modify-write inside a single `edit` so the toggle
     * is atomic against the real stored value (the same reason
     * [toggleEnabled] exists rather than a setter fed from a StateFlow
     * snapshot, which is what made the tile look inert on a cold start).
     */
    suspend fun toggleInstant() {
        context.dataStore.edit { prefs ->
            val current = prefs[Keys.TRIGGER_MODE]
                ?.let { raw -> runCatching { TriggerMode.valueOf(raw) }.getOrNull() }
                ?: TriggerMode.BOTH
            prefs[Keys.TRIGGER_MODE] =
                if (current == TriggerMode.MENU_ONLY) TriggerMode.BOTH.name else TriggerMode.MENU_ONLY.name
        }
    }

    suspend fun setOnboardingDone(value: Boolean) {
        context.dataStore.edit { it[Keys.ONBOARDING_DONE] = value }
    }
}
