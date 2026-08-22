package com.harish.wordlookup.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.harish.wordlookup.WordLookupApp
import com.harish.wordlookup.data.ApiKeyStore
import com.harish.wordlookup.data.LookupRepository
import com.harish.wordlookup.data.Languages
import com.harish.wordlookup.data.LauncherIcon
import com.harish.wordlookup.data.RegisterEntry
import com.harish.wordlookup.data.Settings
import com.harish.wordlookup.data.TriggerMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainViewModel(
    private val settings: Settings,
    private val apiKeyStore: ApiKeyStore,
    private val repository: LookupRepository,
    private val launcherIcon: LauncherIcon,
) : ViewModel() {

    val onboardingDone: StateFlow<Boolean> =
        settings.onboardingDone.stateIn(viewModelScope, SharingStarted.Eagerly, false)
    val targetLanguage: StateFlow<String> =
        settings.targetLanguage.stateIn(viewModelScope, SharingStarted.Eagerly, Languages.DEFAULT.name)
    val enabled: StateFlow<Boolean> =
        settings.enabled.stateIn(viewModelScope, SharingStarted.Eagerly, true)
    val triggerMode: StateFlow<TriggerMode> =
        settings.triggerMode.stateIn(viewModelScope, SharingStarted.Eagerly, TriggerMode.BOTH)
    val register: StateFlow<List<RegisterEntry>> =
        repository.observeRegister().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    // Reading ApiKeyStore means EncryptedSharedPreferences: Android Keystore
    // plus Tink plus disk. The first read in particular is slow enough to be
    // visibly janky. These used to be plain functions called straight from a
    // composable, so every recomposition of the setup screen did that work on
    // the main thread - which is what made "change language" freeze. Now the
    // read happens once, off the main thread, and the UI observes the result.
    private val _hasGeminiKey = MutableStateFlow(false)
    val hasGeminiKey: StateFlow<Boolean> = _hasGeminiKey.asStateFlow()

    init {
        viewModelScope.launch { refreshKeyPresence() }
    }

    private suspend fun refreshKeyPresence() = withContext(Dispatchers.IO) {
        _hasGeminiKey.value = apiKeyStore.geminiApiKey.isNotBlank()
    }

    /** Writes are Keystore-backed too, so they also stay off the main thread. */
    fun saveGeminiKey(key: String) = viewModelScope.launch {
        if (key.isNotBlank()) {
            withContext(Dispatchers.IO) { apiKeyStore.geminiApiKey = key }
            refreshKeyPresence()
        }
    }

    fun setLanguage(name: String) = viewModelScope.launch {
        settings.setTargetLanguage(name)
        // PackageManager writes component state to disk on every call (up to
        // 26 of them here), so this stays off the main thread.
        withContext(Dispatchers.IO) { launcherIcon.switchTo(name) }
    }
    fun setEnabled(value: Boolean) = viewModelScope.launch { settings.setEnabled(value) }
    fun setTriggerMode(mode: TriggerMode) = viewModelScope.launch { settings.setTriggerMode(mode) }
    fun completeOnboarding() = viewModelScope.launch { settings.setOnboardingDone(true) }
    fun deleteWord(language: String, original: String) = viewModelScope.launch { repository.delete(language, original) }

    class Factory(private val app: WordLookupApp) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            MainViewModel(app.settings, app.apiKeyStore, app.repository, app.launcherIcon) as T
    }
}
