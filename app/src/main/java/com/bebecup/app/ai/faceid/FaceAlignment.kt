package com.bebecup.app.ai.faceid

/** A point in image pixels. */
data class FacePoint(val x: Float, val y: Float)

/**
 * The similarity transform (rotation, uniform scale, translation; no mirroring)
 * u = a·x − b·y + tx, v = b·x + a·y + ty.
 */
data class Similarity(val a: Float, val b: Float, val tx: Float, val ty: Float) {
    fun map(p: FacePoint) = FacePoint(a * p.x - b * p.y + tx, b * p.x + a * p.y + ty)

    /** Row-major 3×3, as android.graphics.Matrix.setValues takes it. */
    fun matrixValues(): FloatArray = floatArrayOf(a, -b, tx, b, a, ty, 0f, 0f, 1f)
}

/**
 * Lines a face up the way SFace was trained (the ArcFace 112×112 five-point
 * template). Nose base from ML Kit sits lower than the template's nose tip, so
 * only the eyes and mouth corners are fitted.
 */
object FaceAlignment {
    const val SIZE = 112

    /** Template points as they appear in an upright image: left/right are the viewer's. */
    val EYE_IMAGE_LEFT = FacePoint(38.2946f, 51.6963f)
    val EYE_IMAGE_RIGHT = FacePoint(73.5318f, 51.5014f)
    val MOUTH_IMAGE_LEFT = FacePoint(41.5493f, 92.3655f)
    val MOUTH_IMAGE_RIGHT = FacePoint(70.7299f, 92.2041f)

    /**
     * Least-squares similarity taking [src] onto [dst] (same length, at least two
     * distinct points). Null when the source points coincide.
     */
    fun similarity(src: List<FacePoint>, dst: List<FacePoint>): Similarity? {
        require(src.size == dst.size && src.size >= 2)
        val n = src.size
        val mx = src.sumOf { it.x.toDouble() } / n
        val my = src.sumOf { it.y.toDouble() } / n
        val mu = dst.sumOf { it.x.toDouble() } / n
        val mv = dst.sumOf { it.y.toDouble() } / n
        var norm = 0.0
        var sa = 0.0
        var sb = 0.0
        for (i in 0 until n) {
            val x = src[i].x - mx
            val y = src[i].y - my
            val u = dst[i].x - mu
            val v = dst[i].y - mv
            norm += x * x + y * y
            sa += x * u + y * v
            sb += x * v - y * u
        }
        if (norm < 1e-6) return null
        val a = sa / norm
        val b = sb / norm
        return Similarity(
            a = a.toFloat(),
            b = b.toFloat(),
            tx = (mu - (a * mx - b * my)).toFloat(),
            ty = (mv - (b * mx + a * my)).toFloat()
        )
    }

    /**
     * Transform for a face from its two eyes and two mouth corners, in any order.
     * Which one is on the viewer's left is worked out from the face itself (the
     * direction from the eyes down to the mouth), so it holds for tilted or
     * sideways photos and does not depend on how the detector names them.
     */
    fun forFace(eye1: FacePoint, eye2: FacePoint, mouth1: FacePoint, mouth2: FacePoint): Similarity? {
        // "Down" in the face: from between the eyes to between the mouth corners.
        val downX = (mouth1.x + mouth2.x - eye1.x - eye2.x) / 2f
        val downY = (mouth1.y + mouth2.y - eye1.y - eye2.y) / 2f
        // The viewer's right, for that "down" (image y grows downward): (0,1) → (1,0).
        fun rightness(p: FacePoint) = p.x * downY - p.y * downX
        val (eyeLeft, eyeRight) = if (rightness(eye1) <= rightness(eye2)) eye1 to eye2 else eye2 to eye1
        val (mouthLeft, mouthRight) = if (rightness(mouth1) <= rightness(mouth2)) mouth1 to mouth2 else mouth2 to mouth1
        return similarity(
            listOf(eyeLeft, eyeRight, mouthLeft, mouthRight),
            listOf(EYE_IMAGE_LEFT, EYE_IMAGE_RIGHT, MOUTH_IMAGE_LEFT, MOUTH_IMAGE_RIGHT)
        )
    }
}
