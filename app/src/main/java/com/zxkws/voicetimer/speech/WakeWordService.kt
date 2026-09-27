package com.zxkws.voicetimer.speech

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.ToneGenerator
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.zxkws.voicetimer.R
import com.zxkws.voicetimer.ui.MainActivity
import java.util.concurrent.atomic.AtomicBoolean

class WakeWordService : Service() {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val capturing = AtomicBoolean(false)
    private var captureThread: Thread? = null
    private var recorder: AudioRecord? = null
    private var spotter: WakeWordSpotter? = null
    private var recognizer: SpeechRecognizer? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
        isRunning = true
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        startForeground(NOTIFICATION_ID, notification("等待唤醒词：计时助手"))
        startCapture()
        return START_STICKY
    }

    private fun startCapture() {
        if (!capturing.compareAndSet(false, true)) return
        captureThread = Thread({ captureLoop() }, "voice-timer-kws").apply { start() }
    }

    private fun captureLoop() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            mainHandler.post {
                updateNotification("麦克风权限已关闭")
                stopSelf()
            }
            capturing.set(false)
            return
        }
        val localSpotter = try {
            spotter ?: WakeWordSpotter(assets).also { spotter = it }
        } catch (error: Throwable) {
            mainHandler.post {
                updateNotification("唤醒模型加载失败")
                stopSelf()
            }
            capturing.set(false)
            return
        }
        val minBuffer = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        val localRecorder = AudioRecord(
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            maxOf(minBuffer, SAMPLE_RATE),
        )
        recorder = localRecorder
        val buffer = ShortArray(1_600)
        try {
            localRecorder.startRecording()
            while (capturing.get()) {
                val count = localRecorder.read(buffer, 0, buffer.size)
                if (count > 0 && localSpotter.accept(buffer, count)) {
                    capturing.set(false)
                    mainHandler.post { onWakeDetected() }
                    break
                }
            }
        } finally {
            runCatching { localRecorder.stop() }
            localRecorder.release()
            recorder = null
        }
    }

    private fun onWakeDetected() {
        ToneGenerator(AudioManager.STREAM_NOTIFICATION, 70).apply {
            startTone(ToneGenerator.TONE_PROP_BEEP, 120)
            mainHandler.postDelayed({ release() }, 200)
        }
        updateNotification("已唤醒，请说计时命令")
        mainHandler.postDelayed({ startRecognition() }, 250)
    }

    private fun startRecognition() {
        recognizer?.destroy()
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            resumeWakeListening("系统语音识别不可用")
            return
        }
        recognizer = SpeechRecognizer.createSpeechRecognizer(this).also { speech ->
            speech.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) = Unit
                override fun onBeginningOfSpeech() = Unit
                override fun onRmsChanged(rmsdB: Float) = Unit
                override fun onBufferReceived(buffer: ByteArray?) = Unit
                override fun onEndOfSpeech() = Unit
                override fun onPartialResults(partialResults: Bundle?) = Unit
                override fun onEvent(eventType: Int, params: Bundle?) = Unit
                override fun onError(error: Int) = resumeWakeListening("没听清")

                override fun onResults(results: Bundle?) {
                    val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull().orEmpty()
                    val status = CommandExecutor.execute(this@WakeWordService, SemanticParser.parse(text))
                    resumeWakeListening(status)
                }
            })
            speech.startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "zh-CN")
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
            })
        }
    }

    private fun resumeWakeListening(status: String) {
        recognizer?.destroy()
        recognizer = null
        spotter?.reset()
        updateNotification("$status · 等待：计时助手")
        mainHandler.postDelayed({ startCapture() }, 500)
    }

    private fun notification(text: String): Notification {
        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val stop = PendingIntent.getService(
            this,
            1,
            Intent(this, WakeWordService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_app)
            .setContentTitle("语音计时 · 后台唤醒已开启")
            .setContentText(text)
            .setContentIntent(open)
            .setOngoing(true)
            .addAction(0, "关闭唤醒", stop)
            .build()
    }

    private fun updateNotification(text: String) {
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification(text))
    }

    private fun createChannel() {
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel_wake),
                NotificationManager.IMPORTANCE_LOW,
            ),
        )
    }

    override fun onDestroy() {
        isRunning = false
        capturing.set(false)
        runCatching { recorder?.stop() }
        captureThread?.interrupt()
        runCatching { captureThread?.join(500) }
        recognizer?.destroy()
        spotter?.close()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        @Volatile var isRunning = false
            private set

        private const val ACTION_STOP = "com.zxkws.voicetimer.STOP_WAKE"
        private const val CHANNEL_ID = "wake_word"
        private const val NOTIFICATION_ID = 3001
        private const val SAMPLE_RATE = 16_000

        fun start(context: Context) {
            context.startForegroundService(Intent(context, WakeWordService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, WakeWordService::class.java))
        }
    }
}
