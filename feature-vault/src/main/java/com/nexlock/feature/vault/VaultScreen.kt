package com.nexlock.feature.vault

import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Note
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Upload
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nexlock.common.model.VaultKind
import com.nexlock.common.util.Formatters
import com.nexlock.core.vault.SecureNote
import com.nexlock.core.vault.VaultItem
import com.nexlock.design.components.EmptyState
import com.nexlock.design.components.GlassCard
import com.nexlock.design.components.ListSkeleton
import com.nexlock.design.components.NexConfirmDialog
import com.nexlock.design.components.NexFilterChip
import com.nexlock.design.components.NexPrimaryButton
import com.nexlock.design.components.NexTextField
import com.nexlock.design.components.clickableNoRipple
import com.nexlock.design.strings.LocalStrings
import com.nexlock.design.theme.LocalNexColors
import com.nexlock.security.privacy.SecureScreen

@Composable
fun VaultRoute(
    modifier: Modifier = Modifier,
    viewModel: VaultViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val dialogs by viewModel.dialogs.collectAsStateWithLifecycle()

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let(viewModel::importUri) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("*/*")
    ) { uri -> uri?.let(viewModel::exportTo) }

    VaultScreen(
        state = state,
        dialogs = dialogs,
        modifier = modifier,
        onImport = {
            importLauncher.launch(
                arrayOf("image/*", "video/*", "application/pdf", "text/plain", "application/*")
            )
        },
        onOpenImage = viewModel::openImage,
        onOpenVideo = viewModel::playVideo,
        onShowDocument = viewModel::showDocumentInfo,
        onRequestThumbnail = viewModel::requestThumbnail,
        onRequestExport = viewModel::requestExport,
        onRequestDelete = viewModel::requestDelete,
        onConfirmDelete = viewModel::confirmDelete,
        onDismissDialogs = viewModel::dismissDialogs,
        onStartExportNow = { item -> exportLauncher.launch(item.displayName) },
        onNewNote = viewModel::startNewNote,
        onEditNote = viewModel::editNote,
        onSaveNote = viewModel::saveNote,
        onDeleteNote = viewModel::requestDeleteNote,
        onConfirmDeleteNote = viewModel::confirmDeleteNote,
        onConsumeMessage = viewModel::consumeMessage,
        onConsumeError = viewModel::consumeError,
    )
}

