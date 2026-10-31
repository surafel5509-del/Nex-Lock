package com.nexlock.security.privacy

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext

/**
 * FLAG_SECURE prevents screenshots, screen recording and the recent-apps thumbnail from capturing
 * the window. Used on the lock screen, the vault and any screen that renders decrypted content.
 */
fun Activity.setScreenshotsBlocked(blocked: Boolean) {
    if (blocked) {
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
    } else {
        window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
    }
}

fun Context.findActivity(): Activity? {
    var current: Context? = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}

@Composable
fun SecureScreen(enabled: Boolean = true) {
    val activity = LocalContext.current.findActivity()
    DisposableEffect(activity, enabled) {
        activity?.setScreenshotsBlocked(enabled)
        onDispose { activity?.setScreenshotsBlocked(false) }
    }
}
