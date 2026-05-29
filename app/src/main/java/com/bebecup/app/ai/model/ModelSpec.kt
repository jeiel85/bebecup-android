package com.bebecup.app.ai.model

/**
 * Identifies a downloadable on-device model and the data needed to verify it
 * after download. Source-agnostic so the curation pipeline never depends on
 * *how* the model arrived: [HttpsModelSource] uses [downloadUrl] + [sha256];
 * a future Firebase / Play-AI source would key off [id] and ignore the URL.
 */
data class ModelSpec(
    val id: String,
    val fileName: String,
    val downloadUrl: String,
    /** Lowercase hex SHA-256 of the model file. Blank disables verification (dev only). */
    val sha256: String,
    /** Approximate compressed size in bytes — used only for the download UI. */
    val approxBytes: Long
) {
    /** A real https URL must be set before [HttpsModelSource] can fetch it. */
    val isConfigured: Boolean get() = downloadUrl.startsWith("https://")
}

/**
 * Registry of models the app can pull on demand. Until a concrete aesthetic
 * model is chosen + hosted, [AESTHETIC] carries a placeholder URL: the download
 * pipeline, UI, and integrity checks are all real, but [ModelSpec.isConfigured]
 * stays false so a misconfigured build can't silently fetch the wrong file.
 *
 * To go live: host the .tflite, set [downloadUrl] to its https URL, and paste
 * its SHA-256 into [sha256].
 */
object Models {
    val AESTHETIC = ModelSpec(
        id = "aesthetic-iqa-v1",
        fileName = "aesthetic-iqa-v1.tflite",
        downloadUrl = "", // TODO: set to the hosted .tflite URL (Firebase / Play AI / CDN)
        sha256 = "",      // TODO: paste the model's SHA-256 once hosted
        approxBytes = 0L  // TODO: set for an accurate progress bar / size label
    )
}
