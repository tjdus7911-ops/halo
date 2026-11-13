package com.halo.data

import com.halo.domain.ControlCommand
import com.halo.domain.VehicleState
import kotlinx.coroutines.flow.StateFlow

/** 차량 데이터 연결 상태 */
enum class FeedStatus {
    /** 연결 중 (첫 값을 기다림) */
    CONNECTING,

    /** 값이 들어오고 있음 */
    CONNECTED,

    /** 차량 서비스에 연결할 수 없음 (차량이 아니거나 권한·서비스 문제) */
    UNAVAILABLE,
}

/** 실제 차량의 상태 읽기와 제어 */
interface VehicleFeed {
    val status: StateFlow<FeedStatus>

    /** 지금 차량 상태. 시간에 따라 바뀌는 값(10분 새 기온 변화, 와이퍼 작동 시간)은 [nowMs] 기준으로 계산한다. */
    fun current(nowMs: Long): VehicleState

    /** 차량 제어. [done] 은 메인 스레드에서 한 번 불린다 (응답이 없으면 실패로 본다). */
    fun control(command: ControlCommand, done: (Boolean) -> Unit)

    fun release()
}
