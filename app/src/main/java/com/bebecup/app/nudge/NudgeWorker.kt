package com.bebecup.app.nudge

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.bebecup.app.MainActivity
import com.bebecup.app.R
import com.bebecup.app.data.media.MediaStorePhotoSource
import com.bebecup.app.data.media.PhotoAccess
import com.bebecup.app.data.prefs.BebecupPrefs
import com.bebecup.app.domain.nudge.NudgePolicy
import java.util.concurrent.TimeUnit

/** Counts gallery photos added since the last saved round (screenshots excluded). */
class UnsortedPhotoCounter(
    private val mediaSource: MediaStorePhotoSource,
    private val prefs: BebecupPrefs
) {
    suspend fun count(nowMillis: Long = System.currentTimeMillis()): Int {
        val start = NudgePolicy.unsortedWindowStart(prefs.lastReviewedAtMillis, nowMillis)
        return mediaSource.queryRecent(start, nowMillis, includeScreenshots = false).size
    }
}

/**
 * Daily background check (opt-in): if enough unsorted baby photos piled up,
 * post one gentle notification. Reads only MediaStore metadata — no photo is
 * decoded or sent anywhere.
 */
class NudgeWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val prefs = BebecupPrefs(applicationContext)
        if (!prefs.nudgeEnabled) return Result.success()
        if (!PhotoAccess.canReadPhotos(applicationContext)) return Result.success()
        if (!NudgeNotifier.canPost(applicationContext)) return Result.success()

        val now = System.currentTimeMillis()
        val unsorted = try {
            UnsortedPhotoCounter(MediaStorePhotoSource(applicationContext), prefs).count(now)
        } catch (e: SecurityException) {
            Log.w(TAG, "gallery access revoked; skipping nudge", e)
            return Result.success()
        }
        if (NudgePolicy.shouldNudge(prefs.nudgeEnabled, unsorted, prefs.lastNudgeAtMillis, now)) {
            NudgeNotifier.post(applicationContext, unsorted)
            prefs.lastNudgeAtMillis = now
        }
        return Result.success()
    }

    companion object {
        private const val TAG = "NudgeWorker"
    }
}

object NudgeScheduler {
    private const val WORK_NAME = "bebecup_unsorted_nudge"

    fun enable(context: Context) {
        val request = PeriodicWorkRequestBuilder<NudgeWorker>(1, TimeUnit.DAYS).build()
        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    fun disable(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
    }
}

object NudgeNotifier {
    private const val CHANNEL_ID = "unsorted_photos"
    private const val NOTIFICATION_ID = 1001
    const val EXTRA_OPEN_CURATION = "com.bebecup.app.OPEN_CURATION"

    fun canPost(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) return false
        return NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    fun post(context: Context, unsortedCount: Int) {
        ensureChannel(context)
        val open = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_OPEN_CURATION, true)
        }
        val pending = PendingIntent.getActivity(
            context, 0, open,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_bebecup)
            .setContentTitle(context.getString(R.string.nudge_title, unsortedCount))
            .setContentText(context.getString(R.string.nudge_text))
            .setStyle(NotificationCompat.BigTextStyle().bigText(context.getString(R.string.nudge_text)))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        } catch (e: SecurityException) {
            // Permission revoked between canPost() and notify(); nothing to show.
            Log.w("NudgeNotifier", "notification permission revoked", e)
        }
    }

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.nudge_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply { description = context.getString(R.string.nudge_channel_desc) }
        )
    }
}
