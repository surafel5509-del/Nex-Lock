package com.nexlock.security.privacy

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.PersistableBundle
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Clipboard helper that marks NexLock copies as sensitive on Android 13+ so the system clipboard
 * preview does not show them, and lets the UI wipe the clipboard when leaving a screen.
 */
@Singleton
class SecureClipboard @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    fun copySensitive(label: String, text: String) {
        val clip = ClipData.newPlainText(label, text)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            clip.description.extras = PersistableBundle().apply {
                putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
            }
        }
        clipboard()?.setPrimaryClip(clip)
    }

    fun copy(label: String, text: String) {
        clipboard()?.setPrimaryClip(ClipData.newPlainText(label, text))
    }

    fun clear() {
        val manager = clipboard() ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            manager.clearPrimaryClip()
        } else {
            manager.setPrimaryClip(ClipData.newPlainText("", ""))
        }
    }

    fun hasSensitiveContent(): Boolean {
        val description = clipboard()?.primaryClipDescription ?: return false
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            (description.extras?.getBoolean(ClipDescription.EXTRA_IS_SENSITIVE, false) ?: false)
    }

    private fun clipboard(): ClipboardManager? =
        context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
}
