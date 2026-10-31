package com.nexlock.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.nexlock.AppUiState
import com.nexlock.design.strings.LocalStrings
import com.nexlock.feature.applock.LockedAppsRoute
import com.nexlock.feature.auth.AuthManageRoute
import com.nexlock.feature.auth.AuthSetupRoute
import com.nexlock.feature.auth.ChangeCredentialRoute
import com.nexlock.feature.automation.AutomationRoute
import com.nexlock.feature.dashboard.DashboardActions
import com.nexlock.feature.dashboard.DashboardScreen
import com.nexlock.feature.dashboard.DashboardViewModel
import com.nexlock.feature.intruder.IntruderRoute
import com.nexlock.feature.settings.AboutRoute
import com.nexlock.feature.settings.AppearanceRoute
import com.nexlock.feature.settings.BackupRoute
import com.nexlock.feature.settings.LegalContent
import com.nexlock.feature.settings.LegalScreen
import com.nexlock.feature.settings.NotificationSettingsRoute
import com.nexlock.feature.settings.SecurityLogRoute
import com.nexlock.feature.settings.SecuritySettingsRoute
import com.nexlock.feature.settings.SettingsActions
import com.nexlock.feature.settings.SettingsRoute
import com.nexlock.feature.settings.SystemSettingsIntents
import com.nexlock.feature.vault.VaultRoute

private data class ShellTab(val route: String, val icon: ImageVector, val label: String)

