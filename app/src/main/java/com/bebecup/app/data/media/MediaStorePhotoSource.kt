package com.bebecup.app.data.media

import android.content.ContentUris
import android.content.Context
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Reads recent image candidates from MediaStore by date range, so AI mode does
 * not depend on the manual Photo Picker. Returns metadata only — pixels are
 * decoded later (and downscaled) by [PhotoMetadataReader].
 *
 * Requires READ_MEDIA_IMAGES (API 33+) / READ_EXTERNAL_STORAGE (<=32). Callers
 * must hold the permission before invoking; this class does not request it.
 */
class MediaStorePhotoSource(private val context: Context) {

    /**
     * @param startMillis inclusive lower bound (epoch millis) on DATE_ADDED.
     * @param endMillis inclusive upper bound (epoch millis) on DATE_ADDED.
     * @param includeScreenshots when false (default), images in a "Screenshots"
     *   bucket or named like a screenshot are skipped.
     */
    suspend fun queryRecent(
        startMillis: Long,
        endMillis: Long,
        includeScreenshots: Boolean = false
    ): List<MediaPhotoCandidate> = withContext(Dispatchers.IO) {
        val collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI

        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.MIME_TYPE,
            MediaStore.Images.Media.DATE_TAKEN,
            MediaStore.Images.Media.DATE_ADDED,
            MediaStore.Images.Media.WIDTH,
            MediaStore.Images.Media.HEIGHT,
            MediaStore.Images.Media.BUCKET_DISPLAY_NAME
        )

        // DATE_ADDED is stored in SECONDS; DATE_TAKEN in MILLIS.
        val startSec = startMillis / 1000
        val endSec = endMillis / 1000
        val mimeTypes = listOf("image/jpeg", "image/png", "image/heic", "image/heif")

        val selection = buildString {
            append("${MediaStore.Images.Media.DATE_ADDED} >= ? AND ")
            append("${MediaStore.Images.Media.DATE_ADDED} <= ? AND ")
            append(mimeTypes.joinToString(" OR ", prefix = "(", postfix = ")") { "${MediaStore.Images.Media.MIME_TYPE} = ?" })
        }
        val selectionArgs = (listOf(startSec.toString(), endSec.toString()) + mimeTypes).toTypedArray()
        val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"

        val results = ArrayList<MediaPhotoCandidate>()

        context.contentResolver.query(collection, projection, selection, selectionArgs, sortOrder)?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
            val mimeCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.MIME_TYPE)
            val takenCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_TAKEN)
            val addedCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
            val widthCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.WIDTH)
            val heightCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.HEIGHT)
            val bucketCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_DISPLAY_NAME)

            while (cursor.moveToNext()) {
                val displayName = if (cursor.isNull(nameCol)) null else cursor.getString(nameCol)
                val bucket = if (cursor.isNull(bucketCol)) null else cursor.getString(bucketCol)

                if (!includeScreenshots && isScreenshot(displayName, bucket)) continue

                val id = cursor.getLong(idCol)
                val takenMillis = if (cursor.isNull(takenCol)) null else cursor.getLong(takenCol).takeIf { it > 0 }
                val addedSec = if (cursor.isNull(addedCol)) null else cursor.getLong(addedCol)

                results += MediaPhotoCandidate(
                    uri = ContentUris.withAppendedId(collection, id),
                    mediaStoreId = id,
                    displayName = displayName,
                    mimeType = if (cursor.isNull(mimeCol)) null else cursor.getString(mimeCol),
                    takenAtMillis = takenMillis,
                    addedAtMillis = addedSec?.let { it * 1000 },
                    width = if (cursor.isNull(widthCol)) null else cursor.getInt(widthCol),
                    height = if (cursor.isNull(heightCol)) null else cursor.getInt(heightCol)
                )
            }
        }

        results
    }

    private fun isScreenshot(displayName: String?, bucket: String?): Boolean {
        val nameHit = displayName?.contains("screenshot", ignoreCase = true) == true
        val bucketHit = bucket?.contains("screenshot", ignoreCase = true) == true
        return nameHit || bucketHit
    }
}
