package com.halo.data

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.AudioPlaybackConfiguration
import android.os.Handler
import android.os.Looper
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 내비 길 안내 음성이 나오는 중인지. 헤일로는 그동안 묻지 않고, 읽을 말은 안내가 끝난 뒤에 읽는다.
 * 안드로이드 공개 API(AudioManager 재생 정보)로 재생 중인 소리의 ‘용도’만 본다. 내용이나 앱 이름은 보지 않는다.
 */
class NaviVoiceMonitor(context: Context) {
    private val audio: AudioManager? = context.applicationContext.getSystemService(AudioManager::class.java)
    private val _playing = MutableStateFlow(false)
    val guidancePlaying: StateFlow<Boolean> = _playing.asStateFlow()

    private val callback = object : AudioManager.AudioPlaybackCallback() {
        override fun onPlaybackConfigChanged(configs: MutableList<AudioPlaybackConfiguration>?) {
            _playing.value = isGuidance(configs)
        }
    }

    init {
        try {
            _playing.value = isGuidance(audio?.activePlaybackConfigurations)
            audio?.registerAudioPlaybackCallback(callback, Handler(Looper.getMainLooper()))
        } catch (t: Throwable) {
            Log.w(TAG, "playback monitor unavailable", t)
        }
    }

    private fun isGuidance(configs: List<AudioPlaybackConfiguration>?): Boolean =
        configs.orEmpty().any { it.audioAttributes.usage == AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE }

    fun release() {
        try {
            audio?.unregisterAudioPlaybackCallback(callback)
        } catch (t: Throwable) {
            Log.w(TAG, "unregister failed", t)
        }
    }

    private companion object {
        const val TAG = "NaviVoice"
    }
}
