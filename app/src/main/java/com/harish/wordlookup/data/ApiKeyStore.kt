package com.harish.wordlookup.data

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Secrets (the Gemini key) live in an AndroidX Security EncryptedSharedPreferences
 * file, never in the plaintext Settings DataStore.
 */
class ApiKeyStore(private val context: Context) {

    private val prefs: SharedPreferences by lazy { createPrefs() }

    /**
     * `EncryptedSharedPreferences.create` throws if the Android Keystore entry
     * behind it is corrupt - a real, field-reported AndroidX failure mode,
     * typically after a restore-from-backup onto different hardware. Every key
     * read/write would otherwise crash permanently with no recovery path short
     * of a reinstall. Falling back to a plain (unencrypted) prefs file keeps
     * the app usable - degraded, not bricked - and is still strictly better
     * than the alternative of no settings at all.
     */
    private fun createPrefs(): SharedPreferences = try {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            "secure_settings",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    } catch (e: Exception) {
        Log.w("ApiKeyStore", "EncryptedSharedPreferences unavailable, falling back to plaintext storage", e)
        context.getSharedPreferences("secure_settings_fallback", Context.MODE_PRIVATE)
    }

    var geminiApiKey: String
        get() = prefs.getString(KEY_GEMINI, "") ?: ""
        set(value) = prefs.edit().putString(KEY_GEMINI, value.trim()).apply()

    var geminiModel: String
        get() = prefs.getString(KEY_MODEL, DEFAULT_MODEL) ?: DEFAULT_MODEL
        set(value) = prefs.edit().putString(KEY_MODEL, value.trim()).apply()

    companion object {
        /** Single source of truth for the default model - see GeminiProvider.DEFAULT_MODEL for the measurements behind it. */
        const val DEFAULT_MODEL = GeminiProvider.DEFAULT_MODEL
        private const val KEY_GEMINI = "gemini_api_key"
        private const val KEY_MODEL = "gemini_model"
    }
}
