package com.bebecup.app.domain.monthly

import androidx.annotation.VisibleForTesting
import com.bebecup.app.domain.children.cupsArePerChild
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

    /** Last month's cup that is due, for [childId] (null = the family's one cup). */
    data class Due(val month: Month, val childId: Long?)

    /** A saved photo: when it was taken and which children are in it. */
    data class Photo(val takenDate: String, val childIds: Set<Long>)

    /** A 이달의 월드컵 that was held: its month and child (null = a family cup). */
    data class Held(val monthKey: String, val childId: Long?)

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

    /**
     * Last month's cup that is due, in a family with [childIds] (oldest
     * first). With one child or none it is the family's one cup over all
     * [photos], decided by any cup held for the month. With siblings
     * ([cupsArePerChild]) each child has their own: the first child whose
     * photos of last month can hold a cup and who has no cup for it yet.
     * Cups that mixed siblings (null child) decide nobody's month then.
     * [skippedKey] passes on the month for everyone.
     */
    fun pending(
        today: CalendarDate,
        childIds: List<Long>,
        photos: List<Photo>,
        held: List<Held>,
        skippedKey: String
    ): Due? {
        if (!cupsArePerChild(childIds.size)) {
            val month = pending(today, photos.map { it.takenDate }, held.map { it.monthKey }.toSet(), skippedKey)
            return month?.let { Due(it, null) }
        }
        return childIds.firstNotNullOfOrNull { child ->
            pending(
                today = today,
                takenDates = photos.filter { child in it.childIds }.map { it.takenDate },
                decided = held.filter { it.childId == child }.map { it.monthKey }.toSet(),
                skippedKey = skippedKey
            )?.let { Due(it, child) }
        }
    }

    /**
     * The cups that hold their month's title, one per month and child
     * ([childId] null = the family's cup). A month can be played again (from
     * the setup screen or a late reminder); the latest cup is that month's
     * photo, and earlier ones stay in the hall as ordinary wins.
     */
    fun <T> titleHolders(cups: List<T>, monthKey: (T) -> String?, childId: (T) -> Long?, timestamp: (T) -> Long): Set<T> =
        cups.filter { monthKey(it) != null }
            .groupBy { monthKey(it) to childId(it) }
            .values
            .map { sameMonth -> sameMonth.maxBy(timestamp) }
            .toSet()

    /**
     * Where [today] reads the date. Tests pin it so the month rules don't
     * depend on the day they run (a run across midnight at a month's end
     * would otherwise see two different "last months").
     */
    @VisibleForTesting
    internal var clock: () -> CalendarDate = ::deviceToday

    /** Today on this device's calendar. */
    fun today(): CalendarDate = clock()

    /** The device calendar's today; the default [clock]. */
    internal fun deviceToday(): CalendarDate {
        val now = Calendar.getInstance()
        return CalendarDate(now.get(Calendar.YEAR), now.get(Calendar.MONTH) + 1, now.get(Calendar.DAY_OF_MONTH))
    }

    private fun key(year: Int, month: Int): String = String.format(Locale.ROOT, "%04d-%02d", year, month)
}
