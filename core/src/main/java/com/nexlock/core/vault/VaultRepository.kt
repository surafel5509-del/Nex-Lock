package com.nexlock.core.vault

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import com.nexlock.common.model.SecurityEventType
import com.nexlock.common.model.SecuritySeverity
import com.nexlock.common.model.VaultKind
import com.nexlock.common.util.DispatcherProvider
import com.nexlock.common.util.NexConstants
import com.nexlock.common.util.NexLog
import com.nexlock.common.util.TimeProvider
import com.nexlock.core.events.SecurityEventRepository
import com.nexlock.database.dao.SecureNoteDao
import com.nexlock.database.dao.VaultDao
import com.nexlock.database.entity.SecureNoteEntity
import com.nexlock.database.entity.VaultItemEntity
import com.nexlock.security.crypto.CryptoManager
import com.nexlock.security.crypto.CryptoException
import com.nexlock.security.crypto.SecureFileStore
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * Private vault: media and documents are encrypted with the Keystore-backed AES-256-GCM key and
 * stored in the app sandbox; notes are encrypted field-wise in Room.
 *
 * Import/export use the Storage Access Framework, so NexLock itself never needs broad storage
 * permissions.
 */
@Singleton
class VaultRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val vaultDao: VaultDao,
    private val noteDao: SecureNoteDao,
    private val fileStore: SecureFileStore,
    private val crypto: CryptoManager,
    private val events: SecurityEventRepository,
    private val timeProvider: TimeProvider,
    private val dispatchers: DispatcherProvider,
) {

    fun observeItems(): Flow<List<VaultItem>> =
        vaultDao.observeAll().map { list -> list.map { it.toDomain() } }

    fun observeItems(kind: VaultKind): Flow<List<VaultItem>> =
        vaultDao.observeByKind(kind).map { list -> list.map { it.toDomain() } }

    fun observeCount(kind: VaultKind): Flow<Int> = vaultDao.observeCount(kind)

    fun observeTotalBytes(): Flow<Long> = vaultDao.observeTotalBytes()

    fun observeNotes(): Flow<List<SecureNote>> = noteDao.observeAll().map { notes ->
        notes.mapNotNull { entity ->
            runCatching {
                SecureNote(
                    id = entity.id,
                    title = crypto.decryptString(entity.titleCipher),
                    content = crypto.decryptString(entity.contentCipher),
                    createdAt = entity.createdAt,
                    updatedAt = entity.updatedAt,
                )
            }.onFailure { NexLog.w(TAG, "Skipping unreadable note ${entity.id}") }.getOrNull()
        }
    }

    // ------------------------------------------------------------------ media import / export

    suspend fun importFromUri(uri: Uri, kind: VaultKind? = null): Result<VaultItem> = withContext(dispatchers.io) {
        runCatching {
            val resolver = context.contentResolver
            val metadata = readMetadata(uri)
            if (metadata.sizeBytes > NexConstants.MAX_IMPORT_BYTES) {
                throw IllegalArgumentException("File is larger than ${NexConstants.MAX_IMPORT_BYTES / (1024 * 1024)} MB")
            }
            val stream = resolver.openInputStream(uri)
                ?: throw IllegalStateException("The selected file could not be opened")
            val stored = stream.use { fileStore.importStream(it, metadata.displayName) }
            val item = VaultItemEntity(
                kind = kind ?: kindForMimeType(metadata.mimeType),
                displayName = metadata.displayName,
                fileName = stored.fileName,
                mimeType = metadata.mimeType,
                sizeBytes = stored.sizeBytes,
                createdAt = timeProvider.nowMillis(),
            )
            val id = vaultDao.insert(item)
            events.log(
                SecurityEventType.VAULT_IMPORT,
                SecuritySeverity.INFO,
                "File imported into the private vault",
            )
            item.copy(id = id).toDomain()
        }
    }

    suspend fun exportToUri(itemId: Long, destination: Uri): Result<Unit> = withContext(dispatchers.io) {
        runCatching {
            val item = vaultDao.find(itemId) ?: throw IllegalStateException("Item no longer exists")
            val output = context.contentResolver.openOutputStream(destination)
                ?: throw IllegalStateException("The destination could not be opened")
            output.use { fileStore.exportTo(item.fileName, it) }
            events.log(
                SecurityEventType.VAULT_EXPORT,
                SecuritySeverity.WARNING,
                "A vault file was exported to a user selected location",
            )
            Unit
        }
    }

    suspend fun delete(itemId: Long): Result<Unit> = withContext(dispatchers.io) {
        runCatching {
            val item = vaultDao.find(itemId) ?: throw IllegalStateException("Item no longer exists")
            fileStore.delete(item.fileName)
            vaultDao.delete(itemId)
            events.log(
                SecurityEventType.VAULT_DELETE,
                SecuritySeverity.INFO,
                "A vault file was securely deleted",
            )
            Unit
        }
    }

    /** Decodes a downsampled preview of an image item. Returns null for non-image items. */
    suspend fun loadImage(itemId: Long, maxDimension: Int = 1280): Result<Bitmap> =
        withContext(dispatchers.io) {
            runCatching {
                val item = vaultDao.find(itemId) ?: throw IllegalStateException("Item no longer exists")
                if (!item.mimeType.startsWith("image/")) throw CryptoException("Item is not an image")
                if (item.sizeBytes > NexConstants.MAX_THUMBNAIL_BYTES) {
                    throw CryptoException("Image is too large to preview")
                }
                val bytes = fileStore.readBytes(item.fileName, NexConstants.MAX_THUMBNAIL_BYTES)
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                val sample = calculateInSampleSize(bounds.outWidth, bounds.outHeight, maxDimension)
                val options = BitmapFactory.Options().apply {
                    inSampleSize = sample
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
                    ?: throw CryptoException("Image could not be decoded")
            }
        }

    /** Decrypts a video/document into the private cache for in-app playback. */
    suspend fun prepareForViewing(itemId: Long): Result<File> = withContext(dispatchers.io) {
        runCatching {
            val item = vaultDao.find(itemId) ?: throw IllegalStateException("Item no longer exists")
            val temp = fileStore.decryptToTempFile(item.fileName)
            val extension = item.mimeType.substringAfterLast('/', "").ifBlank { "bin" }
            val named = File(temp.parentFile, "${temp.name}.$extension")
            if (temp.renameTo(named)) named else temp
        }
    }

    suspend fun cleanupTemporaryFiles() = withContext(dispatchers.io) {
        fileStore.tempDirectory().listFiles()?.forEach { it.delete() }
    }

    // ------------------------------------------------------------------ notes

    suspend fun saveNote(id: Long?, title: String, content: String): Result<Long> =
        withContext(dispatchers.io) {
            runCatching {
                require(title.isNotBlank() || content.isNotBlank()) { "Note is empty" }
                val titleCipher = crypto.encryptString(title)
                val contentCipher = crypto.encryptString(content)
                val now = timeProvider.nowMillis()
                if (id == null) {
                    noteDao.insert(
                        SecureNoteEntity(
                            titleCipher = titleCipher,
                            contentCipher = contentCipher,
                            createdAt = now,
                            updatedAt = now,
                        )
                    )
                } else {
                    val existing = noteDao.find(id) ?: throw IllegalStateException("Note no longer exists")
                    noteDao.update(id, titleCipher, contentCipher, now)
                    existing.id
                }
            }
        }

    suspend fun deleteNote(id: Long) = withContext(dispatchers.io) { noteDao.delete(id) }

    suspend fun note(id: Long): SecureNote? = withContext(dispatchers.io) {
        noteDao.find(id)?.let { entity ->
            runCatching {
                SecureNote(
                    id = entity.id,
                    title = crypto.decryptString(entity.titleCipher),
                    content = crypto.decryptString(entity.contentCipher),
                    createdAt = entity.createdAt,
                    updatedAt = entity.updatedAt,
                )
            }.getOrNull()
        }
    }

    // ------------------------------------------------------------------ helpers

    private fun readMetadata(uri: Uri): ImportMetadata {
        val resolver = context.contentResolver
        var name: String? = null
        var size = 0L
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (nameIndex >= 0 && !cursor.isNull(nameIndex)) name = cursor.getString(nameIndex)
                    if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) size = cursor.getLong(sizeIndex)
                }
            }
        val mime = resolver.getType(uri) ?: guessMimeType(name.orEmpty())
        return ImportMetadata(
            displayName = (name ?: "imported_${timeProvider.nowMillis()}").take(120),
            mimeType = mime,
            sizeBytes = size,
        )
    }

    /** Maps a MIME type to the vault bucket it belongs to. */
    fun kindForMimeType(mimeType: String): VaultKind = when {
        mimeType.startsWith("image/") -> VaultKind.PHOTO
        mimeType.startsWith("video/") -> VaultKind.VIDEO
        else -> VaultKind.DOCUMENT
    }

    private fun guessMimeType(name: String): String = when (name.substringAfterLast('.', "").lowercase()) {
        "jpg", "jpeg" -> "image/jpeg"
        "png" -> "image/png"
        "gif" -> "image/gif"
        "webp" -> "image/webp"
        "heic", "heif" -> "image/heic"
        "mp4" -> "video/mp4"
        "mkv" -> "video/x-matroska"
        "mov" -> "video/quicktime"
        "webm" -> "video/webm"
        "pdf" -> "application/pdf"
        "txt", "md" -> "text/plain"
        else -> "application/octet-stream"
    }

    private fun calculateInSampleSize(width: Int, height: Int, maxDimension: Int): Int {
        if (width <= 0 || height <= 0) return 1
        var sample = 1
        var longest = maxOf(width, height)
        while (longest / 2 >= maxDimension) {
            longest /= 2
            sample *= 2
        }
        return sample
    }

    private fun VaultItemEntity.toDomain() = VaultItem(
        id = id,
        kind = kind,
        displayName = displayName,
        fileName = fileName,
        mimeType = mimeType,
        sizeBytes = sizeBytes,
        createdAt = createdAt,
    )

    private data class ImportMetadata(val displayName: String, val mimeType: String, val sizeBytes: Long)

    companion object {
        private const val TAG = "Vault"
    }
}
