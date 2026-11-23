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
        val holders = MonthlyCup.titleHolders(cups, { it.month }, { null }, { it.at })
        assertEquals(setOf(3, 4), holders.map { it.id }.toSet())
    }

    @Test
    fun `siblings each keep their own month's photo`() {
        data class Cup(val id: Int, val month: String, val child: Long?, val at: Long)
        val cups = listOf(
            Cup(1, "2026-10", child = 1L, at = 100),
            Cup(2, "2026-10", child = 2L, at = 200),
            Cup(3, "2026-10", child = 1L, at = 300)
        )
        val holders = MonthlyCup.titleHolders(cups, { it.month }, { it.child }, { it.at })
        assertEquals(setOf(2, 3), holders.map { it.id }.toSet())
    }

    @Test
    fun `siblings are offered last month's cup one child at a time`() {
        val photos = listOf(
            MonthlyCup.Photo("2026-10-01", setOf(1L)),
            MonthlyCup.Photo("2026-10-02", setOf(1L, 2L)),
            MonthlyCup.Photo("2026-10-03", setOf(2L)),
            MonthlyCup.Photo("2026-10-04", emptySet())
        )
        val siblings = listOf(1L, 2L)
        val october = { count: Int -> MonthlyCup.Month("2026-10", count) }
        assertEquals(MonthlyCup.Due(october(2), 1L), MonthlyCup.pending(nov21, siblings, photos, emptyList(), ""))
        assertEquals(
            MonthlyCup.Due(october(2), 2L),
            MonthlyCup.pending(nov21, siblings, photos, listOf(MonthlyCup.Held("2026-10", 1L)), "")
        )
        // A cup that mixed them (before siblings had their own) decides nobody's month.
        assertEquals(1L, MonthlyCup.pending(nov21, siblings, photos, listOf(MonthlyCup.Held("2026-10", null)), "")?.childId)
        assertNull(
            MonthlyCup.pending(nov21, siblings, photos, listOf(MonthlyCup.Held("2026-10", 1L), MonthlyCup.Held("2026-10", 2L)), "")
        )
        assertNull(MonthlyCup.pending(nov21, siblings, photos, emptyList(), "2026-10"))
        // A child with one photo of the month has no cup for it.
        assertNull(MonthlyCup.pending(nov21, siblings, photos.take(1) + photos.drop(2), listOf(MonthlyCup.Held("2026-10", 2L)), ""))
    }

    @Test
    fun `one child keeps the family's one monthly cup over every photo`() {
        val photos = listOf(MonthlyCup.Photo("2026-10-01", setOf(1L)), MonthlyCup.Photo("2026-10-02", emptySet()))
        assertEquals(
            MonthlyCup.Due(MonthlyCup.Month("2026-10", 2), null),
            MonthlyCup.pending(nov21, listOf(1L), photos, emptyList(), "")
        )
        assertNull(MonthlyCup.pending(nov21, listOf(1L), photos, listOf(MonthlyCup.Held("2026-10", 1L)), ""))
        assertNull(MonthlyCup.pending(nov21, emptyList(), photos, listOf(MonthlyCup.Held("2026-10", null)), ""))
    }

    @Test
    fun `january offers last december`() {
        val due = MonthlyCup.pending(CalendarDate(2027, 1, 2), listOf("2026-12-24", "2026-12-25"), emptySet(), "")
        assertEquals("2026-12", due?.key)
    }
}
