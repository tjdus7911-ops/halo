package com.halo.ui

import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.resources.Density
import com.android.resources.ScreenOrientation
import com.halo.data.FeedStatus
import com.halo.demo.DemoScenarios
import com.halo.domain.Answer
import com.halo.domain.TestDrive
import com.halo.ui.screens.HaloActions
import com.halo.ui.screens.HaloApp
import com.halo.ui.state.DemoUi
import com.halo.ui.state.GuideNote
import com.halo.ui.state.HaloUiState
import com.halo.ui.state.Source
import com.halo.ui.state.VoiceUi
import com.halo.voice.VoicePhase
import com.halo.ui.theme.HaloTheme
import org.junit.Rule
import org.junit.Test

/**
 * 차량 화면(1600×900)으로 주요 장면을 그려 확인한다. `./gradlew recordPaparazziDebug`
 * 장면은 시연 시나리오를 판단 엔진에 그대로 넣어 만든다 (화면만을 위한 가짜 상태를 쓰지 않는다).
 */
class ScreensSnapshotTest {

    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.PIXEL_C.copy(
            screenWidth = 1600,
            screenHeight = 900,
            xdpi = 160,
            ydpi = 160,
            density = Density.MEDIUM,
            orientation = ScreenOrientation.LANDSCAPE,
            softButtons = false,
        ),
        showSystemUi = false,
        maxPercentDifference = 1.0,
    )

    private fun shot(name: String, state: HaloUiState) {
        paparazzi.snapshot(name) {
            HaloTheme {
                HaloApp(state, HaloActions())
            }
        }
    }

    /** [frame] 장면까지 넘기며 엔진을 돌린다 */
    private fun drive(id: String, frame: Int, answers: Map<String, Answer?> = emptyMap(), hold: Set<String> = emptySet()): TestDrive {
        val d = TestDrive(DemoScenarios.byId(id), answers = answers, hold = hold)
        d.step()
        repeat(frame) {
            d.next()
            d.step()
        }
        return d
    }

    private fun demoState(d: TestDrive, voice: VoiceUi, guide: GuideNote? = null, settingsOpen: Boolean = false): HaloUiState {
        val run = d.run
        return HaloUiState(
            source = Source.DEMO,
            feed = FeedStatus.CONNECTED,
            snapshot = d.engine.snapshot(),
            voice = voice,
            demo = DemoUi(run.scenario.id, run.scenario.title, run.scenario.persona, run.frame.label, run.index, run.scenario.frames.size),
            guide = guide,
            settingsOpen = settingsOpen,
            isAutomotive = true,
        )
    }

    private fun listening(d: TestDrive) = VoiceUi(VoicePhase.LISTENING, caption = d.engine.snapshot().question?.prompt, canListen = true)

    private fun speaking(d: TestDrive) = VoiceUi(VoicePhase.SPEAKING, caption = d.spoken.last(), canListen = true)

    private val winterAnswers = mapOf("detour" to Answer.NO, "mode-on" to Answer.YES, "lamp-on" to Answer.YES, "mode-off" to Answer.YES)

    @Test
    fun briefingAsksDetour() {
        val d = drive("winter", 0, hold = setOf("detour"))
        shot("01_briefing_detour", demoState(d, listening(d)))
    }

    @Test
    fun briefingAsksSnowMode() {
        val d = drive("winter", 0, answers = mapOf("detour" to Answer.NO), hold = setOf("mode-on"))
        shot("02_briefing_snow_mode", demoState(d, listening(d)))
    }

    @Test
    fun radarBridgeCaution() {
        val d = drive("winter", 2, answers = winterAnswers)
        shot("03_radar_bridge", demoState(d, speaking(d)))
    }

    @Test
    fun tunnelAsksHeadlight() {
        val d = drive("winter", 3, answers = winterAnswers, hold = setOf("lamp-on"))
        shot("04_tunnel_headlight", demoState(d, listening(d)))
    }

    @Test
    fun rainModeOn() {
        val d = drive("monsoon", 1, answers = mapOf("mode-on" to Answer.YES))
        shot("05_rain_mode", demoState(d, speaking(d)))
    }

    @Test
    fun rainAsksHazard() {
        val d = drive("monsoon", 3, answers = mapOf("mode-on" to Answer.YES), hold = setOf("lamp-on:HAZARD"))
        shot("06_rain_hazard", demoState(d, listening(d)))
    }

    @Test
    fun fogAsksFogLight() {
        val d = drive("fog", 2, hold = setOf("lamp-on:FRONT_FOG"))
        shot("07_fog_light", demoState(d, listening(d)))
    }

    @Test
    fun fogGuidesSwitch() {
        val d = drive("fog", 2, answers = mapOf("lamp-on:FRONT_FOG" to Answer.YES))
        val g = d.guides.last()
        shot("08_fog_guide", demoState(d, speaking(d), guide = GuideNote(g.lamp, g.on, g.text, 0L)))
    }

    @Test
    fun arrivedAsksModeOff() {
        val d = drive("winter", 5, answers = winterAnswers - "mode-off", hold = setOf("mode-off"))
        shot("09_arrived_mode_off", demoState(d, listening(d)))
    }

    @Test
    fun noAnswerKeepsQuestion() {
        val d = drive("winter", 0, answers = mapOf("detour" to Answer.NO))
        shot("10_no_answer", demoState(d, VoiceUi(canListen = true)))
    }

    @Test
    fun settingsOpen() {
        val d = drive("fog", 1)
        shot("11_settings", demoState(d, VoiceUi(canListen = false), settingsOpen = true))
    }

    @Test
    fun liveConnecting() = shot("12_live_connecting", HaloUiState(source = Source.LIVE, feed = FeedStatus.CONNECTING, isAutomotive = true))

    @Test
    fun liveUnavailable() = shot("13_live_unavailable", HaloUiState(source = Source.LIVE, feed = FeedStatus.UNAVAILABLE, isAutomotive = false))
}
