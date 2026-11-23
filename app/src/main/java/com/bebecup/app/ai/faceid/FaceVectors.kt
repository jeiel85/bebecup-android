package com.bebecup.app.ai.faceid

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sqrt

/** Face feature vectors: unit length, stored as little-endian float32 bytes. */
object FaceVectors {
    const val DIMENSIONS = 128

    fun normalized(v: FloatArray): FloatArray {
        var sum = 0.0
        for (x in v) sum += x * x
        val norm = sqrt(sum).toFloat()
        if (norm < 1e-12f) return v.copyOf()
        return FloatArray(v.size) { v[it] / norm }
    }

    /** Cosine similarity of two unit vectors. */
    fun cosine(a: FloatArray, b: FloatArray): Float {
        require(a.size == b.size)
        var s = 0f
        for (i in a.indices) s += a[i] * b[i]
        return s
    }

    fun toBytes(v: FloatArray): ByteArray {
        val buffer = ByteBuffer.allocate(v.size * 4).order(ByteOrder.LITTLE_ENDIAN)
        v.forEach { buffer.putFloat(it) }
        return buffer.array()
    }

    /** Null when [bytes] is not a [DIMENSIONS]-float vector (a row from another model). */
    fun fromBytes(bytes: ByteArray): FloatArray? {
        if (bytes.size != DIMENSIONS * 4) return null
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        return FloatArray(DIMENSIONS) { buffer.getFloat() }
    }
}
