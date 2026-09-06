package com.harish.wordlookup.ui

import com.harish.wordlookup.data.review.ReviewCard

/**
 * One quiz run's progress, held on MainViewModel between `startReview()` and
 * the last grade. [index] only ever advances - grading is one-way, there is
 * no "go back" in this round's scope (see CLAUDE.md's "Round 8": flashcard
 * review only, no quiz-navigation UI beyond forward).
 */
data class ReviewSession(val cards: List<ReviewCard> = emptyList(), val index: Int = 0) {
    val current: ReviewCard? get() = cards.getOrNull(index)
    val isDone: Boolean get() = cards.isNotEmpty() && index >= cards.size
    val total: Int get() = cards.size
    /** 1-based, for the "3 of 12" readout - 0 before a session has started. */
    val position: Int get() = (index + 1).coerceAtMost(total)
}
