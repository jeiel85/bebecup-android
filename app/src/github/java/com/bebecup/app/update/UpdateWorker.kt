package com.bebecup.app.update

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.bebecup.app.MainActivity
import com.bebecup.app.R
import java.util.concurrent.TimeUnit

/**
 * Twice a day on any network: check the latest GitHub Release, and either install it
 * without asking (app not in use, Android 12+) or post one notification ([GithubUpdates.backgroundCheck]).
 */
class UpdateWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        if (!AutoUpdatePrefs(applicationContext).backgroundCheck) return Result.success()
        try {
            GithubUpdates.backgroundCheck(applicationContext) { isStopped }
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            // A background check that fails has nothing to tell the parent; the next period tries again.
            Log.w(TAG, "background update check failed", e)
        }
        return Result.success()
    }

    private companion object {
        const val TAG = "UpdateWorker"
    }
}

/** Keeps [UpdateWorker] scheduled to match 설정 "새 버전 확인". */
object UpdateScheduler {
    private const val WORK_NAME = "bebecup_github_update"

    /** Idempotent: KEEP leaves an existing schedule alone, so opening the app often doesn't postpone it. */
    fun sync(context: Context) {
        val workManager = WorkManager.getInstance(context)
        if (!AutoUpdatePrefs(context).backgroundCheck) {
            workManager.cancelUniqueWork(WORK_NAME)
            UpdateNotifier.cancel(context)
            return
        }
        val request = PeriodicWorkRequestBuilder<UpdateWorker>(12, TimeUnit.HOURS, 1, TimeUnit.HOURS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setInitialDelay(1, TimeUnit.HOURS)
            .build()
        workManager.enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
    }
}

/** "새 버전이 나왔어요" and, after an install, "업데이트했어요". One id, so the second replaces the first. */
object UpdateNotifier {
    private const val CHANNEL_ID = "app_update"
    private const val NOTIFICATION_ID = 1003

    /** Posted only when the parent will actually see it (permission, app and channel notifications on). */
    fun canShow(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) return false
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return false
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return true
        val manager = context.getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "앱 업데이트", NotificationManager.IMPORTANCE_DEFAULT)
                    .apply { description = "GitHub에 베베컵 새 버전이 나왔거나 설치했을 때 한 번 알려요" }
            )
        }
        // Android drops posts to a channel the parent turned off without an error; don't count those as shown.
        return manager.getNotificationChannel(CHANNEL_ID)?.importance != NotificationManager.IMPORTANCE_NONE
    }

    /** True when posted. */
    fun showAvailable(context: Context, versionName: String?): Boolean = post(
        context,
        title = versionName?.let { "베베컵 ${VersionParticles.subject(it)} 나왔어요" } ?: "베베컵 새 버전이 나왔어요",
        text = "눌러서 열고 홈의 알림에서 설치하세요.",
        silent = false,
    )

    fun showUpdated(context: Context, versionName: String): Boolean = post(
        context,
        title = "베베컵 ${VersionParticles.direction(versionName)} 업데이트했어요",
        text = "눌러서 열어 보세요.",
        silent = true,
    )

    fun cancel(context: Context) = NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)

    private fun post(context: Context, title: String, text: String, silent: Boolean): Boolean {
        if (!canShow(context)) return false
        val open = PendingIntent.getActivity(
            context,
            NOTIFICATION_ID,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_bebecup)
            .setContentTitle(title)
            .setContentText(text)
            .setPriority(if (silent) NotificationCompat.PRIORITY_LOW else NotificationCompat.PRIORITY_DEFAULT)
            .setSilent(silent)
            .setOnlyAlertOnce(true)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        return try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
            true
        } catch (e: SecurityException) {
            // Permission revoked between canShow() and notify().
            Log.w("UpdateNotifier", "notification permission revoked", e)
            false
        }
    }
}

/**
 * Results of the install sessions [GithubUpdates] commits, and "this app was just replaced"
 * (`MY_PACKAGE_REPLACED`, delivered to the new version's process). Not exported.
 */
class UpdateInstallReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_MY_PACKAGE_REPLACED -> GithubUpdates.onReplaced(context)
            GithubUpdates.ACTION_INSTALL_STATUS -> {
                val versionCode = intent.getIntExtra(GithubUpdates.EXTRA_VERSION_CODE, 0)
                val silent = intent.getBooleanExtra(GithubUpdates.EXTRA_SILENT, false)
                val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
                val confirm = if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
                    IntentCompat.getParcelableExtra(intent, Intent.EXTRA_INTENT, Intent::class.java)
                } else null
                GithubUpdates.onInstallStatus(
                    context,
                    versionCode = versionCode,
                    silent = silent,
                    status = status,
                    sessionId = intent.getIntExtra(PackageInstaller.EXTRA_SESSION_ID, -1),
                    confirm = confirm,
                    message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE),
                )
            }
        }
    }
}

/** `PackageInstaller.STATUS_*` failure codes in words, for 설정 and the log. */
internal fun installStatusText(status: Int): String = when (status) {
    PackageInstaller.STATUS_FAILURE_BLOCKED -> "다른 앱이나 기기 정책이 막았어요"
    PackageInstaller.STATUS_FAILURE_INVALID -> "받은 파일이 올바른 APK가 아니에요"
    PackageInstaller.STATUS_FAILURE_CONFLICT -> "설치된 베베컵과 서명이 달라요"
    PackageInstaller.STATUS_FAILURE_STORAGE -> "저장 공간이 부족해요"
    PackageInstaller.STATUS_FAILURE_INCOMPATIBLE -> "이 기기와 호환되지 않아요"
    else -> "설치하지 못했어요"
}
