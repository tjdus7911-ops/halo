package com.halo.domain

/** 위험 단계: 기상청 도로위험기상정보와 같은 3단계 (관심 · 주의 · 위험) */
enum class RiskLevel(val label: String) {
    WATCH("관심"),
    CAUTION("주의"),
    DANGER("위험");

    fun raised(): RiskLevel = entries.getOrElse(ordinal + 1) { DANGER }
}

enum class HazardKind(val label: String) {
    ICE("살얼음"),
    FOG("안개"),
    HEAVY_RAIN("폭우"),
}

/** 결빙이 먼저 생기는 도로 구조 */
enum class SpotKind(val label: String, val iceProne: Boolean) {
    BRIDGE("교량", true),
    TUNNEL_EXIT("터널 출구", true),
    ICE_HOTSPOT("결빙 사고 다발지", true),
    /** 내비가 알리는 미끄럼 주의 지점 */
    SLIPPERY("미끄럼 주의 구간", true),
    RIVERSIDE("강변 도로", false),
    ROAD("도로", false),
}

enum class Precip { NONE, RAIN, SLEET, SNOW }

/**
 * 경로 위의 위험 후보 지점.
 * 관측 구간(기상청 도로 관측이 있는 곳)은 기상청 단계를 그대로 쓰고, 관측 공백은 내 차 센서로 판단한다.
 */
data class RouteSpot(
    val id: String,
    val name: String,
    val kind: SpotKind,
    /** 출발점부터 지점 시작까지의 거리 */
    val atKm: Double,
    /** 위험 구간의 길이 */
    val lengthKm: Double,
    /** 이 구간의 평소 주행 속도 (지연 시간 계산용) */
    val usualSpeedKmh: Double = 60.0,
    /** 기상청 도로 살얼음 관측 단계. 관측 구간이 아니면 null */
    val observedIce: RiskLevel? = null,
    /** 기상청 도로 가시거리(m). 정보가 없으면 null */
    val visibilityM: Int? = null,
    /** 시간당 강수량(mm). 정보가 없으면 null */
    val rainMmPerHour: Int? = null,
    /** 내비가 이미 음성으로 알리는 구간이면 짧게 한 번만 알린다 */
    val navAnnounced: Boolean = false,
) {
    val endKm: Double get() = atKm + lengthKm
}

/** 오늘 경로의 날씨 문맥: 예보와 최근 강수, 차량 센서로 본 현재 상태 */
data class RoadWeather(
    val minTempTodayC: Double,
    val maxTempTodayC: Double,
    /** 최근 눈·비가 그친 뒤 지난 날짜 (5일 안에 없으면 null, 오늘 내렸으면 0) */
    val daysSincePrecip: Int?,
    /** 최근에 내린 것 (비 / 눈 / 진눈깨비) */
    val lastPrecip: Precip = Precip.RAIN,
    val precipNow: Precip = Precip.NONE,
    val precipForecast: Precip = Precip.NONE,
) {
    val diurnalRangeC: Double get() = maxTempTodayC - minTempTodayC
}

enum class Gear { PARK, REVERSE, NEUTRAL, DRIVE, UNKNOWN }

enum class TurnSignal { OFF, LEFT, RIGHT }

enum class WiperMode { OFF, AUTO, INTERMITTENT, CONTINUOUS, OTHER }

/** 등화 스위치 위치 */
enum class LampSwitch { OFF, ON, AUTO, DAYTIME, UNKNOWN }

data class LampInfo(
    /** 이 차에 있는지 (안개등이 없는 차종이 있다) */
    val available: Boolean = true,
    val switch: LampSwitch = LampSwitch.UNKNOWN,
    /** 실제로 켜져 있는지 (오토 라이트는 스위치가 AUTO여도 꺼져 있을 수 있다) */
    val lit: Boolean? = null,
    /** 앱에서 켜고 끌 수 있는지 (차량 제어 권한이 승인된 차량) */
    val controllable: Boolean = false,
) {
    val isOn: Boolean get() = lit ?: (switch == LampSwitch.ON)
}

