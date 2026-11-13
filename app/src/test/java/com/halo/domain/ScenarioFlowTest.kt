package com.halo.domain

import com.halo.demo.DemoScenarios
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 기획서의 시나리오를 엔진에 그대로 넣어 순서와 문장을 확인한다 */
class ScenarioFlowTest {

    @Test
    fun winterCommute() {
        val d = TestDrive(
            DemoScenarios.byId("winter"),
            answers = mapOf("detour" to Answer.NO, "mode-on" to Answer.YES, "lamp-on" to Answer.YES, "mode-off" to Answer.YES),
        )
        d.playAll()
        val keys = d.asked.map { it.key }
        assertEquals(listOf("detour", "mode-on:SNOW", "lamp-on:LOW_BEAM", "mode-off:SNOW"), keys)
        assertTrue(d.spoken.toString(), d.spoken.any { it == "1킬로미터 앞 다리 위 살얼음 주의예요. 지금부터 천천히 줄이세요." })
        assertTrue(d.spoken.toString(), d.spoken.any { it == "1킬로미터 앞 터널 출구 살얼음 위험이에요. 속도를 절반으로 줄이세요." })
        assertTrue(d.spoken.toString(), d.spoken.any { it.startsWith("300미터 앞 터널 출구예요.") })
        assertTrue(d.spoken.toString(), d.spoken.any { it == "사이드미러와 핸들 열선을 켰어요." })
        // 눈 모드로 켠 열선만 끄고, 오토 디포그는 건드리지 않는다
        assertEquals(
            listOf(
                ControlCommand.SetHeater(Heater.MIRROR, true), ControlCommand.SetHeater(Heater.STEERING, true),
                ControlCommand.SetLamp(Lamp.LOW_BEAM, true),
                ControlCommand.SetHeater(Heater.MIRROR, false), ControlCommand.SetHeater(Heater.STEERING, false),
            ),
            d.controls,
        )
    }

    @Test
    fun detourRemovesBridgeAndAddsMinutes() {
        val d = TestDrive(DemoScenarios.byId("winter"), answers = mapOf("detour" to Answer.YES, "mode-on" to Answer.NO))
        val first = d.step()
        assertTrue(first.detourTaken)
        assertEquals("08:20", first.briefing?.arrivalClock)
        d.playAll()
        assertFalse(d.spoken.any { it.contains("다리 위 살얼음") })
    }

    @Test
    fun monsoonEveningAsksRainModeHazardAndRestores() {
        val d = TestDrive(
            DemoScenarios.byId("monsoon"),
            answers = mapOf("mode-on" to Answer.YES, "lamp-on:HAZARD" to Answer.YES, "lamp-off:HAZARD" to Answer.YES, "mode-off" to Answer.YES),
        )
        d.playAll()
        assertEquals(listOf("mode-on:RAIN", "lamp-on:HAZARD", "lamp-off:HAZARD", "mode-off:RAIN"), d.asked.map { it.key })
        assertTrue(d.asked.first { it.key == "lamp-on:HAZARD" }.urgent)
        // 꼼꼼히 안내: 1.5km 전에 이유와 함께
        assertTrue(d.spoken.toString(), d.spoken.any { it.startsWith("1.5킬로미터 앞 폭우 구간이에요.") && it.endsWith("빗길은 미끄러워요.") })
        // 비상등은 제어 API가 없어 버튼 위치를 안내
        assertTrue(d.guides.any { it.lamp == Lamp.HAZARD && it.on })
        assertEquals(listOf(ControlCommand.SetHeater(Heater.MIRROR, true), ControlCommand.SetHeater(Heater.MIRROR, false)), d.controls)
    }

    @Test
    fun foggyDawnGuidesFogLightAndConfirms() {
        val d = TestDrive(DemoScenarios.byId("fog"), answers = mapOf("lamp-on:FRONT_FOG" to Answer.YES, "lamp-off:FRONT_FOG" to Answer.YES))
        d.playAll()
        assertTrue(d.spoken.toString(), d.spoken.any { it.startsWith("1.5킬로미터 앞 짙은 안개예요. 가시거리 90미터, 속도를 절반으로 줄이세요.") })
        assertEquals(listOf("lamp-on:FRONT_FOG:fog", "lamp-off:FRONT_FOG"), d.asked.map { it.key })
        assertTrue(d.spoken.toString(), d.spoken.contains("왼쪽 레버 끝의 안개등 스위치를 한 칸 돌려 주세요."))
        assertTrue(d.spoken.toString(), d.spoken.contains("안개등 켜졌어요."))
        assertTrue(d.spoken.toString(), d.spoken.contains(Phrases.FOG_LIGHT_TIP))
        assertTrue(d.controls.isEmpty())
    }

    @Test
    fun noAnswerChangesNothingAndCarefulAsksOnceMore() {
        val d = TestDrive(DemoScenarios.byId("winter"), answers = emptyMap(), careful = true)
        val s = d.step()
        assertTrue(d.controls.isEmpty())
        assertEquals("detour", s.question?.key)
        assertFalse(s.asking)
    }

    @Test
    fun turnSignalHoldsQuestionsButNotHazard() {
        val v = VehicleState(turnSignal = TurnSignal.LEFT)
        assertFalse(QuestionPolicy.canAskNow(v, hardBraking = false, naviSpeaking = false, urgent = false))
        assertTrue(QuestionPolicy.canAskNow(v, hardBraking = false, naviSpeaking = false, urgent = true))
        assertFalse(QuestionPolicy.canAskNow(VehicleState(), hardBraking = false, naviSpeaking = true, urgent = false))
    }
}
