package com.harish.wordlookup.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.harish.wordlookup.WordLookupApp
import com.harish.wordlookup.data.ApiKeyStore
import com.harish.wordlookup.data.DigestWeek
import com.harish.wordlookup.data.LookupRepository
import com.harish.wordlookup.data.Languages
import com.harish.wordlookup.data.LauncherIcon
import com.harish.wordlookup.data.RegisterEntry
import com.harish.wordlookup.data.Settings
import com.harish.wordlookup.data.TriggerMode
import com.harish.wordlookup.data.review.ReviewCard
import com.harish.wordlookup.data.review.ReviewGrade
import com.harish.wordlookup.service.DigestWidgetProvider
import com.harish.wordlookup.service.WordOfDayWidgetProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainViewModel(
    private val settings: Settings,
    private val apiKeyStore: ApiKeyStore,
    private val repository: LookupRepository,
    private val launcherIcon: LauncherIcon,
    /** Round 11: only used to repaint home-screen widgets on a language change - `WordLookupApp` itself is a `Context`. */
    private val context: Context? = null,
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

    // Computed once per ViewModel instance, not re-derived on every read - an
    // app kept open across a Monday-00:00 boundary keeps showing last week's
    // digest until the process restarts. A known, deliberate gap (see
    // CLAUDE.md's Round 10 section), not fixed this round: the alternative
    // (recomputing on every collection) would need its own clock-tick source
    // for a boundary that matters once a week at most.
    val weekStartMillis: Long = DigestWeek.startOfWeekMillis()
    val digest: StateFlow<List<RegisterEntry>> = register
        .map { entries -> entries.filter { it.createdAtMillis >= weekStartMillis } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Drives the register's "Quiz - N due" dock - always live, no gate on whether the quiz is ever opened. */
    val dueCount: StateFlow<Int> =
        repository.observeDueCount().stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    private val _reviewSession = MutableStateFlow(ReviewSession())
    val reviewSession: StateFlow<ReviewSession> = _reviewSession.asStateFlow()

    /** Independent of the quiz above - see Settings.kt's own doc for why. */
    val reminderEnabled: StateFlow<Boolean> =
        settings.reminderEnabled.stateIn(viewModelScope, SharingStarted.Eagerly, false)
    val reminderHour: StateFlow<Int> =
        settings.reminderHour.stateIn(viewModelScope, SharingStarted.Eagerly, Settings.DEFAULT_REMINDER_HOUR)

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
        withContext(Dispatchers.IO) {
            launcherIcon.switchTo(name)
            // A placed widget's glyph must not wait for its own ~30-minute
            // floor to catch up with a language switch the user just made.
            context?.let {
                WordOfDayWidgetProvider.updateAll(it)
                DigestWidgetProvider.updateAll(it)
            }
        }
    }
    fun setEnabled(value: Boolean) = viewModelScope.launch { settings.setEnabled(value) }
    fun setTriggerMode(mode: TriggerMode) = viewModelScope.launch { settings.setTriggerMode(mode) }
    fun completeOnboarding() = viewModelScope.launch { settings.setOnboardingDone(true) }
    fun deleteWord(language: String, original: String) = viewModelScope.launch { repository.delete(language, original) }

    /** Loads a fresh batch every time Review is opened - a session started with a stale queue could re-show an already-graded card. */
    fun startReview() = viewModelScope.launch {
        _reviewSession.value = ReviewSession(cards = repository.loadDueBatch(), index = 0)
    }

    fun gradeCurrentCard(grade: ReviewGrade) = viewModelScope.launch {
        val card = _reviewSession.value.current ?: return@launch
        repository.grade(card, grade)
        _reviewSession.value = _reviewSession.value.copy(index = _reviewSession.value.index + 1)
    }

    suspend fun nextDueAt(): Long? = repository.nextDueAt()

    fun setReminderEnabled(value: Boolean) = viewModelScope.launch { settings.setReminderEnabled(value) }
    fun setReminderHour(hour: Int) = viewModelScope.launch { settings.setReminderHour(hour) }

    class Factory(private val app: WordLookupApp) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            MainViewModel(app.settings, app.apiKeyStore, app.repository, app.launcherIcon, app) as T
    }
}
