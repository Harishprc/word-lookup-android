package com.harish.wordlookup.ui

import android.database.ContentObserver
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings as AndroidSettings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.harish.wordlookup.WordLookupApp
import com.harish.wordlookup.data.Languages
import com.harish.wordlookup.data.TriggerMode
import com.harish.wordlookup.service.DigestReminderScheduler
import com.harish.wordlookup.service.ReviewReminderScheduler
import com.harish.wordlookup.service.SelectionAccessibilityService
import com.harish.wordlookup.ui.components.BrandMark
import com.harish.wordlookup.ui.components.NavigationCard
import com.harish.wordlookup.ui.components.SectionCard
import com.harish.wordlookup.ui.components.SegmentedControl
import com.harish.wordlookup.ui.theme.Radius
import com.harish.wordlookup.ui.theme.Spacing
import com.harish.wordlookup.ui.theme.WordLookupTheme

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels {
        MainViewModel.Factory(application as WordLookupApp)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // No edge-to-edge opt-in existed before this: on targetSdk 35 (Android
        // 15+, mandatory edge-to-edge) that leaves the OS drawing its default
        // opaque nav-bar contrast scrim - a hard-edged bar cutting across the
        // app's own background instead of blending into it.
        enableEdgeToEdge()
        val openReview = intent?.getBooleanExtra(EXTRA_OPEN_REVIEW, false) ?: false
        val openDigest = intent?.getBooleanExtra(EXTRA_OPEN_DIGEST, false) ?: false
        setContent {
            WordLookupTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    AppRoot(viewModel, startAtReview = openReview, startAtDigest = openDigest)
                }
            }
        }
    }

    companion object {
        /** Set by ReviewReminderReceiver's PendingIntent so tapping the notification lands on Review, not Home. */
        const val EXTRA_OPEN_REVIEW = "open_review"

        /** Set by DigestReminderReceiver's PendingIntent so tapping the notification lands on the digest, not Home. */
        const val EXTRA_OPEN_DIGEST = "open_digest"
    }
}

