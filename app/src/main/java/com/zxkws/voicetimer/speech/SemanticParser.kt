package com.zxkws.voicetimer.speech

sealed interface VoiceCommand {
    data class StartTimer(val durationMillis: Long) : VoiceCommand
    data object CancelTimer : VoiceCommand
    data object RepeatTimer : VoiceCommand
    data object Unknown : VoiceCommand
}

object SemanticParser {
    fun parse(raw: String): VoiceCommand {
        val text = normalize(raw)
        if (text.contains("取消") || text.contains("停止") || text.contains("别计时")) return VoiceCommand.CancelTimer
        if (text.contains("再来一次") || text.contains("再来一遍") || text.contains("重复")) return VoiceCommand.RepeatTimer

        val duration = parseDuration(text)
        return if (duration > 0) VoiceCommand.StartTimer(duration) else VoiceCommand.Unknown
    }

    private fun parseDuration(text: String): Long {
        var totalSeconds = 0.0
        if (text.contains("一个半小时") || text.contains("1个半小时") || text.contains("1.5小时")) {
            return 90 * 60 * 1000L
        }
        if (text.contains("半分钟") || text.contains("半分")) totalSeconds += 30
        if (text.contains("半小时")) totalSeconds += 30 * 60
        Regex("([0-9]+(?:\\.[0-9]+)?)\\s*(?:小时|时)").find(text)?.groupValues?.get(1)?.toDoubleOrNull()?.let { totalSeconds += it * 3600 }
        Regex("([0-9]+(?:\\.[0-9]+)?)\\s*(?:分钟|分)").find(text)?.groupValues?.get(1)?.toDoubleOrNull()?.let { totalSeconds += it * 60 }
        Regex("([0-9]+(?:\\.[0-9]+)?)\\s*秒").find(text)?.groupValues?.get(1)?.toDoubleOrNull()?.let { totalSeconds += it }
        if (Regex("[0-9]+(?:分钟|分)半").containsMatchIn(text)) totalSeconds += 30
        if (totalSeconds == 0.0) {
            Regex("(?:计时|倒计时|数)\\s*([0-9]+)").find(text)?.groupValues?.get(1)?.toDoubleOrNull()?.let { totalSeconds = it }
        }
        return (totalSeconds * 1000).toLong()
    }

    private fun normalize(raw: String): String {
        var text = raw.trim().replace("唤醒", "").replace("帮我", "").replace("一下", "").replace(" ", "")
        val cn = mapOf("十" to "10", "九" to "9", "八" to "8", "七" to "7", "六" to "6", "五" to "5", "四" to "4", "三" to "3", "两" to "2", "二" to "2", "一" to "1")
        text = text.replace(Regex("([二三四五六七八九])十([一二三四五六七八九])")) {
            ((cn[it.groupValues[1]]!!.toInt() * 10) + cn[it.groupValues[2]]!!.toInt()).toString()
        }
        text = text.replace(Regex("([二三四五六七八九])十(?=(秒|分钟|分|小时|时))")) { (cn[it.groupValues[1]]!!.toInt() * 10).toString() }
        text = text.replace(Regex("十([一二三四五六七八九])(?=(秒|分钟|分|小时|时))")) { (10 + cn[it.groupValues[1]]!!.toInt()).toString() }
        cn.forEach { (k, v) -> text = text.replace(Regex("$k(?=(秒|分钟|分|小时|时))"), v) }
        return text
    }
}