@Composable
fun VaultScreen(
    state: VaultUiState,
    dialogs: VaultDialogState,
    modifier: Modifier = Modifier,
    onImport: () -> Unit = {},
    onOpenImage: (VaultItem) -> Unit = {},
    onOpenVideo: (VaultItem) -> Unit = {},
    onShowDocument: (VaultItem) -> Unit = {},
    onRequestThumbnail: (VaultItem) -> Unit = {},
    onRequestExport: (VaultItem) -> Unit = {},
    onRequestDelete: (VaultItem) -> Unit = {},
    onConfirmDelete: () -> Unit = {},
    onDismissDialogs: () -> Unit = {},
    onStartExportNow: (VaultItem) -> Unit = {},
    onNewNote: () -> Unit = {},
    onEditNote: (SecureNote) -> Unit = {},
    onSaveNote: (Long?, String, String) -> Unit = { _, _, _ -> },
    onDeleteNote: (SecureNote) -> Unit = {},
    onConfirmDeleteNote: () -> Unit = {},
    onConsumeMessage: () -> Unit = {},
    onConsumeError: () -> Unit = {},
) {
    val strings = LocalStrings.current
    val colors = LocalNexColors.current
    var tab by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(VaultTab.PHOTOS) }

    SecureScreen(enabled = true)

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    strings.vaultTitle,
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Text(
                    "${strings.storageUsed}: ${Formatters.fileSize(state.totalBytes)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(colors.accentBrush)
                    .clickableNoRipple(onClick = onImport)
                    .padding(horizontal = 16.dp, vertical = 10.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Upload, null, tint = Color.White, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(strings.importFile, style = MaterialTheme.typography.labelMedium, color = Color.White)
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            VaultTab.entries.forEach { entry ->
                NexFilterChip(
                    label = when (entry) {
                        VaultTab.PHOTOS -> strings.photos
                        VaultTab.VIDEOS -> strings.videos
                        VaultTab.DOCUMENTS -> strings.documents
                        VaultTab.NOTES -> strings.notes
                    },
                    selected = tab == entry,
                    onClick = { tab = entry },
                )
            }
        }

        if (state.message != null) {
            Text(
                state.message,
                style = MaterialTheme.typography.bodySmall,
                color = colors.success,
                modifier = Modifier
                    .padding(horizontal = 20.dp, vertical = 4.dp)
                    .clickableNoRipple(onClick = onConsumeMessage),
            )
        }
        if (state.error != null) {
            Text(
                state.error,
                style = MaterialTheme.typography.bodySmall,
                color = colors.danger,
                modifier = Modifier
                    .padding(horizontal = 20.dp, vertical = 4.dp)
                    .clickableNoRipple(onClick = onConsumeError),
            )
        }

        when {
            state.loading -> ListSkeleton(rows = 5, modifier = Modifier.weight(1f))
            tab == VaultTab.NOTES -> NotesTab(
                notes = state.notes,
                modifier = Modifier.weight(1f),
                onNewNote = onNewNote,
                onEditNote = onEditNote,
                onDeleteNote = onDeleteNote,
            )
            else -> {
                val kind = tab.kind
                val items = state.items.filter { it.kind == kind }
                when {
                    items.isEmpty() -> EmptyState(
                        icon = when (tab) {
                            VaultTab.PHOTOS -> Icons.Rounded.Image
                            VaultTab.VIDEOS -> Icons.Rounded.PlayArrow
                            else -> Icons.Rounded.Description
                        },
                        title = strings.vaultEmpty,
                        description = strings.vaultEmptyDetail,
                        actionLabel = strings.importFile,
                        onAction = onImport,
                        modifier = Modifier.weight(1f),
                    )
                    tab == VaultTab.DOCUMENTS -> DocumentList(
                        items = items,
                        modifier = Modifier.weight(1f),
                        onOpen = onShowDocument,
                        onExport = onRequestExport,
                        onDelete = onRequestDelete,
                    )
                    else -> MediaGrid(
                        items = items,
                        thumbnails = state.thumbnails,
                        isVideo = tab == VaultTab.VIDEOS,
                        modifier = Modifier.weight(1f),
                        onOpen = { if (tab == VaultTab.VIDEOS) onOpenVideo(it) else onOpenImage(it) },
                        onRequestThumbnail = onRequestThumbnail,
                        onExport = onRequestExport,
                        onDelete = onRequestDelete,
                    )
                }
            }
        }
    }

    // ------------------------------------------------------------------ dialogs

    dialogs.previewImage?.let { (item, bitmap) ->
        ImagePreviewDialog(item, bitmap, onDismiss = onDismissDialogs, onExport = { onRequestExport(item) })
    }

    dialogs.playingVideo?.let { (item, file) ->
        VideoPlayerDialog(item, file.absolutePath, onDismiss = onDismissDialogs)
    }

    dialogs.documentInfo?.let { item ->
        DocumentInfoDialog(
            item = item,
            onDismiss = onDismissDialogs,
            onExport = {
                onDismissDialogs()
                onStartExportNow(item)
            },
        )
    }

    dialogs.pendingDelete?.let { item ->
        NexConfirmDialog(
            title = "Delete permanently?",
            message = "\"${item.displayName}\" will be overwritten and removed from NexLock. This cannot be undone.",
            confirmLabel = strings.deleteForever,
            destructive = true,
            onConfirm = onConfirmDelete,
            onDismiss = onDismissDialogs,
        )
    }

    dialogs.pendingDeleteNote?.let { note ->
        NexConfirmDialog(
            title = "Delete note?",
            message = "\"${note.title.ifBlank { "Untitled" }}\" will be removed permanently.",
            confirmLabel = strings.delete,
            destructive = true,
            onConfirm = onConfirmDeleteNote,
            onDismiss = onDismissDialogs,
        )
    }

    if (dialogs.creatingNote || dialogs.noteEditor != null) {
        NoteEditorDialog(
            note = dialogs.noteEditor,
            onSave = onSaveNote,
            onDismiss = onDismissDialogs,
        )
    }
}

