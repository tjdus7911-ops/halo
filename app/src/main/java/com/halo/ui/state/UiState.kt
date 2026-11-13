package com.halo.ui.state

import com.halo.data.FeedStatus
import com.halo.domain.HaloSnapshot
import com.halo.domain.Lamp
import com.halo.voice.VoicePhase

/** 실제 차량으로 보는지, 기획서의 시연 시나리오로 보는지 */
enum class Source { LIVE, DEMO }

data class VoiceUi(
    val phase: VoicePhase = VoicePhase.IDLE,
    /** 지금 읽는 문장 (자막) */
    val caption: String? = null,
    /** 음성으로 답할 수 있는지 */
    val canListen: Boolean = false,
    /** 방금 알아들은 말 */
    val heard: String? = null,
)

data class DemoUi(
    val scenarioId: String,
    val title: String,
    val persona: String,
    val frameLabel: String,
    val index: Int,
    val count: Int,
) {
    val isLast: Boolean get() = index >= count - 1
}

/** 앱이 직접 켤 수 없는 등화: 스위치 위치 안내 */
data class GuideNote(val lamp: Lamp, val on: Boolean, val text: String, val shownAt: Long)

data class HaloUiState(
    val source: Source,
    val feed: FeedStatus,
    /** 판단 엔진이 그린 상태. 차량에 연결하는 중이거나 연결할 수 없으면 null */
    val snapshot: HaloSnapshot? = null,
    val voice: VoiceUi = VoiceUi(),
    val demo: DemoUi? = null,
    val guide: GuideNote? = null,
    val settingsOpen: Boolean = false,
    val isAutomotive: Boolean = false,
)
