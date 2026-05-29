package com.bebecup.app.ai.model

import android.content.Context
import java.io.File

/**
 * Owns the lifecycle of an optional, downloaded-on-demand model: where it lives,
 * whether it's installed, fetching it (one click), and deleting it. Stores the
 * file in app-internal storage so it inherits the app's sandbox — consistent
 * with the privacy promise that everything stays on-device.
 *
 * Inference itself is intentionally NOT here: once a concrete model is chosen,
 * a LiteRT scorer reads [modelFile] and the curation pipeline gates on
 * [isInstalled]. Until then this is the complete, testable download mechanism.
 */
class HqModelManager(
    private val context: Context,
    private val spec: ModelSpec,
    private val source: ModelSource = HttpsModelSource()
) {
    private val modelsDir: File get() = File(context.filesDir, "models")

    /** Absolute location the model occupies once installed. */
    val modelFile: File get() = File(modelsDir, spec.fileName)

    val approxBytes: Long get() = spec.approxBytes
    val isConfigured: Boolean get() = spec.isConfigured

    /** True once a non-empty model file is present. */
    fun isInstalled(): Boolean = modelFile.isFile && modelFile.length() > 0L

    /**
     * Downloads + verifies the model, reporting 0f..1f progress. Throws on
     * failure (caller maps to a UI error state). Re-downloading overwrites.
     */
    suspend fun download(onProgress: (Float) -> Unit = {}) {
        source.download(spec, modelFile, onProgress)
    }

    /** Removes the installed model (and any stale partial). Frees the storage. */
    fun delete() {
        modelFile.delete()
        File(modelsDir, spec.fileName + ".part").delete()
    }
}
