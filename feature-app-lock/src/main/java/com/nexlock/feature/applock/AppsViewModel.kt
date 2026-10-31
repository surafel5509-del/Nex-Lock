package com.nexlock.feature.applock

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexlock.common.model.AppCategory
import com.nexlock.core.apps.InstalledApp
import com.nexlock.core.apps.LockedAppsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class AppRowUi(
    val app: InstalledApp,
    val locked: Boolean,
    val selected: Boolean,
)

data class AppsUiState(
    val loading: Boolean = true,
    val apps: List<AppRowUi> = emptyList(),
    val query: String = "",
    val category: AppCategory? = null,
    val selectionMode: Boolean = false,
    val selectedCount: Int = 0,
    val lockedCount: Int = 0,
    val totalCount: Int = 0,
    val error: String? = null,
)

@HiltViewModel
class AppsViewModel @Inject constructor(
    private val lockedAppsRepository: LockedAppsRepository,
) : ViewModel() {

    private val installedApps = MutableStateFlow<List<InstalledApp>?>(null)
    private val query = MutableStateFlow("")
    private val category = MutableStateFlow<AppCategory?>(null)
    private val selectionMode = MutableStateFlow(false)
    private val selected = MutableStateFlow<Set<String>>(emptySet())
    private val error = MutableStateFlow<String?>(null)

    private data class Filters(
        val query: String,
        val category: AppCategory?,
        val selectionMode: Boolean,
        val selected: Set<String>,
    )

    private val filters = combine(query, category, selectionMode, selected) { q, c, mode, sel ->
        Filters(q, c, mode, sel)
    }

    val state: StateFlow<AppsUiState> = combine(
        installedApps,
        lockedAppsRepository.lockedPackages,
        filters,
        error,
    ) { apps, locked, filter, errorMessage ->
        val all = apps.orEmpty()
        val visible = all.filter { app ->
            val matchesQuery = filter.query.isBlank() ||
                app.label.contains(filter.query, ignoreCase = true) ||
                app.packageName.contains(filter.query, ignoreCase = true)
            val matchesCategory = filter.category == null || app.category == filter.category
            matchesQuery && matchesCategory
        }.map { app ->
            AppRowUi(
                app = app,
                locked = app.packageName in locked,
                selected = app.packageName in filter.selected,
            )
        }.sortedWith(compareByDescending<AppRowUi> { it.locked }.thenBy { it.app.label.lowercase() })

        AppsUiState(
            loading = apps == null,
            apps = visible,
            query = filter.query,
            category = filter.category,
            selectionMode = filter.selectionMode,
            selectedCount = filter.selected.size,
            lockedCount = locked.size,
            totalCount = all.size,
            error = errorMessage,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = AppsUiState(),
    )

    init {
        loadApps()
    }

    fun loadApps() {
        viewModelScope.launch {
            runCatching { lockedAppsRepository.installedApps() }
                .onSuccess { installedApps.value = it }
                .onFailure { error.value = it.message ?: "Unable to list installed apps" }
        }
    }

    fun onQueryChange(value: String) {
        query.value = value
    }

    fun onCategoryChange(value: AppCategory?) {
        category.value = value
    }

    fun setSelectionMode(enabled: Boolean) {
        selectionMode.value = enabled
        if (!enabled) selected.value = emptySet()
    }

    fun toggleSelected(packageName: String) {
        selected.value = selected.value.toMutableSet().apply {
            if (!add(packageName)) remove(packageName)
        }
    }

    fun selectAllVisible() {
        selected.value = state.value.apps.map { it.app.packageName }.toSet()
    }

    fun clearSelection() {
        selected.value = emptySet()
    }

    fun toggleLock(app: InstalledApp, locked: Boolean) {
        viewModelScope.launch {
            runCatching { lockedAppsRepository.setLocked(app, locked) }
                .onFailure { error.value = it.message ?: "Unable to update protection" }
        }
    }

    fun protectSelected() {
        val targets = state.value.apps.filter { it.selected }.map { it.app }
        if (targets.isEmpty()) return
        viewModelScope.launch {
            runCatching { lockedAppsRepository.lockMany(targets) }
                .onFailure { error.value = it.message ?: "Unable to update protection" }
            selected.value = emptySet()
            selectionMode.value = false
        }
    }

    fun unprotectAll() {
        viewModelScope.launch {
            runCatching { lockedAppsRepository.unlockAll() }
                .onFailure { error.value = it.message ?: "Unable to update protection" }
        }
    }

    fun dismissError() {
        error.value = null
    }
}
