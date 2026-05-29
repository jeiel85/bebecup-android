package com.bebecup.app.domain.usecase

import com.bebecup.app.data.BabyPhoto
import com.bebecup.app.data.BabyPhotoRepository
import com.bebecup.app.data.media.MediaStorePhotoSource
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Scans recent gallery images (spec §8.2) and registers any new ones as
 * [BabyPhoto] rows so they can be analyzed and feed the tournament. Existing
 * rows (matched by URI) are reused — re-scanning does not duplicate. Returns
 * the [BabyPhoto] rows (with ids) for the scanned range.
 */
class ScanRecentBabyPhotosUseCase(
    private val repository: BabyPhotoRepository,
    private val mediaSource: MediaStorePhotoSource
) {
    suspend operator fun invoke(
        startMillis: Long,
        endMillis: Long,
        includeScreenshots: Boolean = false
    ): List<BabyPhoto> {
        val candidates = mediaSource.queryRecent(startMillis, endMillis, includeScreenshots)
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val result = ArrayList<BabyPhoto>(candidates.size)

        for (candidate in candidates) {
            val uriString = candidate.uri.toString()
            val existing = repository.getPhotoByUri(uriString)
            if (existing != null) {
                result += existing
                continue
            }
            val takenDate = dateFormat.format(Date(candidate.effectiveTimeMillis))
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
