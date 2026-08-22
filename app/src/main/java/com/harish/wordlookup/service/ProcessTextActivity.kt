package com.harish.wordlookup.service

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.harish.wordlookup.WordLookupApp
import com.harish.wordlookup.data.TextTruncation
import com.harish.wordlookup.data.TriggerMode
import com.harish.wordlookup.ui.LookupCard
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * The "menu" trigger path: Android's own text-selection ACTION_PROCESS_TEXT
 * item. A transparent, singleTop activity - it draws nothing of its own,
 * just a full-screen tap-to-dismiss layer with the same LookupCard the
 * overlay path uses.
 */
class ProcessTextActivity : ComponentActivity() {

    private val viewModel: LookupViewModel by viewModels {
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                val app = application as WordLookupApp
                return LookupViewModel(app.repository, app.settings) as T
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val text = intent?.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString()
        if (text.isNullOrBlank()) {
            finish()
            return
        }
        // A suspend read of the real DataStore value, not app.enabledState.value:
        // that eager snapshot is seeded `true` on cold start and can be briefly
        // stale, which is exactly the class of bug that made the QS tile look
        // broken (see LookupTileService.onClick).
        val app = application as WordLookupApp
        lifecycleScope.launch {
            when {
                !app.settings.enabled.first() ->
                    viewModel.showMessage("Word Lookup is off — turn it on from the app.")
                // Trigger = "Instant" means the user asked for the overlay only.
                app.settings.triggerMode.first() == TriggerMode.INSTANT ->
                    viewModel.showMessage("The menu trigger is off — set Trigger to \"Both\" in Word Lookup to use it.")
                else ->
                    viewModel.start(TextTruncation.truncate(text), languageHint = app.targetLanguageState.value)
            }
        }
        setContent { ProcessTextContent(viewModel) { finish() } }
    }
}

@Composable
private fun ProcessTextContent(viewModel: LookupViewModel, onDismiss: () -> Unit) {
    val state by viewModel.state.collectAsState()

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Transparent)
            .pointerInput(Unit) { detectTapGestures { onDismiss() } }
            .padding(24.dp),
        contentAlignment = Alignment.TopStart,
    ) {
        // Without its own tap handler, a tap on the card falls through to the
        // Box behind it and dismisses - the card should only go away on a tap
        // outside it. An empty detectTapGestures here consumes the down event
        // before it reaches the parent's.
        LookupCard(state, modifier = Modifier.pointerInput(Unit) { detectTapGestures { } })
    }
}
