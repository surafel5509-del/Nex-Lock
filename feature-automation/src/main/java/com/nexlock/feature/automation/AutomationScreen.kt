package com.nexlock.feature.automation

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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nexlock.common.policy.ScheduleCalculator
import com.nexlock.core.automation.LockSchedule
import com.nexlock.core.automation.LocationRule
import com.nexlock.core.automation.TrustedNetwork
import com.nexlock.core.profiles.LockProfile
import com.nexlock.design.components.GlassCard
import com.nexlock.design.components.NexFilterChip
import com.nexlock.design.components.NexPrimaryButton
import com.nexlock.design.components.NexSecondaryButton
import com.nexlock.design.components.NexTextField
import com.nexlock.design.components.NexTopBar
import com.nexlock.design.components.SettingsGroup
import com.nexlock.design.components.SettingsInfoRow
import com.nexlock.design.components.SettingsToggleRow
import com.nexlock.design.components.StatusPill
import com.nexlock.design.components.clickableNoRipple
import com.nexlock.design.theme.LocalNexColors
import com.nexlock.common.util.Formatters
import kotlinx.coroutines.delay

@Composable
fun AutomationRoute(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AutomationViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    AutomationScreen(
        state = state,
        modifier = modifier,
        onBack = onBack,
        onSetAutomationEnabled = viewModel::setAutomationEnabled,
        onSetScheduleEnabled = viewModel::setScheduleEnabled,
        onSetWifiEnabled = viewModel::setWifiEnabled,
        onSetLocationEnabled = viewModel::setLocationEnabled,
        onAddSchedule = viewModel::addSchedule,
        onToggleSchedule = viewModel::toggleSchedule,
        onDeleteSchedule = viewModel::deleteSchedule,
        onAddCurrentNetwork = viewModel::addCurrentNetwork,
        onToggleNetwork = viewModel::toggleNetwork,
        onDeleteNetwork = viewModel::deleteNetwork,
        onAddCurrentLocation = viewModel::addCurrentLocation,
        onToggleLocation = viewModel::toggleLocation,
        onDeleteLocation = viewModel::deleteLocation,
        onStartTemporaryUnlock = viewModel::startTemporaryUnlock,
        onEndTemporaryUnlock = viewModel::endTemporaryUnlock,
        onSetTemporaryUnlockMinutes = viewModel::setTemporaryUnlockMinutes,
        onAddProfile = viewModel::addProfile,
        onActivateProfile = viewModel::activateProfile,
        onDeleteProfile = viewModel::deleteProfile,
        onRefresh = viewModel::refreshContext,
        onConsumeMessage = viewModel::consumeMessage,
        onConsumeError = viewModel::consumeError,
    )
}

