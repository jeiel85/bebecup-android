package com.bebecup.app.ai

import com.bebecup.app.domain.model.PhotoScoreInput

/**
 * Builds parent-friendly Korean reason strings (spec §16.1, §11.5, §17).
 * Pure & deterministic. Tone rule: never describe a baby's face harshly —
 * use gentle "아쉬운 후보 / 제외됨" style phrasing, never "나쁜/실패/못생김".
 */
object PhotoReasonBuilder {

    fun buildPositiveReasons(input: PhotoScoreInput): List<String> {
        val reasons = mutableListOf<String>()
        if (input.blurScore >= 0.75f) reasons += "얼굴이 선명하게 보여요"
        if (input.eyeOpenScore >= 0.75f) reasons += "눈을 또렷하게 뜨고 있어요"
        if (input.expressionScore >= 0.75f) reasons += "표정이 자연스럽고 사랑스러워요"
        if (input.faceCenterScore >= 0.70f) reasons += "아기가 사진의 중심에 잘 담겼어요"
        if (input.uniquenessScore >= 0.80f) reasons += "비슷한 사진 중에서도 가장 좋은 후보예요"
        return reasons
    }

    fun buildRejectReasons(input: PhotoScoreInput, duplicatePenalty: Float): List<String> {
        val reasons = mutableListOf<String>()
        if (input.blurScore < 0.35f) reasons += "얼굴 부분이 조금 흔들렸어요"
        // Sleeping baby photos are emotionally valuable — don't flag closed eyes
        // when the parent has allowed sleeping shots (spec §8.6).
        if (!input.sleepingModeEnabled && input.eyeOpenScore < 0.35f) {
            reasons += "활동 사진 기준으로는 눈이 감겨 있어요"
        }
        if (input.exposureScore < 0.35f) reasons += "사진이 조금 어둡거나 밝게 찍혔어요"
        if (duplicatePenalty > 0.50f) reasons += "비슷한 사진 중 더 선명한 후보가 있어요"
        return reasons
    }
}
