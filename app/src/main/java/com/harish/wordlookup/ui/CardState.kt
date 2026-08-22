package com.harish.wordlookup.ui

import com.harish.wordlookup.data.LookupResult

sealed interface CardState {
    data class Loading(val wordHint: String = "") : CardState
    data class Result(val result: LookupResult) : CardState
    data class Message(val text: String) : CardState
}
