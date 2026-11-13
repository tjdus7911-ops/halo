package com.halo.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GleoCommandTest {

    @Test
    fun parsesModeFromParams() {
        assertEquals(GleoCommand.SetWeatherMode(WeatherMode.SNOW), GleoCommand.parse("SET_WEATHER_MODE", GleoParams.parse("{\"mode\":\"snow\"}")))
        assertEquals(GleoCommand.SetWeatherMode(null), GleoCommand.parse("SET_WEATHER_MODE", mapOf("mode" to "OFF")))
        assertNull(GleoCommand.parse("SET_WEATHER_MODE", emptyMap()))
        assertEquals(GleoCommand.RouteRisk, GleoCommand.parse("CHECK_ROAD_RISK", emptyMap()))
        assertNull(GleoCommand.parse("UNKNOWN", emptyMap()))
    }

    @Test
    fun readsWrappedParams() {
        assertEquals(mapOf("mode" to "RAIN"), GleoParams.parse("\"{\\\"mode\\\":\\\"RAIN\\\"}\""))
        assertEquals(mapOf("mode" to "RAIN"), GleoParams.parse("\"{\"mode\":\"RAIN\"}\""))
        assertEquals(emptyMap<String, String>(), GleoParams.parse("not json"))
    }

    @Test
    fun responseJsonEscapes() {
        val json = GleoResponse.json("CHECK_ROAD_RISK", GleoResult.success("Say \"hi\"", data = mapOf("summary" to "다리 위")))
        assertEquals("{\"functionName\":\"CHECK_ROAD_RISK\",\"status\":\"success\",\"message\":\"Say \\\"hi\\\"\",\"data\":\"[{\\\"summary\\\":\\\"다리 위\\\"}]\"}", json)
    }

    @Test
    fun routeRiskNamesNearestSpot() {
        val bridge = RouteSpot("b", "교량", SpotKind.BRIDGE, atKm = 2.0, lengthKm = 1.0)
        val h = RouteHazard(bridge, HazardKind.ICE, RiskLevel.CAUTION, observed = false, reasons = emptyList(), slowDownPercent = 20)
        val r = GleoReplies.routeRisk(listOf(HazardAhead(h, 1.0)), routeKnown = true)
        assertEquals("There is one risky spot ahead; the nearest is possible black ice at the bridge 1 km ahead (caution).", r.message)
        assertTrue(r.screenText!!.contains("다리 위 살얼음 주의"))
    }
}