@Composable
fun AutomationScreen(
    state: AutomationUiState,
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
    onSetAutomationEnabled: (Boolean) -> Unit = {},
    onSetScheduleEnabled: (Boolean) -> Unit = {},
    onSetWifiEnabled: (Boolean) -> Unit = {},
    onSetLocationEnabled: (Boolean) -> Unit = {},
    onAddSchedule: (String, Int, Int, Int) -> Unit = { _, _, _, _ -> },
    onToggleSchedule: (LockSchedule) -> Unit = {},
    onDeleteSchedule: (LockSchedule) -> Unit = {},
    onAddCurrentNetwork: () -> Unit = {},
    onToggleNetwork: (TrustedNetwork) -> Unit = {},
    onDeleteNetwork: (TrustedNetwork) -> Unit = {},
    onAddCurrentLocation: (Float) -> Unit = {},
    onToggleLocation: (LocationRule) -> Unit = {},
    onDeleteLocation: (LocationRule) -> Unit = {},
    onStartTemporaryUnlock: (Int) -> Unit = {},
    onEndTemporaryUnlock: () -> Unit = {},
    onSetTemporaryUnlockMinutes: (Int) -> Unit = {},
    onAddProfile: (String, Int) -> Unit = { _, _ -> },
    onActivateProfile: (LockProfile) -> Unit = {},
    onDeleteProfile: (LockProfile) -> Unit = {},
    onRefresh: () -> Unit = {},
    onConsumeMessage: () -> Unit = {},
    onConsumeError: () -> Unit = {},
) {
    val colors = LocalNexColors.current
    var showScheduleDialog by remember { mutableStateOf(false) }
    var showProfileDialog by remember { mutableStateOf(false) }
    var tempMinutes by remember { mutableIntStateOf(state.temporaryUnlockMinutes) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }

    LaunchedEffect(Unit) { onRefresh() }
    LaunchedEffect(state.temporaryUnlockUntilMillis) {
        while (state.temporaryUnlockUntilMillis > System.currentTimeMillis()) {
            now = System.currentTimeMillis()
            delay(1_000)
        }
        now = System.currentTimeMillis()
    }
    val remaining = (state.temporaryUnlockUntilMillis - now).coerceAtLeast(0L)

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        NexTopBar(
            title = "Automation",
            subtitle = "Schedules, trusted Wi-Fi, trusted places",
            onBack = onBack,
        )
        Column(modifier = Modifier.padding(horizontal = 20.dp)) {
            SettingsGroup(title = "Automation") {
                SettingsToggleRow(
                    icon = Icons.Rounded.Schedule,
                    title = "Smart automation",
                    subtitle = "Allow rules to relax or enforce protection",
                    checked = state.enabled,
                    onCheckedChange = onSetAutomationEnabled,
                )
            }

            Spacer(Modifier.height(12.dp))

            GlassCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Timer, null, tint = colors.warning, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Temporary unlock",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                    )
                    if (remaining > 0) {
                        StatusPill(text = Formatters.countdown(remaining), positive = false)
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    if (remaining > 0) {
                        "Protection is paused until the timer ends."
                    } else {
                        "Pause protection for a short window without changing any settings."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(5, 15, 30).forEach { minutes ->
                        NexFilterChip(
                            label = "$minutes min",
                            selected = tempMinutes == minutes,
                            onClick = {
                                tempMinutes = minutes
                                onSetTemporaryUnlockMinutes(minutes)
                            },
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                if (remaining > 0) {
                    NexSecondaryButton(
                        text = "End temporary unlock",
                        onClick = onEndTemporaryUnlock,
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    NexPrimaryButton(
                        text = "Unlock for $tempMinutes minutes",
                        onClick = { onStartTemporaryUnlock(tempMinutes) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            SettingsGroup(title = "Schedules") {
                SettingsToggleRow(
                    icon = Icons.Rounded.Schedule,
                    title = "Scheduled locking",
                    subtitle = "Force protection during chosen time windows",
                    checked = state.scheduleEnabled,
                    onCheckedChange = onSetScheduleEnabled,
                )
            }
            Spacer(Modifier.height(8.dp))
            if (state.schedules.isEmpty()) {
                HintCard("No windows yet. Add one to protect apps at night or during work hours.")
            } else {
                state.schedules.forEach { schedule ->
                    ScheduleRow(schedule, onToggle = onToggleSchedule, onDelete = onDeleteSchedule)
                    Spacer(Modifier.height(8.dp))
                }
            }
            Spacer(Modifier.height(8.dp))
            NexSecondaryButton(
                text = "Add schedule",
                onClick = { showScheduleDialog = true },
                modifier = Modifier.fillMaxWidth(),
                icon = Icons.Rounded.Add,
            )

            Spacer(Modifier.height(16.dp))

            SettingsGroup(title = "Trusted Wi-Fi") {
                SettingsToggleRow(
                    icon = Icons.Rounded.Wifi,
                    title = "Relax protection on trusted networks",
                    subtitle = "Useful at home; Android requires location permission to read the network name",
                    checked = state.wifiEnabled,
                    onCheckedChange = onSetWifiEnabled,
                )
                SettingsInfoRow(
                    label = "Connected network",
                    value = state.currentSsid ?: "Not available",
                )
            }
            Spacer(Modifier.height(8.dp))
            state.networks.forEach { network ->
                NetworkRow(network, onToggle = onToggleNetwork, onDelete = onDeleteNetwork)
                Spacer(Modifier.height(8.dp))
            }
            NexSecondaryButton(
                text = "Add current network",
                onClick = onAddCurrentNetwork,
                modifier = Modifier.fillMaxWidth(),
                icon = Icons.Rounded.Add,
            )

            Spacer(Modifier.height(16.dp))

            SettingsGroup(title = "Trusted places") {
                SettingsToggleRow(
                    icon = Icons.Rounded.LocationOn,
                    title = "Relax protection at trusted places",
                    subtitle = "Uses the last known location; requires location permission",
                    checked = state.locationEnabled,
                    onCheckedChange = onSetLocationEnabled,
                )
                SettingsInfoRow(
                    label = "Location permission",
                    value = if (state.locationPermissionGranted) "Granted" else "Not granted",
                )
            }
            Spacer(Modifier.height(8.dp))
            state.locations.forEach { rule ->
                LocationRow(rule, onToggle = onToggleLocation, onDelete = onDeleteLocation)
                Spacer(Modifier.height(8.dp))
            }
            NexSecondaryButton(
                text = "Add current location",
                onClick = { onAddCurrentLocation(150f) },
                modifier = Modifier.fillMaxWidth(),
                icon = Icons.Rounded.Add,
            )

            Spacer(Modifier.height(16.dp))

            SettingsGroup(title = "Lock profiles") {
                if (state.profiles.isEmpty()) {
                    SettingsInfoRow(label = "Profiles", value = "None yet")
                } else {
                    state.profiles.forEach { profile ->
                        ProfileRow(
                            profile = profile,
                            onActivate = onActivateProfile,
                            onDelete = onDeleteProfile,
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            NexSecondaryButton(
                text = "New profile",
                onClick = { showProfileDialog = true },
                modifier = Modifier.fillMaxWidth(),
                icon = Icons.Rounded.Person,
            )

            state.snapshot?.let { snapshot ->
                Spacer(Modifier.height(16.dp))
                GlassCard {
                    Text(
                        "Right now",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = when {
                            snapshot.temporaryUnlockActive -> "Temporary unlock is active"
                            snapshot.trustedLocationLabel != null ->
                                "Inside trusted place: ${snapshot.trustedLocationLabel}"
                            snapshot.trustedNetworkMatched -> "Connected to a trusted network"
                            snapshot.forcedBySchedule ->
                                "Scheduled window active: ${snapshot.activeWindowLabel ?: "schedule"}"
                            else -> "No automation rule is changing protection right now"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (state.message != null) {
                Spacer(Modifier.height(10.dp))
                Text(
                    state.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.success,
                    modifier = Modifier.clickableNoRipple(onClick = onConsumeMessage),
                )
            }
            if (state.error != null) {
                Spacer(Modifier.height(10.dp))
                Text(
                    state.error,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.danger,
                    modifier = Modifier.clickableNoRipple(onClick = onConsumeError),
                )
            }
            Spacer(Modifier.height(28.dp))
        }
    }

    if (showScheduleDialog) {
        AddScheduleDialog(
            onDismiss = { showScheduleDialog = false },
            onSave = { label, start, end, days ->
                showScheduleDialog = false
                onAddSchedule(label, start, end, days)
            },
        )
    }

    if (showProfileDialog) {
        AddProfileDialog(
            onDismiss = { showProfileDialog = false },
            onSave = { name, timeout ->
                showProfileDialog = false
                onAddProfile(name, timeout)
            },
        )
    }
}

@Composable
private fun HintCard(text: String) {
    GlassCard {
        Text(
            text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ScheduleRow(
    schedule: LockSchedule,
    onToggle: (LockSchedule) -> Unit,
    onDelete: (LockSchedule) -> Unit,
) {
    val colors = LocalNexColors.current
    GlassCard(contentPadding = PaddingValues(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    schedule.label,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    "${ScheduleCalculator.formatMinuteOfDay(schedule.startMinute)} – " +
                        "${ScheduleCalculator.formatMinuteOfDay(schedule.endMinute)} · " +
                        ScheduleCalculator.dayLabels(schedule.daysMask),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            StatusPill(
                text = if (schedule.enabled) "On" else "Off",
                positive = schedule.enabled,
            )
            IconButton(onClick = { onToggle(schedule) }) {
                Icon(
                    Icons.Rounded.Schedule,
                    "Toggle",
                    tint = colors.glow,
                    modifier = Modifier.size(20.dp),
                )
            }
            IconButton(onClick = { onDelete(schedule) }) {
                Icon(Icons.Rounded.Delete, "Delete", tint = colors.danger, modifier = Modifier.size(20.dp))
            }
        }
    }
}

@Composable
private fun NetworkRow(
    network: TrustedNetwork,
    onToggle: (TrustedNetwork) -> Unit,
    onDelete: (TrustedNetwork) -> Unit,
) {
    val colors = LocalNexColors.current
    GlassCard(contentPadding = PaddingValues(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Wifi, null, tint = colors.glow, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Text(
                network.ssid,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            StatusPill(text = if (network.enabled) "Trusted" else "Off", positive = network.enabled)
            IconButton(onClick = { onToggle(network) }) {
                Icon(Icons.Rounded.Schedule, "Toggle", tint = colors.glow, modifier = Modifier.size(20.dp))
            }
            IconButton(onClick = { onDelete(network) }) {
                Icon(Icons.Rounded.Delete, "Delete", tint = colors.danger, modifier = Modifier.size(20.dp))
            }
        }
    }
}

@Composable
private fun LocationRow(
    rule: LocationRule,
    onToggle: (LocationRule) -> Unit,
    onDelete: (LocationRule) -> Unit,
) {
    val colors = LocalNexColors.current
    GlassCard(contentPadding = PaddingValues(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.LocationOn, null, tint = colors.glow, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    rule.label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    "${"%.4f".format(rule.latitude)}, ${"%.4f".format(rule.longitude)} · ${rule.radiusMeters.toInt()} m",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            StatusPill(text = if (rule.enabled) "Trusted" else "Off", positive = rule.enabled)
            IconButton(onClick = { onToggle(rule) }) {
                Icon(Icons.Rounded.Schedule, "Toggle", tint = colors.glow, modifier = Modifier.size(20.dp))
            }
            IconButton(onClick = { onDelete(rule) }) {
                Icon(Icons.Rounded.Delete, "Delete", tint = colors.danger, modifier = Modifier.size(20.dp))
            }
        }
    }
}

@Composable
private fun ProfileRow(
    profile: LockProfile,
    onActivate: (LockProfile) -> Unit,
    onDelete: (LockProfile) -> Unit,
) {
    val colors = LocalNexColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                profile.name,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                "Re-lock after ${if (profile.timeoutSeconds == 0) "leaving the app" else "${profile.timeoutSeconds}s"}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (profile.isActive) {
            StatusPill(text = "Active", positive = true)
        } else {
            Text(
                "Activate",
                style = MaterialTheme.typography.labelMedium,
                color = colors.glow,
                modifier = Modifier.clickableNoRipple { onActivate(profile) },
            )
        }
        IconButton(onClick = { onDelete(profile) }) {
            Icon(Icons.Rounded.Delete, "Delete", tint = colors.danger, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun AddScheduleDialog(
    onDismiss: () -> Unit,
    onSave: (String, Int, Int, Int) -> Unit,
) {
    var label by remember { mutableStateOf("") }
    var start by remember { mutableFloatStateOf(22 * 60f) }
    var end by remember { mutableFloatStateOf(7 * 60f) }
    var daysMask by remember { mutableIntStateOf(ScheduleCalculator.ALL_DAYS_MASK) }

    Dialog(onDismissRequest = onDismiss) {
        GlassCard {
            Text("New schedule", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(10.dp))
            NexTextField(value = label, onValueChange = { label = it }, label = "Label (optional)")
            Spacer(Modifier.height(14.dp))
            Text(
                "From ${ScheduleCalculator.formatMinuteOfDay(start.toInt())}",
                style = MaterialTheme.typography.labelMedium,
            )
            Slider(
                value = start,
                onValueChange = { start = it },
                valueRange = 0f..1439f,
                steps = 0,
            )
            Text(
                "To ${ScheduleCalculator.formatMinuteOfDay(end.toInt())}",
                style = MaterialTheme.typography.labelMedium,
            )
            Slider(
                value = end,
                onValueChange = { end = it },
                valueRange = 0f..1439f,
                steps = 0,
            )
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("M", "T", "W", "T", "F", "S", "S").forEachIndexed { index, day ->
                    val bit = 1 shl index
                    NexFilterChip(
                        label = day,
                        selected = daysMask and bit != 0,
                        onClick = { daysMask = daysMask xor bit },
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NexPrimaryButton(
                    text = "Add",
                    onClick = { onSave(label, start.toInt(), end.toInt(), daysMask) },
                    modifier = Modifier.weight(1f),
                )
                NexPrimaryButton(text = "Cancel", onClick = onDismiss, modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun AddProfileDialog(
    onDismiss: () -> Unit,
    onSave: (String, Int) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var timeout by remember { mutableIntStateOf(0) }

    Dialog(onDismissRequest = onDismiss) {
        GlassCard {
            Text("New lock profile", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(10.dp))
            NexTextField(value = name, onValueChange = { name = it }, label = "Profile name")
            Spacer(Modifier.height(12.dp))
            Text("Re-lock timeout", style = MaterialTheme.typography.labelMedium)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(0 to "Instant", 30 to "30s", 60 to "1m", 300 to "5m").forEach { (seconds, text) ->
                    NexFilterChip(
                        label = text,
                        selected = timeout == seconds,
                        onClick = { timeout = seconds },
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NexPrimaryButton(
                    text = "Create",
                    onClick = { onSave(name, timeout) },
                    modifier = Modifier.weight(1f),
                    enabled = name.isNotBlank(),
                )
                NexPrimaryButton(text = "Cancel", onClick = onDismiss, modifier = Modifier.weight(1f))
            }
        }
    }
}
