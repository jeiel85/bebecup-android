package com.bebecup.app.ai

import android.net.Uri
import com.bebecup.app.data.ai.PhotoAnalysisEntity
import com.bebecup.app.data.media.PhotoMetadataReader
import com.bebecup.app.domain.model.PhotoQualityGrade
import com.bebecup.app.domain.model.PhotoScoreInput
import org.json.JSONArray

/**
 * The cascade pipeline (spec §8.1) for a single photo: decode a downscaled
 * thumbnail, run cheap classical CV (blur, exposure), then ML Kit face/eye/
 * expression, combine via [BabyPhotoScorePolicy], and build Korean reasons.
 *
 * Duplicate/uniqueness handling is a no-op here (defaults to unique) until
 * Phase 5 adds clustering. Returns a ready-to-cache [PhotoAnalysisEntity], or
 * null if the image could not be decoded.
 */
class PhotoQualityAnalyzer(
    private val metadataReader: PhotoMetadataReader,
    private val faceAnalyzer: FaceQualityAnalyzer
) : PhotoAnalyzer {

    override suspend fun analyze(
        photoId: Int,
        uri: Uri,
        sleepingModeEnabled: Boolean,
        nowMillis: Long
    ): PhotoAnalysisEntity? {
        val bitmap = metadataReader.decodeThumbnail(uri) ?: return null
        try {
            val blurScore = BlurDetector.blurScore(bitmap)
            val exposureScore = ExposureAnalyzer.exposureScore(bitmap)
            val dHash = PerceptualHash.dHash(bitmap)
            val face = faceAnalyzer.analyze(bitmap)

            val eyeOpenScore = EyeStateAnalyzer.eyeOpenScore(
                face.leftEyeOpenProb, face.rightEyeOpenProb, sleepingModeEnabled
            )
            val expression = ExpressionAnalyzer.analyze(
                face.smilingProb, face.faceDetected, sleepingModeEnabled
            )
            val compositionScore = compositionScore(face)

            // Phase 5 will replace these with real cluster results.
            val uniquenessScore = 1.0f
            val duplicatePenalty = 0.0f

            val input = PhotoScoreInput(
                blurScore = blurScore,
                faceDetected = face.faceDetected,
                faceCenterScore = face.faceCenterScore,
                eyeOpenScore = eyeOpenScore,
                expressionScore = expression.score,
                exposureScore = exposureScore,
                compositionScore = compositionScore,
                uniquenessScore = uniquenessScore,
                sleepingModeEnabled = sleepingModeEnabled
            )

            val overall = BabyPhotoScorePolicy.calculateOverallScore(input)
            val grade = PhotoQualityGrade.fromScore(overall)
            val positives = PhotoReasonBuilder.buildPositiveReasons(input)
            val rejects = PhotoReasonBuilder.buildRejectReasons(input, duplicatePenalty)

            return PhotoAnalysisEntity(
                photoId = photoId,
                analysisVersion = PhotoAnalysisEntity.CURRENT_ANALYSIS_VERSION,
                blurScore = blurScore,
                faceDetected = face.faceDetected,
                faceCount = face.faceCount,
                faceCenterScore = face.faceCenterScore,
                eyeOpenScore = eyeOpenScore,
                expressionScore = expression.score,
                exposureScore = exposureScore,
                compositionScore = compositionScore,
                duplicatePenalty = duplicatePenalty,
                overallScore = overall,
                qualityGrade = grade.label,
                dHash = dHash,
                rejectReasonsJson = toJsonArray(rejects),
                positiveReasonsJson = toJsonArray(positives),
                aiReasonKo = null,
                analyzedAtMillis = nowMillis
            )
        } finally {
            bitmap.recycle()
        }
    }

    /**
     * Cheap composition proxy: a face that fills a comfortable fraction of the
     * frame scores well; too tiny or too cropped scores lower. Neutral when no
     * face is present.
     */
    private fun compositionScore(face: FaceQualityResult): Float {
        if (!face.faceDetected) return 0.5f
        val ideal = 0.18f // ~18% of the frame is a pleasant baby portrait crop.
        val diff = kotlin.math.abs(face.faceAreaRatio - ideal)
        return (1f - (diff / ideal).coerceIn(0f, 1f)).coerceIn(0f, 1f)
    }

    private fun toJsonArray(items: List<String>): String =
        JSONArray(items).toString()
}
