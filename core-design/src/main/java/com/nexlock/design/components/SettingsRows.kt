package com.nexlock.design.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nexlock.design.theme.LocalNexColors

/** Grouped settings card with an optional caption. */
@Composable
fun SettingsGroup(
    title: String? = null,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        if (title != null) {
            Text(
                text = title.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 8.dp, bottom = 8.dp, top = 4.dp),
            )
        }
        GlassCard(
            contentPadding = PaddingValues(vertical = 4.dp),
            content = content,
        )
    }
}

@Composable
fun RowIcon(icon: ImageVector, tint: androidx.compose.ui.graphics.Color? = null) {
    val colors = LocalNexColors.current
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Brush.linearGradient(colors.accentGradient.map { it.copy(alpha = 0.16f) })),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = tint ?: colors.glow, modifier = Modifier.size(20.dp))
    }
}

@Composable
fun SettingsToggleRow(
    icon: ImageVector,
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    enabled: Boolean = true,
) {
    val haptics = rememberNexHaptics()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, role = Role.Switch) {
                haptics.tap()
                onCheckedChange(!checked)
            }
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RowIcon(icon)
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        Switch(
            checked = checked,
            onCheckedChange = {
                haptics.tap()
                onCheckedChange(it)
            },
            enabled = enabled,
            colors = SwitchDefaults.colors(
                checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                checkedTrackColor = MaterialTheme.colorScheme.primary,
            ),
        )
    }
}

@Composable
fun SettingsNavRow(
    icon: ImageVector,
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    trailingText: String? = null,
) {
    val haptics = rememberNexHaptics()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(role = Role.Button) {
                haptics.tap()
                onClick()
            }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RowIcon(icon)
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                )
            }
        }
        if (trailingText != null) {
            Text(
                trailingText,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(6.dp))
        }
        Icon(
            androidx.compose.material.icons.Icons.Rounded.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
fun SettingsActionRow(
    icon: ImageVector,
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    destructive: Boolean = false,
) {
    val colors = LocalNexColors.current
    val tint = if (destructive) colors.danger else null
    val haptics = rememberNexHaptics()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(role = Role.Button) {
                haptics.tap()
                onClick()
            }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RowIcon(icon, tint)
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                color = tint ?: MaterialTheme.colorScheme.onSurface,
            )
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
fun SettingsInfoRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            value,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Card explaining a permission with a direct call to action. */
@Composable
fun PermissionCard(
    title: String,
    description: String,
    granted: Boolean,
    actionLabel: String,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalNexColors.current
    GlassCard(
        modifier = modifier.fillMaxWidth(),
        borderColor = if (granted) colors.success.copy(alpha = 0.4f) else colors.warning.copy(alpha = 0.4f),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
                Spacer(Modifier.height(4.dp))
                Text(
                    description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(12.dp))
            StatusPill(text = if (granted) "Granted" else "Required", positive = granted)
        }
        if (!granted) {
            Spacer(Modifier.height(12.dp))
            NexSecondaryButton(text = actionLabel, onClick = onAction)
        }
    }
}
