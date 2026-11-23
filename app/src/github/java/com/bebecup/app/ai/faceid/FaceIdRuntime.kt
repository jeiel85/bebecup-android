package com.bebecup.app.ai.faceid

import android.content.Context
import java.io.File
import org.tensorflow.lite.InterpreterApi

/**
 * GitHub (standalone) build: the TensorFlow Lite interpreter (LiteRT) is inside the
 * APK, so telling the children apart also works on phones without Google Play
 * services. Only the model file is downloaded.
 */
object FaceIdRuntime {
    @Suppress("UNUSED_PARAMETER", "RedundantSuspendModifier")
    suspend fun open(context: Context, model: File): InterpreterApi {
        val options = InterpreterApi.Options()
            .setRuntime(InterpreterApi.Options.TfLiteRuntime.FROM_APPLICATION_ONLY)
            .setNumThreads(THREADS)
        return InterpreterApi.create(model, options)
    }

    private const val THREADS = 2
}
