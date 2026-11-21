package com.bebecup.app.data.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import com.bebecup.app.domain.profile.CalendarDate
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Decodes downscaled bitmaps for fast on-device analysis. Never decodes
 * full-resolution images here — quality analysis runs on a thumbnail
 * (max side ~512px), classifiers/embeddings on ~224px.
 *
 * The returned bitmap is always upright: phone cameras usually store portrait
 * shots sideways plus an EXIF orientation tag, and the face detector only finds
 * faces that are upright, so the tag must be applied before analysis.
 */
class PhotoMetadataReader(private val context: Context) {

    /**
     * Decode [uri] into an upright bitmap whose longest side is at most about
     * 2×[maxSide] px (power-of-two inSampleSize). Returns null if the image
     * cannot be decoded.
     */
    suspend fun decodeThumbnail(uri: Uri, maxSide: Int = MAX_SIDE_ANALYSIS): Bitmap? =
        withContext(Dispatchers.IO) {
            val (srcWidth, srcHeight) = readBounds(uri) ?: return@withContext null
            if (srcWidth <= 0 || srcHeight <= 0) return@withContext null

            val options = BitmapFactory.Options().apply {
                inSampleSize = computeInSampleSize(srcWidth, srcHeight, maxSide)
            }
            val decoded = context.contentResolver.openInputStream(uri)?.use { input ->
                BitmapFactory.decodeStream(input, null, options)
            } ?: return@withContext null

            applyOrientation(decoded, readExifOrientation(uri))
        }

    /**
     * The day [uri] was taken, "yyyy-MM-dd": the EXIF capture date first (the
     * camera's local date), then the provider's DATE_TAKEN column. Null when
     * neither is known.
     */
    suspend fun readTakenDate(uri: Uri): String? = withContext(Dispatchers.IO) {
        exifTakenDate(uri) ?: providerTakenDate(uri)
    }

    private fun exifTakenDate(uri: Uri): String? = try {
        context.contentResolver.openInputStream(uri)?.use { input ->
            val exif = ExifInterface(input)
            TakenDates.fromExif(exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL))
                ?: TakenDates.fromExif(exif.getAttribute(ExifInterface.TAG_DATETIME))
        }
    } catch (e: Exception) {
        Log.w(TAG, "EXIF date unreadable for $uri", e)
        null
    }

    private fun providerTakenDate(uri: Uri): String? = try {
        context.contentResolver.query(uri, arrayOf(MediaStore.Images.ImageColumns.DATE_TAKEN), null, null, null)
            ?.use { cursor ->
                if (!cursor.moveToFirst() || cursor.isNull(0)) null
                else TakenDates.fromMillis(cursor.getLong(0))
            }
    } catch (e: Exception) {
        // Not every provider (e.g. a document provider) knows this column.
        Log.w(TAG, "DATE_TAKEN unavailable for $uri", e)
        null
    }

    private fun readBounds(uri: Uri): Pair<Int, Int>? {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        // In bounds-only mode decodeStream always returns null (it only fills
        // options.outWidth/outHeight), so the stream must be null-checked on its
        // own — not via the decode result.
        val input = context.contentResolver.openInputStream(uri) ?: return null
        input.use { BitmapFactory.decodeStream(it, null, options) }
        if (options.outWidth <= 0 || options.outHeight <= 0) return null
        return options.outWidth to options.outHeight
    }

    private fun readExifOrientation(uri: Uri): Int = try {
        context.contentResolver.openInputStream(uri)?.use { input ->
            ExifInterface(input).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )
        } ?: ExifInterface.ORIENTATION_NORMAL
    } catch (e: Exception) {
        // Formats without EXIF (PNG, some HEIC on old APIs) are simply upright.
        Log.w(TAG, "EXIF orientation unreadable for $uri; assuming upright", e)
        ExifInterface.ORIENTATION_NORMAL
    }

    private fun applyOrientation(bitmap: Bitmap, exifOrientation: Int): Bitmap {
        val transform = ExifTransform.of(exifOrientation)
        if (transform.isIdentity) return bitmap
        val matrix = Matrix().apply {
            if (transform.flipHorizontal) postScale(-1f, 1f)
            if (transform.rotationDegrees != 0) postRotate(transform.rotationDegrees.toFloat())
        }
        val upright = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        if (upright !== bitmap) bitmap.recycle()
        return upright
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
        private const val TAG = "PhotoMetadataReader"
        const val MAX_SIDE_ANALYSIS = 512
        const val MAX_SIDE_EMBEDDING = 224
    }
}

/** Turns capture times into the "yyyy-MM-dd" dates photos are stored with. */
object TakenDates {

    /** "2026:05:20 14:03:11" (EXIF) → "2026-05-20"; null for blanks and the all-zero placeholder. */
    fun fromExif(value: String?): String? {
        val datePart = value?.trim()?.take(10) ?: return null
        val candidate = datePart.replace(':', '-')
        return CalendarDate.parse(candidate)?.toString()
    }

    fun fromMillis(millis: Long, zone: TimeZone = TimeZone.getDefault()): String? {
        if (millis <= 0L) return null
        val format = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).apply { timeZone = zone }
        return format.format(Date(millis))
    }
}

/**
 * How to turn a stored image upright for a given EXIF orientation value:
 * mirror horizontally first (when [flipHorizontal]), then rotate clockwise by
 * [rotationDegrees]. Pure so it can be unit-tested without Android graphics.
 */
data class ExifTransform(val rotationDegrees: Int, val flipHorizontal: Boolean) {
    val isIdentity: Boolean get() = rotationDegrees == 0 && !flipHorizontal

    companion object {
        // EXIF orientation values (TIFF 6.0 / ExifInterface.ORIENTATION_*).
        fun of(exifOrientation: Int): ExifTransform = when (exifOrientation) {
            2 -> ExifTransform(0, true)    // FLIP_HORIZONTAL
            3 -> ExifTransform(180, false) // ROTATE_180
            4 -> ExifTransform(180, true)  // FLIP_VERTICAL
            // Mirror-then-rotate equals the common rotate-then-mirror recipe
            // (e.g. Glide's) with the angle negated, hence 270/90 here.
            5 -> ExifTransform(270, true)  // TRANSPOSE
            6 -> ExifTransform(90, false)  // ROTATE_90
            7 -> ExifTransform(90, true)   // TRANSVERSE
            8 -> ExifTransform(270, false) // ROTATE_270
            else -> ExifTransform(0, false)
        }
    }
}
