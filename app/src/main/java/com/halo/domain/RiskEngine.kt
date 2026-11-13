package com.halo.domain

import kotlin.math.abs
import kotlin.math.roundToInt

/** 화면에 보여 줄 판단 근거 한 줄 */
data class Reason(val label: String, val value: String, val warn: Boolean = false)

/**
 * 살얼음·안개·폭우 위험 단계 판단.
 *
 * 1. 관측 구간은 기상청 살얼음·가시거리 단계를 그대로 쓴다.
 * 2. 관측소 없는 길은 결빙이 먼저 생기는 구조(교량·터널 출구·결빙 사고 다발지)에서 내 차 외기온도·와이퍼로 직접 판단한다.
 * 3. 최근 5일 안에 눈·비가 왔고 아침이 영하이며 일교차가 9℃를 넘으면 한 단계 올린다.
 */
object RiskEngine {

    /** 현대차 계기판 '도로 결빙 알림'과 같은 기준 (실제 노면은 기온보다 2~3℃ 낮다) */
    const val ICE_TEMP_C = 4.0
    const val SHARP_DROP_C = 3.0
    const val DIURNAL_RANGE_C = 9.0
    const val RECENT_PRECIP_DAYS = 5

    fun evaluateRoute(spots: List<RouteSpot>, vehicle: VehicleState, weather: RoadWeather): List<RouteHazard> =
        spots.flatMap { evaluate(it, vehicle, weather) }

    fun evaluate(spot: RouteSpot, vehicle: VehicleState, weather: RoadWeather): List<RouteHazard> =
        listOfNotNull(fog(spot), rain(spot), ice(spot, vehicle, weather))

    /** 기상청 도로 가시거리 단계: 관심 1,000~500m · 주의 500~200m · 위험 200m 미만 */
    fun fogLevel(visibilityM: Int): RiskLevel? = when {
        visibilityM < 200 -> RiskLevel.DANGER
        visibilityM < 500 -> RiskLevel.CAUTION
        visibilityM < 1000 -> RiskLevel.WATCH
        else -> null
    }

    /** 가시거리 100m 이내는 법정 50% 감속(도로교통법 시행규칙 제19조), 500m 미만은 20% 감속 권고 */
    fun fogSlowDown(visibilityM: Int): Int = when {
        visibilityM <= 100 -> 50
        visibilityM < 500 -> 20
        else -> 0
    }

    private fun fog(spot: RouteSpot): RouteHazard? {
        val vis = spot.visibilityM ?: return null
        val level = fogLevel(vis) ?: return null
        return RouteHazard(
            spot = spot,
            kind = HazardKind.FOG,
            level = level,
            observed = true,
            reasons = listOf(
                Reason("기상청 도로 가시거리", "${vis}m · ‘${level.label}’", warn = level != RiskLevel.WATCH),
                Reason(spot.kind.label, spot.name),
            ),
            slowDownPercent = fogSlowDown(vis),
        )
    }

    /** 시간당 30mm 이상은 폭우(50% 감속 권고), 15mm 이상은 젖은 노면 법정 20% 감속 */
    private fun rain(spot: RouteSpot): RouteHazard? {
        val mm = spot.rainMmPerHour ?: return null
        val level = when {
            mm >= 30 -> RiskLevel.DANGER
            mm >= 15 -> RiskLevel.CAUTION
            mm >= 5 -> RiskLevel.WATCH
            else -> return null
        }
        return RouteHazard(
            spot = spot,
            kind = HazardKind.HEAVY_RAIN,
            level = level,
            observed = true,
            reasons = listOf(Reason("강수", "시간당 ${mm}mm", warn = level == RiskLevel.DANGER)),
            slowDownPercent = if (level == RiskLevel.DANGER) 50 else 20,
        )
    }

