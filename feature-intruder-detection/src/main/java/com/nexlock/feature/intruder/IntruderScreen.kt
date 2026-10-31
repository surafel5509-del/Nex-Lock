package com.nexlock.feature.intruder

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nexlock.common.util.Formatters
import com.nexlock.core.intruder.IntruderIncident
import com.nexlock.design.components.EmptyState
import com.nexlock.design.components.GlassCard
import com.nexlock.design.components.ListSkeleton
import com.nexlock.design.components.NexConfirmDialog
import com.nexlock.design.components.NexFilterChip
import com.nexlock.design.components.NexPrimaryButton
import com.nexlock.design.components.NexSecondaryButton
import com.nexlock.design.components.NexTopBar
import com.nexlock.design.components.PermissionCard
import com.nexlock.design.components.SettingsGroup
import com.nexlock.design.components.SettingsToggleRow
import com.nexlock.design.components.clickableNoRipple
import com.nexlock.design.theme.LocalNexColors

@Composable
fun IntruderRoute(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: IntruderViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { viewModel.refreshPermissions() }

    LaunchedEffect(Unit) { viewModel.refreshPermissions() }

    IntruderScreen(
        state = state,
        modifier = modifier,
        onBack = onBack,
        onSetEnabled = viewModel::setEnabled,
        onSetPhotoEnabled = { enabled ->
            if (enabled && !state.cameraGranted) {
                cameraLauncher.launch(Manifest.permission.CAMERA)
            } else {
                viewModel.setPhotoEnabled(enabled)
            }
        },
        onSetThreshold = viewModel::setThreshold,
        onOpenPhoto = viewModel::openPhoto,
        onClosePreview = viewModel::closePreview,
        onDelete = viewModel::deleteIncident,
        onClearAll = viewModel::clearAll,
        onDismissError = viewModel::dismissError,
    )
}

@Composable
fun IntruderScreen(
    state: IntruderUiState,
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
    onSetEnabled: (Boolean) -> Unit = {},
    onSetPhotoEnabled: (Boolean) -> Unit = {},
    onSetThreshold: (Int) -> Unit = {},
    onOpenPhoto: (IntruderIncident) -> Unit = {},
    onClosePreview: () -> Unit = {},
    onDelete: (IntruderIncident) -> Unit = {},
    onClearAll: () -> Unit = {},
    onDismissError: () -> Unit = {},
) {
    val colors = LocalNexColors.current
    var confirmClear by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        NexTopBar(title = "Intruder detection", subtitle = "Optional · explicit permission only", onBack = onBack)
        Column(modifier = Modifier.padding(horizontal = 20.dp)) {
            SettingsGroup(title = "Detection") {
                SettingsToggleRow(
                    icon = Icons.Rounded.Security,
                    title = "Detect repeated failures",
                    subtitle = "Record failed attempts and show them in a private history",
                    checked = state.enabled,
                    onCheckedChange = onSetEnabled,
                )
                SettingsToggleRow(
                    icon = Icons.Rounded.PhotoCamera,
                    title = "Capture a front-camera photo",
                    subtitle = "Only after repeated failures, never in the background, never without permission",
                    checked = state.photoEnabled,
                    enabled = state.enabled,
                    onCheckedChange = onSetPhotoEnabled,
                )
            }

            Spacer(Modifier.height(12.dp))

            if (state.enabled) {
                Text(
                    "Photo after",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 8.dp, bottom = 8.dp),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(2, 3, 5, 7).forEach { threshold ->
                        NexFilterChip(
                            label = "$threshold failures",
                            selected = state.threshold == threshold,
                            onClick = { onSetThreshold(threshold) },
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
            }

            if (state.enabled && !state.cameraGranted && state.photoEnabled) {
                PermissionCard(
                    title = "Camera permission",
                    description = "NexLock only opens the camera after the failure threshold while the lock screen is visible.",
                    granted = false,
                    actionLabel = "Grant camera access",
                    onAction = { onSetPhotoEnabled(true) },
                )
                Spacer(Modifier.height(12.dp))
            }

            GlassCard(borderColor = colors.warning.copy(alpha = 0.35f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Warning, null, tint = colors.warning, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "NexLock never records silently. Captures stay encrypted in this app and can be deleted at any time. " +
                            "Recording other people without consent may be illegal where you live.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (state.error != null) {
                Spacer(Modifier.height(10.dp))
                Text(
                    state.error,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.danger,
                    modifier = Modifier.clickableNoRipple(onClick = onDismissError),
                )
            }

            Spacer(Modifier.height(20.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Incident history",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                if (state.incidents.isNotEmpty()) {
                    Text(
                        "Clear all",
                        style = MaterialTheme.typography.labelLarge,
                        color = colors.danger,
                        modifier = Modifier.clickableNoRipple { confirmClear = true },
                    )
                }
            }
            Spacer(Modifier.height(10.dp))

            when {
                state.loading -> ListSkeleton(rows = 3)
                state.incidents.isEmpty() -> EmptyState(
                    icon = Icons.Rounded.CameraAlt,
                    title = "No incidents",
                    description = "Repeated failed unlock attempts will appear here. Nothing leaves this device.",
                )
                else -> state.incidents.forEach { incident ->
                    IncidentRow(
                        incident = incident,
                        onOpenPhoto = onOpenPhoto,
                        onDelete = onDelete,
                    )
                    Spacer(Modifier.height(8.dp))
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (confirmClear) {
        NexConfirmDialog(
            title = "Clear incident history?",
            message = "All incident records and captures will be securely deleted.",
            confirmLabel = "Delete all",
            destructive = true,
            onConfirm = {
                confirmClear = false
                onClearAll()
            },
            onDismiss = { confirmClear = false },
        )
    }

    state.preview?.let { (incident, bitmap) ->
        Dialog(onDismissRequest = onClosePreview) {
            GlassCard {
                Text(
                    "Capture · ${Formatters.timestamp(incident.timestamp)}",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(10.dp))
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "Intruder capture",
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(360.dp)
                        .clip(RoundedCornerShape(18.dp)),
                    contentScale = ContentScale.Fit,
                )
                Spacer(Modifier.height(10.dp))
                NexPrimaryButton(text = "Close", onClick = onClosePreview, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun IncidentRow(
    incident: IntruderIncident,
    onOpenPhoto: (IntruderIncident) -> Unit,
    onDelete: (IntruderIncident) -> Unit,
) {
    val colors = LocalNexColors.current
    GlassCard(
        contentPadding = PaddingValues(14.dp),
        onClick = { if (incident.hasPhoto) onOpenPhoto(incident) },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                if (incident.hasPhoto) Icons.Rounded.PhotoCamera else Icons.Rounded.Warning,
                contentDescription = null,
                tint = if (incident.hasPhoto) colors.glow else colors.warning,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "${incident.failedAttempts} failed attempt(s)",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    Formatters.timestamp(incident.timestamp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    when {
                        incident.hasPhoto -> "Encrypted capture available · tap to view"
                        incident.note != null -> incident.note.orEmpty()
                        else -> "No capture stored"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(onClick = { onDelete(incident) }) {
                Icon(Icons.Rounded.Delete, "Delete", tint = colors.danger)
            }
        }
    }
}
