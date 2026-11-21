package com.bebecup.app.domain.monthly

import com.bebecup.app.domain.profile.CalendarDate
import java.util.Calendar
import java.util.Locale

/**
 * "이달의 월드컵": a world cup over the saved photos taken in one calendar
 * month, whose winner is kept as that month's photo. Pure so the calendar
 * rules are unit-tested; the home card, the setup screen and the reminder
 * worker all use it.
 *
 * Months are keyed "yyyy-MM", taken from a photo's `takenDate`.
 */
object MonthlyCup {

    /** A month needs this many saved photos to hold a cup. */
    const val MIN_PHOTOS = 2

    /** A month whose cup is waiting: [photoCount] saved photos were taken in it. */
    data class Month(val key: String, val photoCount: Int)

    /** "2026-10" for "2026-10-05"; null when [date] isn't a valid "yyyy-MM-dd". */
    fun monthKeyOf(date: String?): String? = CalendarDate.parse(date)?.let { key(it.year, it.month) }

    /** The calendar month before [today]'s. */
    fun previousMonth(today: CalendarDate): String =
        if (today.month == 1) key(today.year - 1, 12) else key(today.year, today.month - 1)

    /** "10월", or "2025년 10월" when [withYear] (or a malformed key, shown as is). */
    fun label(monthKey: String, withYear: Boolean): String {
        val parts = monthKey.split('-')
        val year = parts.getOrNull(0)?.toIntOrNull()
        val month = parts.getOrNull(1)?.toIntOrNull()
        if (parts.size != 2 || year == null || month == null || month !in 1..12) return monthKey
        return if (withYear) "${year}년 ${month}월" else "${month}월"
    }

    /** [label] with the year only when the month isn't in [todayYear]. */
    fun label(monthKey: String, todayYear: Int): String =
        label(monthKey, withYear = !monthKey.startsWith("$todayYear-"))

    /** Months with at least [MIN_PHOTOS] photos among [takenDates], newest first. */
    fun months(takenDates: List<String>): List<Month> =
        takenDates.mapNotNull(::monthKeyOf)
            .groupingBy { it }
            .eachCount()
            .filter { it.value >= MIN_PHOTOS }
            .map { Month(it.key, it.value) }
            .sortedByDescending { it.key }

    /**
     * Last month's cup when it is due: last month has enough saved photos,
     * no cup was held for it yet ([decided]) and the parent didn't skip it
     * ([skippedKey]). Only last month is offered, so the card and the
     * reminder come once at the start of each month and never pile up.
     */
    fun pending(
        today: CalendarDate,
        takenDates: List<String>,
        decided: Set<String>,
        skippedKey: String
    ): Month? {
        val key = previousMonth(today)
        if (key in decided || key == skippedKey) return null
        return months(takenDates).firstOrNull { it.key == key }
    }

    /** Today on this device's calendar. */
    fun today(): CalendarDate {
        val now = Calendar.getInstance()
        return CalendarDate(now.get(Calendar.YEAR), now.get(Calendar.MONTH) + 1, now.get(Calendar.DAY_OF_MONTH))
    }

    private fun key(year: Int, month: Int): String = String.format(Locale.ROOT, "%04d-%02d", year, month)
}
