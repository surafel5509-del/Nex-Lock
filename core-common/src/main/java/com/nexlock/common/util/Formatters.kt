package com.nexlock.common.util

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

object Formatters {

    private val timestampFormatter: DateTimeFormatter =
        DateTimeFormatter.ofPattern("d MMM yyyy · HH:mm", Locale.getDefault())

    private val timeFormatter: DateTimeFormatter =
        DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault())

    fun timestamp(epochMillis: Long): String =
        timestampFormatter.format(Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()))

    fun time(epochMillis: Long): String =
        timeFormatter.format(Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()))

    /** mm:ss countdown used by the lockout timer. */
    fun countdown(remainingMillis: Long): String {
        val totalSeconds = (remainingMillis.coerceAtLeast(0L) + 999L) / 1000L
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return "%02d:%02d".format(minutes, seconds)
    }

    fun fileSize(bytes: Long): String = when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> String.format(Locale.getDefault(), "%.1f KB", bytes / 1024.0)
        bytes < 1024L * 1024L * 1024L -> String.format(Locale.getDefault(), "%.1f MB", bytes / (1024.0 * 1024.0))
        else -> String.format(Locale.getDefault(), "%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0))
    }
}
