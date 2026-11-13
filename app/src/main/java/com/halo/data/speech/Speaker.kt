package com.halo.data.speech

import ai.pleos.playground.tts.TextToSpeech as PleosTextToSpeech
import android.content.Context
import android.media.AudioAttributes
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import com.halo.data.pleos.PleosListeners
import java.util.Locale

/** 한 번에 한 문장씩 읽는다. [speak] 의 onDone 은 다 읽었거나 읽지 못했을 때 메인 스레드에서 한 번 불린다. */
interface Speaker {
    fun speak(text: String, onDone: () -> Unit)

    /** 읽던 것을 멈춘다. 멈춘 문장의 onDone 은 부르지 않는다. */
    fun stop()

    fun release()
}

/** 소리 없이 읽는 시간만큼 기다린다 (화면 자막은 그대로 보인다) */
internal fun readingTimeMs(text: String): Long = 800L + text.length * 110L

/**
 * 안드로이드 기본 TextToSpeech. 한국어 음성이 없으면 소리 없이 읽는 시간만큼 기다린다.
 */
class AndroidSpeaker(context: Context) : Speaker {

    private enum class State { STARTING, READY, MUTE }

    private val main = Handler(Looper.getMainLooper())
    private var state = State.STARTING
    private var waiting: Pair<String, () -> Unit>? = null
    private var currentId: String? = null
    private var currentDone: (() -> Unit)? = null
    private var seq = 0

    private val tts: TextToSpeech = TextToSpeech(context.applicationContext) { status -> main.post { onInit(status) } }

    private fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts.setLanguage(Locale.KOREAN)
            state = if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) State.MUTE else State.READY
            tts.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANT)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build(),
            )
            tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) = Unit

                override fun onDone(utteranceId: String?) {
                    main.post { finish(utteranceId) }
                }

                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    main.post { finish(utteranceId) }
                }
            })
        } else {
            state = State.MUTE
        }
        if (state == State.MUTE) Log.w(TAG, "Korean TTS unavailable, showing captions only")
        waiting?.let { (text, done) ->
            waiting = null
            speak(text, done)
        }
    }

    override fun speak(text: String, onDone: () -> Unit) {
        when (state) {
            State.STARTING -> waiting = text to onDone
            State.MUTE -> silent(text, onDone)
            State.READY -> {
                val id = "halo-${++seq}"
                currentId = id
                currentDone = onDone
                val result = tts.speak(text, TextToSpeech.QUEUE_FLUSH, Bundle(), id)
                if (result != TextToSpeech.SUCCESS) {
                    currentId = null
                    currentDone = null
                    silent(text, onDone)
                } else {
                    // 끝 알림이 오지 않는 경우를 대비한다
                    main.postDelayed({ finish(id) }, readingTimeMs(text) + SAFETY_MS)
                }
            }
        }
    }

    private fun silent(text: String, onDone: () -> Unit) {
        val id = "halo-${++seq}"
        currentId = id
        currentDone = onDone
        main.postDelayed({ finish(id) }, readingTimeMs(text))
    }

    private fun finish(id: String?) {
        if (id == null || id != currentId) return
        val done = currentDone
        currentId = null
        currentDone = null
        done?.invoke()
    }

    override fun stop() {
        waiting = null
        currentId = null
        currentDone = null
        try {
            tts.stop()
        } catch (t: Throwable) {
            Log.w(TAG, "stop failed", t)
        }
    }

    override fun release() {
        stop()
        main.removeCallbacksAndMessages(null)
        tts.shutdown()
    }

    private companion object {
        const val TAG = "AndroidSpeaker"
        const val SAFETY_MS = 6_000L
    }
}

/**
 * Pleos TextToSpeech SDK 로 읽는다. 연결이 안 되거나 오류가 나면 [fallback](안드로이드 기본 TTS)으로 넘긴다.
 * 앱을 켠 직후에는 Pleos 음성이 준비될 때까지 잠깐 기다린다.
 */
class PleosSpeaker(context: Context, private val fallback: Speaker) : Speaker {

    private val main = Handler(Looper.getMainLooper())
    private var ready = false
    private var failed = false
    private var waiting: Pair<String, () -> Unit>? = null
    private var current: Pair<String, () -> Unit>? = null
    private var token = 0

    private val tts: PleosTextToSpeech? = try {
        PleosTextToSpeech(context.applicationContext)
    } catch (t: Throwable) {
        Log.w(TAG, "Pleos TTS unavailable", t)
        null
    }

    init {
        val engine = tts
        if (engine == null) {
            failed = true
        } else {
            try {
                engine.initialize(
                    PleosListeners.ttsInit(
                        { main.post { onReady(engine) } },
                        { main.post { giveUp() } },
                    ),
                )
                engine.addEventListener(
                    PleosListeners.ttsEvents(
                        { main.post { finish() } },
                        { main.post { onEngineError() } },
                    ),
                )
            } catch (t: Throwable) {
                Log.w(TAG, "Pleos TTS initialize failed", t)
                failed = true
            }
        }
    }

    private fun onReady(engine: PleosTextToSpeech) {
        ready = true
        safely { engine.setLocale(Locale.KOREAN) }
        waiting?.let { (text, done) ->
            waiting = null
            speak(text, done)
        }
    }

    private fun giveUp() {
        failed = true
        waiting?.let { (text, done) ->
            waiting = null
            fallback.speak(text, done)
        }
    }

    override fun speak(text: String, onDone: () -> Unit) {
        val engine = tts
        if (engine == null || failed) {
            fallback.speak(text, onDone)
            return
        }
        if (!ready) {
            waiting = text to onDone
            main.postDelayed({ if (!ready && waiting != null) giveUp() }, READY_WAIT_MS)
            return
        }
        val my = ++token
        current = text to onDone
        try {
            engine.speak(text)
            main.postDelayed({ if (token == my) finish() }, readingTimeMs(text) + SAFETY_MS)
        } catch (t: Throwable) {
            Log.w(TAG, "Pleos TTS speak failed", t)
            onEngineError()
        }
    }

    private fun finish() {
        val done = current?.second ?: return
        current = null
        token++
        done()
    }

    /** Pleos 음성 엔진이 실패하면 이후로는 기본 TTS 로 읽는다 */
    private fun onEngineError() {
        failed = true
        val c = current ?: return
        current = null
        token++
        fallback.speak(c.first, c.second)
    }

    override fun stop() {
        waiting = null
        current = null
        token++
        safely { tts?.stop() }
        fallback.stop()
    }

    override fun release() {
        stop()
        main.removeCallbacksAndMessages(null)
        safely { tts?.release() }
        fallback.release()
    }

    private inline fun safely(block: () -> Unit) {
        try {
            block()
        } catch (t: Throwable) {
            Log.w(TAG, "Pleos TTS call failed", t)
        }
    }

    private companion object {
        const val TAG = "PleosSpeaker"
        const val READY_WAIT_MS = 3_000L
        const val SAFETY_MS = 6_000L
    }
}
