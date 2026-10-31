package com.nexlock

import android.app.Application
import android.content.pm.ApplicationInfo
import com.nexlock.common.util.NexLog
import com.nexlock.core.notifications.NexNotifier
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class NexLockApplication : Application() {

    @Inject
    lateinit var notifier: NexNotifier

    override fun onCreate() {
        super.onCreate()
        // Logging is a debug-only facility; release builds stay silent.
        NexLog.setEnabled((applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0)
        notifier.ensureChannels()
    }
}
