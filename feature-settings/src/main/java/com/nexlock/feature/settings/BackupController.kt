package com.nexlock.feature.settings

import android.content.Context
import android.net.Uri
import com.nexlock.core.backup.BackupRepository
import com.nexlock.core.backup.RestoreSummary
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Bridges the backup repository with the Storage Access Framework. */
@Singleton
class BackupController @Inject constructor(
    @ApplicationContext private val context: Context,
    private val backupRepository: BackupRepository,
) {

    suspend fun createBackup(passphrase: String): Result<ByteArray> =
        backupRepository.createBackup(passphrase.toCharArray())

    suspend fun writeBytes(uri: Uri, bytes: ByteArray): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
                ?: throw IllegalStateException("The destination could not be opened")
        }
    }

    suspend fun readBytes(uri: Uri): Result<ByteArray> = withContext(Dispatchers.IO) {
        runCatching {
            context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: throw IllegalStateException("The selected file could not be read")
        }
    }

    suspend fun restore(bytes: ByteArray, passphrase: String): Result<RestoreSummary> =
        backupRepository.restoreBackup(bytes, passphrase.toCharArray())
}
