package com.bebecup.app.domain.profile

import java.util.Locale

/**
 * A calendar date ("yyyy-MM-dd"), the form photo dates and the birthday are
 * stored in. Plain arithmetic instead of java.time, which needs API 26 or
 * desugaring (minSdk is 24).
 */
data class CalendarDate(val year: Int, val month: Int, val day: Int) : Comparable<CalendarDate> {

    /** Days since 1970-01-01 (proleptic Gregorian, Hinnant's days_from_civil). */
    val epochDay: Long
        get() {
            val y = if (month <= 2) year - 1 else year
            val era = Math.floorDiv(y, 400)
            val yearOfEra = y - era * 400
            val shiftedMonth = (month + 9) % 12 // March = 0
            val dayOfYear = (153 * shiftedMonth + 2) / 5 + day - 1
            val dayOfEra = yearOfEra * 365 + yearOfEra / 4 - yearOfEra / 100 + dayOfYear
            return era * 146_097L + dayOfEra - 719_468L
        }

    override fun compareTo(other: CalendarDate): Int = epochDay.compareTo(other.epochDay)

    override fun toString(): String = String.format(Locale.ROOT, "%04d-%02d-%02d", year, month, day)

    companion object {
        /** Parses "yyyy-MM-dd"; null for anything else, including impossible dates. */
        fun parse(text: String?): CalendarDate? {
            val parts = text?.trim()?.split('-') ?: return null
            if (parts.size != 3) return null
            val year = parts[0].toIntOrNull() ?: return null
            val month = parts[1].toIntOrNull() ?: return null
            val day = parts[2].toIntOrNull() ?: return null
            if (year !in 1900..2999 || month !in 1..12 || day !in 1..daysInMonth(year, month)) return null
            return CalendarDate(year, month, day)
        }

        fun daysInMonth(year: Int, month: Int): Int = when (month) {
            2 -> if (year % 4 == 0 && (year % 100 != 0 || year % 400 == 0)) 29 else 28
            4, 6, 9, 11 -> 30
            else -> 31
        }
    }
}

/**
 * How old the baby was on a day.
 *
 * [dayNumber] counts the birthday as day 1, the way Korean parents count
 * (백일 = D+100). [months] is completed months (만 개월): from Jan 31 the next
 * month is complete on the last day of February.
 */
data class BabyAge(val dayNumber: Long, val months: Int) {

    /** Short badge text: "D+12" in the first month, "4개월" up to 3 years, then "만 3세". */
    val shortLabel: String
        get() = when {
            months < 1 -> "D+$dayNumber"
            months < 36 -> "${months}개월"
            else -> "만 ${months / 12}세"
        }

    /** Longer text with the day count: "4개월 · D+128". */
    val longLabel: String
        get() = if (months < 1) "D+$dayNumber" else "$shortLabel · D+$dayNumber"

    companion object {
        /** Age on [on]; null when [on] is before [birth] (e.g. a pregnancy photo). */
        fun on(birth: CalendarDate, on: CalendarDate): BabyAge? {
            if (on < birth) return null
            val anniversaryDay = minOf(birth.day, CalendarDate.daysInMonth(on.year, on.month))
            val months = (on.year - birth.year) * 12 + (on.month - birth.month) -
                if (on.day < anniversaryDay) 1 else 0
            return BabyAge(dayNumber = on.epochDay - birth.epochDay + 1, months = months)
        }

        /** Same as [on] for stored "yyyy-MM-dd" strings; null when either is missing or malformed. */
        fun on(birthDate: String?, takenDate: String?): BabyAge? {
            val birth = CalendarDate.parse(birthDate) ?: return null
            val taken = CalendarDate.parse(takenDate) ?: return null
            return on(birth, taken)
        }
    }
}
