package com.halo.data.speech

import ai.pleos.playground.stt.SpeechToText
import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import com.halo.data.pleos.PleosListeners
import com.halo.domain.VoiceAnswer
import java.util.Locale

/** 질문 뒤에 운전자의 대답을 한 번 듣는다 */
interface AnswerListener {
    /** 지금 들을 수 있는지 */
    val ready: Boolean

    /** 들은 문장을 [onResult] 로 한 번 넘긴다 (못 알아들으면 null). 듣기 시작하지 못하면 false. */
    fun listen(onResult: (String?) -> Unit): Boolean

    fun cancel()

    fun release()
}

/** Pleos SpeechToText SDK: 차량의 음성 인식 */
class PleosAnswerListener(context: Context) : AnswerListener {

    private val main = Handler(Looper.getMainLooper())
    private var initialized = false
    private var callback: ((String?) -> Unit)? = null

    private val stt: SpeechToText? = try {
        SpeechToText(context.applicationContext)
    } catch (t: Throwable) {
        Log.w(TAG, "Pleos STT unavailable", t)
        null
    }

    override val ready: Boolean get() = stt != null && initialized

    init {
        val engine = stt
        if (engine != null) {
            try {
                engine.initialize(
                    PleosListeners.sttInit(
                        { main.post { initialized = true; safely { engine.setLocale(Locale.KOREAN) } } },
                        { main.post { initialized = false } },
                    ),
                )
                engine.addListener(
                    PleosListeners.sttResults(object : PleosListeners.SpeechSink {
                        override fun onText(text: String?, isFinal: Boolean) {
                            if (isFinal) main.post { deliver(text) }
                        }

                        override fun onFinished() {
                            // 마지막 결과가 끝 알림보다 늦게 올 수 있어 잠깐 기다린다
                            main.postDelayed({ deliver(null) }, LATE_RESULT_MS)
                        }
                    }),
                )
            } catch (t: Throwable) {
                Log.w(TAG, "Pleos STT initialize failed", t)
            }
        }
    }

    override fun listen(onResult: (String?) -> Unit): Boolean {
        val engine = stt ?: return false
        if (!initialized) return false
        callback = onResult
        return try {
            engine.request()
            true
        } catch (t: Throwable) {
            Log.w(TAG, "Pleos STT request failed", t)
            callback = null
            false
        }
    }

    private fun deliver(text: String?) {
        val cb = callback ?: return
        callback = null
        cb(text)
    }

    override fun cancel() {
        callback = null
        safely { stt?.stop() }
    }

    override fun release() {
        cancel()
        main.removeCallbacksAndMessages(null)
        safely { stt?.release() }
    }

    private inline fun safely(block: () -> Unit) {
        try {
            block()
        } catch (t: Throwable) {
            Log.w(TAG, "Pleos STT call failed", t)
        }
    }

    private companion object {
        const val TAG = "PleosStt"
        const val LATE_RESULT_MS = 700L
    }
}

/**
 * 안드로이드 기본 음성 인식 (태블릿·에뮬레이터 시연용). 마이크 권한이 있어야 한다.
 * 후보 문장 가운데 “응 / 아니”로 읽히는 첫 문장을 넘긴다 (“응”이 “음”으로 먼저 들리는 경우).
 */
class AndroidAnswerListener(private val context: Context) : AnswerListener {

    private var recognizer: SpeechRecognizer? = null
    private var callback: ((String?) -> Unit)? = null

    override val ready: Boolean
        get() = context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED &&
            SpeechRecognizer.isRecognitionAvailable(context)

    override fun listen(onResult: (String?) -> Unit): Boolean {
        if (!ready) return false
        return try {
            val r = recognizer ?: SpeechRecognizer.createSpeechRecognizer(context.applicationContext).also { recognizer = it }
            callback = onResult
            r.setRecognitionListener(object : RecognitionListener {
                override fun onResults(results: Bundle?) {
                    val texts = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION).orEmpty()
                    deliver(texts.firstOrNull { VoiceAnswer.parse(it) != null } ?: texts.firstOrNull())
                }

                override fun onError(error: Int) = deliver(null)

                override fun onReadyForSpeech(params: Bundle?) = Unit

                override fun onBeginningOfSpeech() = Unit

                override fun onRmsChanged(rmsdB: Float) = Unit

                override fun onBufferReceived(buffer: ByteArray?) = Unit

                override fun onEndOfSpeech() = Unit

                override fun onPartialResults(partialResults: Bundle?) = Unit

                override fun onEvent(eventType: Int, params: Bundle?) = Unit
            })
            r.startListening(
                Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                    .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ko-KR")
                    .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5),
            )
            true
        } catch (t: Throwable) {
            Log.w(TAG, "speech recognizer failed", t)
            callback = null
            false
        }
    }

    private fun deliver(text: String?) {
        val cb = callback ?: return
        callback = null
        cb(text)
    }

    override fun cancel() {
        callback = null
        try {
            recognizer?.cancel()
        } catch (t: Throwable) {
            Log.w(TAG, "cancel failed", t)
        }
    }

    override fun release() {
        cancel()
        try {
            recognizer?.destroy()
        } catch (t: Throwable) {
            Log.w(TAG, "destroy failed", t)
        }
        recognizer = null
    }

    private companion object {
        const val TAG = "AndroidStt"
    }
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
