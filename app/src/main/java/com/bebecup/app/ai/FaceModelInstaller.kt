package com.bebecup.app.ai

import android.content.Context
import android.util.Log
import com.google.android.gms.common.moduleinstall.InstallStatusListener
import com.google.android.gms.common.moduleinstall.ModuleInstall
import com.google.android.gms.common.moduleinstall.ModuleInstallRequest
import com.google.android.gms.common.moduleinstall.ModuleInstallStatusUpdate
import com.google.android.gms.common.moduleinstall.ModuleInstallStatusUpdate.InstallState
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.face.FaceDetection
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * The face model is not inside the APK: it comes from Google Play services
 * (`play-services-mlkit-face-detection`) so the app stays small. Detection
 * still runs on the device; only the model itself is downloaded, and no photo
 * leaves the phone.
 *
 * The download is requested when onboarding finishes ([requestInBackground])
 * and awaited before the first scan ([ensureInstalled]) in case it has not
 * finished yet, the device was offline, or onboarding was skipped.
 */
class FaceModelInstaller(context: Context) {

    private val appContext = context.applicationContext
    private val client by lazy { ModuleInstall.getClient(appContext) }

    // Any detector instance names the same optional module.
    private fun faceApi() = FaceDetection.getClient()

    /** Fire-and-forget: ask Play services to start the download now. */
    fun requestInBackground() {
        try {
            val api = faceApi()
            client.installModules(ModuleInstallRequest.newBuilder().addApi(api).build())
                .addOnFailureListener { e -> Log.w(TAG, "face model request failed", e) }
                .addOnCompleteListener { api.close() }
        } catch (e: Exception) {
            // No Play services (or not yet ready): the first scan asks again.
            Log.w(TAG, "face model request failed", e)
        }
    }

    /**
     * Returns true when the face model is ready, downloading it first if
     * needed. [onProgress] gets 0..1 while bytes are known, null otherwise.
     * Returns false when it cannot be installed (no Play services, offline,
     * cancelled or timed out); callers must not analyze photos in that case.
     */
    suspend fun ensureInstalled(onProgress: (Float?) -> Unit = {}): Boolean {
        val api = faceApi()
        try {
            val available = awaitOrNull(client.areModulesAvailable(api))?.areModulesAvailable()
            if (available == true) return true

            val done = CompletableDeferred<Boolean>()
            val listener = InstallStatusListener { update: ModuleInstallStatusUpdate ->
                update.progressInfo?.let { p ->
                    onProgress(
                        if (p.totalBytesToDownload > 0) {
                            (p.bytesDownloaded.toFloat() / p.totalBytesToDownload).coerceIn(0f, 1f)
                        } else null
                    )
                }
                when (update.installState) {
                    InstallState.STATE_COMPLETED -> done.complete(true)
                    InstallState.STATE_FAILED, InstallState.STATE_CANCELED -> done.complete(false)
                    else -> Unit
                }
            }
            onProgress(null)
            val request = ModuleInstallRequest.newBuilder().addApi(api).setListener(listener).build()
            try {
                val response = awaitOrNull(client.installModules(request)) ?: return false
                if (response.areModulesAlreadyInstalled()) return true
                return withTimeoutOrNull(INSTALL_TIMEOUT_MILLIS) { done.await() } ?: false
            } finally {
                client.unregisterListener(listener)
            }
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            Log.w(TAG, "face model install failed", e)
            return false
        } finally {
            api.close()
        }
    }

    /** Task result, or null if the task failed (logged). */
    private suspend fun <T> awaitOrNull(task: Task<T>): T? =
        suspendCancellableCoroutine { cont ->
            task.addOnCompleteListener { t ->
                if (!t.isSuccessful) Log.w(TAG, "Play services task failed", t.exception)
                cont.resume(if (t.isSuccessful) t.result else null)
            }
        }

    private companion object {
        const val TAG = "FaceModelInstaller"
        const val INSTALL_TIMEOUT_MILLIS = 3 * 60_000L
    }
}
