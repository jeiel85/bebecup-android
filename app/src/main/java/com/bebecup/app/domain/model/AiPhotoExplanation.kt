package com.bebecup.app.domain.model

/**
 * A short, parent-friendly Korean explanation for why a photo was recommended
 * (spec §8.10). [source] records whether it came from a local VLM or the
 * deterministic rule-based fallback, for transparency.
 */
data class AiPhotoExplanation(
    val summaryKo: String,
    val source: Source
) {
    enum class Source { RULE_BASED, LOCAL_VLM }
}
