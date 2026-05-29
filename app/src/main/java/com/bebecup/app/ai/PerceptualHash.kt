package com.bebecup.app.ai

import android.graphics.Bitmap

/**
 * dHash (difference hash) for near-duplicate / burst detection (spec §8.8).
 * Downsamples to a 9x8 grayscale grid and emits a 64-bit hash from horizontal
 * gradient signs. Visually similar photos produce hashes a small Hamming
 * distance apart. Core math is pure (luma array in), so it is unit-testable.
 */
object PerceptualHash {

    private const val ROWS = 8
    private const val COLS = 9 // 8 comparisons per row → 64 bits

    /** @param luma row-major grayscale (0..255), length == width*height. */
    fun dHashFromLuma(luma: IntArray, width: Int, height: Int): Long {
        if (width <= 0 || height <= 0 || luma.isEmpty()) return 0L

        // Nearest-neighbor resample to ROWS x COLS.
        val grid = Array(ROWS) { IntArray(COLS) }
        for (r in 0 until ROWS) {
            val sy = ((r + 0.5) * height / ROWS).toInt().coerceIn(0, height - 1)
            for (c in 0 until COLS) {
                val sx = ((c + 0.5) * width / COLS).toInt().coerceIn(0, width - 1)
                grid[r][c] = luma[sy * width + sx]
            }
        }

        var hash = 0L
        var bit = 0
        for (r in 0 until ROWS) {
            for (c in 0 until COLS - 1) {
                if (grid[r][c] < grid[r][c + 1]) {
                    hash = hash or (1L shl bit)
                }
                bit++
            }
        }
        return hash
    }

    fun dHash(bitmap: Bitmap): Long {
        val w = bitmap.width
        val h = bitmap.height
        if (w == 0 || h == 0) return 0L
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)
        val luma = IntArray(pixels.size)
        for (i in pixels.indices) {
            val p = pixels[i]
            val rr = (p shr 16) and 0xFF
            val gg = (p shr 8) and 0xFF
            val bb = p and 0xFF
            luma[i] = (0.299 * rr + 0.587 * gg + 0.114 * bb).toInt()
        }
        return dHashFromLuma(luma, w, h)
    }

    /** Number of differing bits between two hashes (0..64). */
    fun hammingDistance(a: Long, b: Long): Int = java.lang.Long.bitCount(a xor b)
}
