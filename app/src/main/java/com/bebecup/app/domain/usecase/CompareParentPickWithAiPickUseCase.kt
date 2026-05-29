package com.bebecup.app.domain.usecase

/**
 * Compares the parent's tournament winner with the AI's top pick (spec §11.7).
 * Returns null when the tournament didn't originate from an AI shortlist (no AI
 * pick to compare against). The parent's choice is always final — this is just
 * a fun "did the AI agree?" summary.
 */
class CompareParentPickWithAiPickUseCase {

    data class MatchResult(
        val matched: Boolean,
        val aiTopPhotoId: Int,
        val parentWinnerId: Int
    )

    operator fun invoke(parentWinnerId: Int, aiTopPhotoId: Int?): MatchResult? {
        if (aiTopPhotoId == null) return null
        return MatchResult(
            matched = parentWinnerId == aiTopPhotoId,
            aiTopPhotoId = aiTopPhotoId,
            parentWinnerId = parentWinnerId
        )
    }
}
