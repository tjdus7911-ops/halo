package com.halo.domain

/** 내비(Pleos NaviHelper)에서 받은 값. 연결 전이거나 경로가 없으면 비어 있다. */
data class NaviInfo(
    val destinationName: String? = null,
    /** 목적지까지 남은 거리 (m) */
    val remainM: Int? = null,
    /** 목적지까지 남은 시간 (초) */
    val remainSec: Int? = null,
    val arrived: Boolean = false,
    /** 내비가 알리는 주의 지점 */
    val cautions: List<NaviCaution> = emptyList(),
)

/** 내비 주의 지점: [type] 은 내비의 종류 이름 (예: SLIPPERY_ROAD_ALERT), [remainM] 은 받을 때 남은 거리 */
data class NaviCaution(val id: String, val type: String, val remainM: Int)

/**
 * 실제 차량으로 달릴 때의 주행 문맥.
 * 경로의 기상청 관측(도로 살얼음·가시거리)은 아직 연결하지 않았으므로 차량 센서(외기온도·와이퍼)와
 * 내비의 미끄럼 주의 지점만으로 판단한다. 지나온 거리는 속도를 시간에 대해 더해 구한다.
 */
class LiveTrip {
    private var progressKm = 0.0
    private var lastMs: Long? = null
    private var minTemp: Double? = null
    private var maxTemp: Double? = null
    private val spots = LinkedHashMap<String, RouteSpot>()

    fun update(nowMs: Long, clock: String, v: VehicleState, navi: NaviInfo?): DriveContext {
        lastMs?.let { last ->
            val dt = (nowMs - last).coerceIn(0L, MAX_STEP_MS)
            progressKm += v.speedKmh * dt / 3_600_000.0
        }
        lastMs = nowMs
        v.outsideTempC?.let { t ->
            minTemp = minOf(minTemp ?: t, t)
            maxTemp = maxOf(maxTemp ?: t, t)
        }
        navi?.cautions.orEmpty().forEach { c ->
            if (c.type !in SLIPPERY_TYPES || c.id in spots) return@forEach
            spots[c.id] = RouteSpot(
                id = "navi-${c.id}",
                name = SpotKind.SLIPPERY.label,
                kind = SpotKind.SLIPPERY,
                atKm = progressKm + c.remainM.coerceAtLeast(0) / 1000.0,
                lengthKm = SPOT_LENGTH_KM,
                navAnnounced = true,
            )
        }
        spots.entries.removeAll { progressKm > it.value.endKm + PASSED_KEEP_KM }
        val temp = v.outsideTempC
        return DriveContext(
            clock = clock,
            progressKm = progressKm,
            baseRemainingMin = navi?.remainSec?.takeIf { it > 0 }?.let { (it + 59) / 60 },
            weather = RoadWeather(
                minTempTodayC = minTemp ?: temp ?: MILD_C,
                maxTempTodayC = maxTemp ?: temp ?: MILD_C,
                daysSincePrecip = null,
            ),
            spots = spots.values.toList(),
            routeKnown = false,
            arrived = navi?.arrived == true,
            routeLengthKm = navi?.remainM?.takeIf { it > 0 }?.let { progressKm + it / 1000.0 },
        )
    }

    companion object {
        /** 화면이 멈췄다 돌아온 경우 등, 한 번에 더하는 시간의 상한 */
        const val MAX_STEP_MS = 5_000L
        const val SPOT_LENGTH_KM = 0.5
        const val PASSED_KEEP_KM = 1.0
        private const val MILD_C = 15.0
        val SLIPPERY_TYPES = setOf("SLIPPERY_ROAD_ALERT")
    }
}
