package com.bebecup.app

import com.bebecup.app.domain.monthly.MonthlyCup
import com.bebecup.app.domain.profile.CalendarDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MonthlyCupTest {

    private val nov21 = CalendarDate(2026, 11, 21)

    @Test
    fun `month key comes from the taken date`() {
        assertEquals("2026-10", MonthlyCup.monthKeyOf("2026-10-05"))
        assertNull(MonthlyCup.monthKeyOf("2026-13-01"))
        assertNull(MonthlyCup.monthKeyOf(""))
    }

    @Test
    fun `previous month wraps around new year`() {
        assertEquals("2026-10", MonthlyCup.previousMonth(nov21))
        assertEquals("2025-12", MonthlyCup.previousMonth(CalendarDate(2026, 1, 1)))
    }

    @Test
    fun `label shows the year only for another year`() {
        assertEquals("10월", MonthlyCup.label("2026-10", todayYear = 2026))
        assertEquals("2025년 12월", MonthlyCup.label("2025-12", todayYear = 2026))
        assertEquals("2026년 1월", MonthlyCup.label("2026-01", withYear = true))
        assertEquals("garbage", MonthlyCup.label("garbage", withYear = true))
    }

    @Test
    fun `months need two photos and come newest first`() {
        val months = MonthlyCup.months(
            listOf("2026-09-01", "2026-09-30", "2026-10-02", "2026-11-01", "2026-11-03", "2026-11-20", "bad")
        )
        assertEquals(listOf(MonthlyCup.Month("2026-11", 3), MonthlyCup.Month("2026-09", 2)), months)
    }

    @Test
    fun `only last month is due, until it is played or skipped`() {
        val dates = listOf("2026-10-01", "2026-10-31", "2026-09-01", "2026-09-02")
        assertEquals(MonthlyCup.Month("2026-10", 2), MonthlyCup.pending(nov21, dates, emptySet(), ""))
        assertNull(MonthlyCup.pending(nov21, dates, setOf("2026-10"), ""))
        assertNull(MonthlyCup.pending(nov21, dates, emptySet(), "2026-10"))
        // An older unplayed month (September) is never offered.
        assertNull(MonthlyCup.pending(nov21, listOf("2026-09-01", "2026-09-02"), emptySet(), ""))
        // One photo is not a cup.
        assertNull(MonthlyCup.pending(nov21, listOf("2026-10-01"), emptySet(), ""))
    }

    @Test
    fun `dates written with non-latin digits still find their month`() {
        // Kotlin's toIntOrNull reads any Unicode decimal digit, so rows saved
        // under an Arabic-digit locale before dates were pinned to Locale.ROOT
        // still land in their month instead of silently dropping out.
        assertEquals("2026-10", MonthlyCup.monthKeyOf("٢٠٢٦-١٠-٠٥"))
        assertEquals("2026-10", MonthlyCup.monthKeyOf("۲۰۲۶-۱۰-۰۵"))
    }

    @Test
    fun `a month played twice is titled by its latest cup`() {
        data class Cup(val id: Int, val month: String?, val at: Long)
        val cups = listOf(
            Cup(1, "2026-10", at = 100),
            Cup(2, null, at = 150),
            Cup(3, "2026-10", at = 200),
            Cup(4, "2026-09", at = 50)
        )
        val holders = MonthlyCup.titleHolders(cups, { it.month }, { it.at })
        assertEquals(setOf(3, 4), holders.map { it.id }.toSet())
    }

    @Test
    fun `january offers last december`() {
        val due = MonthlyCup.pending(CalendarDate(2027, 1, 2), listOf("2026-12-24", "2026-12-25"), emptySet(), "")
        assertEquals("2026-12", due?.key)
    }
}
