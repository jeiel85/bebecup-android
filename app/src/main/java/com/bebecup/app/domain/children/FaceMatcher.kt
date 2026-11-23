package com.bebecup.app.domain.children

import com.bebecup.app.ai.faceid.FaceVectors

/**
 * Decides whose face a face vector is, from the faces the parent has already
 * named (references), and groups faces nobody has named yet so the parent can
 * name a whole group at once. Pure; the thresholds are for SFace unit vectors.
 *
 * A reference belongs to a child (its id) or to [NOT_CHILD] — a parent, a friend:
 * faces the family said are none of the children, so they stop being asked about.
 *
 * SFace's own same-person threshold is 0.363 (adults, LFW). Babies and siblings
 * look more alike than strangers, so the bar here is higher and a face must also
 * be clearly closer to one person than to the next ([MARGIN]); anything unsure
 * stays unknown and is asked about instead of being guessed.
 */
object FaceMatcher {
    /** Key for "not one of the children". Child ids are positive. */
    const val NOT_CHILD = 0L

    const val TOP_K = 3
    const val ASSIGN_MIN = 0.40f
    const val MARGIN = 0.06f
    const val CLUSTER_MIN = 0.42f

    /** At most this many unknown faces are grouped (newest first), so grouping stays quick. */
    const val MAX_CLUSTER_FACES = 400

    data class Match(val person: Long, val score: Float)

    /** Mean of the [TOP_K] best similarities to one person's references. */
    fun score(face: FloatArray, references: List<FloatArray>): Float {
        if (references.isEmpty()) return -1f
        val sims = references.map { FaceVectors.cosine(face, it) }.sortedDescending()
        val k = minOf(TOP_K, sims.size)
        return sims.take(k).sum() / k
    }

    /** Every person's score for [face], best first. */
    fun ranked(face: FloatArray, references: Map<Long, List<FloatArray>>): List<Match> =
        references.map { (person, refs) -> Match(person, score(face, refs)) }
            .filter { it.score > -1f }
            .sortedByDescending { it.score }

    /** The confident match for [face], or null when it should stay unknown. */
    fun bestMatch(face: FloatArray, references: Map<Long, List<FloatArray>>): Match? =
        confident(ranked(face, references))

    private fun confident(ranked: List<Match>): Match? {
        val best = ranked.firstOrNull() ?: return null
        if (best.score < ASSIGN_MIN) return null
        val second = ranked.getOrNull(1)?.score ?: return best
        return if (best.score - second >= MARGIN) best else null
    }

    /**
     * Matches for all faces of one photo. A child is in a photo at most once, so
     * when two faces match the same child only the closer one keeps it.
     */
    fun assignPhoto(faces: List<FloatArray>, references: Map<Long, List<FloatArray>>): List<Match?> {
        val matches = faces.map { bestMatch(it, references) }.toMutableList()
        matches.withIndex()
            .filter { it.value != null && it.value!!.person != NOT_CHILD }
            .groupBy { it.value!!.person }
            .values
            .forEach { same ->
                val keep = same.maxBy { it.value!!.score }.index
                same.filter { it.index != keep }.forEach { matches[it.index] = null }
            }
        return matches
    }

    /**
     * Average-linkage grouping: two groups join while their faces are, on average,
     * at least [CLUSTER_MIN] alike. Returns groups of indices into [faces], largest first.
     */
    fun cluster(faces: List<FloatArray>): List<List<Int>> {
        val n = faces.size
        if (n == 0) return emptyList()
        val members = MutableList(n) { mutableListOf(it) }
        val alive = BooleanArray(n) { true }
        // sum[i][j]: sum of pairwise similarities between groups i and j.
        val sum = Array(n) { i -> DoubleArray(n) { j -> if (i == j) 0.0 else FaceVectors.cosine(faces[i], faces[j]).toDouble() } }
        while (true) {
            var bi = -1
            var bj = -1
            var best = Double.NEGATIVE_INFINITY
            for (i in 0 until n) {
                if (!alive[i]) continue
                for (j in i + 1 until n) {
                    if (!alive[j]) continue
                    val avg = sum[i][j] / (members[i].size * members[j].size)
                    if (avg > best) {
                        best = avg
                        bi = i
                        bj = j
                    }
                }
            }
            if (bi < 0 || best < CLUSTER_MIN) break
            members[bi].addAll(members[bj])
            alive[bj] = false
            for (k in 0 until n) {
                if (!alive[k] || k == bi) continue
                sum[bi][k] += sum[bj][k]
                sum[k][bi] = sum[bi][k]
            }
        }
        return (0 until n).filter { alive[it] }.map { members[it].sorted() }
            .sortedWith(compareByDescending<List<Int>> { it.size }.thenBy { it.first() })
    }
}
