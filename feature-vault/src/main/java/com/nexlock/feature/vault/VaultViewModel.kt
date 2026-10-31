package com.nexlock.feature.vault

import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexlock.common.model.VaultKind
import com.nexlock.core.vault.SecureNote
import com.nexlock.core.vault.VaultItem
import com.nexlock.core.vault.VaultRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class VaultTab(val kind: VaultKind?) {
    PHOTOS(VaultKind.PHOTO),
    VIDEOS(VaultKind.VIDEO),
    DOCUMENTS(VaultKind.DOCUMENT),
    NOTES(null),
}

data class VaultUiState(
    val loading: Boolean = true,
    val items: List<VaultItem> = emptyList(),
    val notes: List<SecureNote> = emptyList(),
    val totalBytes: Long = 0L,
    val thumbnails: Map<Long, Bitmap> = emptyMap(),
    val importing: Boolean = false,
    val message: String? = null,
    val error: String? = null,
)

data class VaultDialogState(
    val previewImage: Pair<VaultItem, Bitmap>? = null,
    val playingVideo: Pair<VaultItem, File>? = null,
    val documentInfo: VaultItem? = null,
    val noteEditor: SecureNote? = null,
    val creatingNote: Boolean = false,
    val pendingDelete: VaultItem? = null,
    val pendingDeleteNote: SecureNote? = null,
    val exportRequest: VaultItem? = null,
)

@HiltViewModel
class VaultViewModel @Inject constructor(
    private val repository: VaultRepository,
) : ViewModel() {

    private val local = MutableStateFlow(VaultUiState())

    private val _dialogs = MutableStateFlow(VaultDialogState())
    val dialogs: StateFlow<VaultDialogState> = _dialogs

    val state: StateFlow<VaultUiState> = combine(
        repository.observeItems(),
        repository.observeNotes(),
        repository.observeTotalBytes(),
        local,
    ) { items, notes, totalBytes, localState ->
        localState.copy(
            loading = false,
            items = items,
            notes = notes,
            totalBytes = totalBytes,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = VaultUiState(),
    )

    fun importUri(uri: Uri) {
        viewModelScope.launch {
            local.update { it.copy(importing = true, error = null, message = null) }
            repository.importFromUri(uri)
                .onSuccess { item ->
                    local.update {
                        it.copy(importing = false, message = "Imported ${item.displayName}")
                    }
                }
                .onFailure { throwable ->
                    local.update {
                        it.copy(importing = false, error = throwable.message ?: "Import failed")
                    }
                }
        }
    }

    fun requestThumbnail(item: VaultItem) {
        if (item.kind != VaultKind.PHOTO) return
        if (state.value.thumbnails.containsKey(item.id)) return
        if (state.value.thumbnails.size > MAX_THUMBNAILS) return
        viewModelScope.launch {
            repository.loadImage(item.id, maxDimension = 512).onSuccess { bitmap ->
                local.update { s ->
                    s.copy(thumbnails = s.thumbnails + (item.id to bitmap))
                }
            }
        }
    }

    fun openImage(item: VaultItem) {
        viewModelScope.launch {
            repository.loadImage(item.id, maxDimension = 1600)
                .onSuccess { bitmap -> _dialogs.update { it.copy(previewImage = item to bitmap) } }
                .onFailure { throwable ->
                    local.update { it.copy(error = throwable.message ?: "Unable to open image") }
                }
        }
    }

    fun playVideo(item: VaultItem) {
        viewModelScope.launch {
            repository.prepareForViewing(item.id)
                .onSuccess { file -> _dialogs.update { it.copy(playingVideo = item to file) } }
                .onFailure { throwable ->
                    local.update { it.copy(error = throwable.message ?: "Unable to open video") }
                }
        }
    }

    fun showDocumentInfo(item: VaultItem) = _dialogs.update { it.copy(documentInfo = item) }

    fun requestExport(item: VaultItem) = _dialogs.update { it.copy(exportRequest = item) }

    fun exportTo(uri: Uri) {
        val item = _dialogs.value.exportRequest ?: return
        viewModelScope.launch {
            repository.exportToUri(item.id, uri)
                .onSuccess { local.update { it.copy(message = "Exported ${item.displayName}") } }
                .onFailure { throwable ->
                    local.update { it.copy(error = throwable.message ?: "Export failed") }
                }
            _dialogs.update { it.copy(exportRequest = null) }
        }
    }

    fun requestDelete(item: VaultItem) = _dialogs.update { it.copy(pendingDelete = item) }

    fun confirmDelete() {
        val item = _dialogs.value.pendingDelete ?: return
        viewModelScope.launch {
            repository.delete(item.id)
                .onSuccess { local.update { it.copy(message = "Deleted ${item.displayName}") } }
                .onFailure { throwable ->
                    local.update { it.copy(error = throwable.message ?: "Delete failed") }
                }
            _dialogs.update { it.copy(pendingDelete = null) }
        }
    }

    fun startNewNote() = _dialogs.update { it.copy(creatingNote = true, noteEditor = null) }

    fun editNote(note: SecureNote) = _dialogs.update { it.copy(noteEditor = note, creatingNote = false) }

    fun saveNote(id: Long?, title: String, content: String) {
        viewModelScope.launch {
            repository.saveNote(id, title, content)
                .onSuccess { local.update { s -> s.copy(message = "Note saved") } }
                .onFailure { throwable ->
                    local.update { it.copy(error = throwable.message ?: "Unable to save note") }
                }
            _dialogs.update { it.copy(noteEditor = null, creatingNote = false) }
        }
    }

    fun requestDeleteNote(note: SecureNote) = _dialogs.update { it.copy(pendingDeleteNote = note) }

    fun confirmDeleteNote() {
        val note = _dialogs.value.pendingDeleteNote ?: return
        viewModelScope.launch {
            repository.deleteNote(note.id)
            _dialogs.update { it.copy(pendingDeleteNote = null) }
        }
    }

    fun dismissDialogs() {
        viewModelScope.launch {
            _dialogs.value.playingVideo?.second?.delete()
            repository.cleanupTemporaryFiles()
            _dialogs.value = VaultDialogState()
        }
    }

    fun consumeMessage() = local.update { it.copy(message = null) }

    fun consumeError() = local.update { it.copy(error = null) }

    companion object {
        private const val MAX_THUMBNAILS = 60
    }
}
