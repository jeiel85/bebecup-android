package com.bebecup.app.ai

import android.net.Uri
import com.bebecup.app.data.ai.PhotoAnalysisEntity

/**
 * Abstraction over the per-photo analysis cascade so callers (use cases) can be
 * unit-tested with a fake. [PhotoQualityAnalyzer] is the production
 * implementation; tests supply a canned analyzer.
 */
interface PhotoAnalyzer {
    suspend fun analyze(
        photoId: Int,
        uri: Uri,
        sleepingModeEnabled: Boolean,
        nowMillis: Long
    ): PhotoAnalysisEntity?
}
