package com.zxkws.voicetimer.ui

import android.Manifest
import android.content.*
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.work.*
import com.zxkws.voicetimer.BuildConfig
import com.zxkws.voicetimer.databinding.ActivityMainBinding
import com.zxkws.voicetimer.speech.CommandExecutor
import com.zxkws.voicetimer.speech.SemanticParser
import com.zxkws.voicetimer.speech.VoiceCommand
import com.zxkws.voicetimer.speech.WakeWordService
import com.zxkws.voicetimer.timer.TimerService
import com.zxkws.voicetimer.update.UpdateManager
import com.zxkws.voicetimer.update.UpdateWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private var recognizer: SpeechRecognizer? = null
    private var pendingWakeStart = false
    private var restartWakeAfterManual = false

    private val micPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) {
            pendingWakeStart = false
            binding.statusText.text = "需要麦克风权限"
        } else if (pendingWakeStart) {
            pendingWakeStart = false
            startWakeService()
        } else {
            listen()
        }
    }
    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    private val timerReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val remaining = intent?.getLongExtra(TimerService.EXTRA_REMAINING, 0L) ?: 0L
            val running = intent?.getBooleanExtra(TimerService.EXTRA_RUNNING, false) ?: false
            binding.timeText.text = format(remaining)
            binding.statusText.text = if (running) "正在计时" else "准备好了"
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.versionText.text = getString(com.zxkws.voicetimer.R.string.version_format, BuildConfig.VERSION_NAME)
        binding.micButton.setOnClickListener { ensureMicAndListen() }
        binding.cancelButton.setOnClickListener { TimerService.cancel(this) }
        binding.wakeButton.setOnClickListener {
            if (WakeWordService.isRunning) {
                WakeWordService.stop(this)
                binding.wakeButton.setText(com.zxkws.voicetimer.R.string.wake_enable)
            } else {
                ensureMicAndStartWake()
            }
        }
        requestNotifications()
        setupUpdateChecks()
        checkUpdateNow()
    }

    override fun onStart() {
        super.onStart()
        ContextCompat.registerReceiver(this, timerReceiver, IntentFilter(TimerService.ACTION_STATE), ContextCompat.RECEIVER_NOT_EXPORTED)
        binding.wakeButton.setText(
            if (WakeWordService.isRunning) com.zxkws.voicetimer.R.string.wake_disable
            else com.zxkws.voicetimer.R.string.wake_enable,
        )
    }

    override fun onStop() {
        unregisterReceiver(timerReceiver)
        super.onStop()
    }

    private fun ensureMicAndListen() {
        pendingWakeStart = false
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            micPermission.launch(Manifest.permission.RECORD_AUDIO)
            return
        }
        if (WakeWordService.isRunning) {
            restartWakeAfterManual = true
            WakeWordService.stop(this)
            binding.root.postDelayed({ listen() }, 300)
        } else {
            listen()
        }
    }

    private fun ensureMicAndStartWake() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            startWakeService()
        } else {
            pendingWakeStart = true
            micPermission.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    private fun startWakeService() {
        WakeWordService.start(this)
        binding.wakeButton.setText(com.zxkws.voicetimer.R.string.wake_disable)
        binding.statusText.text = "后台唤醒已开启"
    }

    private fun listen() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            binding.statusText.text = "系统语音识别不可用"
            return
        }
        recognizer?.destroy()
        recognizer = SpeechRecognizer.createSpeechRecognizer(this).also { sr ->
            sr.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) { binding.statusText.text = "请说…" }
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() { binding.statusText.text = "正在解析…" }
                override fun onError(error: Int) {
                    binding.statusText.text = "没听清，再试一次"
                    resumeWakeAfterManual()
                }
                override fun onPartialResults(partialResults: Bundle?) {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
                override fun onResults(results: Bundle?) {
                    val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                    binding.heardText.text = if (text.isBlank()) "没有识别到内容" else getString(com.zxkws.voicetimer.R.string.heard_format, text)
                    handleCommand(SemanticParser.parse(text))
                    resumeWakeAfterManual()
                }
            })
            sr.startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "zh-CN")
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
            })
        }
    }

    private fun handleCommand(command: VoiceCommand) {
        binding.statusText.text = CommandExecutor.execute(this, command)
    }

    private fun resumeWakeAfterManual() {
        if (!restartWakeAfterManual) return
        restartWakeAfterManual = false
        binding.root.postDelayed({
            if (!isFinishing && !isDestroyed) startWakeService()
        }, 300)
    }

    private fun setupUpdateChecks() {
        val request = PeriodicWorkRequestBuilder<UpdateWorker>(12, TimeUnit.HOURS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork("update-check", ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    private fun checkUpdateNow() {
        lifecycleScope.launch {
            val info = withContext(Dispatchers.IO) { runCatching { UpdateManager.check() }.getOrNull() } ?: return@launch
            binding.statusText.text = getString(com.zxkws.voicetimer.R.string.update_found_format, info.versionName)
            val apk = withContext(Dispatchers.IO) {
                runCatching { UpdateManager.download(this@MainActivity, info) }.getOrNull()
            } ?: return@launch
            runCatching { UpdateManager.install(this@MainActivity, apk) }
        }
    }

    private fun requestNotifications() {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun format(ms: Long): String {
        val sec = (ms + 999) / 1000
        return "%02d:%02d".format(sec / 60, sec % 60)
    }

    override fun onDestroy() {
        recognizer?.destroy()
        super.onDestroy()
    }
}
