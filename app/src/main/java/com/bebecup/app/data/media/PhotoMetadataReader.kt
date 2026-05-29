package com.bebecup.app.data.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Decodes downscaled bitmaps for fast on-device analysis. Never decodes
 * full-resolution images here — quality analysis runs on a thumbnail
 * (max side ~512px), classifiers/embeddings on ~224px.
 */
class PhotoMetadataReader(private val context: Context) {

    /**
     * Decode [uri] into a bitmap whose longest side is at most [maxSide] px,
     * using BitmapFactory.inSampleSize for cheap downscaling. Returns null if
     * the image cannot be decoded.
     */
    suspend fun decodeThumbnail(uri: Uri, maxSide: Int = MAX_SIDE_ANALYSIS): Bitmap? =
        withContext(Dispatchers.IO) {
            val (srcWidth, srcHeight) = readBounds(uri) ?: return@withContext null
            if (srcWidth <= 0 || srcHeight <= 0) return@withContext null

            val options = BitmapFactory.Options().apply {
                inSampleSize = computeInSampleSize(srcWidth, srcHeight, maxSide)
            }
            context.contentResolver.openInputStream(uri)?.use { input ->
                BitmapFactory.decodeStream(input, null, options)
            }
        }

    private fun readBounds(uri: Uri): Pair<Int, Int>? {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { input ->
            BitmapFactory.decodeStream(input, null, options)
        } ?: return null
        if (options.outWidth <= 0 || options.outHeight <= 0) return null
        return options.outWidth to options.outHeight
    }

    private fun computeInSampleSize(width: Int, height: Int, maxSide: Int): Int {
        var sample = 1
        val longest = maxOf(width, height)
        while (longest / (sample * 2) >= maxSide) {
            sample *= 2
        }
        return sample
    }

    companion object {
        const val MAX_SIDE_ANALYSIS = 512
        const val MAX_SIDE_EMBEDDING = 224
    }
}
