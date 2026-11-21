package com.bebecup.app.domain.usecase

import android.net.Uri
import com.bebecup.app.data.BabyPhoto
import com.bebecup.app.data.BabyPhotoRepository
import com.bebecup.app.data.media.MediaStorePhotoSource
import com.bebecup.app.data.media.TakenDates

/**
 * Scans recent gallery images (spec §8.2) and registers any new ones as
 * [BabyPhoto] rows so they can be analyzed and feed the tournament. Existing
 * rows (matched by URI) are reused — re-scanning does not duplicate. Returns
 * the [BabyPhoto] rows (with ids) for the scanned range.
 *
 * A new row's date comes from [takenDateOf] (the EXIF capture date) first:
 * the gallery's DATE_TAKEN is sometimes the day a messenger or download saved
 * the file, which would make the baby's age wrong.
 */
class ScanRecentBabyPhotosUseCase(
    private val repository: BabyPhotoRepository,
    private val mediaSource: MediaStorePhotoSource,
    private val takenDateOf: suspend (Uri) -> String? = { null }
) {
    suspend operator fun invoke(
        startMillis: Long,
        endMillis: Long,
        includeScreenshots: Boolean = false
    ): List<BabyPhoto> {
        val candidates = mediaSource.queryRecent(startMillis, endMillis, includeScreenshots)
        val result = ArrayList<BabyPhoto>(candidates.size)

        for (candidate in candidates) {
            val uriString = candidate.uri.toString()
            val existing = repository.getPhotoByUri(uriString)
            if (existing != null) {
                result += existing
                continue
            }
            val takenDate = takenDateOf(candidate.uri)
                ?: TakenDates.fromMillis(candidate.effectiveTimeMillis)
                ?: continue // no date at all: not a photo this app can place
            val newId = repository.insertPhoto(
                BabyPhoto(
                    uriString = uriString,
                    title = candidate.displayName ?: "최근 아기사진",
                    description = "",
                    takenDate = takenDate
                )
            ).toInt()
            // Re-read to return a row carrying its generated id.
            repository.getPhotoById(newId)?.let { result += it }
        }
        return result
    }
}
