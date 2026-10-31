package com.nexlock.feature.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nexlock.common.model.RecommendationTarget
import com.nexlock.common.model.SecurityLevel
import com.nexlock.common.model.SecurityRecommendation
import com.nexlock.common.model.SecuritySeverity
import com.nexlock.common.util.Formatters
import com.nexlock.core.apps.LockedApp
import com.nexlock.core.events.SecurityEvent
import com.nexlock.design.components.AnimatedSecurityShield
import com.nexlock.design.components.AppIcon
import com.nexlock.design.components.DashboardSkeleton
import com.nexlock.design.components.EmptyState
import com.nexlock.design.components.GlassCard
import com.nexlock.design.components.NexPrimaryButton
import com.nexlock.design.components.NexSecondaryButton
import com.nexlock.design.components.SectionHeader
import com.nexlock.design.components.StatusPill
import com.nexlock.design.components.clickableNoRipple
import com.nexlock.design.components.rememberNexHaptics
import com.nexlock.design.components.securityEventIcon
import com.nexlock.design.strings.LocalStrings
import com.nexlock.design.theme.LocalNexColors
import kotlinx.coroutines.delay

/** All navigation intents the dashboard can raise, wired by the app module. */
data class DashboardActions(
    val onOpenApps: () -> Unit = {},
    val onOpenSettings: () -> Unit = {},
    val onOpenVault: () -> Unit = {},
    val onOpenAuthentication: () -> Unit = {},
    val onOpenIntruder: () -> Unit = {},
    val onOpenAutomation: () -> Unit = {},
    val onOpenSecurityLog: () -> Unit = {},
    val onOpenAccessibilitySettings: () -> Unit = {},
    val onOpenUsageAccessSettings: () -> Unit = {},
    val onOpenNotificationSettings: () -> Unit = {},
)

