package com.nexlock.design.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.nexlock.common.model.SecurityLevel
import com.nexlock.design.theme.LocalNexColors
import kotlin.math.min

/** Shimmering placeholder used while data loads (never a fake empty screen). */
@Composable
fun ShimmerBox(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(12.dp),
) {
    val colors = LocalNexColors.current
    val transition = rememberInfiniteTransition(label = "shimmer")
    val offset by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1200f,
        animationSpec = infiniteRepeatable(tween(1400, easing = LinearEasing)),
        label = "shimmerOffset",
    )
    val base = if (colors.isDark) Color(0xFF16223A) else Color(0xFFE6EBF4)
    val highlight = if (colors.isDark) Color(0xFF22314F) else Color(0xFFF7FAFF)
    Box(
        modifier = modifier
            .clip(shape)
            .background(
                Brush.linearGradient(
                    colors = listOf(base, highlight, base),
                    start = Offset(offset - 400f, 0f),
                    end = Offset(offset, 400f),
                )
            )
    )
}

/** Skeleton scaffold approximating the dashboard layout. */
@Composable
fun DashboardSkeleton(modifier: Modifier = Modifier) {
    Column(modifier = modifier.padding(20.dp)) {
        ShimmerBox(Modifier.fillMaxWidth(0.45f).height(18.dp))
        Spacer(Modifier.height(12.dp))
        ShimmerBox(Modifier.fillMaxWidth().height(150.dp), RoundedCornerShape(28.dp))
        Spacer(Modifier.height(18.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ShimmerBox(Modifier.weight(1f).height(84.dp))
            ShimmerBox(Modifier.weight(1f).height(84.dp))
        }
        Spacer(Modifier.height(18.dp))
        repeat(3) {
            ShimmerBox(Modifier.fillMaxWidth().height(64.dp))
            Spacer(Modifier.height(10.dp))
        }
    }
}

@Composable
fun ListSkeleton(rows: Int = 6, modifier: Modifier = Modifier) {
    Column(modifier = modifier.padding(horizontal = 20.dp)) {
        repeat(rows) {
            ShimmerBox(Modifier.fillMaxWidth().height(68.dp))
            Spacer(Modifier.height(10.dp))
        }
    }
}

/**
 * Signature animated shield: a sweeping gradient ring whose filled arc reflects the security score,
 * plus two slowly rotating accent arcs and a breathing glow.
 */
@Composable
fun AnimatedSecurityShield(
    progress: Float,
    level: SecurityLevel,
    modifier: Modifier = Modifier,
    iconSize: Dp = 44.dp,
) {
    val colors = LocalNexColors.current
    val tint = when (level) {
        SecurityLevel.OPTIMAL, SecurityLevel.STRONG -> colors.success
        SecurityLevel.FAIR -> colors.warning
        SecurityLevel.AT_RISK -> colors.danger
    }
    val transition = rememberInfiniteTransition(label = "shield")
    val rotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(14000, easing = LinearEasing)),
        label = "shieldRotation",
    )
    val pulse by transition.animateFloat(
        initialValue = 0.97f,
        targetValue = 1.03f,
        animationSpec = infiniteRepeatable(tween(2600, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "shieldPulse",
    )
    val animatedProgress by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = tween(900),
        label = "shieldProgress",
    )

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val radius = min(size.width, size.height) / 2f
            val ringRadius = radius * 0.9f
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(tint.copy(alpha = 0.22f), Color.Transparent),
                    center = center,
                    radius = radius,
                ),
                radius = radius,
                center = center,
            )
            drawCircle(
                color = colors.glassBorder,
                radius = ringRadius,
                center = center,
                style = Stroke(width = 9.dp.toPx()),
            )
            rotate(-90f) {
                drawArc(
                    brush = Brush.sweepGradient(
                        listOf(tint.copy(alpha = 0.15f), tint, Color.White.copy(alpha = 0.9f)),
                    ),
                    startAngle = 0f,
                    sweepAngle = 360f * animatedProgress,
                    useCenter = false,
                    topLeft = Offset(center.x - ringRadius, center.y - ringRadius),
                    size = Size(ringRadius * 2, ringRadius * 2),
                    style = Stroke(width = 9.dp.toPx(), cap = StrokeCap.Round),
                )
            }
            rotate(rotation) {
                drawArc(
                    color = tint.copy(alpha = 0.45f),
                    startAngle = 0f,
                    sweepAngle = 34f,
                    useCenter = false,
                    topLeft = Offset(center.x - radius * 0.98f, center.y - radius * 0.98f),
                    size = Size(radius * 1.96f, radius * 1.96f),
                    style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round),
                )
                drawArc(
                    color = tint.copy(alpha = 0.25f),
                    startAngle = 180f,
                    sweepAngle = 22f,
                    useCenter = false,
                    topLeft = Offset(center.x - radius * 0.98f, center.y - radius * 0.98f),
                    size = Size(radius * 1.96f, radius * 1.96f),
                    style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round),
                )
            }
        }
        Box(
            modifier = Modifier
                .scale(pulse)
                .size(iconSize * 2.2f)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        listOf(tint.copy(alpha = 0.26f), Color.Transparent),
                    )
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Rounded.Shield,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(iconSize),
            )
        }
    }
}

@Composable
fun ScoreRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    positive: Boolean? = null,
) {
    val colors = LocalNexColors.current
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            value,
            style = MaterialTheme.typography.labelLarge,
            color = when (positive) {
                true -> colors.success
                false -> colors.warning
                null -> MaterialTheme.colorScheme.onSurface
            },
        )
    }
}
