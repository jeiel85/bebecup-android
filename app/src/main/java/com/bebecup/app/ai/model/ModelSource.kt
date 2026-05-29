package com.bebecup.app.ai.model

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import kotlin.coroutines.coroutineContext

/**
 * Where a model's bytes come from. Swapping the implementation (HTTPS today,
 * Firebase Model Downloader or Play AI Delivery later) is the *only* change
 * needed to move hosting providers — [HqModelManager] and the UI stay the same.
 */
interface ModelSource {
    /**
     * Downloads [spec] to [dest], reporting fractional progress (0f..1f).
     * Must verify integrity (size/checksum) and leave [dest] present only on
     * success. Throws on any failure.
     */
    suspend fun download(spec: ModelSpec, dest: File, onProgress: (Float) -> Unit)
}

/**
 * Default source: a plain HTTPS GET via the OkHttp client already used by the
 * app. Streams to a `.part` file, verifies SHA-256, then atomically renames —
 * so a cancelled or corrupt download never looks "installed".
 */
class HttpsModelSource(
    private val client: OkHttpClient = OkHttpClient()
) : ModelSource {

    override suspend fun download(
        spec: ModelSpec,
        dest: File,
        onProgress: (Float) -> Unit
    ) = withContext(Dispatchers.IO) {
        require(spec.isConfigured) { "Model download URL is not configured for ${spec.id}" }
        dest.parentFile?.mkdirs()
        val part = File(dest.parentFile, dest.name + ".part")

        val response = client.newCall(Request.Builder().url(spec.downloadUrl).build()).execute()
        response.use {
            if (!it.isSuccessful) throw IOException("HTTP ${it.code} for ${spec.id}")
            val body = it.body ?: throw IOException("empty body for ${spec.id}")
            val total = spec.approxBytes.takeIf { n -> n > 0 } ?: body.contentLength()

            body.byteStream().use { input ->
                part.outputStream().use { out ->
                    val buffer = ByteArray(64 * 1024)
                    var read = 0L
                    while (true) {
                        coroutineContext.ensureActive() // honor cancellation
                        val n = input.read(buffer)
                        if (n < 0) break
                        out.write(buffer, 0, n)
                        read += n
                        if (total > 0) onProgress((read.toFloat() / total).coerceIn(0f, 1f))
                    }
                }
            }
        }

        if (spec.sha256.isNotBlank()) {
            val actual = sha256Hex(part)
            if (!actual.equals(spec.sha256, ignoreCase = true)) {
                part.delete()
                throw IOException("checksum mismatch for ${spec.id} (expected ${spec.sha256}, got $actual)")
            }
        }

        if (dest.exists()) dest.delete()
        if (!part.renameTo(dest)) {
            part.delete()
            throw IOException("could not finalize download for ${spec.id}")
        }
        onProgress(1f)
    }
}
