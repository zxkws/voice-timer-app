package com.zxkws.voicetimer.speech

import org.junit.Assert.assertEquals
import org.junit.Test

class SemanticParserTest {
    @Test fun parsesSeconds() {
        assertEquals(VoiceCommand.StartTimer(10_000), SemanticParser.parse("唤醒，计时10秒"))
    }

    @Test fun parsesMinutesAndSeconds() {
        assertEquals(VoiceCommand.StartTimer(90_000), SemanticParser.parse("倒计时1分30秒"))
    }

    @Test fun parsesChineseNumbers() {
        assertEquals(VoiceCommand.StartTimer(30_000), SemanticParser.parse("计时三十秒"))
    }

    @Test fun parsesCancel() {
        assertEquals(VoiceCommand.CancelTimer, SemanticParser.parse("取消计时"))
    }

    @Test fun parsesRepeat() {
        assertEquals(VoiceCommand.RepeatTimer, SemanticParser.parse("再来一次"))
    }
}
