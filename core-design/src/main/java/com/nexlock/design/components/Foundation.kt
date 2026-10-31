package com.nexlock.design.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nexlock.design.theme.LocalHapticsEnabled
import com.nexlock.design.theme.LocalNexColors
import com.nexlock.design.theme.NexRadius

/** Haptic helper that respects the global "haptics" preference. */
class NexHaptics(
    private val enabled: Boolean,
    private val perform: (HapticFeedbackType) -> Unit,
) {
    fun tap() {
        if (enabled) perform(HapticFeedbackType.LongPress)
    }
}

@Composable
fun rememberNexHaptics(): NexHaptics {
    val enabled = LocalHapticsEnabled.current
    val haptics = LocalHapticFeedback.current
    return NexHaptics(enabled) { type -> haptics.performHapticFeedback(type) }
}

/**
 * Translucent, subtly bordered surface used for nearly every card in NexLock.
 * Works on both themes because the fill/border tokens flip with the color scheme.
 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    shape: Shape = NexRadius.card,
    onClick: (() -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(18.dp),
    borderColor: Color? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = LocalNexColors.current
    Box(
        modifier = modifier
            .clip(shape)
            .background(
                Brush.verticalGradient(
                    listOf(colors.glassFill, colors.glassFill.copy(alpha = colors.glassFill.alpha * 0.55f))
                )
            )
            .border(1.dp, borderColor ?: colors.glassBorder, shape)
            .then(
                if (onClick != null) {
                    Modifier.clickable(role = Role.Button) { onClick() }
                } else {
                    Modifier
                }
            )
            .padding(contentPadding)
    ) {
        Column(content = content)
    }
}

/** Gradient-filled primary action. */
@Composable
fun NexPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    loading: Boolean = false,
) {
    val colors = LocalNexColors.current
    val haptics = rememberNexHaptics()
    val shape = RoundedCornerShape(18.dp)
    Box(
        modifier = modifier
            .height(54.dp)
            .clip(shape)
            .background(if (enabled) colors.accentBrush else Brush.linearGradient(listOf(Color(0xFF2A3244), Color(0xFF2A3244))))
            .clickable(enabled = enabled && !loading, role = Role.Button) {
                haptics.tap()
                onClick()
            },
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (loading) {
                androidx.compose.material3.CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = Color.White,
                )
                Spacer(Modifier.width(10.dp))
            } else if (icon != null) {
                Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
            }
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
fun NexSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
) {
    val colors = LocalNexColors.current
    val haptics = rememberNexHaptics()
    Box(
        modifier = modifier
            .height(52.dp)
            .clip(RoundedCornerShape(18.dp))
            .border(1.dp, colors.glassBorder, RoundedCornerShape(18.dp))
            .clickable(enabled = enabled, role = Role.Button) {
                haptics.tap()
                onClick()
            }
            .padding(horizontal = 20.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
            }
            Text(
                text,
                style = MaterialTheme.typography.labelLarge,
                color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        trailing?.invoke()
    }
}

@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    description: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val colors = LocalNexColors.current
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(84.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(Brush.linearGradient(colors.accentGradient.map { it.copy(alpha = 0.16f) })),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = colors.glow, modifier = Modifier.size(36.dp))
        }
        Spacer(Modifier.height(16.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
        Spacer(Modifier.height(6.dp))
        Text(
            description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 24.dp),
        )
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(18.dp))
            NexSecondaryButton(text = actionLabel, onClick = onAction)
        }
    }
}

/** Minimal screen header used by every feature screen (no experimental TopAppBar APIs). */
@Composable
fun NexTopBar(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    actions: (@Composable androidx.compose.foundation.layout.RowScope.() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            val haptics = rememberNexHaptics()
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .clickable(role = Role.Button) {
                        haptics.tap()
                        onBack()
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    androidx.compose.material.icons.Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
            Spacer(Modifier.width(12.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (actions != null) {
            Row(verticalAlignment = Alignment.CenterVertically, content = actions)
        }
    }
}

@Composable
fun StatusPill(
    text: String,
    positive: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = LocalNexColors.current
    val color = if (positive) colors.success else colors.warning
    Row(
        modifier = modifier
            .clip(NexRadius.chip)
            .background(color.copy(alpha = 0.14f))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(7.dp)
                .clip(NexRadius.chip)
                .background(color)
        )
        Spacer(Modifier.width(7.dp))
        Text(text, style = MaterialTheme.typography.labelMedium, color = color)
    }
}

/**
 * Clickable without the Material ripple, used inside already-styled custom surfaces.
 * Kept as a `@Composable` extension so the interaction source is remembered once.
 */
@Composable
fun Modifier.clickableNoRipple(
    enabled: Boolean = true,
    role: Role = Role.Button,
    onClick: () -> Unit,
): Modifier {
    val interaction = androidx.compose.foundation.interaction.MutableInteractionSource()
    return this.then(
        Modifier.clickable(
            interactionSource = interaction,
            indication = null,
            enabled = enabled,
            role = role,
            onClick = onClick,
        )
    )
}
