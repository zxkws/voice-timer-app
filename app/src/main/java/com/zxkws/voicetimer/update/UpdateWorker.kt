package com.zxkws.voicetimer.update

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.zxkws.voicetimer.R
import com.zxkws.voicetimer.ui.MainActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class UpdateWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        runCatching {
            val info = UpdateManager.check() ?: return@withContext Result.success()
            val apk = UpdateManager.download(applicationContext, info)
            val silent = UpdateManager.trySilentInstall(applicationContext, apk)
            if (!silent) showNotification(info.versionName)
            Result.success()
        }.getOrElse { Result.retry() }
    }

    private fun showNotification(version: String) {
        val nm = applicationContext.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("updates", applicationContext.getString(R.string.notification_channel_update), NotificationManager.IMPORTANCE_DEFAULT))
        val openApp = PendingIntent.getActivity(
            applicationContext,
            0,
            Intent(applicationContext, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        nm.notify(
            2001,
            NotificationCompat.Builder(applicationContext, "updates")
                .setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setContentTitle("发现新版本 $version")
                .setContentText("更新包已下载；如未静默安装，请打开应用完成安装。")
                .setContentIntent(openApp)
                .setAutoCancel(true)
                .build()
        )
    }
}