@Composable
fun DashboardScreen(
    state: DashboardUiState,
    actions: DashboardActions,
    modifier: Modifier = Modifier,
    onToggleProtection: (Boolean) -> Unit = {},
    onLockAll: () -> Unit = {},
    onUnlockAll: () -> Unit = {},
    onStartTemporaryUnlock: () -> Unit = {},
    onEndTemporaryUnlock: () -> Unit = {},
) {
    val strings = LocalStrings.current
    if (state.loading) {
        DashboardSkeleton(modifier = modifier.fillMaxSize())
        return
    }

    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(state.temporaryUnlockUntilMillis) {
        while (state.temporaryUnlockUntilMillis > System.currentTimeMillis()) {
            now = System.currentTimeMillis()
            delay(1_000)
        }
        now = System.currentTimeMillis()
    }
    val tempUnlockRemaining = (state.temporaryUnlockUntilMillis - now).coerceAtLeast(0L)

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = strings.welcomeBack,
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Text(
                        text = strings.appTagline,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(LocalNexColors.current.glassFill)
                        .clickableNoRipple(onClick = actions.onOpenSettings),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Rounded.Settings,
                        contentDescription = strings.settingsTitle,
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
        }

        item {
            ProtectionHero(
                state = state,
                onToggleProtection = onToggleProtection,
                onStartTemporaryUnlock = onStartTemporaryUnlock,
                onEndTemporaryUnlock = onEndTemporaryUnlock,
                tempUnlockRemaining = tempUnlockRemaining,
            )
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MetricCard(
                    modifier = Modifier.weight(1f),
                    value = state.lockedCount.toString(),
                    label = strings.protectedApps,
                    icon = Icons.Rounded.Lock,
                    onClick = actions.onOpenApps,
                )
                MetricCard(
                    modifier = Modifier.weight(1f),
                    value = levelLabel(state.level),
                    label = strings.securityLevel,
                    icon = Icons.Rounded.Shield,
                    onClick = actions.onOpenSecurityLog,
                )
            }
        }

        item {
            QuickActions(
                state = state,
                onLockAll = onLockAll,
                onUnlockAll = onUnlockAll,
                actions = actions,
            )
        }

        if (state.protectedApps.isNotEmpty()) {
            item {
                SectionHeader(
                    title = strings.protectedApps,
                    subtitle = "${state.protectedApps.size} app(s) protected",
                    trailing = {
                        Text(
                            text = "Manage",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.clickableNoRipple(onClick = actions.onOpenApps),
                        )
                    },
                )
            }
            items(state.protectedApps.take(3)) { app ->
                ProtectedAppRow(app, onClick = actions.onOpenApps)
            }
        }

        if (state.recentEvents.isNotEmpty()) {
            item {
                SectionHeader(
                    title = strings.recentActivity,
                    subtitle = "Local only · never uploaded",
                    trailing = {
                        Text(
                            text = strings.securityLog,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.clickableNoRipple(actions.onOpenSecurityLog),
                        )
                    },
                )
            }
            items(state.recentEvents.take(4)) { event -> ActivityRow(event) }
        }

        item {
            if (state.recommendations.isEmpty()) {
                SectionHeader(title = strings.recommendations)
            }
        }
        if (state.recommendations.isEmpty()) {
            item {
                EmptyState(
                    icon = Icons.Rounded.Shield,
                    title = strings.allGood,
                    description = strings.allGoodDetail,
                )
            }
        } else {
            item { SectionHeader(title = strings.recommendations) }
            items(state.recommendations.take(3)) { recommendation ->
                RecommendationCard(recommendation, onClick = { navigate(actions, recommendation.target) })
            }
        }

        item {
            Spacer(Modifier.height(8.dp))
            Text(
                text = if (state.hardwareBackedKey) {
                    "Encryption key is protected by hardware (Keystore)"
                } else {
                    "Encryption key is stored in the Android Keystore"
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ProtectionHero(
    state: DashboardUiState,
    onToggleProtection: (Boolean) -> Unit,
    onStartTemporaryUnlock: () -> Unit,
    onEndTemporaryUnlock: () -> Unit,
    tempUnlockRemaining: Long,
) {
    val strings = LocalStrings.current
    val colors = LocalNexColors.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(30.dp))
            .background(colors.heroBrush)
    ) {
        Column(modifier = Modifier.padding(22.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AnimatedSecurityShield(
                    progress = state.score / 100f,
                    level = state.level,
                    modifier = Modifier.size(118.dp),
                )
                Spacer(Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    StatusPill(
                        text = if (state.protectionEnabled) strings.protectionActive else strings.protectionInactive,
                        positive = state.protectionEnabled,
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = "${state.score}",
                        style = MaterialTheme.typography.displayMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = "${strings.securityScore} · ${levelLabel(state.level)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (state.risk?.signals?.isNotEmpty() == true) {
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Rounded.Shield,
                        contentDescription = null,
                        tint = colors.warning,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = state.risk.signals.first(),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.warning,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            Spacer(Modifier.height(18.dp))
            if (!state.protectionEnabled) {
                NexPrimaryButton(
                    text = "Turn on protection",
                    onClick = { onToggleProtection(true) },
                    modifier = Modifier.fillMaxWidth(),
                    icon = Icons.Rounded.PowerSettingsNew,
                )
            } else if (tempUnlockRemaining > 0) {
                GlassCard(contentPadding = PaddingValues(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Rounded.Timer,
                            contentDescription = null,
                            tint = colors.warning,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = "Temporary unlock · ${Formatters.countdown(tempUnlockRemaining)} left",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = "End",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.clickableNoRipple(onEndTemporaryUnlock),
                        )
                    }
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    NexSecondaryButton(
                        text = "Temporary unlock",
                        onClick = onStartTemporaryUnlock,
                        modifier = Modifier.weight(1f),
                        icon = Icons.Rounded.Timer,
                    )
                    NexSecondaryButton(
                        text = "Turn off",
                        onClick = { onToggleProtection(false) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun MetricCard(
    value: String,
    label: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    GlassCard(modifier = modifier, onClick = onClick, contentPadding = PaddingValues(16.dp)) {
        Icon(icon, contentDescription = null, tint = LocalNexColors.current.glow, modifier = Modifier.size(20.dp))
        Spacer(Modifier.height(10.dp))
        Text(value, style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onSurface)
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun QuickActions(
    state: DashboardUiState,
    onLockAll: () -> Unit,
    onUnlockAll: () -> Unit,
    actions: DashboardActions,
) {
    val strings = LocalStrings.current
    Column {
        SectionHeader(title = strings.quickActions)
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ActionTile(
                modifier = Modifier.weight(1f),
                icon = Icons.Rounded.Lock,
                label = strings.lockAll,
                onClick = onLockAll,
            )
            ActionTile(
                modifier = Modifier.weight(1f),
                icon = Icons.Rounded.LockOpen,
                label = strings.unlockAll,
                onClick = onUnlockAll,
            )
            ActionTile(
                modifier = Modifier.weight(1f),
                icon = Icons.Rounded.Shield,
                label = strings.vaultTitle,
                onClick = actions.onOpenVault,
            )
            ActionTile(
                modifier = Modifier.weight(1f),
                icon = Icons.Rounded.Timer,
                label = strings.automation,
                onClick = actions.onOpenAutomation,
            )
        }
    }
}

@Composable
private fun ActionTile(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    GlassCard(modifier = modifier, onClick = onClick, contentPadding = PaddingValues(12.dp)) {
        Icon(icon, contentDescription = null, tint = LocalNexColors.current.glow, modifier = Modifier.size(20.dp))
        Spacer(Modifier.height(8.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ProtectedAppRow(app: LockedApp, onClick: () -> Unit) {
    GlassCard(onClick = onClick, contentPadding = PaddingValues(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AppIcon(packageName = app.packageName, label = app.appName, size = 38.dp)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    app.appName,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    app.category.name.lowercase().replaceFirstChar { it.uppercase() },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                Icons.Rounded.Lock,
                contentDescription = null,
                tint = LocalNexColors.current.success,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

@Composable
private fun ActivityRow(event: SecurityEvent) {
    val colors = LocalNexColors.current
    val tint = when (event.severity) {
        SecuritySeverity.INFO -> colors.success
        SecuritySeverity.WARNING -> colors.warning
        SecuritySeverity.CRITICAL -> colors.danger
    }
    GlassCard(contentPadding = PaddingValues(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(13.dp))
                    .background(tint.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    securityEventIcon(event.type),
                    contentDescription = null,
                    tint = tint,
                    modifier = Modifier.size(18.dp),
                )
            }
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
                    Formatters.timestamp(event.timestamp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun RecommendationCard(recommendation: SecurityRecommendation, onClick: () -> Unit) {
    val colors = LocalNexColors.current
    val tint = when (recommendation.severity) {
        SecuritySeverity.INFO -> colors.glow
        SecuritySeverity.WARNING -> colors.warning
        SecuritySeverity.CRITICAL -> colors.danger
    }
    GlassCard(onClick = onClick, borderColor = tint.copy(alpha = 0.35f)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    recommendation.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    recommendation.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Icon(
                Icons.Rounded.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun levelLabel(level: SecurityLevel): String = when (level) {
    SecurityLevel.OPTIMAL -> "Optimal"
    SecurityLevel.STRONG -> "Strong"
    SecurityLevel.FAIR -> "Fair"
    SecurityLevel.AT_RISK -> "At risk"
}

private fun navigate(actions: DashboardActions, target: RecommendationTarget) {
    when (target) {
        RecommendationTarget.APPS -> actions.onOpenApps()
        RecommendationTarget.AUTHENTICATION -> actions.onOpenAuthentication()
        RecommendationTarget.BIOMETRIC -> actions.onOpenAuthentication()
        RecommendationTarget.ACCESSIBILITY -> actions.onOpenAccessibilitySettings()
        RecommendationTarget.USAGE_ACCESS -> actions.onOpenUsageAccessSettings()
        RecommendationTarget.NOTIFICATIONS -> actions.onOpenNotificationSettings()
        RecommendationTarget.INTRUDER -> actions.onOpenIntruder()
        RecommendationTarget.SECURITY_LOG -> actions.onOpenSecurityLog()
        RecommendationTarget.AUTOMATION -> actions.onOpenAutomation()
    }
}
