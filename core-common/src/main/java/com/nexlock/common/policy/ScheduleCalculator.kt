package com.nexlock.common.policy

import java.time.DayOfWeek
import java.time.LocalDateTime

/**
 * A recurring time window during which protection should be forced on.
 *
 * @param daysMask bit 0 = Monday .. bit 6 = Sunday.
 * @param startMinute minutes from midnight, inclusive.
 * @param endMinute minutes from midnight, exclusive. May be smaller than [startMinute]
 *        for windows that cross midnight (for example 22:00 -> 07:00).
 */
data class ScheduleWindow(
    val id: Long,
    val label: String,
    val startMinute: Int,
    val endMinute: Int,
    val daysMask: Int,
    val enabled: Boolean,
)

object ScheduleCalculator {

    const val ALL_DAYS_MASK = 0b1111111

    fun isActive(window: ScheduleWindow, now: LocalDateTime): Boolean {
        if (!window.enabled) return false
        if (window.startMinute == window.endMinute) return false
        val minute = now.hour * 60 + now.minute
        val todayBit = 1 shl (now.dayOfWeek.value - 1)

        return if (window.startMinute < window.endMinute) {
            window.daysMask and todayBit != 0 && minute in window.startMinute until window.endMinute
        } else {
            val previousBit = 1 shl ((now.dayOfWeek.value - 2 + 7) % 7)
            val eveningPart = window.daysMask and todayBit != 0 && minute >= window.startMinute
            val morningPart = window.daysMask and previousBit != 0 && minute < window.endMinute
            eveningPart || morningPart
        }
    }

    fun activeWindow(windows: List<ScheduleWindow>, now: LocalDateTime): ScheduleWindow? =
        windows.firstOrNull { isActive(it, now) }

    fun isWithinAnyWindow(windows: List<ScheduleWindow>, now: LocalDateTime): Boolean =
        windows.any { isActive(it, now) }

    fun formatMinuteOfDay(minute: Int): String {
        val safe = ((minute % 1440) + 1440) % 1440
        return "%02d:%02d".format(safe / 60, safe % 60)
    }

    fun dayLabels(daysMask: Int): String =
        DayOfWeek.entries.filter { daysMask and (1 shl (it.value - 1)) != 0 }
            .joinToString("") { it.name.take(2) }
            .ifEmpty { "—" }
}
