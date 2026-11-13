package com.halo.data

import ai.pleos.playground.navi.helper.NaviHelper
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.halo.data.pleos.PleosListeners
import com.halo.domain.NaviCaution
import com.halo.domain.NaviInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Pleos NaviHelper: 목적지까지 남은 거리·시간, 도착, 내비가 알리는 주의 지점(미끄럼 주의 등).
 * 내비에 목적지가 없으면 [info] 는 비어 있고, 헤일로는 차량 센서만으로 판단한다.
 */
class PleosNaviFeed(context: Context) {

    private val main = Handler(Looper.getMainLooper())
    private val _info = MutableStateFlow<NaviInfo?>(null)

    /** 연결 전에는 null */
    val info: StateFlow<NaviInfo?> = _info.asStateFlow()

    private val sink = object : PleosListeners.NaviSink {
        override fun onDestination(name: String?, remainDistance: Int, remainTime: Int) = change {
            // 남은 거리가 크게 늘면 새 경로: 도착 표시와 주의 지점을 비운다
            val newRoute = it.remainM != null && remainDistance > it.remainM + NEW_ROUTE_JUMP_M
            if (newRoute) {
                NaviInfo(destinationName = name, remainM = remainDistance, remainSec = remainTime)
            } else {
                it.copy(destinationName = name, remainM = remainDistance, remainSec = remainTime, arrived = it.arrived && remainDistance <= ARRIVED_M)
            }
        }

        override fun onArrived() = change { it.copy(arrived = true, remainM = 0, remainSec = 0) }

        override fun onRouteEnded() = change {
            // 도착해서 경로가 끝난 경우는 도착 상태를 남겨 둔다 (날씨 모드를 끌지 묻는다)
            NaviInfo(arrived = it.arrived)
        }

        override fun onCautionPoint(id: String?, type: String?, remainDistance: Int) {
            if (id == null || type == null) return
            change { info ->
                val others = info.cautions.filterNot { c -> c.id == id }
                info.copy(cautions = (others + NaviCaution(id, type, remainDistance)).takeLast(MAX_CAUTIONS))
            }
        }
    }

    private val poll = object : Runnable {
        override fun run() {
            val helper = navi ?: return
            safely { helper.getDestinationInfo() }
            main.postDelayed(this, POLL_MS)
        }
    }

    private val navi: NaviHelper? = try {
        NaviHelper(context.applicationContext)
    } catch (t: Throwable) {
        Log.w(TAG, "NaviHelper unavailable", t)
        null
    }

    init {
        try {
            navi?.initialize(
                {
                    main.post {
                        val helper = navi ?: return@post
                        safely { helper.addListener(PleosListeners.naviListener(sink)) }
                        if (_info.value == null) _info.value = NaviInfo()
                        main.removeCallbacks(poll)
                        main.post(poll)
                    }
                },
                { error -> Log.w(TAG, "NaviHelper connection failed", error) },
            )
        } catch (t: Throwable) {
            Log.w(TAG, "NaviHelper initialize failed", t)
        }
    }

    fun release() {
        main.removeCallbacksAndMessages(null)
        try {
            navi?.release()
        } catch (t: Throwable) {
            Log.w(TAG, "release failed", t)
        }
    }

    private fun change(block: (NaviInfo) -> NaviInfo) {
        _info.update { block(it ?: NaviInfo()) }
    }

    private inline fun safely(block: () -> Unit) {
        try {
            block()
        } catch (t: Throwable) {
            Log.w(TAG, "NaviHelper call failed", t)
        }
    }

    private companion object {
        const val TAG = "PleosNavi"
        const val POLL_MS = 5_000L
        const val NEW_ROUTE_JUMP_M = 2_000
        const val ARRIVED_M = 50
        const val MAX_CAUTIONS = 20
    }
}
