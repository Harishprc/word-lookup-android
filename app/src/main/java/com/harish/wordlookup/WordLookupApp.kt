package com.harish.wordlookup

import android.app.Application
import com.harish.wordlookup.data.ApiKeyStore
import com.harish.wordlookup.data.GeminiProvider
import com.harish.wordlookup.data.Languages
import com.harish.wordlookup.data.LauncherIcon
import com.harish.wordlookup.data.LookupRepository
import com.harish.wordlookup.data.Settings
import com.harish.wordlookup.data.TriggerMode
import com.harish.wordlookup.data.cache.LookupDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class WordLookupApp : Application() {

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    lateinit var settings: Settings
        private set
    lateinit var apiKeyStore: ApiKeyStore
        private set
    lateinit var repository: LookupRepository
        private set
    lateinit var launcherIcon: LauncherIcon
        private set
    lateinit var enabledState: StateFlow<Boolean>
        private set

    /**
     * A synchronously-readable, best-effort current language - used only as
     * an advisory hint for LookupRepository.peekMemory's zero-suspension fast
     * path (see SelectionAccessibilityService/ProcessTextActivity). Unlike
     * `enabledState`, staleness here is harmless by construction: a wrong
     * guess just means a cache-hit optimization is missed, never that wrong
     * data is shown - the real lookup always re-reads the authoritative
     * value via a suspend Settings.targetLanguage.first().
     */
    lateinit var targetLanguageState: StateFlow<String>
        private set

    /**
     * Whether the instant (no-tap overlay) trigger is live. Read synchronously
     * in `onAccessibilityEvent`, which is a hot callback with no coroutine
     * context of its own. Seeded `true` to match the BOTH default; the brief
     * cold-start window before DataStore loads can only ever allow one extra
     * lookup, never suppress a wanted one.
     */
    lateinit var instantEnabledState: StateFlow<Boolean>
        private set

    /** Backs the menu path's own trigger-mode check in ProcessTextActivity. */
    lateinit var triggerModeState: StateFlow<TriggerMode>
        private set

    override fun onCreate() {
        super.onCreate()
        settings = Settings(this)
        apiKeyStore = ApiKeyStore(this)
        launcherIcon = LauncherIcon(this)
        enabledState = settings.enabled.stateIn(applicationScope, SharingStarted.Eagerly, true)
        targetLanguageState = settings.targetLanguage.stateIn(applicationScope, SharingStarted.Eagerly, Languages.DEFAULT.name)
        instantEnabledState = settings.instantEnabled.stateIn(applicationScope, SharingStarted.Eagerly, true)
        triggerModeState = settings.triggerMode.stateIn(applicationScope, SharingStarted.Eagerly, TriggerMode.BOTH)
        repository = LookupRepository(
            dao = LookupDatabase.get(this).lookupDao(),
            providerFactory = { language -> GeminiProvider(apiKeyStore.geminiApiKey, apiKeyStore.geminiModel, language) },
        )
    }
}
