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
import com.bebecup.app.data.AppDatabase
import com.bebecup.app.data.media.MediaStorePhotoSource
import com.bebecup.app.data.media.PhotoAccess
import com.bebecup.app.data.prefs.BebecupPrefs
import com.bebecup.app.domain.monthly.MonthlyCup
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
 * Daily background check (opt-in). At the start of a month it announces last
 * month's 이달의 월드컵 once; otherwise, if enough unsorted baby photos piled
 * up, it posts one gentle notification. Reads only the app's own database and
 * MediaStore metadata — no photo is decoded or sent anywhere.
 */
class NudgeWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val prefs = BebecupPrefs(applicationContext)
        if (!prefs.nudgeEnabled) return Result.success()
        if (!NudgeNotifier.canPost(applicationContext)) return Result.success()
        // One reminder a day at most: the monthly cup wins on the day it is due.
        if (remindMonthlyCup(prefs)) return Result.success()
        // Partial ("선택한 사진만") access can't see newly taken photos, so the
        // count would be meaningless; only full access drives this reminder.
        if (!PhotoAccess.hasFullAccess(applicationContext)) return Result.success()

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

    /** Announce last month's cup once when it is due; true if a notification was posted. */
    private suspend fun remindMonthlyCup(prefs: BebecupPrefs): Boolean {
        val db = AppDatabase.getDatabase(applicationContext)
        val dao = db.babyPhotoDao()
        val childrenDao = db.childrenDao()
        val today = MonthlyCup.today()
        val photoChildren = childrenDao.getPhotoChildren().groupBy({ it.photoId }, { it.childId })
        val children = childrenDao.getChildren()
        val due = MonthlyCup.pending(
            today = today,
            childIds = children.map { it.id },
            photos = dao.getBestTakenDates().map { MonthlyCup.Photo(it.takenDate, photoChildren[it.photoId].orEmpty().toSet()) },
            held = dao.getMonthlyCups().map { MonthlyCup.Held(it.monthKey, it.childId) },
            skippedKey = prefs.monthlyCupSkippedMonth
        ) ?: return false
        // Once a month: with siblings the home card then offers each child's cup in turn.
        if (prefs.monthlyCupNotifiedMonth == due.month.key) return false
        val child = children.firstOrNull { it.id == due.childId }
        NudgeNotifier.postMonthlyCup(applicationContext, MonthlyCup.label(due.month.key, today.year), due.month, child?.id, child?.name)
        prefs.monthlyCupNotifiedMonth = due.month.key
        return true
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
    private const val MONTHLY_CHANNEL_ID = "monthly_cup"
    private const val MONTHLY_NOTIFICATION_ID = 1002
    const val EXTRA_OPEN_CURATION = "com.bebecup.app.OPEN_CURATION"
    /** "yyyy-MM" of the 이달의 월드컵 to open. */
    const val EXTRA_OPEN_MONTHLY_CUP = "com.bebecup.app.OPEN_MONTHLY_CUP"
    /** With [EXTRA_OPEN_MONTHLY_CUP]: whose cup it is (a child's id), when the family has siblings. */
    const val EXTRA_MONTHLY_CUP_CHILD = "com.bebecup.app.MONTHLY_CUP_CHILD"

    fun canPost(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) return false
        return NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    fun post(context: Context, unsortedCount: Int) {
        ensureChannel(context, CHANNEL_ID, R.string.nudge_channel_name, R.string.nudge_channel_desc)
        val open = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_OPEN_CURATION, true)
        }
        notify(
            context, CHANNEL_ID, NOTIFICATION_ID, open,
            context.getString(R.string.nudge_title, unsortedCount),
            context.getString(R.string.nudge_text)
        )
    }

    /** "10월의 월드컵을 열 때예요" — once per month, tapping opens that month's cup. */
    /** [childId]/[childName]: the child whose cup it is; null for the family's one cup. */
    fun postMonthlyCup(context: Context, monthLabel: String, month: MonthlyCup.Month, childId: Long? = null, childName: String? = null) {
        ensureChannel(context, MONTHLY_CHANNEL_ID, R.string.monthly_cup_channel_name, R.string.monthly_cup_channel_desc)
        val open = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_OPEN_MONTHLY_CUP, month.key)
            if (childId != null) putExtra(EXTRA_MONTHLY_CUP_CHILD, childId)
        }
        notify(
            context, MONTHLY_CHANNEL_ID, MONTHLY_NOTIFICATION_ID, open,
            if (childName == null) context.getString(R.string.monthly_cup_title, monthLabel)
            else context.getString(R.string.monthly_cup_child_title, monthLabel, childName),
            if (childName == null) context.getString(R.string.monthly_cup_text, monthLabel, month.photoCount)
            else context.getString(R.string.monthly_cup_child_text, monthLabel, childName, month.photoCount)
        )
    }

    private fun notify(context: Context, channelId: String, id: Int, open: Intent, title: String, text: String) {
        // The request code is the notification id so the two reminders' intents
        // (which differ only in extras) don't overwrite each other.
        val pending = PendingIntent.getActivity(
            context, id, open,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_stat_bebecup)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(id, notification)
        } catch (e: SecurityException) {
            // Permission revoked between canPost() and notify(); nothing to show.
            Log.w("NudgeNotifier", "notification permission revoked", e)
        }
    }

    private fun ensureChannel(context: Context, id: String, nameRes: Int, descRes: Int) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(id) != null) return
        manager.createNotificationChannel(
            NotificationChannel(id, context.getString(nameRes), NotificationManager.IMPORTANCE_DEFAULT)
                .apply { description = context.getString(descRes) }
        )
    }
}
