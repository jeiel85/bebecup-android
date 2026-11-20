package com.bebecup.app

import com.bebecup.app.data.ai.PhotoAnalysisEntity
import com.bebecup.app.data.media.ExifTransform
import com.bebecup.app.domain.nudge.NudgePolicy
import com.bebecup.app.domain.usecase.ShortlistRanking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NudgeAndRankingTest {

    private val day = 24L * 60 * 60 * 1000
    private val now = 1_000L * day

    // ---------------- NudgePolicy ----------------

    @Test
    fun `nudge needs opt-in and enough unsorted photos`() {
        assertFalse(NudgePolicy.shouldNudge(false, 100, 0L, now))
        assertFalse(NudgePolicy.shouldNudge(true, NudgePolicy.UNSORTED_THRESHOLD - 1, 0L, now))
        assertTrue(NudgePolicy.shouldNudge(true, NudgePolicy.UNSORTED_THRESHOLD, 0L, now))
    }

    @Test
    fun `nudge is rate limited`() {
        val justBefore = now - (NudgePolicy.MIN_INTERVAL_DAYS * day) + 1
        val longAgo = now - NudgePolicy.MIN_INTERVAL_DAYS * day
        assertFalse(NudgePolicy.shouldNudge(true, 50, justBefore, now))
        assertTrue(NudgePolicy.shouldNudge(true, 50, longAgo, now))
    }

    @Test
    fun `unsorted window starts at the last saved round, else a look-back`() {
        assertEquals(now - 3 * day, NudgePolicy.unsortedWindowStart(now - 3 * day, now))
        assertEquals(
            now - NudgePolicy.FIRST_RUN_LOOKBACK_DAYS * day,
            NudgePolicy.unsortedWindowStart(0L, now)
        )
        assertNull(NudgePolicy.daysSinceReview(0L, now))
        assertEquals(3L, NudgePolicy.daysSinceReview(now - 3 * day - 5, now))
    }

    // ---------------- EXIF orientation ----------------

    @Test
    fun `portrait photos stored sideways are rotated upright`() {
        assertTrue(ExifTransform.of(1).isIdentity)
        assertTrue(ExifTransform.of(0).isIdentity) // undefined
        assertEquals(ExifTransform(90, false), ExifTransform.of(6))
        assertEquals(ExifTransform(180, false), ExifTransform.of(3))
        assertEquals(ExifTransform(270, false), ExifTransform.of(8))
    }

    @Test
    fun `mirrored orientations flip as well as rotate`() {
        assertEquals(ExifTransform(0, true), ExifTransform.of(2))
        assertEquals(ExifTransform(180, true), ExifTransform.of(4))
        assertEquals(ExifTransform(270, true), ExifTransform.of(5))
        assertEquals(ExifTransform(90, true), ExifTransform.of(7))
    }

    // ---------------- Shortlist ranking ----------------

    private fun analysis(photoId: Int, score: Float, blur: Float) = PhotoAnalysisEntity(
        photoId = photoId,
        analysisVersion = PhotoAnalysisEntity.CURRENT_ANALYSIS_VERSION,
        blurScore = blur,
        faceDetected = true,
        faceCount = 1,
        faceCenterScore = 0.7f,
        eyeOpenScore = 0.9f,
        expressionScore = 0.8f,
        exposureScore = 0.7f,
        compositionScore = 0.6f,
        duplicatePenalty = 0f,
        overallScore = score,
        qualityGrade = "A",
        rejectReasonsJson = "[]",
        positiveReasonsJson = "[]",
        analyzedAtMillis = 1L
    )

    private val photos = listOf(
        analysis(1, 90f, blur = 0.2f), // top score but shaken
        analysis(2, 80f, blur = 0.9f),
        analysis(3, 70f, blur = 0.8f),
        analysis(4, 60f, blur = 0.7f)
    )

    @Test
    fun `excluding blurry photos keeps them out of the shortlist`() {
        val split = ShortlistRanking.split(photos, limit = 2, excludeBlurry = true)
        assertEquals(listOf(2, 3), split.shortlist.map { it.photoId })
        assertEquals(listOf(1, 4), split.rejected.map { it.photoId })
    }

    @Test
    fun `without the toggle the shaken photo can still rank first`() {
        val split = ShortlistRanking.split(photos, limit = 2, excludeBlurry = false)
        assertEquals(listOf(1, 2), split.shortlist.map { it.photoId })
        assertEquals(listOf(3, 4), split.rejected.map { it.photoId })
    }

    @Test
    fun `shortlist and rejected never overlap and cover everything`() {
        val split = ShortlistRanking.split(photos, limit = 10, excludeBlurry = true)
        val all = (split.shortlist + split.rejected).map { it.photoId }.sorted()
        assertEquals(listOf(1, 2, 3, 4), all)
        assertEquals(listOf(1), split.rejected.map { it.photoId })
    }
}
