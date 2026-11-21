package com.bebecup.app.ai

import android.content.Context

/**
 * GitHub (standalone) build: the face model is bundled in the APK
 * (`com.google.mlkit:face-detection`), so there is nothing to download and the
 * app works on phones without Google Play services. Same API as the Play
 * build's installer, which fetches the model from Play services instead.
 */
@Suppress("UNUSED_PARAMETER")
class FaceModelInstaller(context: Context) {

    fun requestInBackground() = Unit

    suspend fun ensureInstalled(onProgress: (Float?) -> Unit = {}): Boolean = true
}
