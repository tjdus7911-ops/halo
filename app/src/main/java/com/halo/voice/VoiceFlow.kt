package com.halo.voice

import com.halo.domain.Answer
import com.halo.domain.Question
import com.halo.domain.VoiceAnswer

enum class VoicePhase {
    IDLE,

    /** 읽는 중 */
    SPEAKING,

    /** 질문 뒤 대답을 듣는 중 */
    LISTENING,

    /** 음성 인식을 쓸 수 없어 화면 버튼을 기다리는 중 */
    WAITING_TAP,
}

data class VoiceState(
    val phase: VoicePhase = VoicePhase.IDLE,
    /** 지금(또는 방금) 읽은 문장 */
    val caption: String? = null,
    /** 방금 알아들은 말 */
    val heard: String? = null,
)

/**
 * 헤일로의 말과 질문을 한 번에 하나씩 차례로 처리한다.
 * - 질문은 읽은 뒤 대답을 듣는다. 알아듣지 못하면 한 번 더 듣고, 그래도 없으면 [Callbacks.onNoAnswer].
 * - 음성 인식을 쓸 수 없으면 잠깐 화면 버튼을 기다린다.
 * - 내비 길 안내가 나오는 동안에는 최대 6초 기다렸다 읽는다.
 * - 뒤차에 알려야 하는 질문(urgent)은 줄 맨 앞에 선다.
 */
class VoiceFlow(
    private val speaker: Speaker,
    private val listener: AnswerListener,
    private val scheduler: Scheduler,
    private val callbacks: Callbacks,
) {
    interface Callbacks {
        /** 이 질문을 엔진이 지금도 묻고 있는지 (이미 답했거나 상황이 바뀌었으면 읽지 않는다) */
        fun isAsking(questionId: String): Boolean

        fun onAnswer(questionId: String, answer: Answer, heard: String?)

        fun onNoAnswer(questionId: String)

        fun onStateChanged(state: VoiceState)

        fun naviSpeaking(): Boolean
    }

    private sealed interface Item {
        data class Say(val text: String) : Item
        data class Ask(val question: Question) : Item
    }

    var state = VoiceState()
        private set

    /** 지금 읽거나 듣고 있는 질문 */
    var askingId: String? = null
        private set

    val busy: Boolean get() = current != null

    private val queue = ArrayDeque<Item>()
    private var current: Item? = null
    private var token = 0
    private var listenSeq = 0
    private var naviWaitSince: Long? = null
    private var naviRetryScheduled = false

    fun say(text: String) {
        queue.addLast(Item.Say(text))
        pump()
    }

    fun ask(question: Question) {
        if (question.urgent) queue.addFirst(Item.Ask(question)) else queue.addLast(Item.Ask(question))
        pump()
    }

    /**
     * 화면 버튼으로 답한 질문: 그 질문을 읽거나 듣는 중이면 멈춘다.
     * [then] 에서 대답을 처리하고(확인 문장을 줄 세우고) 난 뒤에 다음으로 넘어간다.
     */
    fun answeredElsewhere(questionId: String, then: () -> Unit) {
        if (askingId == questionId) stopCurrent()
        then()
        pump()
    }

    /** 읽던 말을 멈추고 줄 선 말을 버린다. 묻던 질문은 대답 없음으로 알린다 (화면에는 남는다). */
    fun interrupt() {
        val asking = askingId
        stopCurrent()
        queue.removeAll { it is Item.Say }
        asking?.let { callbacks.onNoAnswer(it) }
        setState(VoiceState())
        pump()
    }

    /** 모두 멈추고 비운다 (실제 차량 ↔ 시연 전환) */
    fun clear() {
        stopCurrent()
        queue.clear()
        setState(VoiceState())
    }

    /** 읽지 않고 화면에만 잠깐 보여 준다 (Gleo 가 대신 말할 때) */
    fun showCaption(text: String, forMs: Long) {
        if (busy) return
        setState(VoiceState(caption = text))
        scheduler.after(forMs) { if (!busy && state.caption == text) setState(VoiceState()) }
    }

    private fun pump() {
        if (current != null) return
        val next = queue.firstOrNull() ?: return
        if (callbacks.naviSpeaking()) {
            val now = scheduler.now()
            val since = naviWaitSince ?: now.also { naviWaitSince = it }
            if (now - since < NAVI_WAIT_MS) {
                if (!naviRetryScheduled) {
                    naviRetryScheduled = true
                    scheduler.after(NAVI_RETRY_MS) {
                        naviRetryScheduled = false
                        pump()
                    }
                }
                return
            }
        }
        naviWaitSince = null
        queue.removeFirst()
        if (next is Item.Ask && !callbacks.isAsking(next.question.id)) {
            pump()
            return
        }
        current = next
        val t = ++token
        when (next) {
            is Item.Say -> {
                setState(VoiceState(VoicePhase.SPEAKING, next.text))
                speaker.speak(next.text) { if (t == token) finish() }
            }
            is Item.Ask -> {
                askingId = next.question.id
                setState(VoiceState(VoicePhase.SPEAKING, next.question.prompt))
                speaker.speak(next.question.prompt) { if (t == token) listen(next.question, t, retried = false) }
            }
        }
    }

    private fun listen(q: Question, t: Int, retried: Boolean) {
        if (!callbacks.isAsking(q.id)) {
            finish()
            return
        }
        val seq = ++listenSeq
        val listening = listener.listen { text -> if (t == token && seq == listenSeq) heard(q, t, text, retried) }
        setState(VoiceState(if (listening) VoicePhase.LISTENING else VoicePhase.WAITING_TAP, q.prompt))
        scheduler.after(if (listening) LISTEN_MS else TAP_WAIT_MS) {
            if (t == token && seq == listenSeq) noAnswer(q)
        }
    }

    private fun heard(q: Question, t: Int, text: String?, retried: Boolean) {
        val answer = VoiceAnswer.parse(text)
        if (answer != null) {
            endCurrent()
            setState(VoiceState(heard = text))
            callbacks.onAnswer(q.id, answer, text)
            pump()
            return
        }
        // 알아듣지 못하면 한 번만 다시 듣는다 (“응”이 다른 말로 들린 경우)
        if (!retried && !text.isNullOrBlank()) listen(q, t, retried = true) else noAnswer(q)
    }

    private fun noAnswer(q: Question) {
        listener.cancel()
        endCurrent()
        setState(VoiceState())
        callbacks.onNoAnswer(q.id)
        pump()
    }

    /** 다 읽은 문장은 다음 말이 나올 때까지(최대 몇 초) 화면에 남겨 둔다 */
    private fun finish() {
        val caption = state.caption
        endCurrent()
        setState(VoiceState(caption = caption))
        val t = token
        scheduler.after(CAPTION_HOLD_MS) { if (t == token && current == null && state.caption == caption) setState(VoiceState()) }
        pump()
    }

    private fun endCurrent() {
        current = null
        askingId = null
        listenSeq++
        token++
    }

    private fun stopCurrent() {
        if (current == null) return
        speaker.stop()
        listener.cancel()
        endCurrent()
    }

    private fun setState(s: VoiceState) {
        state = s
        callbacks.onStateChanged(s)
    }

    companion object {
        const val LISTEN_MS = 6_000L
        const val TAP_WAIT_MS = 5_000L
        const val NAVI_WAIT_MS = 6_000L
        const val NAVI_RETRY_MS = 400L
        const val CAPTION_HOLD_MS = 3_000L
    }
}
