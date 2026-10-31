package com.nexlock.feature.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Autorenew
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.ColorLens
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.PrivacyTip
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material.icons.rounded.Vibration
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nexlock.common.model.AppLanguage
import com.nexlock.common.model.RecommendationTarget
import com.nexlock.common.model.SecurityLevel
import com.nexlock.common.model.ThemeMode
import com.nexlock.common.util.Formatters
import com.nexlock.core.security.SecurityPosture
import com.nexlock.design.components.EmptyState
import com.nexlock.design.components.GlassCard
import com.nexlock.design.components.ListSkeleton
import com.nexlock.design.components.NexConfirmDialog
import com.nexlock.design.components.NexFilterChip
import com.nexlock.design.components.NexTopBar
import com.nexlock.design.components.PermissionCard
import com.nexlock.design.components.SettingsGroup
import com.nexlock.design.components.SettingsInfoRow
import com.nexlock.design.components.SettingsActionRow
import com.nexlock.design.components.SettingsNavRow
import com.nexlock.design.components.SettingsToggleRow
import com.nexlock.design.components.StatusPill
import com.nexlock.design.components.securityEventIcon
import com.nexlock.design.strings.LocalStrings
import com.nexlock.design.theme.LocalNexColors
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember

/** Navigation callbacks for everything the settings surface can reach. */
data class SettingsActions(
    val onOpenAuthentication: () -> Unit = {},
    val onOpenIntruder: () -> Unit = {},
    val onOpenAutomation: () -> Unit = {},
    val onOpenAppearance: () -> Unit = {},
    val onOpenSecurity: () -> Unit = {},
    val onOpenNotifications: () -> Unit = {},
    val onOpenBackup: () -> Unit = {},
    val onOpenSecurityLog: () -> Unit = {},
    val onOpenAbout: () -> Unit = {},
    val onOpenPrivacy: () -> Unit = {},
    val onOpenTerms: () -> Unit = {},
    val onOpenVault: () -> Unit = {},
    val onOpenApps: () -> Unit = {},
)

@Composable
fun SettingsRoute(
    actions: SettingsActions,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.refresh() }
    SettingsHubScreen(
        state = state,
        actions = actions,
        modifier = modifier,
        onSetProtection = viewModel::setProtectionEnabled,
    )
}

