package com.nexlock

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nexlock.design.theme.NexLockTheme
import com.nexlock.feature.auth.AuthSetupRoute
import com.nexlock.navigation.NexLockNavHost
import com.nexlock.security.privacy.SecureScreen
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : FragmentActivity() {

    private val viewModel: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        setContent {
            val state by viewModel.state.collectAsStateWithLifecycle()
            NexLockTheme(
                themeMode = state.settings.themeMode,
                dynamicColor = state.settings.dynamicColor,
                hapticsEnabled = state.settings.hapticsEnabled,
                language = state.settings.language,
            ) {
                SecureScreen(enabled = state.settings.screenshotsBlocked)
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    when {
                        state.loading -> LoadingScreen()
                        // First launch: a credential is mandatory before any protection can be enabled.
                        !state.settings.onboardingCompleted && !state.settings.credentialConfigured ->
                            AuthSetupRoute(
                                onDone = viewModel::refresh,
                                showSkip = true,
                            )
                        else -> NexLockNavHost(
                            state = state,
                            onRefresh = viewModel::refresh,
                        )
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.onResume()
    }
}

@Composable
private fun LoadingScreen() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
    }
}
