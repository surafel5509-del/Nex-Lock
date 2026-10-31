package com.nexlock

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nexlock.design.theme.NexLockTheme
import com.nexlock.feature.applock.LockRoute
import com.nexlock.feature.applock.LockViewModel
import dagger.hilt.android.AndroidEntryPoint

/**
 * The authentication gate shown above a protected app.
 *
 * Security notes:
 * * lives in its own task and is excluded from recents;
 * * `Theme.NexLock.Lock` is translucent so it can sit above the protected app;
 * * screenshot protection is applied by the Compose screen (FLAG_SECURE), configurable by the user;
 * * pressing back sends the user home instead of revealing the protected app.
 */
@AndroidEntryPoint
class LockActivity : FragmentActivity() {

    private val appViewModel: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val uiState by appViewModel.state.collectAsStateWithLifecycle()
            NexLockTheme(
                themeMode = uiState.settings.themeMode,
                dynamicColor = uiState.settings.dynamicColor,
                hapticsEnabled = uiState.settings.hapticsEnabled,
                language = uiState.settings.language,
            ) {
                LockRoute(
                    onUnlocked = { finish() },
                    onBackToHome = {
                        moveTaskToBack(true)
                        finish()
                    },
                    onRequestBiometric = { viewModel -> viewModel.authenticateWithBiometrics(this) },
                )
            }
        }
    }

    companion object {
        /**
         * Extras are read by [LockViewModel] through its `SavedStateHandle`, which Hilt seeds from
         * the launching intent's extras. The keys must therefore match the view-model argument names.
         */
        const val EXTRA_PACKAGE = LockViewModel.ARG_PACKAGE
        const val EXTRA_APP_NAME = LockViewModel.ARG_APP_NAME

        fun intent(context: Context, packageName: String, appName: String): Intent =
            Intent(context, LockActivity::class.java).apply {
                putExtra(EXTRA_PACKAGE, packageName)
                putExtra(EXTRA_APP_NAME, appName)
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TASK or
                        Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
                )
            }
    }
}
