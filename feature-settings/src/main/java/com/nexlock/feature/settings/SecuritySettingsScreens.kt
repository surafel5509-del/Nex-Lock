package com.nexlock.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BatterySaver
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nexlock.common.model.SecuritySeverity
import com.nexlock.common.util.Formatters
import com.nexlock.core.permissions.PermissionStatus
import com.nexlock.design.components.EmptyState
import com.nexlock.design.components.GlassCard
import com.nexlock.design.components.ListSkeleton
import com.nexlock.design.components.NexConfirmDialog
import com.nexlock.design.components.NexFilterChip
import com.nexlock.design.components.NexTopBar
import com.nexlock.design.components.PermissionCard
import com.nexlock.design.components.SettingsActionRow
import com.nexlock.design.components.SettingsGroup
import com.nexlock.design.components.SettingsInfoRow
import com.nexlock.design.components.SettingsNavRow
import com.nexlock.design.components.SettingsToggleRow
import com.nexlock.design.components.StatusPill
import com.nexlock.design.components.clickableNoRipple
import com.nexlock.design.components.securityEventIcon
import com.nexlock.design.strings.LocalStrings
import com.nexlock.design.theme.LocalNexColors

@Composable
fun SecuritySettingsRoute(
    onBack: () -> Unit,
    onOpenSecurityLog: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LaunchedEffect(Unit) { viewModel.refresh() }

    SecuritySettingsScreen(
        state = state,
        modifier = modifier,
        onBack = onBack,
        onOpenSecurityLog = onOpenSecurityLog,
        onOpenAccessibility = { SystemSettingsIntents.openAccessibility(context) },
        onOpenUsageAccess = { SystemSettingsIntents.openUsageAccess(context) },
        onOpenNotifications = { SystemSettingsIntents.openNotificationSettings(context) },
        onOpenBattery = { SystemSettingsIntents.openBatteryOptimization(context) },
        onSetScreenshotsBlocked = viewModel::setScreenshotsBlocked,
        onSetSecureClipboard = viewModel::setSecureClipboard,
        onSetLockTimeout = viewModel::setLockTimeout,
        onSetRelockOnScreenOff = viewModel::setRelockOnScreenOff,
        onSetShowRiskWarnings = viewModel::setShowRiskWarnings,
        onSetLogRetention = viewModel::setLogRetention,
    )
}

