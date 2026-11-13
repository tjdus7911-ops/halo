package com.halo.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AlertPlannerTest {

    private fun hazard(level: RiskLevel, at: Double = 5.0, nav: Boolean = false) = RouteHazard(
        RouteSpot("s", "교량", SpotKind.BRIDGE, at, 1.0, navAnnounced = nav), HazardKind.ICE, level, false, emptyList(), 20,
    )

    @Test
    fun cautionAlertsOnceAtOneKilometre() {
        val h = hazard(RiskLevel.CAUTION)
        assertTrue(AlertPlanner.due(listOf(h), 3.9, careful = false, fired = emptySet()).isEmpty())
        val due = AlertPlanner.due(listOf(h), 4.0, careful = false, fired = emptySet())
        assertEquals(1, due.size)
        val fired = due.map { it.key }.toSet()
        assertTrue(AlertPlanner.due(listOf(h), 4.8, careful = false, fired = fired).isEmpty())
    }

    @Test
    fun carefulModeAlertsAtOnePointFiveKilometres() {
        val h = hazard(RiskLevel.CAUTION)
        assertEquals(1, AlertPlanner.due(listOf(h), 3.5, careful = true, fired = emptySet()).size)
    }

    @Test
    fun dangerAlertsAgainAtThreeHundredMetres() {
        val h = hazard(RiskLevel.DANGER)
        val first = AlertPlanner.due(listOf(h), 4.0, false, emptySet()).single()
        assertEquals(AlertPlanner.Stage.FIRST, first.stage)
        val second = AlertPlanner.due(listOf(h), 4.75, false, setOf(first.key)).single()
        assertEquals(AlertPlanner.Stage.SECOND, second.stage)
    }

    @Test
    fun watchLevelIsScreenOnly() {
        assertTrue(AlertPlanner.due(listOf(hazard(RiskLevel.WATCH)), 4.5, false, emptySet()).isEmpty())
    }

    @Test
    fun navAnnouncedSpotIsAlertedOnlyOnce() {
        val h = hazard(RiskLevel.DANGER, nav = true)
        val first = AlertPlanner.due(listOf(h), 4.0, false, emptySet()).single()
        assertTrue(AlertPlanner.due(listOf(h), 4.8, false, setOf(first.key)).isEmpty())
    }

    @Test
    fun raisedLevelIsAnnouncedAgain() {
        val caution = hazard(RiskLevel.CAUTION)
        val fired = AlertPlanner.due(listOf(caution), 4.0, false, emptySet()).map { it.key }.toSet()
        val danger = AlertPlanner.due(listOf(hazard(RiskLevel.DANGER)), 4.2, false, fired)
        assertEquals(RiskLevel.DANGER, danger.single().hazard.level)
    }
}
