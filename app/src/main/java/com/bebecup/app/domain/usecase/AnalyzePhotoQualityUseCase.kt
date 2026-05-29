package com.bebecup.app.domain.usecase

import android.net.Uri
import com.bebecup.app.ai.PhotoAnalyzer
import com.bebecup.app.data.BabyPhoto
import com.bebecup.app.data.ai.AiCurationRepository
import com.bebecup.app.data.ai.PhotoAnalysisEntity

/**
 * Runs the CV/AI cascade over photos that don't yet have a cached result for
 * the current analysis version, and persists the results. Re-analysis is
 * skipped for already-analyzed photos (spec §4.2 — "re-scanning does not
 * reprocess unchanged images"). Local-only.
 *
 * @param onProgress optional callback (analyzed, total) for UI progress.
 * @return number of photos newly analyzed.
 */
class AnalyzePhotoQualityUseCase(
    private val aiRepository: AiCurationRepository,
    private val analyzer: PhotoAnalyzer
) {
    suspend operator fun invoke(
        photos: List<BabyPhoto>,
        sleepingModeEnabled: Boolean,
        nowMillis: Long,
        onProgress: ((analyzed: Int, total: Int) -> Unit)? = null
    ): Int {
        val alreadyAnalyzed = aiRepository
            .getAnalyzedPhotoIds(PhotoAnalysisEntity.CURRENT_ANALYSIS_VERSION)
            .toHashSet()
        val pending = photos.filter { it.id !in alreadyAnalyzed }

        var done = 0
        for (photo in pending) {
            // A single undecodable/locked image must not abort the whole scan.
            val analysis = try {
                analyzer.analyze(
                    photoId = photo.id,
                    uri = Uri.parse(photo.uriString),
                    sleepingModeEnabled = sleepingModeEnabled,
                    nowMillis = nowMillis
                )
            } catch (e: Exception) {
                null
            }
            if (analysis != null) {
                aiRepository.saveAnalysis(analysis)
                done++
            }
            onProgress?.invoke(done, pending.size)
        }
        return done
    }
}
