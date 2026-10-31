package com.nexlock.design.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Backspace
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nexlock.design.theme.LocalNexColors
import com.nexlock.design.theme.NexRadius
import kotlin.math.hypot

/** Filled/empty indicator dots for PIN entry. */
@Composable
fun PinDots(
    filledCount: Int,
    maxDots: Int,
    modifier: Modifier = Modifier,
    error: Boolean = false,
) {
    val colors = LocalNexColors.current
    val activeColor = if (error) colors.danger else colors.glow
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(maxDots) { index ->
            val filled = index < filledCount
            Box(
                modifier = Modifier
                    .size(if (filled) 16.dp else 13.dp)
                    .clip(CircleShape)
                    .background(if (filled) activeColor else Color.Transparent)
                    .border(
                        width = 1.5.dp,
                        color = if (filled) activeColor else MaterialTheme.colorScheme.outline,
                        shape = CircleShape,
                    )
            )
        }
    }
}

/** Numeric keypad with an optional biometric key. */
@Composable
fun PinPad(
    onDigit: (Char) -> Unit,
    onBackspace: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    showBiometric: Boolean = false,
    onBiometric: (() -> Unit)? = null,
) {
    val haptics = rememberNexHaptics()
    val rows = listOf(
        listOf("1", "2", "3"),
        listOf("4", "5", "6"),
        listOf("7", "8", "9"),
        listOf("bio", "0", "back"),
    )
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        rows.forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                row.forEach { key ->
                    KeypadKey(
                        key = key,
                        modifier = Modifier.weight(1f),
                        enabled = enabled,
                        showBiometric = showBiometric,
                        onClick = {
                            haptics.tap()
                            when (key) {
                                "back" -> onBackspace()
                                "bio" -> onBiometric?.invoke()
                                else -> onDigit(key.first())
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun KeypadKey(
    key: String,
    modifier: Modifier = Modifier,
    enabled: Boolean,
    showBiometric: Boolean,
    onClick: () -> Unit,
) {
    val colors = LocalNexColors.current
    val interactive = enabled && (key != "bio" || showBiometric)
    Box(
        modifier = modifier
            .height(64.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(if (interactive) colors.glassFill else Color.Transparent)
            .border(1.dp, colors.glassBorder, RoundedCornerShape(22.dp))
            .clickable(enabled = interactive, role = Role.Button) { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        when (key) {
            "bio" -> if (showBiometric) {
                Icon(
                    Icons.Rounded.Fingerprint,
                    contentDescription = "Biometric unlock",
                    tint = colors.glow,
                    modifier = Modifier.size(26.dp),
                )
            }
            "back" -> Icon(
                Icons.AutoMirrored.Rounded.Backspace,
                contentDescription = "Delete last digit",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp),
            )
            else -> Text(
                text = key,
                style = MaterialTheme.typography.headlineSmall,
                fontSize = 26.sp,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

/**
 * 3x3 pattern grid. Draws the active path with a gradient and reports the encoded path
 * (cell indices joined by '-') when the gesture ends.
 */
@Composable
fun PatternLock(
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    error: Boolean = false,
    resetSignal: Int = 0,
    minCells: Int = 4,
    onComplete: (String) -> Unit,
) {
    val colors = LocalNexColors.current
    var selected by remember { mutableStateOf(emptyList<Int>()) }
    val haptics = rememberNexHaptics()

    LaunchedEffect(resetSignal) { selected = emptyList() }

    val activeColor = if (error) colors.danger else colors.glow
    val idleColor = MaterialTheme.colorScheme.outline

    Box(
        modifier = modifier
            .size(268.dp)
            .pointerInput(enabled, resetSignal) {
                if (!enabled) return@pointerInput
                detectDragGestures(
                    onDragStart = { offset ->
                        val cell = cellAt(offset, size)
                        selected = if (cell != null) listOf(cell) else emptyList()
                        if (cell != null) haptics.tap()
                    },
                    onDrag = { change, _ ->
                        val cell = cellAt(change.position, size)
                        if (cell != null && !selected.contains(cell)) {
                            selected = selected + cell
                            haptics.tap()
                        }
                    },
                    onDragEnd = {
                        if (selected.size >= minCells) {
                            onComplete(selected.joinToString("-"))
                        }
                        selected = emptyList()
                    },
                    onDragCancel = { selected = emptyList() },
                )
            },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val cellWidth = this.size.width / 3f
            val cellHeight = this.size.height / 3f
            val dotRadius = minOf(cellWidth, cellHeight) * 0.13f
            fun centerOf(index: Int): Offset {
                val row = index / 3
                val col = index % 3
                return Offset(cellWidth * (col + 0.5f), cellHeight * (row + 0.5f))
            }

            for (i in 0 until selected.size - 1) {
                drawLine(
                    brush = Brush.linearGradient(listOf(activeColor, colors.accentGradient.last())),
                    start = centerOf(selected[i]),
                    end = centerOf(selected[i + 1]),
                    strokeWidth = dotRadius * 0.55f,
                    cap = StrokeCap.Round,
                )
            }
            for (index in 0..8) {
                val center = centerOf(index)
                val isSelected = selected.contains(index)
                if (isSelected) {
                    drawCircle(
                        brush = Brush.radialGradient(
                            listOf(activeColor, activeColor.copy(alpha = 0.65f)),
                            center = center,
                            radius = dotRadius * 2.4f,
                        ),
                        radius = dotRadius * 1.9f,
                        center = center,
                    )
                } else {
                    drawCircle(
                        color = idleColor.copy(alpha = 0.55f),
                        radius = dotRadius,
                        center = center,
                        style = Stroke(width = 2.dp.toPx()),
                    )
                }
            }
        }
    }
}

private fun cellAt(offset: Offset, size: IntSize): Int? {
    if (size.width == 0 || size.height == 0) return null
    val cellWidth = size.width / 3f
    val cellHeight = size.height / 3f
    val col = (offset.x / cellWidth).toInt()
    val row = (offset.y / cellHeight).toInt()
    if (col !in 0..2 || row !in 0..2) return null
    val centerX = cellWidth * (col + 0.5f)
    val centerY = cellHeight * (row + 0.5f)
    val touchRadius = minOf(cellWidth, cellHeight) * 0.42f
    return if (hypot(offset.x - centerX, offset.y - centerY) <= touchRadius) row * 3 + col else null
}

@Composable
fun PasswordField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isError: Boolean = false,
    imeAction: ImeAction = ImeAction.Done,
    onImeAction: () -> Unit = {},
) {
    var visible by remember { mutableStateOf(false) }
    val colors = LocalNexColors.current
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        label = { Text(label) },
        singleLine = true,
        enabled = enabled,
        isError = isError,
        shape = RoundedCornerShape(16.dp),
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Password,
            imeAction = imeAction,
        ),
        trailingIcon = {
            IconButton(onClick = { visible = !visible }) {
                Icon(
                    imageVector = if (visible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                    contentDescription = if (visible) "Hide" else "Show",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = colors.glow,
            unfocusedBorderColor = MaterialTheme.colorScheme.outline,
            focusedLabelColor = colors.glow,
        ),
    )
}

@Composable
fun NexTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    singleLine: Boolean = true,
    minLines: Int = 1,
    isError: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Default,
    supportingText: String? = null,
) {
    val colors = LocalNexColors.current
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        label = { Text(label) },
        enabled = enabled,
        singleLine = singleLine,
        minLines = minLines,
        isError = isError,
        shape = RoundedCornerShape(16.dp),
        supportingText = supportingText?.let { { Text(it) } },
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = imeAction),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = colors.glow,
            unfocusedBorderColor = MaterialTheme.colorScheme.outline,
            focusedLabelColor = colors.glow,
        ),
    )
}

/** Chip used for category filters. */
@Composable
fun NexFilterChip(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val colors = LocalNexColors.current
    Box(
        modifier = modifier
            .clip(NexRadius.chip)
            .background(
                if (selected) {
                    Brush.linearGradient(colors.accentGradient)
                } else {
                    Brush.linearGradient(listOf(Color.Transparent, Color.Transparent))
                }
            )
            .border(
                1.dp,
                if (selected) Color.Transparent else colors.glassBorder,
                NexRadius.chip,
            )
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 9.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
fun DividerSpacer(modifier: Modifier = Modifier) {
    Spacer(
        modifier = modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(MaterialTheme.colorScheme.outlineVariant)
    )
}
