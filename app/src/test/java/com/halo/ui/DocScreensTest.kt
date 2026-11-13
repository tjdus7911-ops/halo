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
import com.halo.ui.state.GuideNote
import com.halo.ui.state.HaloUiState
import com.halo.ui.state.Source
import com.halo.ui.state.VoiceUi
import com.halo.ui.theme.HaloTheme
import com.halo.voice.VoicePhase
import org.junit.Rule
import org.junit.Test

/**
 * 기획서·발표 자료용 고해상도 화면 (2배 해상도, 발표용 시연 바는 빼고 그린다).
 * 장면 합성용은 배경 사진의 화면 비율에 맞춰 크기를 바꿔 그린다.
 */
class DocScreensTest {

    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = config(1600, 833),
        showSystemUi = false,
        maxPercentDifference = 1.0,
        useDeviceResolution = true,
    )

    private fun config(widthDp: Int, heightDp: Int) = DeviceConfig.PIXEL_C.copy(
        screenWidth = widthDp * 2,
        screenHeight = heightDp * 2,
        xdpi = 320,
        ydpi = 320,
        density = Density.XHIGH,
        orientation = ScreenOrientation.LANDSCAPE,
        softButtons = false,
    )

    private fun shot(name: String, state: HaloUiState, widthDp: Int = 1600, heightDp: Int = 833) {
        paparazzi.unsafeUpdateConfig(deviceConfig = config(widthDp, heightDp))
        paparazzi.snapshot(name) {
            HaloTheme {
                HaloApp(state, HaloActions())
            }
        }
    }

    private fun drive(id: String, frame: Int, answers: Map<String, Answer?> = emptyMap(), hold: Set<String> = emptySet()): TestDrive {
        val d = TestDrive(DemoScenarios.byId(id), answers = answers, hold = hold)
        d.step()
        repeat(frame) {
            d.next()
            d.step()
        }
        return d
    }

    private fun state(d: TestDrive, voice: VoiceUi, guide: GuideNote? = null, settingsOpen: Boolean = false) = HaloUiState(
        source = Source.DEMO,
        feed = FeedStatus.CONNECTED,
        snapshot = d.engine.snapshot(),
        voice = voice,
        demo = null,
        guide = guide,
        settingsOpen = settingsOpen,
        isAutomotive = true,
    )

    private fun listening(d: TestDrive) = VoiceUi(VoicePhase.LISTENING, d.engine.snapshot().question?.prompt, canListen = true)

    private fun speaking(d: TestDrive) = VoiceUi(VoicePhase.SPEAKING, d.spoken.last(), canListen = true)

    private val winter = mapOf("detour" to Answer.NO, "mode-on" to Answer.YES, "lamp-on" to Answer.YES, "mode-off" to Answer.YES)

    private fun brief() = drive("winter", 0, hold = setOf("detour")).let { state(it, listening(it)) }
    private fun snow() = drive("winter", 0, answers = mapOf("detour" to Answer.NO), hold = setOf("mode-on")).let { state(it, listening(it)) }
    private fun radar() = drive("winter", 2, answers = winter).let { state(it, speaking(it)) }
    private fun rain() = drive("monsoon", 3, answers = mapOf("mode-on" to Answer.YES), hold = setOf("lamp-on:HAZARD")).let { state(it, listening(it)) }
    private fun fog() = drive("fog", 2, hold = setOf("lamp-on:FRONT_FOG")).let { state(it, listening(it)) }

    // ---------------- 기획서 화면 1~5 ----------------

    @Test fun docBrief() = shot("doc_brief", brief())

    @Test fun docSnow() = shot("doc_snow", snow())

    @Test fun docRadar() = shot("doc_radar", radar())

    @Test fun docRain() = shot("doc_rain", rain())

    @Test fun docFog() = shot("doc_fog", fog())

    // ---------------- 발표 자료에 더하는 화면 ----------------

    @Test fun docTunnel() = drive("winter", 3, answers = winter, hold = setOf("lamp-on")).let { shot("doc_tunnel", state(it, listening(it))) }

    @Test fun docRainMode() = drive("monsoon", 1, answers = mapOf("mode-on" to Answer.YES)).let { shot("doc_rain_mode", state(it, speaking(it))) }

    @Test fun docFogGuide() = drive("fog", 2, answers = mapOf("lamp-on:FRONT_FOG" to Answer.YES)).let { d ->
        val g = d.guides.last()
        shot("doc_fog_guide", state(d, speaking(d), guide = GuideNote(g.lamp, g.on, g.text, 0L)))
    }

    @Test fun docArrived() = drive("winter", 5, answers = winter - "mode-off", hold = setOf("mode-off")).let { shot("doc_arrived", state(it, listening(it))) }

    @Test fun docSettings() = drive("fog", 1).let { shot("doc_settings", state(it, VoiceUi(canListen = true), settingsOpen = true)) }

    // ---------------- 장면 합성용 (배경 사진의 화면 비율) ----------------

    @Test fun scenePersona() = shot("scene_persona", brief(), heightDp = 842)

    @Test fun sceneDawn() = shot("scene_dawn", snow(), heightDp = 766)

    @Test fun sceneBridge() = shot("scene_bridge", radar(), heightDp = 829)

    @Test fun sceneRain() = shot("scene_rain", rain(), heightDp = 800)

    @Test fun sceneFog() = shot("scene_fog", fog(), heightDp = 829)
}
