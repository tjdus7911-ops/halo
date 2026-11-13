package com.halo.domain

/** 비·눈 모드 */
enum class WeatherMode(val label: String) {
    SNOW("눈 모드"),
    RAIN("비 모드"),
}

/** 앱이 켜고 끄는 열선·공조 */
enum class Heater(val label: String) {
    MIRROR("사이드미러 열선"),
    STEERING("핸들 열선"),
    DEFROST("앞유리 김서림 방지"),
}

/** 날씨에 맞춰 묻는 등화 */
enum class Lamp(val label: String) {
    LOW_BEAM("전조등"),
    FRONT_FOG("안개등"),
    HAZARD("비상등"),
}

data class PlannedHeater(val heater: Heater, val note: String)

/** 자동으로 이미 돌고 있어 건드리지 않는 기능 */
data class KeptAuto(val label: String, val note: String)

data class ModeProposal(
    val mode: WeatherMode,
    val turnOn: List<PlannedHeater>,
    val keep: List<KeptAuto>,
    /** 판단 근거 (화면 칩) */
    val evidence: List<String>,
)

/**
 * 비·눈 모드: 차가 자동으로 하는 기능(오토 디포그·오토 라이트)은 그대로 두고, 자동이 없는 것만 골라 제안한다.
 */
object WeatherModePlanner {

    /** 비 모드는 와이퍼가 이만큼 쉬지 않고 움직이면 묻는다 */
    const val RAIN_WIPER_MIN = 10

    fun trigger(v: VehicleState, w: RoadWeather): WeatherMode? {
        val temp = v.outsideTempC
        val snowy = w.precipNow == Precip.SNOW || w.precipNow == Precip.SLEET ||
            w.precipForecast == Precip.SNOW || w.precipForecast == Precip.SLEET
        if (temp != null && temp <= 2.0 && (snowy || v.wiperRunningMin >= 3)) return WeatherMode.SNOW
        if (v.wiperRunningMin >= RAIN_WIPER_MIN) return WeatherMode.RAIN
        return null
    }

    fun propose(mode: WeatherMode, v: VehicleState, w: RoadWeather): ModeProposal? {
        val on = mutableListOf<PlannedHeater>()
        if (v.mirrorHeat.available && !v.mirrorHeat.isOn) on += PlannedHeater(Heater.MIRROR, "자동으로 켜지지 않아요")
        if (mode == WeatherMode.SNOW && v.steeringHeat.available && !v.steeringHeat.isOn) {
            on += PlannedHeater(Heater.STEERING, "지금 꺼져 있어요")
        }
        if (v.climateAuto == false && v.frontDefrost == false) on += PlannedHeater(Heater.DEFROST, "공조 AUTO가 꺼져 있어요")
        if (on.isEmpty()) return null

        val keep = mutableListOf<KeptAuto>()
        if (v.climateAuto == true) keep += KeptAuto("오토 디포그", "공조 AUTO로 작동 중")
        if (v.lowBeam.switch == LampSwitch.AUTO) keep += KeptAuto("전조등", "오토 라이트")

        val evidence = mutableListOf<String>()
        v.outsideTempC?.let { evidence += "외기 ${RiskEngine.celsius(it)}" }
        when {
            w.precipNow != Precip.NONE -> evidence += "지금 ${RiskEngine.precipLabel(w.precipNow)}"
            w.precipForecast != Precip.NONE -> evidence += "${RiskEngine.precipLabel(w.precipForecast)} 예보"
        }
        if (v.wiperRunningMin >= 3) evidence += "와이퍼 ${v.wiperRunningMin}분째 작동"
        if ((v.outsideTempC ?: 99.0) <= 0.0 && !v.isDriving) evidence += "앞유리 성에 주의"
        return ModeProposal(mode, on, keep, evidence)
    }

    /** 날이 갰는지: 눈·비가 그치고 와이퍼가 멈췄으며, 눈 모드는 기온도 오른 경우 */
    fun cleared(mode: WeatherMode, v: VehicleState, w: RoadWeather, arrived: Boolean): Boolean {
        if (arrived) return true
        val dry = w.precipNow == Precip.NONE && !v.wiperRunning
        return when (mode) {
            WeatherMode.RAIN -> dry
            WeatherMode.SNOW -> dry && (v.outsideTempC ?: 0.0) > 3.0
        }
    }
}

