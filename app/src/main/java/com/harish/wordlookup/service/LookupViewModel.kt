package com.harish.wordlookup.service

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.harish.wordlookup.data.LookupFailedException
import com.harish.wordlookup.data.LookupRepository
import com.harish.wordlookup.data.Settings
import com.harish.wordlookup.ui.CardState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class LookupViewModel(
    private val repository: LookupRepository,
    private val settings: Settings,
) : ViewModel() {

    private val _state = MutableStateFlow<CardState>(CardState.Loading())
    val state: StateFlow<CardState> = _state.asStateFlow()

    /** Skips the network entirely - used when the app-wide toggle is off. */
    fun showMessage(text: String) {
        _state.value = CardState.Message(text)
    }

    /**
     * [languageHint], if given, is checked against the zero-suspension memory
     * cache before anything else - a hit renders instantly with no Loading
     * flash at all. A wrong or stale hint just falls through to the normal
     * path below, so passing a possibly-stale value here is always safe.
     */
    fun start(text: String, languageHint: String? = null) {
        if (languageHint != null) {
            repository.peekMemory(text, languageHint)?.let {
                _state.value = CardState.Result(it)
                return
            }
        }
        _state.value = CardState.Loading(text)
        viewModelScope.launch {
            try {
                val language = settings.targetLanguage.first()
                _state.value = CardState.Result(repository.lookup(text, language))
            } catch (e: LookupFailedException) {
                _state.value = CardState.Message(e.message ?: "")
            } catch (e: Exception) {
                _state.value = CardState.Message("Unexpected error: ${e.message}")
            }
        }
    }
}
