package com.halo.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RiskEngineTest {

    private val bridge = RouteSpot("b", "강변 교량", SpotKind.BRIDGE, 7.2, 1.8)
    private val tunnel = RouteSpot("t", "터널 출구", SpotKind.TUNNEL_EXIT, 15.8, 0.6)
    private val road = RouteSpot("r", "일반 도로", SpotKind.ROAD, 3.0, 1.0)
    private val morning = RoadWeather(minTempTodayC = -4.0, maxTempTodayC = 4.0, daysSincePrecip = 2)

    private fun ice(spot: RouteSpot, v: VehicleState, w: RoadWeather = morning): RouteHazard? =
        RiskEngine.evaluate(spot, v, w).firstOrNull { it.kind == HazardKind.ICE }

    @Test
    fun refrozenMorningMakesIceProneSpotsCaution() {
        val v = VehicleState(outsideTempC = -4.0)
        assertEquals(RiskLevel.CAUTION, ice(bridge, v)?.level)
        assertEquals(RiskLevel.CAUTION, ice(tunnel, v)?.level)
    }

    @Test
    fun plainRoadIsNotJudgedWithoutObservation() {
        assertNull(ice(road, VehicleState(outsideTempC = -4.0)))
    }

    @Test
    fun aboveFourDegreesThereIsNoIceRisk() {
        assertNull(ice(bridge, VehicleState(outsideTempC = 4.5, tempDrop10MinC = 5.0)))
    }

    @Test
    fun coldWithoutOtherSignsIsWatch() {
        val dry = RoadWeather(minTempTodayC = 2.0, maxTempTodayC = 8.0, daysSincePrecip = null)
        assertEquals(RiskLevel.WATCH, ice(bridge, VehicleState(outsideTempC = 3.0), dry)?.level)
    }

    @Test
    fun sharpDropOnBridgeIsCaution() {
        val h = ice(bridge, VehicleState(outsideTempC = 0.5, tempDrop10MinC = 3.0))!!
        assertEquals(RiskLevel.CAUTION, h.level)
        assertTrue(h.reasons.any { it.value.contains("10분 새") })
        assertTrue(h.reasons.any { it.label == "기상청 관측 구간" && it.value.startsWith("아님") })
    }

    @Test
    fun freezingAndWetIsDanger() {
        val v = VehicleState(outsideTempC = -1.0, wiperRunning = true)
        assertEquals(RiskLevel.DANGER, ice(tunnel, v, morning.copy(precipNow = Precip.SLEET))?.level)
    }

    @Test
    fun largeDiurnalRangeRaisesOneLevel() {
        val w = morning.copy(maxTempTodayC = 7.0) // 일교차 11℃
        assertEquals(RiskLevel.DANGER, ice(bridge, VehicleState(outsideTempC = -4.0), w)?.level)
    }

    @Test
    fun observedSpotUsesWeatherServiceLevel() {
        val observed = road.copy(observedIce = RiskLevel.DANGER)
        val h = ice(observed, VehicleState(outsideTempC = 10.0))!!
        assertEquals(RiskLevel.DANGER, h.level)
        assertTrue(h.observed)
    }

    @Test
    fun fogLevelsFollowVisibilityBands() {
        assertEquals(RiskLevel.WATCH, RiskEngine.fogLevel(800))
        assertEquals(RiskLevel.CAUTION, RiskEngine.fogLevel(300))
        assertEquals(RiskLevel.DANGER, RiskEngine.fogLevel(90))
        assertNull(RiskEngine.fogLevel(1200))
        assertEquals(50, RiskEngine.fogSlowDown(90))
        assertEquals(20, RiskEngine.fogSlowDown(300))
    }

    @Test
    fun heavyRainIsDangerWithHalfSpeed() {
        val storm = road.copy(rainMmPerHour = 32)
        val h = RiskEngine.evaluate(storm, VehicleState(outsideTempC = 23.0), morning).first { it.kind == HazardKind.HEAVY_RAIN }
        assertEquals(RiskLevel.DANGER, h.level)
        assertEquals(50, h.slowDownPercent)
    }

    @Test
    fun celsiusUsesMinusSign() {
        assertEquals("−4℃", RiskEngine.celsius(-4.0))
        assertEquals("0.5℃", RiskEngine.celsius(0.5))
    }
}