@Composable
fun SettingsHubScreen(
    state: SettingsUiState,
    actions: SettingsActions,
    modifier: Modifier = Modifier,
    onSetProtection: (Boolean) -> Unit = {},
) {
    val strings = LocalStrings.current
    val posture = state.posture

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        NexTopBar(title = strings.settingsTitle, subtitle = "NexLock ${state.settings.authMode.name.lowercase()}")
        Column(modifier = Modifier.padding(horizontal = 20.dp)) {
            ProtectionCard(state, onSetProtection)
            Spacer(Modifier.height(16.dp))

            SettingsGroup(title = "Protection") {
                SettingsNavRow(
                    icon = Icons.Rounded.Fingerprint,
                    title = strings.authentication,
                    subtitle = "Unlock method, biometrics, recovery",
                    onClick = actions.onOpenAuthentication,
                )
                SettingsNavRow(
                    icon = Icons.Rounded.PhotoCamera,
                    title = strings.intruderDetection,
                    subtitle = "Repeated failure detection and captures",
                    onClick = actions.onOpenIntruder,
                    trailingText = if (state.settings.intruderDetectionEnabled) "On" else "Off",
                )
                SettingsNavRow(
                    icon = Icons.Rounded.Autorenew,
                    title = strings.automation,
                    subtitle = "Schedules, trusted networks and places",
                    onClick = actions.onOpenAutomation,
                )
                SettingsNavRow(
                    icon = Icons.Rounded.Security,
                    title = strings.security,
                    subtitle = "Screenshots, clipboard, timeout, permissions",
                    onClick = actions.onOpenSecurity,
                )
                SettingsNavRow(
                    icon = Icons.Rounded.BugReport,
                    title = strings.securityLog,
                    subtitle = "${state.eventCount} local events",
                    onClick = actions.onOpenSecurityLog,
                )
            }

            Spacer(Modifier.height(14.dp))

            SettingsGroup(title = "Personalisation") {
                SettingsNavRow(
                    icon = Icons.Rounded.Palette,
                    title = strings.appearance,
                    subtitle = "Theme, language, haptics",
                    onClick = actions.onOpenAppearance,
                )
                SettingsNavRow(
                    icon = Icons.Rounded.Notifications,
                    title = strings.notifications,
                    subtitle = "Security alerts and status",
                    onClick = actions.onOpenNotifications,
                    trailingText = if (state.settings.notificationsEnabled) "On" else "Off",
                )
            }

            Spacer(Modifier.height(14.dp))

            SettingsGroup(title = "Data") {
                SettingsNavRow(
                    icon = Icons.Rounded.Backup,
                    title = strings.backup,
                    subtitle = "Encrypted backup and restore",
                    onClick = actions.onOpenBackup,
                )
                SettingsNavRow(
                    icon = Icons.Rounded.Description,
                    title = strings.vaultTitle,
                    subtitle = "${Formatters.fileSize(state.vaultBytes)} encrypted",
                    onClick = actions.onOpenVault,
                )
            }

            Spacer(Modifier.height(14.dp))

            SettingsGroup(title = "About") {
                SettingsNavRow(
                    icon = Icons.Rounded.Info,
                    title = strings.about,
                    onClick = actions.onOpenAbout,
                )
                SettingsNavRow(
                    icon = Icons.Rounded.PrivacyTip,
                    title = strings.privacyPolicy,
                    onClick = actions.onOpenPrivacy,
                )
                SettingsNavRow(
                    icon = Icons.Rounded.Description,
                    title = strings.termsOfService,
                    onClick = actions.onOpenTerms,
                )
            }

            posture?.let { p ->
                Spacer(Modifier.height(16.dp))
                Text(
                    "NexLock has no internet permission. Your data never leaves this device.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 8.dp),
                )
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun ProtectionCard(state: SettingsUiState, onSetProtection: (Boolean) -> Unit) {
    val colors = LocalNexColors.current
    val score = state.posture?.score?.score ?: 0
    GlassCard(borderColor = if (state.settings.protectionEnabled) colors.success.copy(alpha = 0.4f) else colors.warning.copy(alpha = 0.4f)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Shield, null, tint = colors.glow, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    if (state.settings.protectionEnabled) "Protection is on" else "Protection is off",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    "Security score $score · ${levelLabel(state.posture?.score?.level)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            StatusPill(
                text = if (state.settings.protectionEnabled) "Active" else "Off",
                positive = state.settings.protectionEnabled,
            )
        }
        Spacer(Modifier.height(12.dp))
        SettingsToggleRow(
            icon = Icons.Rounded.Lock,
            title = "Protection service",
            subtitle = "Turn off to temporarily stop enforcing locks",
            checked = state.settings.protectionEnabled,
            onCheckedChange = onSetProtection,
        )
    }
}

private fun levelLabel(level: SecurityLevel?): String = when (level) {
    SecurityLevel.OPTIMAL -> "Optimal"
    SecurityLevel.STRONG -> "Strong"
    SecurityLevel.FAIR -> "Fair"
    SecurityLevel.AT_RISK -> "At risk"
    null -> "Unknown"
}

@Composable
fun AppearanceRoute(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    AppearanceScreen(
        state = state,
        modifier = modifier,
        onBack = onBack,
        onSetTheme = viewModel::setTheme,
        onSetDynamicColor = viewModel::setDynamicColor,
        onSetHaptics = viewModel::setHaptics,
        onSetLanguage = viewModel::setLanguage,
    )
}

@Composable
fun AppearanceScreen(
    state: SettingsUiState,
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
    onSetTheme: (ThemeMode) -> Unit = {},
    onSetDynamicColor: (Boolean) -> Unit = {},
    onSetHaptics: (Boolean) -> Unit = {},
    onSetLanguage: (AppLanguage) -> Unit = {},
) {
    val strings = LocalStrings.current
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        NexTopBar(title = strings.appearance, onBack = onBack)
        Column(modifier = Modifier.padding(horizontal = 20.dp)) {
            SettingsGroup(title = "Theme") {
                Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                    Row {
                        listOf(
                            ThemeMode.SYSTEM to strings.systemDefault,
                            ThemeMode.DARK to strings.dark,
                            ThemeMode.LIGHT to strings.light,
                        ).forEach { (mode, label) ->
                            NexFilterChip(
                                label = label,
                                selected = state.settings.themeMode == mode,
                                onClick = { onSetTheme(mode) },
                                modifier = Modifier.padding(end = 8.dp),
                            )
                        }
                    }
                }
                SettingsToggleRow(
                    icon = Icons.Rounded.ColorLens,
                    title = "Dynamic colour",
                    subtitle = "Use Material You colours on Android 12 and newer",
                    checked = state.settings.dynamicColor,
                    onCheckedChange = onSetDynamicColor,
                )
                SettingsToggleRow(
                    icon = Icons.Rounded.Vibration,
                    title = "Haptic feedback",
                    subtitle = "Subtle vibration on key interactions",
                    checked = state.settings.hapticsEnabled,
                    onCheckedChange = onSetHaptics,
                )
            }

            Spacer(Modifier.height(14.dp))

            SettingsGroup(title = "Language") {
                Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                    AppLanguage.entries.forEach { language ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = when (language) {
                                    AppLanguage.ENGLISH -> "English"
                                    AppLanguage.AMHARIC -> "አማርኛ"
                                    AppLanguage.SPANISH -> "Español"
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f),
                            )
                            if (state.settings.language == language) {
                                StatusPill(text = "Selected", positive = true)
                            } else {
                                NexFilterChip(
                                    label = "Use",
                                    selected = false,
                                    onClick = { onSetLanguage(language) },
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "The interface language updates immediately. Legal text stays in English.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
