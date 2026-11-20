package com.bebecup.app.domain.tournament

/**
 * Single-elimination bracket over distinct entrants. Pure and synchronous so
 * the bracket rules are unit-tested and a vote is applied atomically — the UI
 * cannot double-count a fast double tap.
 *
 * Entrants are never duplicated: a bracket is only as big as the photos given.
 * When a round has an odd count, the last entrant gets a bye to the next round.
 */
class TournamentEngine<T>(entrants: List<T>) {

    init {
        require(entrants.size >= 2) { "a tournament needs at least 2 entrants" }
    }

    data class Match<T>(val round: Int, val index: Int, val left: T, val right: T)

    val bracketSize: Int = entrants.size

    private var pool: List<T> = entrants.toList()
    private val winners = mutableListOf<T>()
    private var matchIndex = 0
    private var round = 0

    var champion: T? = null
        private set

    val isFinished: Boolean get() = champion != null

    /** Entrants still alive at the start of the current round. */
    val roundSize: Int get() = pool.size

    /** Matches in the current round (a bye is not a match). */
    val matchesInRound: Int get() = pool.size / 2

    /** 0-based index of the current match within the round. */
    val currentMatchNumber: Int get() = matchIndex

    val currentMatch: Match<T>?
        get() {
            if (isFinished) return null
            return Match(round, matchIndex, pool[2 * matchIndex], pool[2 * matchIndex + 1])
        }

    /**
     * Record [winner] for [match]. Returns false (and changes nothing) when
     * [match] is no longer the current match — e.g. a second tap that arrives
     * after the first already advanced the bracket — or [winner] isn't in it.
     */
    fun vote(match: Match<T>, winner: T): Boolean {
        val current = currentMatch ?: return false
        if (current != match) return false
        if (winner != current.left && winner != current.right) return false

        winners += winner
        matchIndex++
        if (matchIndex >= matchesInRound) advanceRound()
        return true
    }

    private fun advanceRound() {
        if (pool.size % 2 == 1) winners += pool.last() // bye
        if (winners.size == 1) {
            champion = winners.single()
            return
        }
        pool = winners.toList()
        winners.clear()
        matchIndex = 0
        round++
    }

    companion object {
        /** Bracket sizes offered to the parent, capped by how many photos exist. */
        val STANDARD_SIZES = listOf(4, 8, 16)

        fun availableSizes(photoCount: Int): List<Int> = when {
            photoCount < 2 -> emptyList()
            photoCount < 4 -> listOf(photoCount)
            else -> STANDARD_SIZES.filter { it <= photoCount }
        }

        fun roundLabel(roundSize: Int): String = when (roundSize) {
            2 -> "결승전"
            4 -> "준결승 (4강)"
            else -> "${roundSize}강"
        }
    }
}
