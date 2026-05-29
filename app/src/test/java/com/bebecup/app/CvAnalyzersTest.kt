package com.bebecup.app

import com.bebecup.app.ai.BlurDetector
import com.bebecup.app.ai.ExposureAnalyzer
import com.bebecup.app.ai.ExpressionAnalyzer
import com.bebecup.app.ai.EyeStateAnalyzer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CvAnalyzersTest {

    // --- Blur ---

    @Test
    fun `flat image is detected as blurry, high-contrast as sharp`() {
        val w = 16
        val h = 16
        val flat = IntArray(w * h) { 128 }
        val checker = IntArray(w * h) { i -> if (((i % w) + (i / w)) % 2 == 0) 0 else 255 }

        val flatScore = BlurDetector.laplacianVarianceScore(flat, w, h)
        val sharpScore = BlurDetector.laplacianVarianceScore(checker, w, h)

        assertEquals(0f, flatScore, 0.001f)
        assertTrue("checker should score sharper than flat", sharpScore > flatScore)
        assertTrue("checker should score high", sharpScore > 0.9f)
    }

    // --- Exposure ---

    @Test
    fun `mid-tone exposes well, all-black scores low`() {
        val mid = IntArray(100) { 128 }
        val black = IntArray(100) { 0 }
        assertEquals(1f, ExposureAnalyzer.exposureScore(mid), 0.001f)
        assertEquals(0f, ExposureAnalyzer.exposureScore(black), 0.001f)
    }

    // --- Eyes ---

    @Test
    fun `eye score averages probabilities, sleeping mode floors it`() {
        assertEquals(0.8f, EyeStateAnalyzer.eyeOpenScore(0.9f, 0.7f, sleepingModeEnabled = false), 0.001f)
        // closed eyes (0.1) get floored to 0.7 when sleeping is allowed
        assertEquals(0.7f, EyeStateAnalyzer.eyeOpenScore(0.1f, 0.1f, sleepingModeEnabled = true), 0.001f)
        // unknown probabilities → neutral 0.5
        assertEquals(0.5f, EyeStateAnalyzer.eyeOpenScore(null, null, sleepingModeEnabled = false), 0.001f)
    }

    // --- Expression ---

    @Test
    fun `expression labels follow smile probability`() {
        assertEquals(ExpressionAnalyzer.ExpressionLabel.NATURAL_SMILE,
            ExpressionAnalyzer.analyze(0.9f, faceDetected = true, sleepingModeEnabled = false).label)
        assertEquals(ExpressionAnalyzer.ExpressionLabel.CALM,
            ExpressionAnalyzer.analyze(0.1f, faceDetected = true, sleepingModeEnabled = false).label)
        assertEquals(ExpressionAnalyzer.ExpressionLabel.SLEEPING,
            ExpressionAnalyzer.analyze(0.0f, faceDetected = true, sleepingModeEnabled = true).label)
        assertEquals(ExpressionAnalyzer.ExpressionLabel.UNCLEAR_FACE,
            ExpressionAnalyzer.analyze(null, faceDetected = false, sleepingModeEnabled = false).label)
    }
}