@Composable
private fun MediaGrid(
    items: List<VaultItem>,
    thumbnails: Map<Long, Bitmap>,
    isVideo: Boolean,
    modifier: Modifier = Modifier,
    onOpen: (VaultItem) -> Unit,
    onRequestThumbnail: (VaultItem) -> Unit,
    onExport: (VaultItem) -> Unit,
    onDelete: (VaultItem) -> Unit,
) {
    val colors = LocalNexColors.current
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        gridItems(items, key = { it.id }) { item ->
            if (!isVideo) {
                LaunchedEffect(item.id) { onRequestThumbnail(item) }
            }
            GlassCard(contentPadding = PaddingValues(8.dp)) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(16.dp))
                        .background(colors.glassFill)
                        .clickableNoRipple { onOpen(item) },
                    contentAlignment = Alignment.Center,
                ) {
                    val bitmap = thumbnails[item.id]
                    if (bitmap != null && !isVideo) {
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = item.displayName,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop,
                        )
                    } else {
                        Icon(
                            imageVector = if (isVideo) Icons.Rounded.PlayArrow else Icons.Rounded.Image,
                            contentDescription = null,
                            tint = colors.glow,
                            modifier = Modifier.size(34.dp),
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    item.displayName,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        Formatters.fileSize(item.sizeBytes),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = { onExport(item) }, modifier = Modifier.size(30.dp)) {
                        Icon(
                            Icons.Rounded.Download,
                            contentDescription = "Export",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                    IconButton(onClick = { onDelete(item) }, modifier = Modifier.size(30.dp)) {
                        Icon(
                            Icons.Rounded.Delete,
                            contentDescription = "Delete",
                            tint = colors.danger,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DocumentList(
    items: List<VaultItem>,
    modifier: Modifier = Modifier,
    onOpen: (VaultItem) -> Unit,
    onExport: (VaultItem) -> Unit,
    onDelete: (VaultItem) -> Unit,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(items, key = { it.id }) { item ->
            GlassCard(onClick = { onOpen(item) }, contentPadding = PaddingValues(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Rounded.Description,
                        contentDescription = null,
                        tint = LocalNexColors.current.glow,
                        modifier = Modifier.size(22.dp),
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            item.displayName,
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            "${Formatters.fileSize(item.sizeBytes)} · ${item.mimeType}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                    }
                    IconButton(onClick = { onExport(item) }) {
                        Icon(Icons.Rounded.Download, "Export", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = { onDelete(item) }) {
                        Icon(Icons.Rounded.Delete, "Delete", tint = LocalNexColors.current.danger)
                    }
                }
            }
        }
    }
}

@Composable
private fun NotesTab(
    notes: List<SecureNote>,
    modifier: Modifier = Modifier,
    onNewNote: () -> Unit,
    onEditNote: (SecureNote) -> Unit,
    onDeleteNote: (SecureNote) -> Unit,
) {
    Column(modifier = modifier.fillMaxSize()) {
        Row(modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)) {
            NexPrimaryButton(
                text = "New note",
                onClick = onNewNote,
                modifier = Modifier.fillMaxWidth(),
                icon = Icons.Rounded.Add,
            )
        }
        if (notes.isEmpty()) {
            EmptyState(
                icon = Icons.Rounded.Note,
                title = "No private notes yet",
                description = "Notes are encrypted with AES-256-GCM before they touch the database.",
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            LazyColumn(
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(notes, key = { it.id }) { note ->
                    GlassCard(onClick = { onEditNote(note) }, contentPadding = PaddingValues(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Rounded.Note,
                                contentDescription = null,
                                tint = LocalNexColors.current.glow,
                                modifier = Modifier.size(20.dp),
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    note.title.ifBlank { "Untitled note" },
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    note.content,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    Formatters.timestamp(note.updatedAt),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            IconButton(onClick = { onDeleteNote(note) }) {
                                Icon(
                                    Icons.Rounded.Delete,
                                    "Delete",
                                    tint = LocalNexColors.current.danger,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ImagePreviewDialog(
    item: VaultItem,
    bitmap: Bitmap,
    onDismiss: () -> Unit,
    onExport: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        GlassCard {
            Text(
                item.displayName,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(10.dp))
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = item.displayName,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(380.dp)
                    .clip(RoundedCornerShape(18.dp)),
                contentScale = ContentScale.Fit,
            )
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NexPrimaryButton(
                    text = "Export",
                    onClick = onExport,
                    modifier = Modifier.weight(1f),
                    icon = Icons.Rounded.Download,
                )
                NexPrimaryButton(
                    text = "Close",
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun VideoPlayerDialog(item: VaultItem, path: String, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        GlassCard {
            Text(
                item.displayName,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(10.dp))
            AndroidView(
                factory = { context ->
                    android.widget.VideoView(context).apply {
                        setVideoPath(path)
                        val controller = android.widget.MediaController(context)
                        controller.setAnchorView(this)
                        setMediaController(controller)
                        setOnPreparedListener { player ->
                            player.isLooping = false
                            start()
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(260.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color.Black),
            )
            Spacer(Modifier.height(10.dp))
            Text(
                "The decrypted video only exists in NexLock's private cache while this window is open.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(10.dp))
            NexPrimaryButton(text = "Close", onClick = onDismiss, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun DocumentInfoDialog(item: VaultItem, onDismiss: () -> Unit, onExport: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        GlassCard {
            Text(item.displayName, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(10.dp))
            InfoLine("Type", item.mimeType)
            InfoLine("Size", Formatters.fileSize(item.sizeBytes))
            InfoLine("Imported", Formatters.timestamp(item.createdAt))
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NexPrimaryButton(
                    text = "Export",
                    onClick = onExport,
                    modifier = Modifier.weight(1f),
                    icon = Icons.Rounded.Download,
                )
                NexPrimaryButton(text = "Close", onClick = onDismiss, modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun InfoLine(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            value,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun NoteEditorDialog(
    note: SecureNote?,
    onSave: (Long?, String, String) -> Unit,
    onDismiss: () -> Unit,
) {
    var title by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(note?.title.orEmpty()) }
    var content by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(note?.content.orEmpty()) }
    Dialog(onDismissRequest = onDismiss) {
        GlassCard {
            Text(
                if (note == null) "New note" else "Edit note",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(10.dp))
            NexTextField(
                value = title,
                onValueChange = { title = it },
                label = "Title",
            )
            Spacer(Modifier.height(8.dp))
            NexTextField(
                value = content,
                onValueChange = { content = it },
                label = "Note",
                singleLine = false,
                minLines = 6,
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NexPrimaryButton(
                    text = "Save",
                    onClick = { onSave(note?.id, title, content) },
                    modifier = Modifier.weight(1f),
                    enabled = title.isNotBlank() || content.isNotBlank(),
                )
                NexPrimaryButton(text = "Cancel", onClick = onDismiss, modifier = Modifier.weight(1f))
            }
        }
    }
}