@Composable
private fun AppRoot(viewModel: MainViewModel, startAtReview: Boolean = false, startAtDigest: Boolean = false) {
    var screen by remember {
        mutableStateOf(
            when {
                startAtReview -> Screen.REVIEW
                startAtDigest -> Screen.DIGEST
                else -> Screen.HOME
            },
        )
    }
    val onboardingDone by viewModel.onboardingDone.collectAsStateWithLifecycle()
    val language by viewModel.targetLanguage.collectAsStateWithLifecycle()
    val register by viewModel.register.collectAsStateWithLifecycle()
    val digest by viewModel.digest.collectAsStateWithLifecycle()
    val hasGeminiKey by viewModel.hasGeminiKey.collectAsStateWithLifecycle()
    val dueCount by viewModel.dueCount.collectAsStateWithLifecycle()
    val reviewSession by viewModel.reviewSession.collectAsStateWithLifecycle()

    // The notification's PendingIntent can't call startReview() itself - it
    // only carries a static "open Review" flag, so the load happens here,
    // once, the same way RegisterScreen's own quiz tap already triggers it.
    LaunchedEffect(startAtReview) {
        if (startAtReview) viewModel.startReview()
    }

    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var overlayGranted by remember { mutableStateOf(Permissions.hasOverlay(context)) }
    var accessibilityGranted by remember {
        mutableStateOf(Permissions.hasAccessibilityServiceEnabled(context, SELECTION_SERVICE_CLASS))
    }

    val reminderEnabled by viewModel.reminderEnabled.collectAsStateWithLifecycle()
    val reminderHour by viewModel.reminderHour.collectAsStateWithLifecycle()

    // The single place that keeps the actual AlarmManager alarm in sync with
    // Settings - reacts to any change (the switch, the hour picker, a fresh
    // app launch with the reminder already on) rather than each call site
    // that could flip these values remembering to also touch the scheduler.
    LaunchedEffect(reminderEnabled, reminderHour) {
        if (reminderEnabled) {
            ReviewReminderScheduler.schedule(context, reminderHour)
        } else {
            ReviewReminderScheduler.cancel(context)
        }
    }

    // Always-on, unlike the review reminder above - no Settings toggle for
    // the digest (see CLAUDE.md's Round 10 section for why). Keyed on Unit:
    // there's no settings state to react to, just a once-per-cold-launch
    // re-arm, idempotent since re-scheduling the same request code just
    // replaces the pending alarm.
    LaunchedEffect(Unit) {
        DigestReminderScheduler.schedule(context)
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { /* Declining leaves the reminder switch on but silent - see SettingsScreen's own doc. */ }

    // Both permissions are granted from Settings, outside the app, so re-check
    // on resume. Hoisted here (not inside HomeScreen/SettingsScreen) so the
    // check keeps running regardless of which screen is on top - the pause
    // action on Settings, in particular, needs this to notice the change the
    // moment the user comes back to the app.
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                overlayGranted = Permissions.hasOverlay(context)
                accessibilityGranted = Permissions.hasAccessibilityServiceEnabled(context, SELECTION_SERVICE_CLASS)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)

        // Round 9: Settings' "Pause now" calls disableSelf() without the app
        // ever leaving this screen, so there is no resume for the observer
        // above to catch - accessibilityGranted stayed stale until the user
        // backgrounded and returned. disableSelf() is also async across a
        // Binder call, so writing `false` optimistically in the pause lambda
        // would race it and could lie if the pause failed. A ContentObserver
        // on the actual setting is authoritative either way, and also covers
        // a case nobody reported: the service being toggled from system
        // Settings while this app stays visible (split-screen, or back via
        // recents with no full resume).
        val accessibilityObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                accessibilityGranted = Permissions.hasAccessibilityServiceEnabled(context, SELECTION_SERVICE_CLASS)
            }
        }
        context.contentResolver.registerContentObserver(
            AndroidSettings.Secure.getUriFor("enabled_accessibility_services"),
            false,
            accessibilityObserver,
        )

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            context.contentResolver.unregisterContentObserver(accessibilityObserver)
        }
    }

    Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
        Column(Modifier.padding(padding)) {
            when {
                !onboardingDone -> SetupScreen(
                    currentLanguage = language,
                    hasGeminiKey = hasGeminiKey,
                    onSave = { lang, geminiKey ->
                        viewModel.setLanguage(lang)
                        viewModel.saveGeminiKey(geminiKey)
                        viewModel.completeOnboarding()
                        screen = Screen.HOME
                    },
                )
                screen == Screen.EDIT_SETUP -> SetupScreen(
                    currentLanguage = language,
                    hasGeminiKey = hasGeminiKey,
                    onSave = { lang, geminiKey ->
                        viewModel.setLanguage(lang)
                        viewModel.saveGeminiKey(geminiKey)
                        screen = Screen.SETTINGS
                    },
                )
                screen == Screen.SETTINGS -> SettingsScreen(
                    overlayGranted = overlayGranted,
                    accessibilityGranted = accessibilityGranted,
                    showAddTile = Permissions.needsNotificationRuntimePermission(),
                    onBack = { screen = Screen.HOME },
                    onOpenOverlaySettings = { context.startActivity(Permissions.overlayIntent(context)) },
                    onOpenAccessibilitySettings = { screen = Screen.ACCESSIBILITY_CONSENT },
                    onOpenAppInfo = { context.startActivity(Permissions.appInfoIntent(context)) },
                    onEditSetup = { screen = Screen.EDIT_SETUP },
                    onAddTile = { Permissions.requestAddTile(context) { } },
                    onPauseForBanking = { SelectionAccessibilityService.pause() },
                    reminderEnabled = reminderEnabled,
                    reminderHour = reminderHour,
                    onReminderEnabledChange = { enabled ->
                        viewModel.setReminderEnabled(enabled)
                        if (enabled && Permissions.needsNotificationRuntimePermission()) {
                            notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                        }
                    },
                    onReminderHourChange = viewModel::setReminderHour,
                    languageName = language,
                    languageGlyph = Languages.get(language).glyph,
                    onOpenLanguage = { screen = Screen.LANGUAGE },
                    registerEntries = register,
                    onOpenWidgetPreview = { screen = Screen.WIDGET_PREVIEW },
                )
                screen == Screen.DIGEST -> DigestScreen(
                    entries = digest,
                    weekStartMillis = viewModel.weekStartMillis,
                    onDelete = viewModel::deleteWord,
                    onBack = { screen = Screen.HOME },
                )
                screen == Screen.LANGUAGE -> LanguagePickerScreen(
                    current = language,
                    onPick = { name ->
                        viewModel.setLanguage(name)
                        screen = Screen.SETTINGS
                    },
                    onBack = { screen = Screen.SETTINGS },
                )
                screen == Screen.TRY_DEMO -> TryLookupScreen(onBack = { screen = Screen.HOME })
                screen == Screen.WIDGET_PREVIEW -> WidgetPreviewScreen(
                    languageGlyph = Languages.get(language).glyph,
                    onBack = { screen = Screen.SETTINGS },
                )
                screen == Screen.REGISTER -> RegisterScreen(
                    entries = register,
                    onDelete = viewModel::deleteWord,
                    onBack = { screen = Screen.HOME },
                    dueCount = dueCount,
                    onOpenQuiz = {
                        viewModel.startReview()
                        screen = Screen.REVIEW
                    },
                )
                screen == Screen.REVIEW -> ReviewScreen(
                    session = reviewSession,
                    onGrade = viewModel::gradeCurrentCard,
                    // Entered only from the register, so back returns there -
                    // not Home. See Screen.kt's doc for why.
                    onBack = { screen = Screen.REGISTER },
                    loadNextDueAt = viewModel::nextDueAt,
                )
                screen == Screen.ACCESSIBILITY_CONSENT -> AccessibilityConsentScreen(
                    onContinue = {
                        context.startActivity(Permissions.accessibilitySettingsIntent())
                        screen = Screen.SETTINGS
                    },
                    onNotNow = { screen = Screen.SETTINGS },
                )
                else -> HomeScreen(
                    viewModel = viewModel,
                    onOpenSettings = { screen = Screen.SETTINGS },
                    onOpenRegister = { screen = Screen.REGISTER },
                    onOpenDigest = { screen = Screen.DIGEST },
                    onOpenTryDemo = { screen = Screen.TRY_DEMO },
                )
            }
        }
    }
}

