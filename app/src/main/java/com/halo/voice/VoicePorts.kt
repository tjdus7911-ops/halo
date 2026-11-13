package com.halo.voice

/** 한 번에 한 문장씩 읽는다. [speak] 의 onDone 은 다 읽었거나 읽지 못했을 때 메인 스레드에서 한 번 불린다. */
interface Speaker {
    fun speak(text: String, onDone: () -> Unit)

    /** 읽던 것을 멈춘다. 멈춘 문장의 onDone 은 부르지 않는다. */
    fun stop()

    fun release()
}

/** 질문 뒤에 운전자의 대답을 한 번 듣는다 */
interface AnswerListener {
    /** 지금 들을 수 있는지 */
    val ready: Boolean

    /** 들은 문장을 [onResult] 로 한 번 넘긴다 (못 알아들으면 null). 듣기 시작하지 못하면 false. */
    fun listen(onResult: (String?) -> Unit): Boolean

    fun cancel()

    fun release()
}

/** 시간: 지금 시각과 나중에 할 일 (앱에서는 메인 스레드 Handler, 테스트에서는 가짜 시계) */
interface Scheduler {
    fun now(): Long

    fun after(delayMs: Long, block: () -> Unit)
}

/** 차량 음성 인식을 먼저 쓰고, 없으면 기본 음성 인식을 쓴다 */
class FirstReadyListener(private val listeners: List<AnswerListener>) : AnswerListener {
    private var active: AnswerListener? = null

    override val ready: Boolean get() = listeners.any { it.ready }

    override fun listen(onResult: (String?) -> Unit): Boolean {
        for (l in listeners) {
            if (l.ready && l.listen(onResult)) {
                active = l
                return true
            }
        }
        return false
    }

    override fun cancel() {
        active?.cancel()
        active = null
    }

    override fun release() = listeners.forEach { it.release() }
}
