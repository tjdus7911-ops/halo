package com.halo.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VoiceAnswerTest {
    @Test
    fun yes() {
        listOf("응", "어", "네", "그래", "좋아", "켜 줘", "응 켜줘", "꺼 줘", "다른 길로", "부탁해").forEach {
            assertEquals(it, Answer.YES, VoiceAnswer.parse(it))
        }
    }

    @Test
    fun no() {
        listOf("아니", "아니요", "괜찮아", "나중에", "그냥 가자", "이대로 갈게", "안 켜도 돼", "켜지 마", "됐어").forEach {
            assertEquals(it, Answer.NO, VoiceAnswer.parse(it))
        }
    }

    @Test
    fun unclear() {
        assertNull(VoiceAnswer.parse(""))
        assertNull(VoiceAnswer.parse(null))
        assertNull(VoiceAnswer.parse("오늘 점심 뭐 먹지"))
    }
}