@Composable
internal fun HomeScreen(
    viewModel: MainViewModel,
    onOpenSettings: () -> Unit,
    onOpenRegister: () -> Unit,
    onOpenDigest: () -> Unit,
    onOpenTryDemo: () -> Unit,
) {
    val language by viewModel.targetLanguage.collectAsStateWithLifecycle()
    val enabled by viewModel.enabled.collectAsStateWithLifecycle()
    val triggerMode by viewModel.triggerMode.collectAsStateWithLifecycle()
    val digest by viewModel.digest.collectAsStateWithLifecycle()

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(Spacing.ProminentCard),
        verticalArrangement = Arrangement.spacedBy(Spacing.MD),
    ) {
        HomeHeader(
            glyph = Languages.get(language).glyph,
            enabled = enabled,
            onEnabledChange = { viewModel.setEnabled(it) },
        )

        SummaryCard(language = language)

        SectionCard(title = "How it opens") {
            SegmentedControl(
                options = TRIGGER_LABELS,
                selectedIndex = TRIGGER_ORDER.indexOf(triggerMode).coerceAtLeast(0),
                onSelect = { viewModel.setTriggerMode(TRIGGER_ORDER[it]) },
            )
            Text(
                "The Quick Settings tile switches the instant popup on and off: that's this setting, between \"Both\" and \"Menu only\".",
                modifier = Modifier.padding(top = Spacing.SM),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        NavigationCard(
            title = "This week",
            subtitle = if (digest.isEmpty()) {
                "No new words yet"
            } else {
                "${digest.size} new word${if (digest.size == 1) "" else "s"} since Monday"
            },
            onClick = onOpenDigest,
            showActivityDot = digest.isNotEmpty(),
        )

        NavigationCard(
            title = "Try a lookup",
            subtitle = "Select a word, or a whole sentence",
            onClick = onOpenTryDemo,
        )

        NavigationCard(
            title = "Word register",
            subtitle = "Every lookup you've made, searchable",
            onClick = onOpenRegister,
        )

        NavigationCard(
            title = "Settings",
            subtitle = "Permissions, language, API key",
            onClick = onOpenSettings,
        )
    }
}

@Composable
internal fun HomeHeader(glyph: String, enabled: Boolean, onEnabledChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BrandMark(glyph = glyph, size = 40.dp)
            Spacer(Modifier.width(Spacing.SM + Spacing.XS))
            Column {
                Text("Word Lookup", style = MaterialTheme.typography.headlineMedium)
                Text(
                    if (enabled) "Running" else "Paused",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Switch(
            checked = enabled,
            onCheckedChange = onEnabledChange,
            colors = SwitchDefaults.colors(
                checkedTrackColor = MaterialTheme.colorScheme.secondary,
                checkedThumbColor = MaterialTheme.colorScheme.onSecondary,
            ),
            modifier = Modifier.semantics { contentDescription = "Enable Word Lookup" },
        )
    }
}

@Composable
internal fun SummaryCard(language: String) {
    val ink = MaterialTheme.colorScheme.onSurface
    val inkDeep = lerp(ink, Color.Black, 0.28f)
    val onInk = MaterialTheme.colorScheme.surface

    Surface(
        shape = RoundedCornerShape(Radius.XL),
        color = ink,
        contentColor = onInk,
        shadowElevation = 12.dp,
        modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.SM),
    ) {
        Column(
            Modifier
                .background(Brush.linearGradient(listOf(ink, inkDeep)))
                .padding(Spacing.ProminentCard),
        ) {
            Text(
                "ACTIVE LANGUAGE",
                style = MaterialTheme.typography.labelSmall,
                color = onInk.copy(alpha = 0.65f),
            )
            Text(
                language,
                style = MaterialTheme.typography.headlineLarge,
                modifier = Modifier.padding(top = Spacing.XS, bottom = Spacing.XS),
            )
            Text(
                "Select a word anywhere to begin.",
                style = MaterialTheme.typography.bodyMedium,
                color = onInk.copy(alpha = 0.72f),
            )
        }
    }
}

/** Segment order is the single source of truth for label <-> TriggerMode mapping. */
private val TRIGGER_ORDER = listOf(TriggerMode.INSTANT, TriggerMode.MENU_ONLY, TriggerMode.BOTH)
private val TRIGGER_LABELS = listOf("Instant", "Menu", "Both")

private const val SELECTION_SERVICE_CLASS = "com.harish.wordlookup.service.SelectionAccessibilityService"