/**
 * 날씨 맞춤 등화: 꺼져 있을 때만 묻는다. 운전 판단에 직접 개입하는 상향등·와이퍼는 다루지 않는다.
 */
object LightPlanner {

    /** 비·눈·안개인데 전조등이 꺼져 있으면 (오토 라이트는 밝기 기준이라 낮의 비·안개를 놓치기 쉽다) */
    fun headlightNeeded(v: VehicleState, w: RoadWeather, fogAhead: Boolean): Boolean {
        if (!v.lowBeam.available || v.lowBeam.isOn) return false
        return v.wiperRunning || w.precipNow != Precip.NONE || fogAhead
    }

    fun fogLightNeeded(v: VehicleState, fog: RouteHazard?): Boolean =
        fog != null && fog.level != RiskLevel.WATCH && v.frontFog.available && !v.frontFog.isOn

    /** 스위치 위치 안내 (앱이 직접 켤 수 없는 차량) */
    fun guide(lamp: Lamp, on: Boolean): String = when (lamp) {
        Lamp.FRONT_FOG -> if (on) "왼쪽 레버 끝의 안개등 스위치를 한 칸 돌려 주세요." else "왼쪽 레버 끝의 안개등 스위치를 돌려 꺼 주세요."
        Lamp.LOW_BEAM -> if (on) "왼쪽 레버 끝을 돌려 전조등을 켜 주세요." else "왼쪽 레버 끝을 AUTO로 돌려 주세요."
        Lamp.HAZARD -> if (on) "가운데 비상등 버튼을 눌러 주세요." else "비상등 버튼을 한 번 더 눌러 꺼 주세요."
    }
}

/** 최근 속도 기록으로 급감속을 본다 */
class SpeedHistory(private val windowMs: Long = 4_000L) {
    private val samples = ArrayDeque<Pair<Long, Double>>()

    fun add(nowMs: Long, speedKmh: Double) {
        samples.addLast(nowMs to speedKmh)
        while (samples.size > 2 && nowMs - samples.first().first > windowMs) samples.removeFirst()
    }

    /** 3초 안에 50km/h 이상에서 25km/h 넘게 떨어졌는지 */
    fun hardBraking(nowMs: Long): Boolean {
        val recent = samples.filter { nowMs - it.first <= 3_000L }
        if (recent.size < 2) return false
        val peak = recent.maxOf { it.second }
        val latest = recent.last().second
        return peak >= 50.0 && peak - latest >= 25.0
    }

    fun clear() = samples.clear()
}

/** 질문을 해도 되는 순간인지 */
object QuestionPolicy {
    /**
     * 방향지시등이 켜져 있거나(차로 변경·회전 중) 급감속 중, 내비 음성 안내 중에는 묻지 않는다.
     * 뒤차에 알려야 하는 비상등 제안처럼 바로 필요한 것만 예외.
     */
    fun canAskNow(v: VehicleState, hardBraking: Boolean, naviSpeaking: Boolean, urgent: Boolean): Boolean {
        if (urgent) return true
        return v.turnSignal == TurnSignal.OFF && !hardBraking && !naviSpeaking
    }
}

enum class Answer { YES, NO }

/** 말로 한 대답을 예 / 아니오로 읽는다. 모르겠으면 null. */
object VoiceAnswer {
    private val no = listOf("아니", "괜찮", "됐어", "됐습", "싫어", "나중", "그냥", "그대로", "이대로", "지마", "안켜", "안해", "안돼", "필요없", "노노", "ㄴㄴ")
    private val yesWords = setOf("응", "어", "네", "예", "넹", "ㅇㅇ", "yes", "ok", "오케이", "콜")
    private val yesParts = listOf("그래", "좋아", "좋습", "켜줘", "켜주", "꺼줘", "꺼주", "부탁", "해줘", "해주", "그렇게", "다른길", "그길로", "켜", "꺼")

    fun parse(text: String?): Answer? {
        val t = text?.lowercase()?.filter { it.isLetterOrDigit() }.orEmpty()
        if (t.isEmpty()) return null
        if (no.any { it in t }) return Answer.NO
        if (t in yesWords || yesParts.any { it in t }) return Answer.YES
        return null
    }
}
