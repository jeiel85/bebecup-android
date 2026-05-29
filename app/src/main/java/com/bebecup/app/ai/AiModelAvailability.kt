package com.bebecup.app.ai

/**
 * Decides which [LocalVisionExplainer] to use based on whether an optional
 * local VLM model is installed on the device (spec §8.10). The app degrades
 * gracefully: when no model is present, the deterministic rule-based explainer
 * is used and everything still works.
 *
 * No VLM is bundled in this build (size/licensing), so [isLocalVlmAvailable] is
 * currently false. When a model is added, return its explainer here — callers
 * don't change.
 */
class AiModelAvailability(
    private val ruleBasedExplainer: LocalVisionExplainer = RuleBasedVisionExplainer()
) {
    fun isLocalVlmAvailable(): Boolean = false

    fun explainer(): LocalVisionExplainer = ruleBasedExplainer
}
