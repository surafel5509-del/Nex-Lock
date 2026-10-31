package com.nexlock.core.intruder

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.nexlock.common.model.SecurityEventType
import com.nexlock.common.model.SecuritySeverity
import com.nexlock.common.util.DispatcherProvider
import com.nexlock.common.util.NexLog
import com.nexlock.common.util.TimeProvider
import com.nexlock.core.events.SecurityEventRepository
import com.nexlock.core.settings.SettingsRepository
import com.nexlock.database.dao.IntruderDao
import com.nexlock.database.entity.IntruderIncidentEntity
import com.nexlock.security.crypto.SecureFileStore
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

data class IntruderIncident(
    val id: Long,
    val timestamp: Long,
    val failedAttempts: Int,
    val hasPhoto: Boolean,
    val packageName: String?,
    val note: String?,
)

/**
 * Private incident history for repeated failed authentication attempts.
 *
 * Captures are opt-in, stay in the app sandbox encrypted with the Keystore key, and can be deleted
 * by the user at any time (individually or all at once).
 */
@Singleton
class IntruderRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dao: IntruderDao,
    private val fileStore: SecureFileStore,
    private val events: SecurityEventRepository,
    private val settingsRepository: SettingsRepository,
    private val timeProvider: TimeProvider,
    private val dispatchers: DispatcherProvider,
) {

    fun observeIncidents(): Flow<List<IntruderIncident>> =
        dao.observeAll().map { list -> list.map { it.toDomain() } }

    fun observeCount(): Flow<Int> = dao.observeCount()

    /** Encrypts and stores a front-camera capture, then removes the plaintext temp file. */
    suspend fun recordCapture(
        photo: File,
        failedAttempts: Int,
        packageName: String?,
    ): Result<Long> = withContext(dispatchers.io) {
        runCatching {
            val settings = settingsRepository.current()
            require(settings.intruderDetectionEnabled) { "Intruder detection is disabled" }
            val bytes = photo.readBytes()
            val fileName = try {
                fileStore.writeBytes(bytes, fileStore.intruderDirectory(), "jpg")
            } finally {
                bytes.fill(0)
                photo.delete()
            }
            val id = dao.insert(
                IntruderIncidentEntity(
                    timestamp = timeProvider.nowMillis(),
                    failedAttempts = failedAttempts,
                    photoFileName = fileName,
                    packageName = packageName,
                )
            )
            events.log(
                SecurityEventType.INTRUDER_CAPTURED,
                SecuritySeverity.CRITICAL,
                "Intruder capture stored after $failedAttempts failed attempts",
            )
            id
        }.onFailure { NexLog.w(TAG, "Unable to store intruder capture") }
    }

    /** Records the incident without a photo (permission denied or feature disabled). */
    suspend fun recordIncident(
        failedAttempts: Int,
        packageName: String?,
        note: String? = null,
    ): Result<Long> = withContext(dispatchers.io) {
        runCatching {
            val id = dao.insert(
                IntruderIncidentEntity(
                    timestamp = timeProvider.nowMillis(),
                    failedAttempts = failedAttempts,
                    photoFileName = null,
                    packageName = packageName,
                    note = note,
                )
            )
            events.log(
                SecurityEventType.AUTH_FAILURE,
                SecuritySeverity.WARNING,
                "Repeated failed attempts recorded ($failedAttempts)",
            )
            id
        }
    }

    suspend fun deleteIncident(id: Long): Result<Unit> = withContext(dispatchers.io) {
        runCatching {
            val incidents = dao.all()
            incidents.firstOrNull { it.id == id }?.photoFileName?.let { fileName ->
                fileStore.delete(fileName, fileStore.intruderDirectory())
            }
            dao.delete(id)
        }
    }

    suspend fun clearAll(): Result<Unit> = withContext(dispatchers.io) {
        runCatching {
            dao.all().forEach { incident ->
                incident.photoFileName?.let { fileName ->
                    fileStore.delete(fileName, fileStore.intruderDirectory())
                }
            }
            dao.clear()
            events.log(
                SecurityEventType.INTRUDER_CAPTURED,
                SecuritySeverity.INFO,
                "Intruder incident history cleared",
            )
        }
    }

    suspend fun loadPhoto(id: Long): Result<Bitmap> = withContext(dispatchers.io) {
        runCatching {
            val incident = dao.all().firstOrNull { it.id == id }
                ?: throw IllegalStateException("Incident no longer exists")
            val fileName = incident.photoFileName ?: throw IllegalStateException("No capture for this incident")
            val bytes = fileStore.readBytes(fileName, MAX_PHOTO_BYTES, fileStore.intruderDirectory())
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            var sample = 1
            while (bounds.outWidth / (sample * 2) >= 640) sample *= 2
            BitmapFactory.decodeByteArray(
                bytes,
                0,
                bytes.size,
                BitmapFactory.Options().apply { inSampleSize = sample },
            ) ?: throw IllegalStateException("Capture could not be decoded")
        }
    }

    suspend fun hasPhoto(id: Long): Boolean = withContext(dispatchers.io) {
        dao.all().firstOrNull { it.id == id }?.photoFileName
            ?.let { fileStore.exists(it, fileStore.intruderDirectory()) } == true
    }

    private fun IntruderIncidentEntity.toDomain() = IntruderIncident(
        id = id,
        timestamp = timestamp,
        failedAttempts = failedAttempts,
        hasPhoto = photoFileName != null,
        packageName = packageName,
        note = note,
    )

    companion object {
        private const val TAG = "Intruder"
        private const val MAX_PHOTO_BYTES = 16L * 1024 * 1024
    }
}
