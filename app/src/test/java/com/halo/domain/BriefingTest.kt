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

/** 기획서 ②: 눈 온 다음 날 체크 · 출발 전 자동 기능 점검 · 전기차 배터리 */
class BriefingChecksTest {

    private val parked = VehicleState(gear = Gear.PARK, outsideTempC = -6.0)

    @Test
    fun snowOvernightAsksToClearLightsFirst() {
        val w = RoadWeather(minTempTodayC = -6.0, maxTempTodayC = 1.0, daysSincePrecip = 0, lastPrecip = Precip.SNOW)
        val checks = BriefingPlanner.checks(parked, w)
        assertEquals("snow", checks.first().id)
        val b = BriefingPlanner.build(parked, DriveContext("07:30", weather = w), emptyList(), full = true, careful = false, firstFrost = false)
        assertTrue(b.speech, "밤사이 눈이 와서 라이트와 후미등, 센서의 눈부터 치워 주세요." in b.speech)
        // 달리는 중에는 묻지 않는다
        assertTrue(BriefingPlanner.checks(parked.copy(gear = Gear.DRIVE, speedKmh = 40.0), w).none { it.id == "snow" })
    }

    @Test
    fun autoFeaturesOffAreMentionedBeforeDeparture() {
        val v = parked.copy(climateAuto = false, lowBeam = LampInfo(switch = LampSwitch.OFF))
        val w = RoadWeather(minTempTodayC = -6.0, maxTempTodayC = 1.0, daysSincePrecip = null)
        val b = BriefingPlanner.build(v, DriveContext("07:30", weather = w), emptyList(), full = true, careful = false, firstFrost = false)
        assertTrue(b.speech, "전조등을 AUTO로 두고 출발하세요." in b.speech)
        assertTrue(b.speech, "공조를 AUTO로 두면 김서림을 알아서 막아요." in b.speech)
        // 요약 브리핑에서는 말하지 않는다
        val short = BriefingPlanner.build(v, DriveContext("07:30", weather = w), emptyList(), full = false, careful = false, firstFrost = false)
        assertTrue(short.speech, "AUTO로 두고" !in short.speech)
    }

    @Test
    fun coldLowBatteryIsAWarning() {
        val low = BriefingPlanner.checks(parked.copy(batteryPercent = 32))
        assertEquals("32% · 충전 확인", low.single { it.id == "battery" }.status)
        val fine = BriefingPlanner.checks(parked.copy(batteryPercent = 80))
        assertEquals(CheckLevel.OK, fine.single { it.id == "battery" }.level)
        assertTrue(BriefingPlanner.checks(parked).none { it.id == "battery" })
        val b = BriefingPlanner.build(parked.copy(batteryPercent = 32), DriveContext("07:30", weather = RoadWeather(-6.0, 1.0, null)), emptyList(), full = true, careful = false, firstFrost = false)
        assertTrue(b.speech, "배터리가 32%예요. 추운 날은 주행 가능 거리가 줄어요." in b.speech)
    }
}
