package com.bebecup.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.bebecup.app.ai.model.HqModelManager
import com.bebecup.app.ai.model.ModelSource
import com.bebecup.app.ai.model.ModelSpec
import com.bebecup.app.ai.model.sha256Hex
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.io.IOException

/**
 * Verifies the one-click model download mechanism end-to-end against a fake
 * source: install detection, byte-for-byte placement, delete, and that a failed
 * download never leaves the model looking "installed".
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class HqModelManagerTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private val spec = ModelSpec(
        id = "test-model",
        fileName = "test-model.tflite",
        downloadUrl = "https://example.com/test-model.tflite",
        sha256 = "",
        approxBytes = 0L
    )

    /** Writes fixed bytes to the destination, like a successful network fetch. */
    private class FakeSource(private val content: ByteArray) : ModelSource {
        override suspend fun download(spec: ModelSpec, dest: File, onProgress: (Float) -> Unit) {
            dest.parentFile?.mkdirs()
            dest.writeBytes(content)
            onProgress(1f)
        }
    }

    private class FailingSource : ModelSource {
        override suspend fun download(spec: ModelSpec, dest: File, onProgress: (Float) -> Unit) {
            throw IOException("boom")
        }
    }

    @Test
    fun download_installs_then_delete_removes() = runBlocking {
        val content = "fake-tflite-bytes".toByteArray()
        val manager = HqModelManager(context, spec, FakeSource(content))
        manager.delete() // clean slate

        assertFalse(manager.isInstalled())

        var lastProgress = 0f
        manager.download { lastProgress = it }

        assertTrue(manager.isInstalled())
        assertEquals(1f, lastProgress, 0.0001f)
        assertEquals(content.toList(), manager.modelFile.readBytes().toList())

        manager.delete()
        assertFalse(manager.isInstalled())
    }

    @Test
    fun failed_download_is_not_installed() = runBlocking {
        val manager = HqModelManager(context, spec, FailingSource())
        manager.delete()

        var threw = false
        try {
            manager.download()
        } catch (e: IOException) {
            threw = true
        }

        assertTrue(threw)
        assertFalse(manager.isInstalled())
    }

    @Test
    fun sha256_matches_known_vector() {
        // SHA-256("abc") — RFC test vector.
        val file = File.createTempFile("sha", ".bin")
        file.writeBytes("abc".toByteArray())
        assertEquals(
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            sha256Hex(file)
        )
        file.delete()
    }
}
