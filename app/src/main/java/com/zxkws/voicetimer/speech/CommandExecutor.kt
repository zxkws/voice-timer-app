package com.zxkws.voicetimer.speech

import android.content.Context
import com.zxkws.voicetimer.timer.TimerService

object CommandExecutor {
    private const val PREFS = "voice_timer"
    private const val KEY_LAST_DURATION = "last_duration"

    fun execute(context: Context, command: VoiceCommand): String = when (command) {
        is VoiceCommand.StartTimer -> {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putLong(KEY_LAST_DURATION, command.durationMillis).apply()
            TimerService.start(context, command.durationMillis)
            "开始计时"
        }
        VoiceCommand.CancelTimer -> {
            TimerService.cancel(context)
            "已取消计时"
        }
        VoiceCommand.RepeatTimer -> {
            val duration = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getLong(KEY_LAST_DURATION, 0L)
            if (duration > 0) {
                TimerService.start(context, duration)
                "重新开始计时"
            } else "还没有可重复的计时"
        }
        VoiceCommand.Unknown -> "没理解，请说“计时10秒”"
    }
}