@Composable
fun SecuritySettingsScreen(
    state: SettingsUiState,
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
    onOpenSecurityLog: () -> Unit = {},
    onOpenAccessibility: () -> Unit = {},
    onOpenUsageAccess: () -> Unit = {},
    onOpenNotifications: () -> Unit = {},
    onOpenBattery: () -> Unit = {},
    onSetScreenshotsBlocked: (Boolean) -> Unit = {},
    onSetSecureClipboard: (Boolean) -> Unit = {},
    onSetLockTimeout: (Int) -> Unit = {},
    onSetRelockOnScreenOff: (Boolean) -> Unit = {},
    onSetShowRiskWarnings: (Boolean) -> Unit = {},
    onSetLogRetention: (Int) -> Unit = {},
) {
    val strings = LocalStrings.current
    val colors = LocalNexColors.current
    val permissions = state.posture?.permissions ?: PermissionStatus()
    val risk = state.posture?.risk

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        NexTopBar(title = strings.security, onBack = onBack)
        Column(modifier = Modifier.padding(horizontal = 20.dp)) {
            Text(
                "Monitoring",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 8.dp, bottom = 8.dp),
            )
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                PermissionCard(
                    title = "Real-time monitoring (accessibility)",
                    description = "Shows the lock screen the instant a protected app opens. Recommended.",
                    granted = permissions.accessibilityServiceEnabled,
                    actionLabel = "Open accessibility settings",
                    onAction = onOpenAccessibility,
                )
                PermissionCard(
                    title = "Usage access fallback",
                    description = "Polling-based detection when accessibility is off. Adds ~1 second of latency.",
                    granted = permissions.usageAccessGranted,
                    actionLabel = "Open usage access",
                    onAction = onOpenUsageAccess,
                )
                PermissionCard(
                    title = "Notifications",
                    description = "Security alerts and protection status. No protected app names are shown.",
                    granted = permissions.notificationsGranted,
                    actionLabel = "Open notification settings",
                    onAction = onOpenNotifications,
                )
            }

            Spacer(Modifier.height(16.dp))

            SettingsGroup(title = "Privacy") {
                SettingsToggleRow(
                    icon = Icons.Rounded.Smartphone,
                    title = "Block screenshots",
                    subtitle = "FLAG_SECURE on sensitive screens and the recent-apps thumbnail",
                    checked = state.settings.screenshotsBlocked,
                    onCheckedChange = onSetScreenshotsBlocked,
                )
                SettingsToggleRow(
                    icon = Icons.Rounded.ContentCopy,
                    title = "Sensitive clipboard handling",
                    subtitle = "Mark copied secrets as sensitive on Android 13+",
                    checked = state.settings.secureClipboardEnabled,
                    onCheckedChange = onSetSecureClipboard,
                )
            }

            Spacer(Modifier.height(14.dp))

            SettingsGroup(title = "Locking") {
                Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                    Text(
                        "Re-lock timeout",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        "How long an app stays unlocked after authentication.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(0 to "Instant", 30 to "30s", 60 to "1m", 300 to "5m", 900 to "15m").forEach { (seconds, label) ->
                            NexFilterChip(
                                label = label,
                                selected = state.settings.lockTimeoutSeconds == seconds,
                                onClick = { onSetLockTimeout(seconds) },
                            )
                        }
                    }
                }
                SettingsToggleRow(
                    icon = Icons.Rounded.Timer,
                    title = "Re-lock when leaving an app",
                    subtitle = "Clear an instant-unlock session as soon as you switch apps",
                    checked = state.settings.relockOnScreenOff,
                    onCheckedChange = onSetRelockOnScreenOff,
                )
            }

            Spacer(Modifier.height(14.dp))

            SettingsGroup(title = "Device integrity") {
                SettingsNavRow(
                    icon = Icons.Rounded.BugReport,
                    title = strings.securityLog,
                    subtitle = "Local security journal",
                    onClick = onOpenSecurityLog,
                    trailingText = "${state.eventCount}",
                )
                SettingsToggleRow(
                    icon = Icons.Rounded.Warning,
                    title = "Show risk warnings",
                    subtitle = "Surface root, debugger and emulator indicators",
                    checked = state.settings.showRiskWarnings,
                    onCheckedChange = onSetShowRiskWarnings,
                )
                SettingsInfoRow(
                    label = "Keystore security level",
                    value = if (state.posture?.hardwareBackedKey == true) "Hardware-backed" else "Software-backed",
                )
                SettingsInfoRow(
                    label = "Signature check",
                    value = if (risk?.isSignatureValid == true) "Passed" else "Not configured",
                )
                SettingsInfoRow(
                    label = "Root indicators",
                    value = if (risk?.isRooted == true) "Detected" else "None",
                )
                SettingsActionRow(
                    icon = Icons.Rounded.BatterySaver,
                    title = "Battery optimisation",
                    subtitle = if (permissions.batteryOptimizationIgnored) {
                        "NexLock is excluded from battery optimisation"
                    } else {
                        "Exclude NexLock so the monitor is not killed in the background"
                    },
                    onClick = onOpenBattery,
                )
            }

            if (risk != null && risk.signals.isNotEmpty() && state.settings.showRiskWarnings) {
                Spacer(Modifier.height(14.dp))
                GlassCard(borderColor = colors.warning.copy(alpha = 0.35f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Warning, null, tint = colors.warning, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "These are indicators, not guarantees. App-level protection can be bypassed on a " +
                                "compromised device.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    risk.signals.forEach { signal ->
                        Text(
                            "• $signal",
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.warning,
                        )
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            SettingsGroup(title = "Log retention") {
                Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(7, 30, 90, 365).forEach { days ->
                            NexFilterChip(
                                label = "$days days",
                                selected = state.settings.securityLogRetentionDays == days,
                                onClick = { onSetLogRetention(days) },
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
fun NotificationSettingsRoute(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    NotificationSettingsScreen(
        state = state,
        modifier = modifier,
        onBack = onBack,
        onOpenSystemSettings = { SystemSettingsIntents.openNotificationSettings(context) },
        onSetEnabled = viewModel::setNotificationsEnabled,
        onSetIncidents = viewModel::setNotifyIncidents,
        onSetFailedAuth = viewModel::setNotifyFailedAuth,
        onSetPermissionIssues = viewModel::setNotifyPermissionIssues,
        onSetProtectionState = viewModel::setNotifyProtectionState,
    )
}

@Composable
fun NotificationSettingsScreen(
    state: SettingsUiState,
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
    onOpenSystemSettings: () -> Unit = {},
    onSetEnabled: (Boolean) -> Unit = {},
    onSetIncidents: (Boolean) -> Unit = {},
    onSetFailedAuth: (Boolean) -> Unit = {},
    onSetPermissionIssues: (Boolean) -> Unit = {},
    onSetProtectionState: (Boolean) -> Unit = {},
) {
    val strings = LocalStrings.current
    val granted = state.posture?.permissions?.notificationsGranted == true
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        NexTopBar(title = strings.notifications, onBack = onBack)
        Column(modifier = Modifier.padding(horizontal = 20.dp)) {
            if (!granted) {
                PermissionCard(
                    title = "Notification permission",
                    description = "Android 13+ requires an explicit permission before NexLock can alert you.",
                    granted = false,
                    actionLabel = "Open system settings",
                    onAction = onOpenSystemSettings,
                )
                Spacer(Modifier.height(12.dp))
            }
            SettingsGroup(title = "Alerts") {
                SettingsToggleRow(
                    icon = Icons.Rounded.Notifications,
                    title = "Enable notifications",
                    checked = state.settings.notificationsEnabled,
                    onCheckedChange = onSetEnabled,
                )
                SettingsToggleRow(
                    icon = Icons.Rounded.BugReport,
                    title = "Security incidents",
                    subtitle = "Intruder detections and tampering indicators",
                    checked = state.settings.notifyIncidents,
                    enabled = state.settings.notificationsEnabled,
                    onCheckedChange = onSetIncidents,
                )
                SettingsToggleRow(
                    icon = Icons.Rounded.Security,
                    title = "Failed unlock attempts",
                    subtitle = "Alert after incorrect credentials",
                    checked = state.settings.notifyFailedAuth,
                    enabled = state.settings.notificationsEnabled,
                    onCheckedChange = onSetFailedAuth,
                )
                SettingsToggleRow(
                    icon = Icons.Rounded.Warning,
                    title = "Permission problems",
                    subtitle = "When protection stops working because a permission was revoked",
                    checked = state.settings.notifyPermissionIssues,
                    enabled = state.settings.notificationsEnabled,
                    onCheckedChange = onSetPermissionIssues,
                )
                SettingsToggleRow(
                    icon = Icons.Rounded.Key,
                    title = "Protection status",
                    subtitle = "A quiet notification while protection is active",
                    checked = state.settings.notifyProtectionState,
                    enabled = state.settings.notificationsEnabled,
                    onCheckedChange = onSetProtectionState,
                )
            }
            Spacer(Modifier.height(12.dp))
            Text(
                "Notifications never include protected app names or private content.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 8.dp),
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
fun SecurityLogRoute(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SecurityLogViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    SecurityLogScreen(
        state = state,
        modifier = modifier,
        onBack = onBack,
        onSetFilter = viewModel::setFilter,
        onClear = viewModel::clear,
    )
}

@Composable
fun SecurityLogScreen(
    state: SecurityLogUiState,
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
    onSetFilter: (SecuritySeverity?) -> Unit = {},
    onClear: () -> Unit = {},
) {
    val colors = LocalNexColors.current
    var confirmClear by remember { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxSize()) {
        NexTopBar(
            title = "Security log",
            subtitle = "Local journal · never uploaded",
            onBack = onBack,
            actions = {
                Text(
                    "Clear",
                    style = MaterialTheme.typography.labelLarge,
                    color = colors.danger,
                    modifier = Modifier
                        .padding(end = 12.dp)
                        .clickableNoRipple { confirmClear = true },
                )
            },
        )
        Row(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            NexFilterChip("All", state.filter == null) { onSetFilter(null) }
            NexFilterChip("Info", state.filter == SecuritySeverity.INFO) {
                onSetFilter(SecuritySeverity.INFO)
            }
            NexFilterChip("Warning", state.filter == SecuritySeverity.WARNING) {
                onSetFilter(SecuritySeverity.WARNING)
            }
            NexFilterChip("Critical", state.filter == SecuritySeverity.CRITICAL) {
                onSetFilter(SecuritySeverity.CRITICAL)
            }
        }
        when {
            state.loading -> ListSkeleton(rows = 8)
            state.events.isEmpty() -> EmptyState(
                icon = Icons.Rounded.Security,
                title = "No events",
                description = "Security events are recorded locally as you use NexLock.",
            )
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(state.events, key = { it.id }) { event ->
                    val tint = when (event.severity) {
                        SecuritySeverity.INFO -> colors.success
                        SecuritySeverity.WARNING -> colors.warning
                        SecuritySeverity.CRITICAL -> colors.danger
                    }
                    GlassCard(contentPadding = PaddingValues(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                securityEventIcon(event.type),
                                contentDescription = null,
                                tint = tint,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    event.message,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    "${Formatters.timestamp(event.timestamp)} · ${event.type.name.lowercase().replace('_', ' ')}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            StatusPill(
                                text = event.severity.name.lowercase().replaceFirstChar { it.uppercase() },
                                positive = event.severity == SecuritySeverity.INFO,
                            )
                        }
                    }
                }
            }
        }
    }

    if (confirmClear) {
        NexConfirmDialog(
            title = "Clear the security log?",
            message = "All locally stored security events will be deleted. This cannot be undone.",
            confirmLabel = "Clear log",
            destructive = true,
            onConfirm = {
                confirmClear = false
                onClear()
            },
            onDismiss = { confirmClear = false },
        )
    }
}
