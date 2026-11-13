package com.halo.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 실제 차량 주행: 내비 주의 지점과 차량 센서만으로 판단한다 */
class LiveTripTest {

    private val driving = VehicleState(gear = Gear.DRIVE, speedKmh = 72.0, outsideTempC = 1.0)

    @Test
    fun progressFollowsSpeed() {
        val trip = LiveTrip()
        trip.update(0L, "08:00", driving, null)
        var ctx = trip.update(1_000L, "08:00", driving, null)
        repeat(49) { ctx = trip.update(2_000L + it * 1_000L, "08:00", driving, null) }
        // 72km/h 로 50초 = 1km
        assertEquals(1.0, ctx.progressKm, 0.01)
        assertFalse(ctx.routeKnown)
    }

    @Test
    fun longPauseCountsOnlyFiveSeconds() {
        val trip = LiveTrip()
        trip.update(0L, "08:00", driving, null)
        val ctx = trip.update(60_000L, "08:01", driving, null)
        assertEquals(0.1, ctx.progressKm, 0.001)
    }

    @Test
    fun slipperyCautionBecomesSpotOnce() {
        val trip = LiveTrip()
        val navi = NaviInfo(remainM = 12_000, remainSec = 900, cautions = listOf(NaviCaution("c1", "SLIPPERY_ROAD_ALERT", 1_000), NaviCaution("c2", "SPEED_BUMP", 300)))
        val ctx = trip.update(0L, "08:00", driving, navi)
        assertEquals(1, ctx.spots.size)
        val spot = ctx.spots.single()
        assertEquals(SpotKind.SLIPPERY, spot.kind)
        assertEquals(1.0, spot.atKm, 0.001)
        assertTrue(spot.navAnnounced)
        assertEquals(15, ctx.baseRemainingMin)
        // 같은 지점이 다시 와도 처음 위치를 지킨다
        val again = trip.update(1_000L, "08:00", driving, navi.copy(cautions = listOf(NaviCaution("c1", "SLIPPERY_ROAD_ALERT", 980))))
        assertEquals(1.0, again.spots.single().atKm, 0.001)
    }

    @Test
    fun engineWarnsAtSlipperySpotWhenCold() {
        val trip = LiveTrip()
        val engine = HaloEngine(HaloSettings(carefulOffered = true, lastFullBriefingDay = "2027-01-12", firstFrostSeason = "2026-27"), "2027-01-12", "2026-27", offerCareful = false)
        val navi = NaviInfo(cautions = listOf(NaviCaution("c1", "SLIPPERY_ROAD_ALERT", 900)))
        val spoken = mutableListOf<String>()
        // 1℃, 와이퍼 작동(젖은 노면) → 주의
        val wet = driving.copy(wiperRunning = true)
        val fx = engine.update(wet, trip.update(0L, "08:00", wet, navi), 0L)
        fx.filterIsInstance<Effect.Speak>().forEach { spoken += it.text }
        assertTrue(spoken.toString(), spoken.any { it.startsWith("900미터 앞 미끄럼 주의 구간이에요.") })
        val alert = engine.snapshot().alert
        assertEquals(SpotKind.SLIPPERY, alert?.hazard?.spot?.kind)
    }

    @Test
    fun noRouteStillGivesGenericIceWarning() {
        val trip = LiveTrip()
        val engine = HaloEngine(HaloSettings(carefulOffered = true, lastFullBriefingDay = "2027-01-12", firstFrostSeason = "2026-27"), "2027-01-12", "2026-27", offerCareful = false)
        val cold = driving.copy(outsideTempC = 0.5, tempDrop10MinC = 3.5)
        val fx = engine.update(cold, trip.update(0L, "08:00", cold, null), 0L)
        assertTrue(fx.filterIsInstance<Effect.Speak>().any { it.text.startsWith("기온이 10분 새") })
    }
}