    private fun ice(spot: RouteSpot, vehicle: VehicleState, weather: RoadWeather): RouteHazard? {
        spot.observedIce?.let { level ->
            return RouteHazard(
                spot = spot,
                kind = HazardKind.ICE,
                level = level,
                observed = true,
                reasons = listOf(Reason("기상청 관측 구간", "살얼음 ‘${level.label}’", warn = level != RiskLevel.WATCH)),
                slowDownPercent = slowDownFor(level),
            )
        }
        if (!spot.kind.iceProne) return null
        val temp = vehicle.outsideTempC ?: return null
        if (temp > ICE_TEMP_C) return null

        val wet = vehicle.wiperRunning || weather.precipNow != Precip.NONE
        val days = weather.daysSincePrecip
        val refrozen = days != null && days in 0..RECENT_PRECIP_DAYS && weather.minTempTodayC <= 0.0
        val sharpDrop = vehicle.tempDrop10MinC >= SHARP_DROP_C

        val reasons = mutableListOf(Reason("기상청 관측 구간", "아님 · 내 차가 직접 판단"))
        val tempText = if (sharpDrop) "${celsius(temp)} (10분 새 −${celsius(vehicle.tempDrop10MinC)})" else celsius(temp)
        reasons += Reason("차량 외기온도", tempText, warn = sharpDrop || temp <= 0.0)
        if (refrozen) reasons += Reason(recentPrecipText(days!!, weather) + " · 영하 아침", "녹았다 언 길", warn = true)
        if (wet) reasons += Reason(if (weather.precipNow == Precip.NONE) "와이퍼 작동" else precipLabel(weather.precipNow), "젖은 노면", warn = temp <= 0.0)
        reasons += if (spot.kind == SpotKind.SLIPPERY) Reason("내비 주의 지점", spot.kind.label) else Reason("${spot.kind.label} 구간", structureNote(spot.kind))

        var level = when {
            temp <= 0.0 && wet -> RiskLevel.DANGER
            sharpDrop || refrozen || wet -> RiskLevel.CAUTION
            else -> RiskLevel.WATCH
        }
        if (refrozen && weather.diurnalRangeC > DIURNAL_RANGE_C) {
            level = level.raised()
            reasons += Reason("일교차", celsius(weather.diurnalRangeC), warn = true)
        }
        return RouteHazard(spot, HazardKind.ICE, level, observed = false, reasons = reasons, slowDownPercent = slowDownFor(level))
    }

    /** 노면이 얼어붙은 경우 법정 50% 감속, 젖은 노면 20% */
    private fun slowDownFor(level: RiskLevel): Int = when (level) {
        RiskLevel.DANGER -> 50
        RiskLevel.CAUTION -> 20
        RiskLevel.WATCH -> 0
    }

    private fun structureNote(kind: SpotKind): String = when (kind) {
        SpotKind.BRIDGE -> "노면이 먼저 얼어요"
        SpotKind.TUNNEL_EXIT -> "그늘이라 먼저 얼어요"
        SpotKind.ICE_HOTSPOT -> "결빙 사고가 잦은 곳"
        else -> ""
    }

    /** "이틀 전 비", "어제 눈", "지금 진눈깨비" */
    fun recentPrecipText(days: Int, weather: RoadWeather): String {
        if (days == 0 && weather.precipNow != Precip.NONE) return "지금 ${precipLabel(weather.precipNow)}"
        val what = precipLabel(weather.lastPrecip)
        return when (days) {
            0 -> "오늘 $what"
            1 -> "어제 $what"
            2 -> "이틀 전 $what"
            3 -> "사흘 전 $what"
            else -> "${days}일 전 $what"
        }
    }

    fun precipLabel(p: Precip): String = when (p) {
        Precip.RAIN -> "비"
        Precip.SLEET -> "진눈깨비"
        Precip.SNOW -> "눈"
        Precip.NONE -> "맑음"
    }

    /** 0.5 → "0.5℃", -4.0 → "−4℃" (화면용 마이너스 기호) */
    fun celsius(value: Double): String {
        val rounded = (value * 10).roundToInt() / 10.0
        val body = if (rounded % 1.0 == 0.0) abs(rounded).toInt().toString() else abs(rounded).toString()
        return (if (rounded < 0) "−" else "") + body + "℃"
    }
}
