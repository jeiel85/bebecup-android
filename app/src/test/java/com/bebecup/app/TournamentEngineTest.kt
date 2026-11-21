package com.bebecup.app

import com.bebecup.app.domain.tournament.TournamentEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TournamentEngineTest {

    /** Always picks the left photo; returns the number of votes cast. */
    private fun playLeft(engine: TournamentEngine<String>): Int {
        var votes = 0
        while (!engine.isFinished) {
            val match = engine.currentMatch!!
            assertTrue(engine.vote(match, match.left))
            votes++
        }
        return votes
    }

    @Test
    fun `4-photo bracket takes 3 votes and crowns one entrant`() {
        val engine = TournamentEngine(listOf("a", "b", "c", "d"))
        assertEquals("준결승 (4강)", TournamentEngine.roundLabel(engine.roundSize))
        assertEquals(3, playLeft(engine))
        assertEquals("a", engine.champion)
        assertNull(engine.currentMatch)
    }

    @Test
    fun `a second tap on an already decided match is ignored`() {
        val engine = TournamentEngine(listOf("a", "b", "c", "d"))
        val first = engine.currentMatch!!
        assertTrue(engine.vote(first, first.left))
        // Fast double tap: the same (now stale) match arrives again.
        assertFalse(engine.vote(first, first.left))
        assertFalse(engine.vote(first, first.right))

        val second = engine.currentMatch!!
        assertEquals(listOf("c", "d"), listOf(second.left, second.right))
        assertTrue(engine.vote(second, second.right))
        // Final is a-vs-d: the double tap neither skipped a match nor duplicated "a".
        val final = engine.currentMatch!!
        assertEquals(listOf("a", "d"), listOf(final.left, final.right))
    }

    @Test
    fun `a vote for someone outside the match is rejected`() {
        val engine = TournamentEngine(listOf("a", "b", "c", "d"))
        val match = engine.currentMatch!!
        assertFalse(engine.vote(match, "z"))
        assertEquals(0, engine.currentMatchNumber)
    }

    @Test
    fun `odd round gives the last entrant a bye`() {
        val engine = TournamentEngine(listOf("a", "b", "c"))
        assertEquals(1, engine.matchesInRound)
        val first = engine.currentMatch!!
        engine.vote(first, first.right) // b beats a; c gets a bye
        val final = engine.currentMatch!!
        assertEquals(listOf("b", "c"), listOf(final.left, final.right))
        engine.vote(final, final.right)
        assertEquals("c", engine.champion)
    }

    @Test
    fun `two photos are a single final`() {
        val engine = TournamentEngine(listOf("a", "b"))
        assertEquals("결승전", TournamentEngine.roundLabel(engine.roundSize))
        assertEquals(1, playLeft(engine))
    }

    @Test
    fun `16-photo bracket plays 15 matches`() {
        val engine = TournamentEngine((1..16).map { "p$it" })
        assertEquals(15, playLeft(engine))
    }

    @Test
    fun `offered sizes never exceed the photos available`() {
        assertEquals(emptyList<Int>(), TournamentEngine.availableSizes(1))
        assertEquals(listOf(2), TournamentEngine.availableSizes(2))
        assertEquals(listOf(3), TournamentEngine.availableSizes(3))
        assertEquals(listOf(4), TournamentEngine.availableSizes(7))
        assertEquals(listOf(4, 8), TournamentEngine.availableSizes(15))
        assertEquals(listOf(4, 8, 16), TournamentEngine.availableSizes(40))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `a single photo cannot make a tournament`() {
        TournamentEngine(listOf("a"))
    }

    @Test
    fun `a snapshot resumes the bracket exactly where it stopped`() {
        val original = TournamentEngine(listOf("a", "b", "c", "d", "e"))
        original.vote(original.currentMatch!!, "a")
        original.vote(original.currentMatch!!, "d") // round 2 starts: a, d, e(bye)
        original.vote(original.currentMatch!!, "a")

        val saved = original.snapshot()!!.map { it.uppercase() }
        val resumed = TournamentEngine.restore(saved.map { it.lowercase() })!!

        assertEquals(5, resumed.bracketSize)
        assertEquals(original.currentMatch, resumed.currentMatch)
        assertEquals(original.roundSize, resumed.roundSize)
        assertEquals(original.currentMatchNumber, resumed.currentMatchNumber)
        assertTrue(resumed.vote(resumed.currentMatch!!, "a"))
        assertTrue(original.vote(original.currentMatch!!, "a"))
        assertEquals(original.champion, resumed.champion)
    }

    @Test
    fun `a finished bracket has no snapshot and a broken snapshot is refused`() {
        val engine = TournamentEngine(listOf("a", "b"))
        engine.vote(engine.currentMatch!!, "b")
        assertNull(engine.snapshot())

        val good = TournamentEngine.Snapshot(4, listOf("a", "b", "c", "d"), listOf("a"), 1, 0)
        assertTrue(TournamentEngine.restore(good) != null)
        assertNull(TournamentEngine.restore(good.copy(winners = emptyList())))   // winners must match the match index
        assertNull(TournamentEngine.restore(good.copy(matchIndex = 2, winners = listOf("a", "c"))))
        assertNull(TournamentEngine.restore(good.copy(pool = listOf("a"), winners = emptyList(), matchIndex = 0)))
        assertNull(TournamentEngine.restore(good.copy(pool = listOf("a", "a", "c", "d"))))
        assertNull(TournamentEngine.restore(good.copy(bracketSize = 2)))
    }
}
