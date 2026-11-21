package com.bebecup.app

import com.bebecup.app.data.media.TakenDates
import com.bebecup.app.domain.profile.BabyAge
import com.bebecup.app.domain.profile.CalendarDate
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BabyAgeTest {

    private fun age(birth: String, on: String) = BabyAge.on(birth, on)

    @Test
    fun `birthday is day 1 and the hundredth day is D+100`() {
        assertEquals(1L, age("2026-01-01", "2026-01-01")!!.dayNumber)
        assertEquals(100L, age("2026-01-01", "2026-04-10")!!.dayNumber)
        assertEquals("D+1", age("2026-01-01", "2026-01-01")!!.shortLabel)
    }

    @Test
    fun `months count only completed months`() {
        assertEquals(0, age("2026-01-15", "2026-02-14")!!.months)
        assertEquals(1, age("2026-01-15", "2026-02-15")!!.months)
        assertEquals(12, age("2025-03-15", "2026-03-15")!!.months)
        assertEquals(11, age("2025-03-15", "2026-03-14")!!.months)
    }

    @Test
    fun `a birthday late in the month completes on the last day of a shorter month`() {
        assertEquals(0, age("2026-01-31", "2026-02-27")!!.months)
        assertEquals(1, age("2026-01-31", "2026-02-28")!!.months)
        assertEquals(1, age("2026-01-31", "2026-03-30")!!.months)
        assertEquals(2, age("2026-01-31", "2026-03-31")!!.months)
        assertEquals(1, age("2024-01-30", "2024-02-29")!!.months) // leap year
    }

    @Test
    fun `labels switch from days to months to years`() {
        assertEquals("D+31", age("2026-01-01", "2026-01-31")!!.shortLabel)
        assertEquals("4개월", age("2026-01-01", "2026-05-08")!!.shortLabel)
        assertEquals("4개월 · D+128", age("2026-01-01", "2026-05-08")!!.longLabel)
        assertEquals("35개월", age("2023-01-01", "2025-12-31")!!.shortLabel)
        assertEquals("만 3세", age("2023-01-01", "2026-01-01")!!.shortLabel)
    }

    @Test
    fun `no age before birth or without dates`() {
        assertNull(age("2026-05-01", "2026-04-30"))
        assertNull(age("", "2026-04-30"))
        assertNull(age("2026-05-01", "not a date"))
    }

    @Test
    fun `calendar dates parse strictly and count days across years`() {
        assertNull(CalendarDate.parse("2026-02-30"))
        assertNull(CalendarDate.parse("2026-13-01"))
        assertNull(CalendarDate.parse("2026/01/01"))
        assertEquals("2026-03-09", CalendarDate.parse(" 2026-3-9 ").toString())
        assertEquals(0L, CalendarDate(1970, 1, 1).epochDay)
        assertEquals(366L, CalendarDate(2025, 1, 1).epochDay - CalendarDate(2024, 1, 1).epochDay)
        assertEquals(-1L, CalendarDate(1969, 12, 31).epochDay)
    }

    @Test
    fun `epoch days convert back to the same calendar date`() {
        assertEquals(CalendarDate(1970, 1, 1), CalendarDate.fromEpochDay(0))
        assertEquals(CalendarDate(1969, 12, 31), CalendarDate.fromEpochDay(-1))
        listOf("2000-02-29", "2024-02-29", "2026-03-01", "2026-12-31", "2100-03-01").forEach { text ->
            val date = CalendarDate.parse(text)!!
            assertEquals(text, CalendarDate.fromEpochDay(date.epochDay).toString())
        }
    }

    @Test
    fun `taken dates come from EXIF text or epoch millis`() {
        assertEquals("2026-05-20", TakenDates.fromExif("2026:05:20 14:03:11"))
        assertNull(TakenDates.fromExif("0000:00:00 00:00:00"))
        assertNull(TakenDates.fromExif("   "))
        assertNull(TakenDates.fromExif(null))
        val seoul = TimeZone.getTimeZone("Asia/Seoul")
        // 2026-05-19T20:00Z is already the 20th in Seoul.
        assertEquals("2026-05-20", TakenDates.fromMillis(1_779_220_800_000L, seoul))
        assertNull(TakenDates.fromMillis(0L, seoul))
    }
}
