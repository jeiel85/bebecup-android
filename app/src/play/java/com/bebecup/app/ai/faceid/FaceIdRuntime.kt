package com.bebecup.app.ai.faceid

import android.content.Context
import com.google.android.gms.tasks.Task
import com.google.android.gms.tflite.java.TfLite
import java.io.File
import java.io.IOException
import kotlinx.coroutines.suspendCancellableCoroutine
import org.tensorflow.lite.InterpreterApi
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Play build: TensorFlow Lite comes from Google Play services, so the app carries
 * only the small Java API. Throws when Play services cannot provide it.
 */
object FaceIdRuntime {
    suspend fun open(context: Context, model: File): InterpreterApi {
        await(TfLite.initialize(context.applicationContext))
        val options = InterpreterApi.Options()
            .setRuntime(InterpreterApi.Options.TfLiteRuntime.FROM_SYSTEM_ONLY)
            .setNumThreads(THREADS)
        return InterpreterApi.create(model, options)
    }

    private const val THREADS = 2

    private suspend fun await(task: Task<Void>) = suspendCancellableCoroutine { cont ->
        task.addOnCompleteListener { t ->
            if (t.isSuccessful) cont.resume(Unit)
            else cont.resumeWithException(IOException("TensorFlow Lite from Play services is unavailable", t.exception))
        }
    }
}
