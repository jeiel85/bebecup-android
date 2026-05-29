package com.bebecup.app.ai

import android.graphics.Bitmap

/**
 * Rough exposure quality from overall brightness + clipping (spec §8.7 area).
 * A well-exposed photo sits near mid-tone with few blown/crushed pixels.
 * Pure math on a luma array; bitmap helper provided for convenience.
 */
object ExposureAnalyzer {

    private const val SHADOW_CLIP = 8
    private const val HIGHLIGHT_CLIP = 248

    /** @return exposure quality in 0..1 (higher = better exposed). */
    fun exposureScore(luma: IntArray): Float {
        if (luma.isEmpty()) return 0f
        var sum = 0L
        var clipped = 0
        for (v in luma) {
            sum += v
            if (v <= SHADOW_CLIP || v >= HIGHLIGHT_CLIP) clipped++
        }
        val mean = sum.toFloat() / luma.size
        // 1.0 at mid-tone (128), tapering to 0 at the extremes.
        val midScore = 1f - (kotlin.math.abs(mean - 128f) / 128f)
        val clipFraction = clipped.toFloat() / luma.size
        return (midScore * (1f - clipFraction)).coerceIn(0f, 1f)
    }

    fun exposureScore(bitmap: Bitmap): Float {
        val w = bitmap.width
        val h = bitmap.height
        if (w == 0 || h == 0) return 0f
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)
        val luma = IntArray(pixels.size)
        for (i in pixels.indices) {
            val p = pixels[i]
            val r = (p shr 16) and 0xFF
            val g = (p shr 8) and 0xFF
            val b = p and 0xFF
            luma[i] = (0.299 * r + 0.587 * g + 0.114 * b).toInt()
        }
        return exposureScore(luma)
    }
}
