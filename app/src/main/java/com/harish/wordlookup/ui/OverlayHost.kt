package com.harish.wordlookup.ui

import android.content.Context
import android.graphics.PixelFormat
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import kotlin.math.max

/**
 * Owns the single floating card window shared by both trigger paths
 * (ProcessTextActivity's popup and the accessibility overlay). Window flags,
 * gravity, and the auto-dismiss timeout are recovered byte-exact from the
 * shipped v0.1.0 APK via jadx - anchoring near the wrong spot or using the
 * wrong window type is invisible in a diff but obviously wrong on a device.
 */
class OverlayHost(private val context: Context) {

    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val handler = Handler(Looper.getMainLooper())
    private val cardState = mutableStateOf<CardState>(CardState.Loading())
    private var composeView: ComposeView? = null
    private var scrimView: View? = null
    private var dismissRunnable: Runnable? = null

    // Paired 1:1 with composeView, not a field that outlives it: LifecycleRegistry
    // treats DESTROYED as terminal - calling start() on an owner that has already
    // been through dismiss()'s stop() throws IllegalArgumentException. A single
    // OverlayHost lives for the whole accessibility service and shows many cards
    // over time, so reusing one owner across show/dismiss cycles crashed on the
    // *second* card every time: fine on first selection, then the app process
    // (which the accessibility service also lives in) died on the next one -
    // exactly the "instant popup works once, then falls back to the menu" report.
    private var lifecycleOwner: OverlayLifecycleOwner? = null

    var timeoutMs: Long = 6000L

    fun show(state: CardState, anchor: Rect?) {
        cardState.value = state
        if (!ensureViewAttached()) return
        position(anchor)
        composeView?.post { position(anchor) }
        rescheduleDismiss(state)
    }

    /**
     * Round 8: pushes the pending auto-dismiss back out to [timeoutMs] from
     * now. Called from a speaker button's tap so playback on the *instant*
     * overlay (the only host with a timeout at all - the popup path has
     * none, and the register isn't time-limited) doesn't get cut mid-word
     * by a dismiss that was scheduled before the tap happened.
     *
     * Deliberate departure, called out explicitly: the 6s window itself was
     * recovered byte-exact from the shipped v0.1.0 APK via jadx (see this
     * file's class doc). Extending it on a speak tap is new behavior this
     * app is choosing, not something recovered - window flags, gravity and
     * positioning above are untouched.
     */
    fun extendTimeout() {
        if (composeView == null) return
        rescheduleDismiss(cardState.value)
    }

    fun dismiss() {
        dismissRunnable?.let { handler.removeCallbacks(it) }
        val view = composeView ?: return
        composeView = null
        lifecycleOwner?.stop()
        lifecycleOwner = null
        runCatching { windowManager.removeView(view) }
        scrimView?.let { runCatching { windowManager.removeView(it) } }
        scrimView = null
    }

    /**
     * Returns whether a view is attached and safe to position. `addView` can
     * throw - SYSTEM_ALERT_WINDOW revoked mid-session, or an OEM refusing
     * TYPE_APPLICATION_OVERLAY - and letting that propagate would crash
     * [SelectionAccessibilityService][com.harish.wordlookup.service.SelectionAccessibilityService],
     * which Android then disables: the instant trigger dies silently and
     * permanently. A Toast (needs no special permission) tells the user what
     * to do instead of the app going dark.
     *
     * A full-screen, invisible scrim is added *first* so it sits underneath
     * the card's own window: `FLAG_NOT_TOUCH_MODAL` on the card window means
     * a tap landing outside the card's bounds falls through to whatever
     * window is below it, which is now this scrim instead of the app
     * underneath - so an outside tap dismisses the card, while a tap on the
     * card itself is consumed by the card's own window and never reaches the
     * scrim at all.
     */
    private fun ensureViewAttached(): Boolean {
        if (composeView != null) return true
        val owner = OverlayLifecycleOwner().also { lifecycleOwner = it }
        owner.start()

        val scrim = View(context).apply { setOnClickListener { dismiss() } }
        val scrimParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        )

        val view = ComposeView(context).apply {
            setViewTreeLifecycleOwner(owner)
            setViewTreeViewModelStoreOwner(owner)
            setViewTreeSavedStateRegistryOwner(owner)
            setContent {
                // Speak buttons on the instant overlay's card, wired through
                // the shared factory (round 8) - only meaningful once a
                // result exists, so Loading/Message states pass null and
                // LookupCard renders exactly as it always has for them.
                // Every onSpeak is wrapped to extend() the auto-dismiss
                // first - this host is the only one with a timeout at all,
                // so the wrapping happens here, not inside the shared
                // factory the other three hosts also call.
                val state = cardState.value
                val speech = (state as? CardState.Result)?.let { result ->
                    val base = rememberCardSpeech(result.result)
                    remember(base) {
                        CardSpeech(
                            english = base.english.copy(onSpeak = { this@OverlayHost.extendTimeout(); base.english.onSpeak() }),
                            native = base.native.copy(onSpeak = { this@OverlayHost.extendTimeout(); base.native.onSpeak() }),
                        )
                    }
                }
                LookupCard(state, speech = speech)
            }
        }
        val layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply { gravity = Gravity.TOP or Gravity.START }

        return try {
            windowManager.addView(scrim, scrimParams)
            scrimView = scrim
            windowManager.addView(view, layoutParams)
            composeView = view
            true
        } catch (e: Exception) {
            Log.w("OverlayHost", "Could not attach the overlay window", e)
            scrimView?.let { runCatching { windowManager.removeView(it) } }
            scrimView = null
            owner.stop()
            lifecycleOwner = null
            Toast.makeText(
                context,
                "Word Lookup needs \"Display over other apps\" permission to show results here.",
                Toast.LENGTH_LONG,
            ).show()
            false
        }
    }

    fun position(anchor: Rect?) {
        val view = composeView ?: return
        val layoutParams = view.layoutParams as? WindowManager.LayoutParams ?: return
        val metrics = context.resources.displayMetrics

        val cardWidth = view.width.takeIf { it > 0 } ?: dpToPx(340)
        val cardHeight = view.height.takeIf { it > 0 } ?: dpToPx(160)

        val x = anchor?.left ?: (metrics.widthPixels / 2)
        val y = anchor?.let { it.bottom + dpToPx(12) } ?: (metrics.heightPixels / 2)

        layoutParams.x = x.coerceIn(0, max(metrics.widthPixels - cardWidth, 0))
        layoutParams.y = y.coerceIn(0, max(metrics.heightPixels - cardHeight, 0))
        // A dismiss() (auto-timeout, a tap, or a fresh selection superseding
        // this one) can remove the view between this being scheduled and it
        // running - updateViewLayout on an unattached view throws
        // IllegalArgumentException, another process-crash risk not worth
        // taking for a position update that's now moot anyway.
        runCatching { windowManager.updateViewLayout(view, layoutParams) }
    }

    private fun rescheduleDismiss(state: CardState) {
        dismissRunnable?.let { handler.removeCallbacks(it) }
        if (state is CardState.Loading) return
        val runnable = Runnable { dismiss() }
        dismissRunnable = runnable
        handler.postDelayed(runnable, timeoutMs)
    }

    private fun dpToPx(dp: Int): Int = (dp * context.resources.displayMetrics.density).toInt()
}
