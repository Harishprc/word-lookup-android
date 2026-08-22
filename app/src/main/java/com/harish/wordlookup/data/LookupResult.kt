package com.harish.wordlookup.data

data class LookupResult(
    val original: String,
    val translation: String,
    val partOfSpeech: String = "",
    val meaning: String = "",
    val synonyms: String = "",
    val exampleEn: String = "",
    val exampleNative: String = "",
    val synonymsNative: String = "",
)
