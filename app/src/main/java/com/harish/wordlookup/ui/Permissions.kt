package com.harish.wordlookup.ui

import android.app.StatusBarManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.text.TextUtils
import com.harish.wordlookup.R
import com.harish.wordlookup.service.LookupTileService

/** Recovered byte-exact from the shipped v0.1.0 APK's ui.Permissions class. */
object Permissions {

    fun hasOverlay(context: Context): Boolean = Settings.canDrawOverlays(context)

    fun overlayIntent(context: Context): Intent =
        Intent("android.settings.action.MANAGE_OVERLAY_PERMISSION", Uri.parse("package:${context.packageName}"))

    fun hasAccessibilityServiceEnabled(context: Context, serviceClassName: String): Boolean {
        val enabled = Settings.Secure.getString(context.contentResolver, "enabled_accessibility_services") ?: return false
        val target = "${context.packageName}/$serviceClassName"
        val splitter = TextUtils.SimpleStringSplitter(':').apply { setString(enabled) }
        return splitter.any { it.equals(target, ignoreCase = true) }
    }

    fun accessibilitySettingsIntent(): Intent = Intent("android.settings.ACCESSIBILITY_SETTINGS")

    fun requestAddTile(context: Context, onResult: (Int) -> Unit): Boolean {
        if (Build.VERSION.SDK_INT < 33) return false
        val manager = context.getSystemService(StatusBarManager::class.java) ?: return false
        manager.requestAddTileService(
            ComponentName(context, LookupTileService::class.java),
            context.getString(R.string.tile_label),
            Icon.createWithResource(context, R.drawable.ic_tile),
            { it.run() },
            { result -> onResult(result) },
        )
        return true
    }

    fun appInfoIntent(context: Context): Intent =
        Intent("android.settings.APPLICATION_DETAILS_SETTINGS", Uri.parse("package:${context.packageName}"))

    fun needsNotificationRuntimePermission(): Boolean = Build.VERSION.SDK_INT >= 33
}
