package com.nexlock.feature.applock

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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.hilt.navigation.compose.hiltViewModel
import com.nexlock.common.model.AppCategory
import com.nexlock.core.apps.InstalledApp
import com.nexlock.design.components.AppIcon
import com.nexlock.design.components.EmptyState
import com.nexlock.design.components.GlassCard
import com.nexlock.design.components.ListSkeleton
import com.nexlock.design.components.NexFilterChip
import com.nexlock.design.components.NexTextField
import com.nexlock.design.components.StatusPill
import com.nexlock.design.components.clickableNoRipple
import com.nexlock.design.components.rememberNexHaptics
import com.nexlock.design.strings.LocalStrings
import com.nexlock.design.theme.LocalNexColors
import com.nexlock.design.theme.NexRadius

@Composable
fun LockedAppsRoute(
    modifier: Modifier = Modifier,
    viewModel: AppsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LockedAppsScreen(
        state = state,
        modifier = modifier,
        onQueryChange = viewModel::onQueryChange,
        onCategoryChange = viewModel::onCategoryChange,
        onToggleLock = viewModel::toggleLock,
        onToggleSelectionMode = viewModel::setSelectionMode,
        onToggleSelected = viewModel::toggleSelected,
        onSelectAll = viewModel::selectAllVisible,
        onProtectSelected = viewModel::protectSelected,
        onRefresh = viewModel::loadApps,
        onDismissError = viewModel::dismissError,
    )
}

@Composable
fun LockedAppsScreen(
    state: AppsUiState,
    modifier: Modifier = Modifier,
    onQueryChange: (String) -> Unit = {},
    onCategoryChange: (AppCategory?) -> Unit = {},
    onToggleLock: (InstalledApp, Boolean) -> Unit = { _, _ -> },
    onToggleSelectionMode: (Boolean) -> Unit = {},
    onToggleSelected: (String) -> Unit = {},
    onSelectAll: () -> Unit = {},
    onProtectSelected: () -> Unit = {},
    onRefresh: () -> Unit = {},
    onDismissError: () -> Unit = {},
) {
    val strings = LocalStrings.current
    val colors = LocalNexColors.current
    val haptics = rememberNexHaptics()

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    strings.appsTitle,
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Text(
                    "${state.lockedCount} of ${state.totalCount} apps protected",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = if (state.selectionMode) "Cancel" else "Select",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clickableNoRipple {
                    haptics.tap()
                    onToggleSelectionMode(!state.selectionMode)
                },
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Rounded.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(8.dp))
            NexTextField(
                value = state.query,
                onValueChange = onQueryChange,
                label = strings.searchApps,
                modifier = Modifier.weight(1f),
            )
        }

        Spacer(Modifier.height(12.dp))

        LazyRow(
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                NexFilterChip(
                    label = strings.allCategories,
                    selected = state.category == null,
                    onClick = { onCategoryChange(null) },
                )
            }
            items(AppCategory.entries.toList()) { category ->
                NexFilterChip(
                    label = category.name.lowercase().replaceFirstChar { it.uppercase() },
                    selected = state.category == category,
                    onClick = { onCategoryChange(if (state.category == category) null else category) },
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        if (state.error != null) {
            GlassCard(
                modifier = Modifier.padding(horizontal = 20.dp),
                borderColor = colors.danger.copy(alpha = 0.4f),
                onClick = onDismissError,
            ) {
                Text(state.error, style = MaterialTheme.typography.bodySmall, color = colors.danger)
            }
            Spacer(Modifier.height(12.dp))
        }

        when {
            state.loading -> ListSkeleton(rows = 7, modifier = Modifier.fillMaxSize())
            state.apps.isEmpty() -> EmptyState(
                icon = Icons.Rounded.Apps,
                title = strings.noAppsFound,
                description = strings.noAppsFoundDetail,
                actionLabel = strings.retry,
                onAction = onRefresh,
                modifier = Modifier.fillMaxSize(),
            )
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(state.apps, key = { it.app.packageName }) { row ->
                    AppRow(
                        row = row,
                        selectionMode = state.selectionMode,
                        onToggleLock = onToggleLock,
                        onToggleSelected = onToggleSelected,
                    )
                }
            }
        }

        if (state.selectionMode) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.glassFill)
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "${state.selectedCount} ${strings.selected}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    "Select all",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clickableNoRipple { onSelectAll() },
                )
                Spacer(Modifier.width(16.dp))
                Box(
                    modifier = Modifier
                        .clip(NexRadius.chip)
                        .background(colors.accentBrush)
                        .clickableNoRipple(enabled = state.selectedCount > 0) {
                            haptics.tap()
                            onProtectSelected()
                        }
                        .padding(horizontal = 18.dp, vertical = 10.dp),
                ) {
                    Text(strings.lockSelected, style = MaterialTheme.typography.labelMedium, color = androidx.compose.ui.graphics.Color.White)
                }
            }
        }
    }
}

@Composable
private fun AppRow(
    row: AppRowUi,
    selectionMode: Boolean,
    onToggleLock: (InstalledApp, Boolean) -> Unit,
    onToggleSelected: (String) -> Unit,
) {
    val colors = LocalNexColors.current
    val haptics = rememberNexHaptics()
    GlassCard(
        contentPadding = PaddingValues(12.dp),
        onClick = {
            if (selectionMode) {
                haptics.tap()
                onToggleSelected(row.app.packageName)
            }
        },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AppIcon(packageName = row.app.packageName, label = row.app.label, size = 44.dp)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    row.app.label,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        row.app.category.name.lowercase().replaceFirstChar { it.uppercase() },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (row.app.isSystemApp) {
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "· system",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            if (selectionMode) {
                Checkbox(checked = row.selected, onCheckedChange = { onToggleSelected(row.app.packageName) })
            } else {
                if (row.locked) {
                    StatusPill(text = "Locked", positive = true)
                    Spacer(Modifier.width(8.dp))
                }
                Switch(
                    checked = row.locked,
                    onCheckedChange = { checked ->
                        haptics.tap()
                        onToggleLock(row.app, checked)
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                        checkedTrackColor = colors.glow,
                    ),
                )
            }
        }
    }
}
