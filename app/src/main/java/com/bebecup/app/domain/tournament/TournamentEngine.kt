package com.bebecup.app.domain.tournament

/**
 * Single-elimination bracket over distinct entrants. Pure and synchronous so
 * the bracket rules are unit-tested and a vote is applied atomically — the UI
 * cannot double-count a fast double tap.
 *
 * Entrants are never duplicated: a bracket is only as big as the photos given.
 * When a round has an odd count, the last entrant gets a bye to the next round.
 */
class TournamentEngine<T> private constructor(entrants: List<T>, val bracketSize: Int) {

    constructor(entrants: List<T>) : this(entrants, entrants.size)

    init {
        require(entrants.size >= 2) { "a tournament needs at least 2 entrants" }
    }

    data class Match<T>(val round: Int, val index: Int, val left: T, val right: T)

    /**
     * Everything needed to resume an unfinished bracket, e.g. after Android
     * ends the app while the family is away. [map] turns entrants into ids for
     * saving and back.
     */
    data class Snapshot<T>(
        val bracketSize: Int,
        val pool: List<T>,
        val winners: List<T>,
        val matchIndex: Int,
        val round: Int
    ) {
        fun <R> map(transform: (T) -> R): Snapshot<R> =
            Snapshot(bracketSize, pool.map(transform), winners.map(transform), matchIndex, round)
    }

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

    /** The bracket so far, or null once there is a champion. */
    fun snapshot(): Snapshot<T>? =
        if (isFinished) null else Snapshot(bracketSize, pool, winners.toList(), matchIndex, round)

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
        /** Resume [snapshot]; null when it does not describe a bracket in progress. */
        fun <T> restore(snapshot: Snapshot<T>): TournamentEngine<T>? {
            val matches = snapshot.pool.size / 2
            val valid = snapshot.pool.size >= 2 &&
                snapshot.bracketSize >= snapshot.pool.size &&
                snapshot.matchIndex in 0 until matches &&
                snapshot.winners.size == snapshot.matchIndex &&
                snapshot.round >= 0 &&
                snapshot.pool.toSet().size == snapshot.pool.size
            if (!valid) return null
            return TournamentEngine(snapshot.pool, snapshot.bracketSize).apply {
                winners += snapshot.winners
                matchIndex = snapshot.matchIndex
                round = snapshot.round
            }
        }

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

/**
 * A bracket of photo ids as one IntArray, the form Android's saved state keeps:
 * [bracketSize, matchIndex, round, poolSize, pool..., winners...].
 */
object TournamentSnapshots {

    fun encode(snapshot: TournamentEngine.Snapshot<Int>): IntArray =
        (listOf(snapshot.bracketSize, snapshot.matchIndex, snapshot.round, snapshot.pool.size) +
            snapshot.pool + snapshot.winners).toIntArray()

    /** Null for anything [encode] did not produce; [TournamentEngine.restore] checks the rest. */
    fun decode(saved: IntArray?): TournamentEngine.Snapshot<Int>? {
        if (saved == null || saved.size < 4) return null
        val poolSize = saved[3]
        if (poolSize < 0 || saved.size < 4 + poolSize) return null
        val pool = saved.slice(4 until 4 + poolSize)
        val winners = saved.drop(4 + poolSize)
        return TournamentEngine.Snapshot(saved[0], pool, winners, saved[1], saved[2])
    }
}
