package com.bebecup.app.update

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import com.bebecup.app.BuildConfig
import com.google.android.play.core.appupdate.AppUpdateInfo
import com.google.android.play.core.appupdate.AppUpdateManager
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.appupdate.testing.FakeAppUpdateManager
import com.google.android.play.core.install.model.UpdateAvailability
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

/**
 * "Is there a newer version on Google Play?" Testers often have Play
 * auto-update off, so home shows a banner that opens the Play listing.
 *
 * The Play In-App Updates API asks the Google Play app on the phone, which
 * knows every track open to this user (closed testing included). Play does the
 * installing; this app never downloads or installs anything itself. A copy not
 * installed from Play (the GitHub APK, a debug build) gets no answer, so no
 * banner.
 */
object StoreUpdates {

    private const val TAG = "StoreUpdates"
    private const val TIMEOUT_MILLIS = 15_000L

    private val _available = MutableStateFlow<Int?>(null)

    /** The newer versionCode Play offers, or null. */
    val available: StateFlow<Int?> = _available.asStateFlow()

    @Volatile private var lastCheckedAt = 0L

    /** Debug builds only ([useFakeUpdate]): the versionCode to pretend Play offers, or 0. */
    @Volatile private var fakeVersionCode = 0

    /**
     * Asks Play again when the last answer is older than [StoreUpdateDecision.RECHECK_MILLIS].
     * Only a definite answer changes [available]; a failed check keeps what we knew.
     */
    suspend fun checkIfStale(context: Context) {
        if (!StoreUpdateDecision.isStale(lastCheckedAt, System.currentTimeMillis())) return
        val answer = withTimeoutOrNull(TIMEOUT_MILLIS) { query(context.applicationContext) } ?: return
        _available.value = answer.takeIf { it > 0 }
        lastCheckedAt = System.currentTimeMillis()
    }

    /** The offered versionCode, 0 when there is none, or null when Play could not answer. */
    private suspend fun query(context: Context): Int? = try {
        val info = manager(context).requestInfo()
        StoreUpdateDecision.offeredVersionCode(
            updateAvailable = info.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE,
            availableVersionCode = info.availableVersionCode(),
            installedVersionCode = BuildConfig.VERSION_CODE,
        )
    } catch (e: CancellationException) {
        throw e // the timeout above, or the screen went away
    } catch (e: Exception) {
        // Not installed from Play, no Play Store, offline: nothing to show.
        Log.i(TAG, "Play could not check for updates: $e")
        null
    }

    private fun manager(context: Context): AppUpdateManager =
        if (BuildConfig.DEBUG && fakeVersionCode > 0) {
            FakeAppUpdateManager(context).apply { setUpdateAvailable(fakeVersionCode) }
        } else {
            AppUpdateManagerFactory.create(context)
        }

    private suspend fun AppUpdateManager.requestInfo(): AppUpdateInfo =
        suspendCancellableCoroutine { cont ->
            appUpdateInfo
                .addOnSuccessListener { cont.resume(it) }
                .addOnFailureListener { e -> cont.resumeWith(Result.failure(e)) }
        }

    /** Debug builds only: pretend Play offers [versionCode], to see the banner. Lasts for this process. */
    fun useFakeUpdate(versionCode: Int) {
        if (!BuildConfig.DEBUG) return
        fakeVersionCode = versionCode
        lastCheckedAt = 0L
    }

    /** The Play Store app's page for this app, or the web listing when there is no Play Store app. */
    fun openStore(context: Context) {
        val packageName = context.packageName
        try {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$packageName"))
                    .setPackage("com.android.vending")
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (_: ActivityNotFoundException) {
            try {
                context.startActivity(
                    Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$packageName"))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            } catch (e: ActivityNotFoundException) {
                Log.w(TAG, "no app can open the Play listing", e)
            }
        }
    }
}

/** The update rules, free of Android and Play calls so tests can pin them. */
object StoreUpdateDecision {

    /** Coming back to the app re-asks Play only after this long. */
    val RECHECK_MILLIS: Long = TimeUnit.HOURS.toMillis(6)

    fun isStale(lastCheckedAt: Long, now: Long): Boolean =
        lastCheckedAt <= 0L || lastCheckedAt > now || now - lastCheckedAt >= RECHECK_MILLIS

    /**
     * The versionCode to offer, or 0. Only a strictly higher versionCode counts,
     * which also guards against a local build newer than the store's. An update
     * Play is already installing is not "available" and so not offered again.
     */
    fun offeredVersionCode(updateAvailable: Boolean, availableVersionCode: Int, installedVersionCode: Int): Int =
        if (updateAvailable && availableVersionCode > installedVersionCode) availableVersionCode else 0
}
