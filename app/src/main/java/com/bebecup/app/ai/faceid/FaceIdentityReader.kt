package com.bebecup.app.ai.faceid

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.net.Uri
import com.bebecup.app.data.media.PhotoMetadataReader
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.google.mlkit.vision.face.FaceLandmark
import java.io.Closeable
import java.io.IOException
import kotlinx.coroutines.CancellationException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.tensorflow.lite.InterpreterApi
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.abs

/** A face found in a photo: its box as 0..1 of the upright photo, and its unit feature vector. */
class FaceSample(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
    val embedding: FloatArray
)

/** Face-ID cannot run on this device right now: the model is not downloaded, or TensorFlow Lite is unavailable. */
class FaceIdUnavailableException(message: String, cause: Throwable? = null) : IOException(message, cause)

/** Reads the faces in one photo; null when the photo cannot be opened. */
interface PhotoFaceReader {
    suspend fun read(uri: Uri): List<FaceSample>?
}

/**
 * Finds faces with ML Kit (eyes and mouth corners), lines each one up on the
 * SFace template and turns it into a feature vector with the downloaded model.
 * Everything runs on the device. Faces too small or too far turned to tell
 * apart are skipped rather than guessed at.
 */
class FaceIdentityReader(
    private val context: Context,
    private val metadataReader: PhotoMetadataReader,
    private val store: FaceIdModelStore
) : PhotoFaceReader, Closeable {

    private val mutex = Mutex()
    private var interpreter: InterpreterApi? = null
    /** [close] came while a photo was being read; that read closes everything when it ends. */
    @Volatile private var closeRequested = false
    private val input: ByteBuffer = ByteBuffer.allocateDirect(SIZE * SIZE * 3 * 4).order(ByteOrder.nativeOrder())
    private val output = Array(1) { FloatArray(FaceVectors.DIMENSIONS) }
    private val pixels = IntArray(SIZE * SIZE)

    private val detectorLazy = lazy {
        FaceDetection.getClient(
            FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
                .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
                .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_NONE)
                .setMinFaceSize(MIN_FACE_FRACTION)
                .build()
        )
    }
    private val detector by detectorLazy

    override suspend fun read(uri: Uri): List<FaceSample>? = mutex.withLock {
        if (closeRequested) throw FaceIdUnavailableException("face-ID reader is closed")
        val bitmap = metadataReader.decodeThumbnail(uri, MAX_SIDE) ?: return@withLock null
        try {
            val faces = detect(bitmap)
                .filter(::usable)
                .sortedByDescending { it.boundingBox.width() * it.boundingBox.height() }
                .take(MAX_FACES)
            if (faces.isEmpty()) return@withLock emptyList()
            val model = interpreter ?: open().also { interpreter = it }
            withContext(Dispatchers.Default) { faces.mapNotNull { embed(model, bitmap, it) } }
        } finally {
            bitmap.recycle()
            if (closeRequested) release()
        }
    }

    /** Opens the model; throws [FaceIdUnavailableException] when it is missing or cannot run here. */
    private suspend fun open(): InterpreterApi {
        if (!store.isInstalled()) throw FaceIdUnavailableException("face-ID model is not downloaded")
        val model = try {
            FaceIdRuntime.open(context, store.file)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw FaceIdUnavailableException("TensorFlow Lite could not load the face-ID model", e)
        }
        val inShape = model.getInputTensor(0).shape().toList()
        val outShape = model.getOutputTensor(0).shape().toList()
        if (inShape != listOf(1, SIZE, SIZE, 3) || outShape != listOf(1, FaceVectors.DIMENSIONS)) {
            model.close()
            throw FaceIdUnavailableException("unexpected face-ID model shape $inShape → $outShape")
        }
        return model
    }

    private fun usable(face: Face): Boolean {
        if (face.boundingBox.width() < MIN_FACE_PX) return false
        if (abs(face.headEulerAngleY) > MAX_YAW) return false
        return LANDMARKS.all { face.getLandmark(it) != null }
    }

    private fun embed(model: InterpreterApi, bitmap: Bitmap, face: Face): FaceSample? {
        fun point(type: Int) = face.getLandmark(type)!!.position.let { FacePoint(it.x, it.y) }
        // ML Kit's left/right naming is not relied on: alignment orders the points itself.
        val transform = FaceAlignment.forFace(
            eye1 = point(FaceLandmark.LEFT_EYE),
            eye2 = point(FaceLandmark.RIGHT_EYE),
            mouth1 = point(FaceLandmark.MOUTH_LEFT),
            mouth2 = point(FaceLandmark.MOUTH_RIGHT)
        ) ?: return null

        val aligned = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888)
        try {
            val matrix = Matrix().apply { setValues(transform.matrixValues()) }
            Canvas(aligned).drawBitmap(bitmap, matrix, Paint(Paint.FILTER_BITMAP_FLAG))
            aligned.getPixels(pixels, 0, SIZE, 0, 0, SIZE, SIZE)
        } finally {
            aligned.recycle()
        }
        input.rewind()
        for (p in pixels) {
            input.putFloat(((p shr 16) and 0xFF).toFloat())
            input.putFloat(((p shr 8) and 0xFF).toFloat())
            input.putFloat((p and 0xFF).toFloat())
        }
        input.rewind()
        model.run(input, output)

        val w = bitmap.width.toFloat()
        val h = bitmap.height.toFloat()
        val box = face.boundingBox
        return FaceSample(
            left = (box.left / w).coerceIn(0f, 1f),
            top = (box.top / h).coerceIn(0f, 1f),
            right = (box.right / w).coerceIn(0f, 1f),
            bottom = (box.bottom / h).coerceIn(0f, 1f),
            embedding = FaceVectors.normalized(output[0])
        )
    }

    private suspend fun detect(bitmap: Bitmap): List<Face> =
        suspendCancellableCoroutine { cont ->
            detector.process(InputImage.fromBitmap(bitmap, 0))
                .addOnSuccessListener { cont.resume(it) }
                .addOnFailureListener { cont.resumeWithException(it) }
        }

    /**
     * Frees the model. Native inference cannot be interrupted, so while a photo is
     * being read this only asks that read to free it when it finishes.
     */
    override fun close() {
        closeRequested = true
        if (mutex.tryLock()) {
            try {
                release()
            } finally {
                mutex.unlock()
            }
        }
    }

    private fun release() {
        interpreter?.close()
        interpreter = null
        if (detectorLazy.isInitialized()) detector.close()
    }

    private companion object {
        const val SIZE = FaceAlignment.SIZE
        /** Decoded long side is 768..1535 px: more than scoring needs, for small faces. */
        const val MAX_SIDE = 768
        const val MIN_FACE_FRACTION = 0.06f
        const val MIN_FACE_PX = 40
        const val MAX_YAW = 40f
        const val MAX_FACES = 6
        val LANDMARKS = listOf(
            FaceLandmark.LEFT_EYE, FaceLandmark.RIGHT_EYE, FaceLandmark.MOUTH_LEFT, FaceLandmark.MOUTH_RIGHT
        )
    }
}