@Composable
fun NexLockNavHost(
    state: AppUiState,
    onRefresh: () -> Unit,
    navController: NavHostController = rememberNavController(),
) {
    val strings = LocalStrings.current
    val context = LocalContext.current
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    val tabs = remember(strings) {
        listOf(
            ShellTab(Routes.DASHBOARD, Icons.Rounded.Home, strings.navDashboard),
            ShellTab(Routes.APPS, Icons.Rounded.Apps, strings.navApps),
            ShellTab(Routes.VAULT, Icons.Rounded.Lock, strings.navVault),
            ShellTab(Routes.SETTINGS, Icons.Rounded.Settings, strings.navSettings),
        )
    }

    val dashboardActions = DashboardActions(
        onOpenApps = { navController.switchTab(Routes.APPS) },
        onOpenSettings = { navController.switchTab(Routes.SETTINGS) },
        onOpenVault = { navController.switchTab(Routes.VAULT) },
        onOpenAuthentication = { navController.navigate(Routes.AUTH_MANAGE) },
        onOpenIntruder = { navController.navigate(Routes.INTRUDER) },
        onOpenAutomation = { navController.navigate(Routes.AUTOMATION) },
        onOpenSecurityLog = { navController.navigate(Routes.SECURITY_LOG) },
        onOpenAccessibilitySettings = { SystemSettingsIntents.openAccessibility(context) },
        onOpenUsageAccessSettings = { SystemSettingsIntents.openUsageAccess(context) },
        onOpenNotificationSettings = { SystemSettingsIntents.openNotificationSettings(context) },
    )

    val settingsActions = SettingsActions(
        onOpenAuthentication = { navController.navigate(Routes.AUTH_MANAGE) },
        onOpenIntruder = { navController.navigate(Routes.INTRUDER) },
        onOpenAutomation = { navController.navigate(Routes.AUTOMATION) },
        onOpenAppearance = { navController.navigate(Routes.APPEARANCE) },
        onOpenSecurity = { navController.navigate(Routes.SECURITY) },
        onOpenNotifications = { navController.navigate(Routes.NOTIFICATIONS) },
        onOpenBackup = { navController.navigate(Routes.BACKUP) },
        onOpenSecurityLog = { navController.navigate(Routes.SECURITY_LOG) },
        onOpenAbout = { navController.navigate(Routes.ABOUT) },
        onOpenPrivacy = { navController.navigate(Routes.PRIVACY) },
        onOpenTerms = { navController.navigate(Routes.TERMS) },
        onOpenVault = { navController.switchTab(Routes.VAULT) },
        onOpenApps = { navController.switchTab(Routes.APPS) },
    )

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            if (currentRoute != null && currentRoute in Routes.topLevel) {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                    tabs.forEach { tab ->
                        NavigationBarItem(
                            selected = currentRoute == tab.route,
                            onClick = { navController.switchTab(tab.route) },
                            icon = { Icon(tab.icon, contentDescription = tab.label) },
                            label = { Text(tab.label) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                            ),
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Routes.DASHBOARD,
            modifier = Modifier.padding(padding),
        ) {
            composable(Routes.DASHBOARD) {
                val dashboardViewModel: DashboardViewModel = hiltViewModel()
                val dashboardState by dashboardViewModel.state.collectAsStateWithLifecycle()
                DashboardScreen(
                    state = dashboardState,
                    actions = dashboardActions,
                    onToggleProtection = dashboardViewModel::setProtectionEnabled,
                    onLockAll = dashboardViewModel::lockAll,
                    onUnlockAll = dashboardViewModel::unlockAll,
                    onStartTemporaryUnlock = dashboardViewModel::startTemporaryUnlock,
                    onEndTemporaryUnlock = dashboardViewModel::endTemporaryUnlock,
                )
            }

            composable(Routes.APPS) {
                LockedAppsRoute()
            }

            composable(Routes.VAULT) {
                VaultRoute()
            }

            composable(Routes.SETTINGS) {
                SettingsRoute(actions = settingsActions)
            }

            composable(Routes.AUTH_SETUP) {
                AuthSetupRoute(
                    onDone = {
                        onRefresh()
                        navController.popBackStack()
                    },
                    showSkip = true,
                )
            }

            composable(Routes.AUTH_MANAGE) {
                AuthManageRoute(
                    onBack = { navController.popBackStack() },
                    onChangeCredential = { navController.navigate(Routes.AUTH_CHANGE) },
                    onSetupMissingCredential = { navController.navigate(Routes.AUTH_SETUP) },
                )
            }

            composable(Routes.AUTH_CHANGE) {
                ChangeCredentialRoute(
                    onBack = { navController.popBackStack() },
                    onSuccess = {
                        onRefresh()
                        navController.popBackStack()
                    },
                )
            }

            composable(Routes.INTRUDER) {
                IntruderRoute(onBack = { navController.popBackStack() })
            }

            composable(Routes.AUTOMATION) {
                AutomationRoute(onBack = { navController.popBackStack() })
            }

            composable(Routes.APPEARANCE) {
                AppearanceRoute(onBack = { navController.popBackStack() })
            }

            composable(Routes.SECURITY) {
                SecuritySettingsRoute(
                    onBack = { navController.popBackStack() },
                    onOpenSecurityLog = { navController.navigate(Routes.SECURITY_LOG) },
                )
            }

            composable(Routes.NOTIFICATIONS) {
                NotificationSettingsRoute(onBack = { navController.popBackStack() })
            }

            composable(Routes.SECURITY_LOG) {
                SecurityLogRoute(onBack = { navController.popBackStack() })
            }

            composable(Routes.BACKUP) {
                BackupRoute(onBack = { navController.popBackStack() })
            }

            composable(Routes.ABOUT) {
                AboutRoute(
                    onBack = { navController.popBackStack() },
                    onOpenPrivacy = { navController.navigate(Routes.PRIVACY) },
                    onOpenTerms = { navController.navigate(Routes.TERMS) },
                )
            }

            composable(Routes.PRIVACY) {
                LegalScreen(
                    title = strings.privacyPolicy,
                    content = LegalContent.privacyPolicy,
                    onBack = { navController.popBackStack() },
                )
            }

            composable(Routes.TERMS) {
                LegalScreen(
                    title = strings.termsOfService,
                    content = LegalContent.termsOfService,
                    onBack = { navController.popBackStack() },
                )
            }
        }
    }

    // Keeps the monitor aligned when the user returns from a system permission screen.
    LaunchedEffect(currentRoute) { onRefresh() }
}

/**
 * Tab switches reuse the existing back-stack entry so each tab keeps its scroll position and state.
 */
private fun NavHostController.switchTab(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
