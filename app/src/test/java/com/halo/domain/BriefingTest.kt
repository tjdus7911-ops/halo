package com.halo.domain

import com.halo.demo.DemoScenarios
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BriefingTest {

    @Test
    fun winterCommuteBriefingMatchesPlan() {
        val s = DemoScenarios.byId("winter")
        val f = s.frames.first()
        val hazards = RiskEngine.evaluateRoute(f.context.spots, f.vehicle, f.context.weather)
        val b = BriefingPlanner.build(f.vehicle, f.context, hazards, full = true, careful = false, firstFrost = false)
        assertEquals(5, b.delayMin)
        assertEquals("08:20", b.arrivalClock)
        assertTrue(b.speech, b.speech.startsWith("앞유리가 녹는 동안 오늘 길 알려 드릴게요."))
        assertTrue(b.speech, "살얼음 주의 2곳, 천천히 가면 5분 늦어요." in b.speech)
        assertTrue(b.speech, "이틀 전 비가 와서 녹았다 언 곳이 있을 수 있어요." in b.speech)
        assertTrue(b.checks.any { it.id == "tire" && it.level == CheckLevel.WARN })
        assertTrue(b.checks.any { it.id == "wiper" && it.status == "얼음 확인" })
    }

    @Test
    fun clockAddsMinutesAcrossHours() {
        assertEquals("08:20", Clock.plus("07:40", 40))
        assertEquals("00:10", Clock.plus("23:50", 20))
    }

    @Test
    fun phrasesUseRightParticles() {
        assertEquals("열선은", Phrases.josa("열선", "은/는"))
        assertEquals("디포그는", Phrases.josa("디포그", "은/는"))
        assertEquals("1km", Phrases.distance(1.0))
        assertEquals("1.5km", Phrases.distance(1.5))
        assertEquals("300m", Phrases.distance(0.3))
        assertEquals("300미터", Phrases.spokenDistance(0.3))
        assertEquals("1킬로미터", Phrases.spokenDistance(0.98))
        assertEquals("1.5킬로미터", Phrases.spokenDistance(1.43))
        assertEquals("1km", Phrases.distance(1.02))
        assertEquals("영하 4도", Phrases.spokenTemp(-4.0))
    }

    @Test
    fun modeConfirmationNamesWhatWasTurnedOn() {
        val p = ModeProposal(
            WeatherMode.SNOW,
            listOf(PlannedHeater(Heater.MIRROR, ""), PlannedHeater(Heater.STEERING, "")),
            listOf(KeptAuto("오토 디포그", "")),
            emptyList(),
        )
        assertEquals("사이드미러와 핸들 열선을 켰어요.", Phrases.modeOnDone(p, careful = false))
        assertEquals(
            "사이드미러와 핸들 열선을 켰어요. 오토 디포그는 자동으로 작동 중이라 그대로 둘게요.",
            Phrases.modeOnDone(p, careful = true),
        )
        val withDefrost = p.copy(turnOn = p.turnOn + PlannedHeater(Heater.DEFROST, ""))
        assertEquals("사이드미러·핸들 열선과 김서림 방지를 켰어요.", Phrases.modeOnDone(withDefrost, careful = false))
    }
}
