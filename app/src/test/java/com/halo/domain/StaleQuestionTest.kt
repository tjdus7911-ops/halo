package com.halo.domain

import com.halo.demo.DemoScenarios
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 대답하지 않은 제안은 화면에 남기되, 그 이유(비·눈·안개·급감속)가 사라지거나 도착하면 거둔다 */
class StaleQuestionTest {

    /** 대답하지 않고 끝까지 달렸을 때 장면마다 화면에 남은 질문의 키 */
    private fun keysByFrame(id: String): List<Pair<String, String?>> {
        val d = TestDrive(DemoScenarios.byId(id))
        val out = mutableListOf<Pair<String, String?>>()
        do {
            out += d.run.frame.label to d.step().question?.key
        } while (d.next())
        return out
    }

    @Test
    fun winterHeadlightQuestionLeavesOnArrival() {
        val frames = keysByFrame("winter")
        assertEquals(frames.toString(), "lamp-on:LOW_BEAM", frames[4].second)
        assertNull(frames.toString(), frames.last().second)
    }

    @Test
    fun monsoonHazardQuestionLeavesWhenSpeedRecovers() {
        val frames = keysByFrame("monsoon")
        assertEquals(frames.toString(), "lamp-on:HAZARD", frames[3].second)
        assertFalse(frames.toString(), frames[4].second == "lamp-on:HAZARD")
        assertNull(frames.toString(), frames.last().second)
    }

    @Test
    fun fogLightQuestionLeavesWhenFogEnds() {
        val frames = keysByFrame("fog")
        assertTrue(frames.toString(), frames[3].second?.startsWith("lamp-on:FRONT_FOG") == true)
        assertNull(frames.toString(), frames[4].second)
        assertNull(frames.toString(), frames.last().second)
    }

    @Test
    fun rainModeQuestionLeavesWhenRainStops() {
        val e = HaloEngine(HaloSettings(carefulOffered = true), today = "2027-07-10", season = "2026-27", offerCareful = false)
        val weather = RoadWeather(minTempTodayC = 22.0, maxTempTodayC = 28.0, daysSincePrecip = 0, precipNow = Precip.RAIN)
        val wet = VehicleState(
            gear = Gear.DRIVE, speedKmh = 50.0, outsideTempC = 23.0,
            wiperMode = WiperMode.CONTINUOUS, wiperRunning = true, wiperRunningMin = 12,
            lowBeam = LampInfo(switch = LampSwitch.AUTO, lit = true),
            mirrorHeat = HeaterInfo(level = 0, controllable = true),
        )
        val ask = e.update(wet, DriveContext("19:20", weather = weather), 0L).filterIsInstance<Effect.Ask>().single()
        assertEquals("mode-on:RAIN", ask.question.key)
        e.noAnswer(ask.question.id, 2_000L)
        assertEquals("mode-on:RAIN", e.snapshot().question?.key)

        val dry = wet.copy(wiperMode = WiperMode.AUTO, wiperRunning = false, wiperRunningMin = 0)
        e.update(dry, DriveContext("19:40", weather = weather.copy(precipNow = Precip.NONE)), 20 * 60_000L)
        assertNull(e.snapshot().question)
    }

    @Test
    fun answeredLampStillAsksToTurnOffLater() {
        val d = TestDrive(DemoScenarios.byId("monsoon"), answers = mapOf("lamp-on:HAZARD" to Answer.YES, "lamp-off:HAZARD" to Answer.YES))
        d.playAll()
        assertTrue(d.asked.map { it.key }.toString(), d.asked.any { it.key == "lamp-off:HAZARD" })
    }
}
