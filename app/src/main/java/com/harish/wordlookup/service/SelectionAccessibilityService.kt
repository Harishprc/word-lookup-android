package com.harish.wordlookup.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.graphics.Rect
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.harish.wordlookup.WordLookupApp
import com.harish.wordlookup.data.GeminiProvider
import com.harish.wordlookup.data.LookupFailedException
import com.harish.wordlookup.data.TextTruncation
import com.harish.wordlookup.ui.CardState
import com.harish.wordlookup.ui.OverlayHost
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * The "instant" trigger path: watches TYPE_VIEW_TEXT_SELECTION_CHANGED
 * (accessibilityEventTypes 0x2000 in the manifest config) and fires a lookup
 * with no tap needed. Skips password fields and events from this app's own UI.
 *
 * Dragging a selection handle sends a stream of *different* strings ("au",
 * "auto", "autodi", ...), each a distinct cache miss. A same-text-only
 * debounce (v0.1.0's approach, and this app's until now) does not suppress
 * that stream at all - one drag could burn a double-digit slice of the
 * ~1,500/day free-tier quota and flicker the card through intermediate
 * words. This is a genuine quiet-window debounce instead: every event
 * cancels and reschedules a delayed fire, so a lookup only happens once the
 * selection has stopped changing for [DEBOUNCE_MS].
 *
 * Some apps (confirmed on-device: Samsung's My Files PDF viewer) never
 * dispatch this event at all and expose no accessibility text for their
 * document content either - a diagnostic probe confirmed that directly
 * (see git history / CLAUDE.md for the finding). For those, only the
 * ACTION_PROCESS_TEXT menu path (ProcessTextActivity) can work.
 */
class SelectionAccessibilityService : AccessibilityService() {

    private lateinit var app: WordLookupApp
    private lateinit var overlay: OverlayHost
    private val scope = CoroutineScope(Dispatchers.Main)
    private var debounceJob: Job? = null
    private var lookupJob: Job? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onCreate() {
        super.onCreate()
        app = application as WordLookupApp
        overlay = OverlayHost(this)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || event.eventType != AccessibilityEvent.TYPE_VIEW_TEXT_SELECTION_CHANGED) return
        if (!app.enabledState.value) return
        // The Trigger setting (and the Quick Settings tile, which switches it)
        // is enforced here. It previously wasn't read anywhere at all, so
        // "Menu only" silently still fired instant lookups.
        if (!app.instantEnabledState.value) return
        if (event.packageName == packageName) return

        val source = event.source
        if (source?.isPassword == true) return
        val selection = extractSelection(event, source)
        if (selection.isNullOrBlank()) return

        val bounds = Rect().also { source?.getBoundsInScreen(it) }.takeUnless { it.isEmpty }
        val text = TextTruncation.truncate(selection)

        // A word already in the memory cache (re-selected, or the other
        // trigger path just looked it up) needs neither the debounce wait
        // nor a Loading flash - show it immediately and skip everything else.
        app.repository.peekMemory(text, app.targetLanguageState.value)?.let { cached ->
            debounceJob?.cancel()
            lookupJob?.cancel()
            overlay.show(CardState.Result(cached), bounds)
            return
        }

        // The first event of a new burst is the moment to start warming a
        // connection: it overlaps the DNS+TLS handshake with the rest of the
        // quiet window instead of paying for it on the lookup's critical path.
        val burstJustStarted = debounceJob?.isActive != true
        debounceJob?.cancel()
        if (burstJustStarted) GeminiProvider.warmUp()

        debounceJob = scope.launch {
            delay(DEBOUNCE_MS)
            fireLookup(text, bounds)
        }
    }

    private fun extractSelection(event: AccessibilityEvent, source: AccessibilityNodeInfo?): String? =
        SelectionExtractor.extract(
            eventText = event.text?.joinToString(""),
            sourceText = source?.text?.toString(),
            fromIndex = event.fromIndex,
            toIndex = event.toIndex,
            nodeSelStart = source?.textSelectionStart ?: -1,
            nodeSelEnd = source?.textSelectionEnd ?: -1,
        )

    private fun fireLookup(text: String, bounds: Rect?) {
        // GeminiProvider's network call is now suspend-cancellable (Call.await(),
        // not the blocking execute()), so cancelling this Job for a lookup that
        // just got superseded genuinely aborts its socket instead of letting it
        // run to completion and burn quota on an answer nobody will see.
        lookupJob?.cancel()
        overlay.show(CardState.Loading(text.take(40)), bounds)
        lookupJob = scope.launch {
            try {
                val language = app.settings.targetLanguage.first()
                val result = app.repository.lookup(text, language)
                overlay.show(CardState.Result(result), bounds)
            } catch (e: LookupFailedException) {
                overlay.show(CardState.Message(e.message ?: "Lookup failed"), bounds)
            } catch (e: Exception) {
                overlay.show(CardState.Message("Unexpected error: ${e.message}"), bounds)
            }
        }
    }

    override fun onInterrupt() {
        overlay.dismiss()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (instance === this) instance = null
        debounceJob?.cancel()
        lookupJob?.cancel()
        overlay.dismiss()
    }

    override fun onUnbind(intent: Intent?): Boolean {
        if (instance === this) instance = null
        return super.onUnbind(intent)
    }

    companion object {
        private const val DEBOUNCE_MS = 250L

        @Volatile
        private var instance: SelectionAccessibilityService? = null

        /**
         * "Pause for banking" (Settings screen). `disableSelf()` genuinely
         * un-registers this service with the OS - it leaves
         * `Settings.Secure.enabled_accessibility_services`, which is what a
         * banking app's fraud SDK actually reads. The app's own toggles
         * (master switch, the Quick Settings tile) never did this: both only
         * change in-app flags this service reads, so the service stays
         * registered and a bank app that scans for it still refuses to run.
         *
         * Android does not let an app re-enable its own accessibility
         * service - the caller is responsible for sending the user to
         * Settings afterward (see `Permissions.accessibilitySettingsIntent`).
         *
         * @return false if the service wasn't running (there was nothing to pause).
         */
        fun pause(): Boolean {
            val running = instance ?: return false
            running.disableSelf()
            instance = null
            return true
        }
    }
}
