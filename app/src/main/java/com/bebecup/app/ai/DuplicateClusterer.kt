package com.bebecup.app.ai

/**
 * Groups near-duplicate photos by perceptual-hash similarity (spec §8.8) using
 * union-find over pairwise Hamming distance. The representative of each cluster
 * is the highest-scoring member; the rest are demoted downstream. Pure &
 * deterministic — unit-testable without Android.
 */
object DuplicateClusterer {

    /** Default Hamming threshold (out of 64 bits) for "visually the same". */
    const val DEFAULT_THRESHOLD = 10

    data class Item(val photoId: Int, val dHash: Long, val score: Float)

    data class Cluster(val representativeId: Int, val memberIds: List<Int>) {
        val isDuplicateGroup: Boolean get() = memberIds.size > 1
    }

    fun cluster(items: List<Item>, hammingThreshold: Int = DEFAULT_THRESHOLD): List<Cluster> {
        if (items.isEmpty()) return emptyList()

        val n = items.size
        val parent = IntArray(n) { it }

        fun find(x: Int): Int {
            var root = x
            while (parent[root] != root) root = parent[root]
            var cur = x
            while (parent[cur] != cur) {
                val next = parent[cur]
                parent[cur] = root
                cur = next
            }
            return root
        }

        fun union(a: Int, b: Int) {
            val ra = find(a)
            val rb = find(b)
            if (ra != rb) parent[ra] = rb
        }

        for (i in 0 until n) {
            for (j in i + 1 until n) {
                if (PerceptualHash.hammingDistance(items[i].dHash, items[j].dHash) <= hammingThreshold) {
                    union(i, j)
                }
            }
        }

        val groups = LinkedHashMap<Int, MutableList<Int>>()
        for (i in 0 until n) {
            groups.getOrPut(find(i)) { mutableListOf() }.add(i)
        }

        return groups.values.map { indices ->
            val members = indices.map { items[it] }
            val representative = members.maxByOrNull { it.score }!!
            Cluster(
                representativeId = representative.photoId,
                memberIds = members.map { it.photoId }
            )
        }
    }
}
