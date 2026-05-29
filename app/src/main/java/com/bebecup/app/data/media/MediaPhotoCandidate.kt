package com.bebecup.app.data.media

import android.net.Uri

/**
 * A lightweight descriptor of an image found by [MediaStorePhotoSource].
 * Holds only metadata + a content [uri]; no pixel data is loaded here.
 */
data class MediaPhotoCandidate(
    val uri: Uri,
    val mediaStoreId: Long,
    val displayName: String?,
    val mimeType: String?,
    /** EXIF capture time (DATE_TAKEN), in epoch millis. Null when unavailable. */
    val takenAtMillis: Long?,
    /** When the file was added to the device (DATE_ADDED), in epoch millis. */
    val addedAtMillis: Long?,
    val width: Int?,
    val height: Int?
) {
    /** Best available timestamp: capture time if known, else add time. */
    val effectiveTimeMillis: Long
        get() = takenAtMillis ?: addedAtMillis ?: 0L
}
