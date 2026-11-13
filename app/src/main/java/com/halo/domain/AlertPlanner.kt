package com.halo.domain

/**
 * 살얼음·안개 레이더의 음성 경보 시점.
 * 관심: 화면에만 · 주의: 1km 전 1회 · 위험: 1km·300m 전 2회 (꼼꼼히 안내는 첫 경보를 1.5km 전에).
 * 같은 구간·같은 단계는 한 번만 알린다. 단계가 올라가면 새로 알린다.
 */
object AlertPlanner {

    const val SECOND_ALERT_KM = 0.3

    /** 위치 갱신 간격 때문에 생기는 오차 (50m) */
    private const val SLACK_KM = 0.05

    fun firstAlertKm(careful: Boolean): Double = if (careful) 1.5 else 1.0

    enum class Stage { FIRST, SECOND }

    data class Due(val hazard: RouteHazard, val stage: Stage, val distanceKm: Double) {
        val key: String get() = keyOf(hazard, stage)
    }

    fun keyOf(h: RouteHazard, stage: Stage): String = "${h.spot.id}:${h.kind}:${h.level}:$stage"

    /** 지금 알려야 할 경보 (가까운 순) */
    fun due(hazards: List<RouteHazard>, progressKm: Double, careful: Boolean, fired: Set<String>): List<Due> {
        val first = firstAlertKm(careful)
        val out = mutableListOf<Due>()
        for (h in hazards) {
            if (h.level == RiskLevel.WATCH) continue
            if (progressKm >= h.spot.endKm) continue // 이미 지난 구간
            val distance = h.spot.atKm - progressKm
            if (distance > first + SLACK_KM) continue
            val firstKey = keyOf(h, Stage.FIRST)
            if (firstKey !in fired) {
                out += Due(h, Stage.FIRST, distance)
                continue
            }
            // 내비가 이미 알리는 구간은 짧게 한 번만
            if (h.level == RiskLevel.DANGER && !h.spot.navAnnounced && distance <= SECOND_ALERT_KM + SLACK_KM) {
                val secondKey = keyOf(h, Stage.SECOND)
                if (secondKey !in fired) out += Due(h, Stage.SECOND, distance)
            }
        }
        return out.sortedBy { it.distanceKm }
    }

    /** 경보를 띄운 채로 둘 구간: 지점을 지날 때까지 */
    fun stillRelevant(h: RouteHazard, progressKm: Double): Boolean = progressKm < h.spot.endKm
}
