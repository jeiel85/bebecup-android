package com.bebecup.app.ai.faceid

import android.content.Context
import android.util.Log
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import javax.net.ssl.HttpsURLConnection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/**
 * The face-ID model (아이 얼굴 구분) is not inside either build: the parent turns the
 * feature on in onboarding or 설정 and the app downloads it once. It is OpenCV Zoo's
 * SFace (Apache-2.0) rebuilt as TensorFlow Lite with int8 weights; see DECISIONS.md.
 *
 * The file is only accepted when its size and SHA-256 match the values pinned here,
 * so a changed or truncated file is never loaded. It lives in no-backup storage: it
 * can always be downloaded again and has nothing personal in it.
 */
class FaceIdModelStore(context: Context) {

    private val dir = File(context.applicationContext.noBackupFilesDir, DIR)
    val file: File = File(dir, FILE_NAME)

    /** Present with the pinned size. The SHA-256 was checked when it was written. */
    fun isInstalled(): Boolean = file.isFile && file.length() == SIZE_BYTES

    /**
     * Download and verify the model. [onProgress] gets 0..1. Returns normally once
     * [file] is in place; throws [IOException] (offline, HTTP error, mismatch) otherwise.
     */
    suspend fun download(onProgress: (Float) -> Unit = {}) = withContext(Dispatchers.IO) {
        if (isInstalled()) return@withContext
        dir.mkdirs()
        // Earlier model versions are of no use once a newer one is pinned.
        dir.listFiles()?.filter { it.name != FILE_NAME }?.forEach { it.delete() }
        val partial = File(dir, "$FILE_NAME.part")
        val digest = MessageDigest.getInstance("SHA-256")
        var connection: HttpURLConnection? = null
        try {
            connection = (URL(URL).openConnection() as HttpsURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = TIMEOUT_MILLIS
                readTimeout = TIMEOUT_MILLIS
                useCaches = false
                instanceFollowRedirects = true // github.com → release-assets host, https only
            }
            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                throw IOException("HTTP ${connection.responseCode}")
            }
            var received = 0L
            var lastPercent = -1
            connection.inputStream.use { input ->
                partial.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        ensureActive()
                        val n = input.read(buffer)
                        if (n < 0) break
                        received += n
                        if (received > SIZE_BYTES) throw IOException("verify: larger than pinned")
                        digest.update(buffer, 0, n)
                        output.write(buffer, 0, n)
                        val percent = (received * 100 / SIZE_BYTES).toInt()
                        if (percent != lastPercent) {
                            lastPercent = percent
                            onProgress(percent / 100f)
                        }
                    }
                }
            }
            val sha = digest.digest().joinToString("") { "%02x".format(it) }
            if (received != SIZE_BYTES || sha != SHA256) throw IOException("verify: size or SHA-256 mismatch")
            if (!partial.renameTo(file)) throw IOException("could not move the model into place")
        } catch (e: Exception) {
            Log.w(TAG, "face-ID model download failed: $e")
            throw e
        } finally {
            partial.delete()
            connection?.disconnect()
        }
    }

    /** 설정 > 받은 파일 지우기: frees the space; the feature is off until downloaded again. */
    fun delete() {
        dir.listFiles()?.forEach { it.delete() }
    }

    companion object {
        private const val TAG = "FaceIdModelStore"
        private const val DIR = "face_id"
        private const val TIMEOUT_MILLIS = 30_000

        /** Bump with the file: embeddings from another version are not comparable. */
        const val MODEL_VERSION = 1
        const val FILE_NAME = "bebecup-face-id-sface-v1.tflite"
        const val URL = "https://github.com/jeiel85/bebecup-android/releases/download/face-id-model-v1/$FILE_NAME"
        const val SIZE_BYTES = 9_865_200L
        const val SHA256 = "0fd54bf710145b33bd43963443fd1c4c658a998cf8ac86d7d387d6d908697ff6"
    }
}
