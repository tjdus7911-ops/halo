package com.halo.domain

import kotlin.math.roundToInt

enum class CheckLevel { OK, WARN, INFO }

/** 오늘의 악천후 차 상태 한 줄 */
data class CheckItem(val id: String, val label: String, val status: String, val level: CheckLevel, val note: String? = null)

/** 시동 브리핑 */
data class Briefing(
    val tempC: Double?,
    /** "이틀 전 비 · 진눈깨비 예보" */
    val weatherLine: String,
    /** 경로 위 위험 (관심 포함, 출발점에서 가까운 순) */
    val hazards: List<RouteHazard>,
    val delayMin: Int,
    val baseRemainingMin: Int?,
    /** 감속을 반영한 도착 시각 */
    val arrivalClock: String?,
    val checks: List<CheckItem>,
    val firstFrost: Boolean,
    val speech: String,
)

object BriefingPlanner {

    /** 위험 구간 앞에서 미리 속도를 줄이는 거리 */
    const val APPROACH_KM = 1.0

    /** 녹았다 언 길·눈길이면 경로 전체를 이만큼 천천히 달린다고 본다 */
    const val SLIPPERY_ROUTE_FACTOR = 0.10

    fun build(
        v: VehicleState,
        ctx: DriveContext,
        hazards: List<RouteHazard>,
        full: Boolean,
        careful: Boolean,
        firstFrost: Boolean,
    ): Briefing {
        val sorted = hazards.sortedBy { it.spot.atKm }
        val delay = delayMinutes(sorted, ctx.baseRemainingMin, ctx.weather)
        val arrival = ctx.baseRemainingMin?.let { Clock.plus(ctx.clock, it + delay) }
        val checks = checks(v, ctx.weather)
        val weatherLine = weatherLine(ctx.weather)
        val speech = Phrases.briefing(v, ctx, sorted, delay, checks, full, careful, firstFrost)
        return Briefing(v.outsideTempC, weatherLine, sorted, delay, ctx.baseRemainingMin, arrival, checks, firstFrost, speech)
    }

    fun weatherLine(w: RoadWeather): String {
        val parts = mutableListOf<String>()
        w.daysSincePrecip?.let { if (it in 0..RiskEngine.RECENT_PRECIP_DAYS) parts += RiskEngine.recentPrecipText(it, w) }
        if (w.precipNow == Precip.NONE && w.precipForecast != Precip.NONE) parts += "${RiskEngine.precipLabel(w.precipForecast)} 예보"
        parts += "최저 ${RiskEngine.celsius(w.minTempTodayC)} · 최고 ${RiskEngine.celsius(w.maxTempTodayC)}"
        return parts.joinToString(" · ")
    }

    /**
     * 감속으로 늦어지는 시간 (분).
     * 주의·위험 구간과 그 앞 1km에서 감속률(젖은 노면 20%, 결빙·가시거리 100m 이내 50%)을 지킨다고 보고,
     * 녹았다 언 길이거나 지금 눈·진눈깨비가 오면 경로 전체를 10% 천천히 달린다고 본다.
     */
    fun delayMinutes(hazards: List<RouteHazard>, baseRemainingMin: Int?, w: RoadWeather): Int {
        var minutes = 0.0
        hazards.filter { it.level != RiskLevel.WATCH }
            .groupBy { it.spot.id }
            .forEach { (_, list) ->
                val spot = list.first().spot
                val cut = list.maxOf { it.slowDownPercent } / 100.0
                if (cut <= 0.0) return@forEach
                val zone = spot.lengthKm + APPROACH_KM
                val v = spot.usualSpeedKmh
                minutes += (zone / (v * (1 - cut)) - zone / v) * 60.0
            }
        val days = w.daysSincePrecip
        val slippery = (days != null && days in 0..RiskEngine.RECENT_PRECIP_DAYS && w.minTempTodayC <= 0.0) ||
            w.precipNow == Precip.SNOW || w.precipNow == Precip.SLEET
        if (slippery && baseRemainingMin != null) minutes += baseRemainingMin * SLIPPERY_ROUTE_FACTOR
        return minutes.roundToInt()
    }

    /** 추운 날 전기차 배터리를 확인하라고 할 잔량 */
    const val COLD_BATTERY_PERCENT = 40

    /** 날씨와 관련된 항목만 ‘정상 / 확인 필요’로 */
    fun checks(v: VehicleState, w: RoadWeather? = null): List<CheckItem> {
        val out = mutableListOf<CheckItem>()
        // 눈 온 다음 날: 라이트·후미등·센서의 눈부터 (출발 전에만)
        if (w != null && w.lastPrecip == Precip.SNOW && (w.daysSincePrecip ?: Int.MAX_VALUE) <= 1 && !v.isDriving) {
            out += CheckItem("snow", "라이트·센서 눈", "먼저 치우기", CheckLevel.WARN, "쌓인 눈이 라이트·후미등·센서를 가리면 다른 차가 나를 못 봐요")
        }
        v.tireLow?.let {
            out += if (it) CheckItem("tire", "타이어 공기압", "확인 필요", CheckLevel.WARN, "추운 날은 공기압이 떨어져요")
            else CheckItem("tire", "타이어 공기압", "정상", CheckLevel.OK)
        }
        val temp = v.outsideTempC
        if (v.wiperMode == WiperMode.AUTO) {
            out += if (temp != null && temp <= 0.0) {
                CheckItem("wiper", "와이퍼 AUTO", "얼음 확인", CheckLevel.WARN, "유리에 얼어붙은 와이퍼를 바로 움직이면 고무가 찢어질 수 있어요")
            } else {
                CheckItem("wiper", "와이퍼 AUTO", "정상", CheckLevel.OK)
            }
        }
        v.washerLow?.let {
            out += if (it) CheckItem("washer", "워셔액", "보충 필요", CheckLevel.WARN, "눈·비 오는 날은 워셔액이 빨리 줄어요")
            else CheckItem("washer", "워셔액", "충분", CheckLevel.OK)
        }
        v.climateAuto?.let {
            out += if (it) CheckItem("defog", "오토 디포그", "작동 중", CheckLevel.OK)
            else CheckItem("defog", "오토 디포그", "꺼짐", CheckLevel.INFO, "공조를 AUTO로 두면 김서림을 알아서 막아요")
        }
        if (v.lowBeam.switch == LampSwitch.AUTO) out += CheckItem("light", "전조등", "오토 라이트", CheckLevel.OK)
        else if (v.lowBeam.switch == LampSwitch.OFF) out += CheckItem("light", "전조등", "꺼짐", CheckLevel.INFO, "AUTO로 두면 어두울 때 켜져요")
        v.batteryPercent?.let { pct ->
            val cold = (v.outsideTempC ?: Double.MAX_VALUE) <= 0.0
            out += if (cold && pct <= COLD_BATTERY_PERCENT) {
                CheckItem("battery", "배터리", "$pct% · 충전 확인", CheckLevel.WARN, "추운 날은 주행 가능 거리가 줄어요")
            } else {
                CheckItem("battery", "배터리", "$pct%", CheckLevel.OK)
            }
        }
        return out
    }
}

/** "07:40" 같은 화면 시각 계산 */
object Clock {
    fun plus(clock: String, minutes: Int): String {
        val parts = clock.split(":")
        val total = (parts[0].toInt() * 60 + parts[1].toInt() + minutes).mod(24 * 60)
        return "%02d:%02d".format(total / 60, total % 60)
    }
}
