package com.bebecup.app.domain.model

import com.bebecup.app.data.BabyPhoto
import com.bebecup.app.data.ai.PhotoAnalysisEntity
import org.json.JSONArray

/** A photo paired with its cached analysis, for the AI shortlist UI. */
data class ShortlistItem(
    val photo: BabyPhoto,
    val analysis: PhotoAnalysisEntity
) {
    val overallScore: Float get() = analysis.overallScore
    val grade: PhotoQualityGrade get() = PhotoQualityGrade.fromScore(analysis.overallScore)
    val aiReason: String? get() = analysis.aiReasonKo
    val positiveReasons: List<String> get() = parseReasons(analysis.positiveReasonsJson)
    val rejectReasons: List<String> get() = parseReasons(analysis.rejectReasonsJson)

    private fun parseReasons(json: String): List<String> = try {
        val arr = JSONArray(json)
        List(arr.length()) { arr.getString(it) }
    } catch (e: Exception) {
        emptyList()
    }
}
