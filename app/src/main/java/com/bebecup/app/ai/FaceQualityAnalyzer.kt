package com.bebecup.app.ai

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.abs

/**
 * On-device face quality via ML Kit Face Detection (spec §8.5). Runs entirely
 * locally — no upload, no remote embeddings. This is a thin wrapper: it returns
 * raw measurements as a [FaceQualityResult]; turning those into scores is done
 * by the pure analyzers so the math stays testable.
 */
class FaceQualityAnalyzer {

    private val detector by lazy {
        val options = FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL) // eye-open + smiling
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_NONE)
            .build()
        FaceDetection.getClient(options)
    }

    suspend fun analyze(bitmap: Bitmap): FaceQualityResult {
        val image = InputImage.fromBitmap(bitmap, 0)
        val faces = detectFaces(image)
        if (faces.isEmpty()) return FaceQualityResult.NONE

        val imgW = bitmap.width.toFloat()
        val imgH = bitmap.height.toFloat()
        // Use the largest face as the subject.
        val face = faces.maxByOrNull { it.boundingBox.width() * it.boundingBox.height() }!!
        val box = face.boundingBox

        val faceArea = (box.width().toFloat() * box.height().toFloat())
        val faceAreaRatio = if (imgW > 0 && imgH > 0) (faceArea / (imgW * imgH)).coerceIn(0f, 1f) else 0f

        // Centered-ness: 1.0 when the face center sits at the image center.
        val cx = box.exactCenterX()
        val cy = box.exactCenterY()
        val dx = abs(cx - imgW / 2f) / (imgW / 2f)
        val dy = abs(cy - imgH / 2f) / (imgH / 2f)
        val faceCenterScore = (1f - (dx + dy) / 2f).coerceIn(0f, 1f)

        return FaceQualityResult(
            faceDetected = true,
            faceCount = faces.size,
            faceCenterScore = faceCenterScore,
            faceAreaRatio = faceAreaRatio,
            headYaw = face.headEulerAngleY,
            headPitch = face.headEulerAngleX,
            headRoll = face.headEulerAngleZ,
            leftEyeOpenProb = face.leftEyeOpenProbability,
            rightEyeOpenProb = face.rightEyeOpenProbability,
            smilingProb = face.smilingProbability
        )
    }

    private suspend fun detectFaces(image: InputImage): List<Face> =
        suspendCancellableCoroutine { cont ->
            detector.process(image)
                .addOnSuccessListener { faces -> cont.resume(faces) }
                .addOnFailureListener { e -> cont.resumeWithException(e) }
        }
}
