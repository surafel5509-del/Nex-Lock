package com.nexlock.security.risk

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Debug
import com.nexlock.common.model.RiskLevel
import com.nexlock.common.model.RiskReport
import com.nexlock.common.util.NexLog
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/** Provides the expected signing-certificate digest (SHA-256, lowercase hex) for this build. */
interface SignatureExpectation {
    val expectedSha256: String
}

/**
 * Best-effort tamper and environment assessment.
 *
 * These checks are **indicators, not guarantees**: a determined attacker with root can hide them.
 * The UI therefore presents them as risk signals and never claims absolute protection.
 */
@Singleton
class RiskAssessor @Inject constructor(
    @ApplicationContext private val context: Context,
    private val signatureExpectation: SignatureExpectation,
) {

    fun assess(): RiskReport {
        val rooted = isRooted()
        val emulator = isEmulator()
        val debuggable = isDebuggable()
        val signatureValid = verifySignature()

        val signals = buildList {
            if (rooted) add("Root indicators found on the device")
            if (Build.TAGS?.contains("test-keys") == true) add("Device build is signed with test keys")
            if (debuggerAttached()) add("A debugger is attached to this process")
            if (debuggable) add("NexLock is running as a debuggable build")
            if (emulator) add("Running on an emulator")
            if (!signatureValid) add("Signing certificate does not match the expected digest")
            if (signatureExpectation.expectedSha256.isBlank()) add("Signature pinning not configured for this build")
        }

        val level = when {
            rooted || !signatureValid -> RiskLevel.HIGH
            emulator || debuggable || debuggerAttached() -> RiskLevel.MEDIUM
            else -> RiskLevel.LOW
        }

        return RiskReport(
            level = level,
            signals = signals,
            isRooted = rooted,
            isDebuggable = debuggable,
            isEmulator = emulator,
            isSignatureValid = signatureValid,
        )
    }

    private fun isRooted(): Boolean {
        if (Build.TAGS?.contains("test-keys") == true) return true
        if (SU_PATHS.any { path -> runCatching { File(path).exists() }.getOrDefault(false) }) return true
        if (ROOT_PACKAGES.any { installed(it) }) return true

        // Magisk can leave mount entries visible in /proc/mounts.
        val mounts = runCatching {
            File("/proc/mounts").useLines { lines ->
                lines.any { it.contains("magisk", ignoreCase = true) }
            }
        }.getOrDefault(false)
        return mounts
    }

    private fun installed(packageName: String): Boolean = runCatching {
        context.packageManager.getPackageInfo(packageName, 0)
        true
    }.getOrDefault(false)

    private fun isEmulator(): Boolean {
        val fingerprint = Build.FINGERPRINT
        return fingerprint.startsWith("generic") ||
            fingerprint.contains("emulator", ignoreCase = true) ||
            Build.MODEL.contains("google_sdk", ignoreCase = true) ||
            Build.MODEL.contains("Emulator", ignoreCase = true) ||
            Build.MODEL.contains("Android SDK built for", ignoreCase = true) ||
            Build.HARDWARE.contains("goldfish", ignoreCase = true) ||
            Build.HARDWARE.contains("ranchu", ignoreCase = true) ||
            Build.PRODUCT.contains("sdk", ignoreCase = true)
    }

    private fun isDebuggable(): Boolean =
        (context.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0

    private fun debuggerAttached(): Boolean = runCatching { Debug.isDebuggerConnected() }.getOrDefault(false)

    @Suppress("DEPRECATION")
    private fun verifySignature(): Boolean {
        val expected = signatureExpectation.expectedSha256.trim().lowercase()
        if (expected.isBlank()) return true // pinning not configured for this build; flagged as a signal
        val actual = currentSignatureHashes()
        return actual.any { it.equals(expected, ignoreCase = true) }
    }

    @Suppress("DEPRECATION")
    fun currentSignatureHashes(): List<String> = runCatching {
        val pm = context.packageManager
        val packageName = context.packageName
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val info = pm.getPackageInfo(packageName, PackageManager.GET_SIGNING_CERTIFICATES)
            val signingInfo = info.signingInfo ?: return emptyList()
            val signers = if (signingInfo.hasMultipleSigners()) {
                signingInfo.apkContentsSigners
            } else {
                signingInfo.signingCertificateHistory
            }
            signers.map { sha256(it.toByteArray()) }
        } else {
            val info = pm.getPackageInfo(packageName, PackageManager.GET_SIGNATURES)
            info.signatures?.map { sha256(it.toByteArray()) } ?: emptyList()
        }
    }.onFailure { NexLog.w(TAG, "Unable to read signing certificate", it) }.getOrDefault(emptyList())

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    companion object {
        private const val TAG = "RiskAssessor"

        private val SU_PATHS = listOf(
            "/system/bin/su", "/system/xbin/su", "/sbin/su", "/su/bin/su",
            "/data/local/bin/su", "/data/local/xbin/su", "/data/local/su",
            "/system/sd/xbin/su", "/system/bin/failsafe/su", "/system/app/Superuser.apk",
        )

        private val ROOT_PACKAGES = listOf(
            "com.topjohnwu.magisk",
            "com.koushikdutta.superuser",
            "com.noshufou.android.su",
            "eu.chainfire.supersu",
            "com.devadvance.rootcloak",
            "com.thirdparty.superuser",
        )
    }
}
