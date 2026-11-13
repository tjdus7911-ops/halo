package com.halo.voice

import com.halo.domain.Answer
import com.halo.domain.Lamp
import com.halo.domain.Question
import com.halo.domain.QuestionKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 음성 줄: 한 번에 하나씩, 질문 뒤에는 듣고, 대답이 없으면 아무것도 바꾸지 않는다 */
class VoiceFlowTest {

    private class FakeSpeaker : Speaker {
        val spoken = mutableListOf<String>()
        var pending: (() -> Unit)? = null
        var stops = 0
        override fun speak(text: String, onDone: () -> Unit) {
            spoken += text
            pending = onDone
        }

        override fun stop() {
            stops++
            pending = null
        }

        override fun release() = Unit

        fun done() {
            val p = pending
            pending = null
            p?.invoke()
        }
    }

    private class FakeListener(override var ready: Boolean = true) : AnswerListener {
        var pending: ((String?) -> Unit)? = null
        var listens = 0
        override fun listen(onResult: (String?) -> Unit): Boolean {
            if (!ready) return false
            listens++
            pending = onResult
            return true
        }

        override fun cancel() {
            pending = null
        }

        override fun release() = Unit

        fun hear(text: String?) {
            val p = pending
            pending = null
            p?.invoke(text)
        }
    }

    private class FakeClock : Scheduler {
        var time = 0L
        private val tasks = mutableListOf<Pair<Long, () -> Unit>>()
        override fun now() = time
        override fun after(delayMs: Long, block: () -> Unit) {
            tasks += (time + delayMs) to block
        }

        fun advance(ms: Long) {
            val end = time + ms
            while (true) {
                val next = tasks.filter { it.first <= end }.minByOrNull { it.first } ?: break
                tasks.remove(next)
                time = next.first
                next.second()
            }
            time = end
        }
    }

    private val speaker = FakeSpeaker()
    private val listener = FakeListener()
    private val clock = FakeClock()
    private val asking = mutableSetOf<String>()
    private val answers = mutableListOf<Pair<String, Answer>>()
    private val noAnswers = mutableListOf<String>()
    private var navi = false

    private val flow = VoiceFlow(speaker, listener, clock, object : VoiceFlow.Callbacks {
        override fun isAsking(questionId: String) = questionId in asking
        override fun onAnswer(questionId: String, answer: Answer, heard: String?) {
            asking -= questionId
            answers += questionId to answer
        }

        override fun onNoAnswer(questionId: String) {
            asking -= questionId
            noAnswers += questionId
        }

        override fun onStateChanged(state: VoiceState) = Unit
        override fun naviSpeaking() = navi
    })

    private fun question(id: String, urgent: Boolean = false) = Question(
        id = id, key = id, kind = QuestionKind.LampOn(Lamp.LOW_BEAM), prompt = "$id?", title = id,
        yesLabel = "응", noLabel = "아니", urgent = urgent,
    ).also { asking += id }

    @Test
    fun speaksOneAtATimeInOrder() {
        flow.say("하나")
        flow.say("둘")
        assertEquals(listOf("하나"), speaker.spoken)
        assertEquals(VoicePhase.SPEAKING, flow.state.phase)
        speaker.done()
        assertEquals(listOf("하나", "둘"), speaker.spoken)
        speaker.done()
        assertEquals(VoicePhase.IDLE, flow.state.phase)
        // 다 읽은 문장은 잠깐 남았다가 사라진다
        assertEquals("둘", flow.state.caption)
        clock.advance(VoiceFlow.CAPTION_HOLD_MS)
        assertNull(flow.state.caption)
    }

    @Test
    fun asksThenListensAndTakesYes() {
        flow.ask(question("q1"))
        assertEquals("q1?", speaker.spoken.single())
        speaker.done()
        assertEquals(VoicePhase.LISTENING, flow.state.phase)
        listener.hear("응 켜 줘")
        assertEquals(listOf("q1" to Answer.YES), answers)
        assertFalse(flow.busy)
        // 늦게 온 시간 초과는 무시한다
        clock.advance(VoiceFlow.LISTEN_MS)
        assertTrue(noAnswers.isEmpty())
    }

    @Test
    fun silenceMeansNoAnswerAndQueueMovesOn() {
        flow.ask(question("q1"))
        flow.say("다음 말")
        speaker.done()
        clock.advance(VoiceFlow.LISTEN_MS)
        assertEquals(listOf("q1"), noAnswers)
        assertTrue(answers.isEmpty())
        assertEquals("다음 말", speaker.spoken.last())
    }

    @Test
    fun unclearWordListensOnceMore() {
        flow.ask(question("q1"))
        speaker.done()
        listener.hear("음")
        assertEquals(2, listener.listens)
        listener.hear("아니 괜찮아")
        assertEquals(listOf("q1" to Answer.NO), answers)
    }

    @Test
    fun twoUnclearWordsAreNoAnswer() {
        flow.ask(question("q1"))
        speaker.done()
        listener.hear("음")
        listener.hear("어어")
        assertEquals(listOf("q1"), noAnswers)
    }

    @Test
    fun withoutRecognitionWaitsForTapThenGivesUp() {
        listener.ready = false
        flow.ask(question("q1"))
        speaker.done()
        assertEquals(VoicePhase.WAITING_TAP, flow.state.phase)
        clock.advance(VoiceFlow.TAP_WAIT_MS)
        assertEquals(listOf("q1"), noAnswers)
    }

    @Test
    fun tapWhileAskingStopsVoiceAndSpeaksConfirmationNext() {
        flow.ask(question("q1"))
        flow.say("밀린 말")
        flow.answeredElsewhere("q1") {
            asking -= "q1"
            flow.say("켰어요")
        }
        assertEquals(1, speaker.stops)
        // 확인 문장이 밀린 말보다 먼저 나오지는 않지만, 묻던 질문은 다시 읽지 않는다
        assertEquals(listOf("q1?", "밀린 말"), speaker.spoken)
        speaker.done()
        assertEquals("켰어요", speaker.spoken.last())
        assertTrue(noAnswers.isEmpty())
    }

    @Test
    fun staleQuestionIsSkipped() {
        flow.say("먼저")
        val q = question("q1")
        flow.ask(q)
        asking -= "q1"
        speaker.done()
        assertEquals(listOf("먼저"), speaker.spoken)
        assertFalse(flow.busy)
    }

    @Test
    fun urgentQuestionJumpsTheQueue() {
        flow.say("말하는 중")
        flow.say("줄 선 말")
        flow.ask(question("hazard", urgent = true))
        speaker.done()
        assertEquals("hazard?", speaker.spoken.last())
    }

    @Test
    fun waitsForNaviGuidanceAtMostSixSeconds() {
        navi = true
        flow.say("안내")
        assertTrue(speaker.spoken.isEmpty())
        clock.advance(2_000)
        assertTrue(speaker.spoken.isEmpty())
        navi = false
        clock.advance(500)
        assertEquals(listOf("안내"), speaker.spoken)

        speaker.done()
        navi = true
        flow.say("또 안내")
        clock.advance(VoiceFlow.NAVI_WAIT_MS + 500)
        assertEquals("또 안내", speaker.spoken.last())
    }

    @Test
    fun interruptDropsSaysAndReportsCurrentQuestion() {
        flow.ask(question("q1"))
        flow.say("버릴 말")
        speaker.done()
        flow.interrupt()
        assertEquals(listOf("q1"), noAnswers)
        assertEquals(listOf("q1?"), speaker.spoken)
        assertFalse(flow.busy)
    }
}
