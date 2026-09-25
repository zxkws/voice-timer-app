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
import com.zxkws.voicetimer.speech.SemanticParser
import com.zxkws.voicetimer.speech.VoiceCommand
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
    private val prefs by lazy { getSharedPreferences("voice_timer", MODE_PRIVATE) }
    private var lastDuration: Long
        get() = prefs.getLong("last_duration", 0L)
        set(value) { prefs.edit().putLong("last_duration", value).apply() }

    private val micPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) listen() else binding.statusText.text = "需要麦克风权限"
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
        requestNotifications()
        setupUpdateChecks()
        checkUpdateNow()
    }

    override fun onStart() {
        super.onStart()
        ContextCompat.registerReceiver(this, timerReceiver, IntentFilter(TimerService.ACTION_STATE), ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    override fun onStop() {
        unregisterReceiver(timerReceiver)
        super.onStop()
    }

    private fun ensureMicAndListen() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) listen()
        else micPermission.launch(Manifest.permission.RECORD_AUDIO)
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
                override fun onError(error: Int) { binding.statusText.text = "没听清，再试一次" }
                override fun onPartialResults(partialResults: Bundle?) {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
                override fun onResults(results: Bundle?) {
                    val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                    binding.heardText.text = if (text.isBlank()) "没有识别到内容" else getString(com.zxkws.voicetimer.R.string.heard_format, text)
                    handleCommand(SemanticParser.parse(text))
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
        when (command) {
            is VoiceCommand.StartTimer -> {
                lastDuration = command.durationMillis
                TimerService.start(this, command.durationMillis)
                binding.statusText.text = "开始计时"
            }
            VoiceCommand.CancelTimer -> TimerService.cancel(this)
            VoiceCommand.RepeatTimer -> if (lastDuration > 0) TimerService.start(this, lastDuration) else binding.statusText.text = "还没有可重复的计时"
            VoiceCommand.Unknown -> binding.statusText.setText(com.zxkws.voicetimer.R.string.unknown_command)
        }
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
