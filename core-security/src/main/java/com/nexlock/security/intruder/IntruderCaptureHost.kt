package com.nexlock.security.intruder

import android.content.Context
import android.content.pm.PackageManager
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.OutputFileOptions
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.common.util.concurrent.ListenableFuture
import java.io.File
import java.util.concurrent.Executor
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Headless front-camera capture used by intruder detection.
 *
 * The camera is only opened when [request] changes to a non-zero value, which the lock screen does
 * exclusively after repeated failed attempts **and** only when the user has enabled intruder photos
 * and granted the camera permission. NexLock never records silently: the feature is opt-in, is
 * described in settings and can be switched off at any time.
 */
@Composable
fun IntruderCaptureHost(
    request: Long,
    onCaptured: (File) -> Unit,
    onError: (String) -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    LaunchedEffect(request) {
        if (request <= 0L) return@LaunchedEffect
        if (!hasCameraPermission(context)) {
            onError("Camera permission not granted")
            return@LaunchedEffect
        }
        val directory = File(context.cacheDir, "intruder_captures").apply { mkdirs() }
        val target = File(directory, "capture_${System.currentTimeMillis()}.jpg")
        val provider = try {
            ProcessCameraProvider.getInstance(context).awaitProvider(context)
        } catch (e: Exception) {
            onError(e.message ?: "Camera unavailable")
            return@LaunchedEffect
        }
        try {
            provider.unbindAll()
            val imageCapture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .build()
            provider.bindToLifecycle(
                lifecycleOwner,
                CameraSelector.DEFAULT_FRONT_CAMERA,
                imageCapture,
            )
            val result = imageCapture.capture(target, ContextCompat.getMainExecutor(context))
            if (result.isSuccess) {
                onCaptured(target)
            } else {
                onError(result.exceptionOrNull()?.message ?: "Capture failed")
            }
        } catch (e: Exception) {
            onError(e.message ?: "Front camera not available")
        } finally {
            runCatching { provider.unbindAll() }
        }
    }
}

fun hasCameraPermission(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, android.Manifest.permission.CAMERA) ==
        PackageManager.PERMISSION_GRANTED

private suspend fun ImageCapture.capture(target: File, executor: Executor): Result<File> =
    suspendCancellableCoroutine { continuation ->
        val options = OutputFileOptions.Builder(target).build()
        takePicture(
            options,
            executor,
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    if (continuation.isActive) continuation.resume(Result.success(target))
                }

                override fun onError(exception: ImageCaptureException) {
                    target.delete()
                    if (continuation.isActive) continuation.resume(Result.failure(exception))
                }
            },
        )
    }

private suspend fun ListenableFuture<ProcessCameraProvider>.awaitProvider(
    context: Context,
): ProcessCameraProvider = suspendCancellableCoroutine { continuation ->
    addListener(
        {
            try {
                continuation.resume(get())
            } catch (e: Exception) {
                continuation.cancel(e)
            }
        },
        ContextCompat.getMainExecutor(context),
    )
}
