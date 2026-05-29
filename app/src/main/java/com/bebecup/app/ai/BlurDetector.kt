package com.bebecup.app.ai

import android.graphics.Bitmap

/**
 * Classical, fast blur detection via Laplacian variance (spec §8.4). A sharp
 * image has high second-derivative variance; a blurry one is flat.
 *
 * The raw variance is unbounded, so we squash it to 0..1 with v/(v+K). The
 * core math takes a luma IntArray, keeping it pure and unit-testable without
 * Android bitmaps.
 */
object BlurDetector {

    /** Reference constant for the squashing curve; tuned so typical sharp baby photos land >0.45. */
    private const val K = 300.0

    /**
     * @param luma row-major grayscale values (0..255), length == width*height.
     * @return normalized sharpness in 0..1 (higher = sharper).
     */
    fun laplacianVarianceScore(luma: IntArray, width: Int, height: Int): Float {
        if (width < 3 || height < 3) return 0f
        var sum = 0.0
        var sumSq = 0.0
        var count = 0
        for (y in 1 until height - 1) {
            val row = y * width
            for (x in 1 until width - 1) {
                val i = row + x
                val lap = 4 * luma[i] - luma[i - 1] - luma[i + 1] - luma[i - width] - luma[i + width]
                sum += lap
                sumSq += lap.toDouble() * lap
                count++
            }
        }
        if (count == 0) return 0f
        val mean = sum / count
        val variance = (sumSq / count) - (mean * mean)
        return (variance / (variance + K)).toFloat().coerceIn(0f, 1f)
    }

    /** Convenience: ARGB pixels → luma → score. */
    fun blurScoreFromArgb(pixels: IntArray, width: Int, height: Int): Float {
        val luma = IntArray(pixels.size)
        for (i in pixels.indices) {
            val p = pixels[i]
            val r = (p shr 16) and 0xFF
            val g = (p shr 8) and 0xFF
            val b = p and 0xFF
            // Rec. 601 luma.
            luma[i] = (0.299 * r + 0.587 * g + 0.114 * b).toInt()
        }
        return laplacianVarianceScore(luma, width, height)
    }

    fun blurScore(bitmap: Bitmap): Float {
        val w = bitmap.width
        val h = bitmap.height
        if (w < 3 || h < 3) return 0f
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)
        return blurScoreFromArgb(pixels, w, h)
    }
}
