package com.nexlock.design.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.nexlock.design.theme.LocalNexColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Installed-app icon loader.
 *
 * Icons are queried on demand from PackageManager (never persisted, never uploaded) and rendered
 * with a deterministic monogram fallback when an app has no launcher icon.
 */
@Composable
fun AppIcon(
    packageName: String,
    label: String,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
) {
    val context = LocalContext.current
    val bitmap by produceState<ImageBitmap?>(initialValue = null, packageName, size) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val drawable = context.packageManager.getApplicationIcon(packageName)
                val pixels = (size.value * 2).toInt().coerceAtLeast(48)
                drawable.toBitmap(width = pixels, height = pixels).asImageBitmap()
            }.getOrNull()
        }
    }
    val colors = LocalNexColors.current
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.3f))
            .background(Brush.linearGradient(colors.accentGradient.map { it.copy(alpha = 0.22f) })),
        contentAlignment = Alignment.Center,
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap!!,
                contentDescription = label,
                modifier = Modifier.size(size).clip(RoundedCornerShape(size * 0.3f)),
                contentScale = ContentScale.Fit,
            )
        } else {
            Text(
                text = label.trim().take(1).uppercase().ifBlank { "?" },
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}