data class HeaterInfo(
    val available: Boolean = true,
    /** 단계 (0 = 꺼짐). 모르면 null */
    val level: Int? = null,
    val controllable: Boolean = false,
) {
    val isOn: Boolean get() = (level ?: 0) > 0
}

/** 차량에서 읽은 값. 차종마다 없는 값은 null 로 둔다. */
data class VehicleState(
    val ignitionOn: Boolean = true,
    val gear: Gear = Gear.UNKNOWN,
    val speedKmh: Double = 0.0,
    val outsideTempC: Double? = null,
    /** 최근 10분 사이 외기온도가 떨어진 폭 (양수 = 떨어짐) */
    val tempDrop10MinC: Double = 0.0,
    val turnSignal: TurnSignal = TurnSignal.OFF,
    val wiperMode: WiperMode = WiperMode.OFF,
    val wiperRunning: Boolean = false,
    /** 와이퍼가 쉬지 않고 움직인 시간 (분) */
    val wiperRunningMin: Int = 0,
    val lowBeam: LampInfo = LampInfo(),
    val frontFog: LampInfo = LampInfo(),
    val mirrorHeat: HeaterInfo = HeaterInfo(),
    val steeringHeat: HeaterInfo = HeaterInfo(),
    /** 공조 AUTO (현대차는 AUTO 공조에서 오토 디포그가 함께 동작) */
    val climateAuto: Boolean? = null,
    val frontDefrost: Boolean? = null,
    val defrostControllable: Boolean = false,
    val washerLow: Boolean? = null,
    val tireLow: Boolean? = null,
) {
    val isDriving: Boolean get() = gear == Gear.DRIVE || speedKmh > 3.0
}

/** 경로 위의 위험 판단 결과 */
data class RouteHazard(
    val spot: RouteSpot,
    val kind: HazardKind,
    val level: RiskLevel,
    /** 기상청 관측으로 판단했는지 (false 면 내 차 센서로 판단) */
    val observed: Boolean,
    /** 화면에 보여 줄 판단 근거 */
    val reasons: List<Reason>,
    /** 법정·권고 감속률 (%) */
    val slowDownPercent: Int,
)

/** 우회 경로 (시연 시나리오나 내비가 대체 경로를 줄 때) */
data class Detour(val extraMin: Int, val avoidsSpotIds: Set<String>, val label: String)

/** 지금 주행의 위치와 경로 */
data class DriveContext(
    /** 화면에 보이는 시각 */
    val clock: String,
    /** 출발점부터 온 거리 */
    val progressKm: Double = 0.0,
    /** 남은 시간 (감속 없이) */
    val baseRemainingMin: Int? = null,
    val weather: RoadWeather,
    val spots: List<RouteSpot> = emptyList(),
    val detour: Detour? = null,
    /** 경로·관측 정보를 쓸 수 있는지 (시연 시나리오 또는 연결된 데이터) */
    val routeKnown: Boolean = spots.isNotEmpty(),
    /** 목적지에 도착했는지 */
    val arrived: Boolean = false,
    /** 경로 전체 길이 (모르면 null) */
    val routeLengthKm: Double? = null,
)

data class HaloSettings(
    /** 꼼꼼히 안내: 더 일찍, 천천히, 이유를 덧붙여, 못 들으면 한 번 더 */
    val careful: Boolean = false,
    /** 첫 실행 때 꼼꼼히 안내를 권했는지 */
    val carefulOffered: Boolean = false,
    /** 마지막으로 전체 브리핑을 들려준 날 (yyyy-MM-dd) */
    val lastFullBriefingDay: String? = null,
    /** 올겨울 첫 영하 안내를 한 겨울 (예: "2026-27") */
    val firstFrostSeason: String? = null,
)
