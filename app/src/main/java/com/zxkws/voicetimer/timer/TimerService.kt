package com.zxkws.voicetimer.timer

import android.app.*
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.os.Build
import android.os.CountDownTimer
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.core.app.NotificationCompat
import com.zxkws.voicetimer.R
import com.zxkws.voicetimer.ui.MainActivity

class TimerService : Service() {
    private var timer: CountDownTimer? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_CANCEL) {
            timer?.cancel()
            stopSelf()
            sendState(0, false)
            return START_NOT_STICKY
        }
        val duration = intent?.getLongExtra(EXTRA_DURATION, 0L) ?: 0L
        if (duration <= 0) return START_NOT_STICKY

        startForeground(NOTIFICATION_ID, notification(duration))
        timer?.cancel()
        timer = object : CountDownTimer(duration, 250) {
            override fun onTick(ms: Long) {
                sendState(ms, true)
                getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification(ms))
            }

            override fun onFinish() {
                sendState(0, false)
                ring()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }.start()
        return START_NOT_STICKY
    }

    private fun notification(ms: Long): Notification {
        val openIntent = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val cancelIntent = PendingIntent.getService(this, 1, Intent(this, TimerService::class.java).setAction(ACTION_CANCEL), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("语音计时")
            .setContentText("剩余 ${format(ms)}")
            .setContentIntent(openIntent)
            .setOngoing(true)
            .addAction(0, "取消", cancelIntent)
            .build()
    }

    private fun ring() {
        RingtoneManager.getRingtone(this, RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION))?.play()
        val vibrator = getSystemService(Vibrator::class.java)
        if (Build.VERSION.SDK_INT >= 26) vibrator.vibrate(VibrationEffect.createOneShot(350, VibrationEffect.DEFAULT_AMPLITUDE))
        else @Suppress("DEPRECATION") vibrator.vibrate(350)
        getSystemService(NotificationManager::class.java).notify(
            FINISH_NOTIFICATION_ID,
            NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle("时间到")
                .setContentText("叮！计时结束")
                .setAutoCancel(true)
                .build()
        )
    }

    private fun sendState(ms: Long, running: Boolean) {
        sendBroadcast(Intent(ACTION_STATE).apply {
            setPackage(packageName)
            putExtra(EXTRA_REMAINING, ms)
            putExtra(EXTRA_RUNNING, running)
        })
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(CHANNEL_ID, getString(R.string.notification_channel_timer), NotificationManager.IMPORTANCE_LOW)
            )
        }
    }

    private fun format(ms: Long): String {
        val sec = (ms + 999) / 1000
        return "%02d:%02d".format(sec / 60, sec % 60)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val ACTION_STATE = "com.zxkws.voicetimer.TIMER_STATE"
        const val ACTION_CANCEL = "com.zxkws.voicetimer.CANCEL_TIMER"
        const val EXTRA_DURATION = "duration"
        const val EXTRA_REMAINING = "remaining"
        const val EXTRA_RUNNING = "running"
        private const val CHANNEL_ID = "timer"
        private const val NOTIFICATION_ID = 1001
        private const val FINISH_NOTIFICATION_ID = 1002

        fun start(context: Context, durationMs: Long) {
            context.startForegroundService(Intent(context, TimerService::class.java).putExtra(EXTRA_DURATION, durationMs))
        }

        fun cancel(context: Context) {
            context.startService(Intent(context, TimerService::class.java).setAction(ACTION_CANCEL))
        }
    }
}
