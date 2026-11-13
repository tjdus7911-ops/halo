package com.halo.demo

import com.halo.domain.ControlCommand
import com.halo.domain.Detour
import com.halo.domain.DriveContext
import com.halo.domain.Gear
import com.halo.domain.Heater
import com.halo.domain.HeaterInfo
import com.halo.domain.Lamp
import com.halo.domain.LampInfo
import com.halo.domain.LampSwitch
import com.halo.domain.Precip
import com.halo.domain.RoadWeather
import com.halo.domain.RouteSpot
import com.halo.domain.SpotKind
import com.halo.domain.VehicleState
import com.halo.domain.WiperMode

/** 시연 한 장면: 이 시각의 차량 상태와 경로 위치 */
data class DemoFrame(
    /** 시연 바에 보이는 설명 */
    val label: String,
    /** 시나리오 시작부터 지난 시간(초) */
    val atSec: Int,
    val vehicle: VehicleState,
    val context: DriveContext,
    /** 급감속 장면: 2초 전 속도 (이 값에서 지금 속도로 떨어진다) */
    val brakeFromKmh: Double? = null,
)

data class DemoScenario(
    val id: String,
    val title: String,
    val persona: String,
    /** 이 시나리오에서 꼼꼼히 안내를 켜고 시작하는지 */
    val careful: Boolean,
    val frames: List<DemoFrame>,
)

/**
 * 기획서의 시나리오 3가지. 화면은 이 장면들을 판단 엔진(HaloEngine)에 그대로 넣어 만든다.
 * 기상청·도로 정보는 시연용 예시 값이다.
 */
object DemoScenarios {

    val all: List<DemoScenario> by lazy { listOf(winterCommute(), monsoonEvening(), foggyDawn()) }

    fun byId(id: String): DemoScenario = all.firstOrNull { it.id == id } ?: all.first()

    // ---------------- 시나리오 1: 1월, 영하 4도의 출근길 ----------------

    private fun winterCommute(): DemoScenario {
        val bridge = RouteSpot("bridge", "강변 교량", SpotKind.BRIDGE, atKm = 7.2, lengthKm = 1.8, usualSpeedKmh = 60.0)
        val tunnel = RouteSpot("tunnel", "터널 출구", SpotKind.TUNNEL_EXIT, atKm = 15.8, lengthKm = 0.6, usualSpeedKmh = 60.0)
        val detour = Detour(extraMin = 3, avoidsSpotIds = setOf(bridge.id), label = "교량을 피하는 길")
        val morning = RoadWeather(minTempTodayC = -4.0, maxTempTodayC = 4.0, daysSincePrecip = 2, lastPrecip = Precip.RAIN, precipForecast = Precip.SLEET)
        val sleet = morning.copy(precipNow = Precip.SLEET)
        val cleared = morning.copy(precipForecast = Precip.NONE)

        val car = VehicleState(
            ignitionOn = true,
            gear = Gear.PARK,
            outsideTempC = -4.0,
            wiperMode = WiperMode.AUTO,
            lowBeam = LampInfo(switch = LampSwitch.AUTO, lit = false, controllable = true),
            frontFog = LampInfo(switch = LampSwitch.OFF, lit = false, controllable = true),
            mirrorHeat = HeaterInfo(level = 0, controllable = true),
            steeringHeat = HeaterInfo(level = 0, controllable = true),
            climateAuto = true,
            frontDefrost = false,
            washerLow = false,
            tireLow = true,
        )
        fun ctx(clock: String, km: Double, remain: Int, w: RoadWeather = morning, arrived: Boolean = false) =
            DriveContext(clock, km, remain, w, listOf(bridge, tunnel), detour, arrived = arrived, routeLengthKm = 26.0)

        return DemoScenario(
            id = "winter",
            title = "1월 출근길 · 영하 4도",
            persona = "30대 직장인 · 40분 거리",
            careful = false,
            frames = listOf(
                DemoFrame("07:40 시동, 앞유리 성에", 0, car, ctx("07:40", 0.0, 35)),
                DemoFrame("07:42 출발", 120, car.copy(gear = Gear.DRIVE, speedKmh = 32.0), ctx("07:42", 0.3, 33)),
                DemoFrame(
                    "07:55 국도 교량 1km 전 · 관측소 없는 구간", 15 * 60,
                    car.copy(gear = Gear.DRIVE, speedKmh = 62.0, outsideTempC = 0.5, tempDrop10MinC = 3.0),
                    ctx("07:55", 6.2, 23),
                ),
                DemoFrame(
                    "08:05 진눈깨비, 터널 출구 1km 전", 25 * 60,
                    car.copy(
                        gear = Gear.DRIVE, speedKmh = 58.0, outsideTempC = -1.0,
                        wiperRunning = true, wiperRunningMin = 4,
                    ),
                    ctx("08:05", 14.8, 13, sleet),
                ),
                DemoFrame(
                    "08:06 터널 출구 300m 전", 26 * 60,
                    car.copy(
                        gear = Gear.DRIVE, speedKmh = 45.0, outsideTempC = -1.0,
                        wiperRunning = true, wiperRunningMin = 5,
                    ),
                    ctx("08:06", 15.5, 12, sleet),
                ),
                DemoFrame(
                    "08:20 도착, 날씨 갬", 40 * 60,
                    car.copy(gear = Gear.PARK, speedKmh = 0.0, outsideTempC = 1.0),
                    ctx("08:20", 26.0, 0, cleared, arrived = true),
                ),
            ),
        )
    }

    // ---------------- 시나리오 2: 7월, 장마철 퇴근길 폭우 ----------------

    private fun monsoonEvening(): DemoScenario {
        val storm = RouteSpot("storm", "폭우 구간", SpotKind.ROAD, atKm = 12.0, lengthKm = 4.0, usualSpeedKmh = 70.0, rainMmPerHour = 32)
        val rain = RoadWeather(minTempTodayC = 23.0, maxTempTodayC = 29.0, daysSincePrecip = 0, lastPrecip = Precip.RAIN, precipNow = Precip.RAIN)
        val dry = rain.copy(precipNow = Precip.NONE)
        val car = VehicleState(
            ignitionOn = true,
            gear = Gear.PARK,
            outsideTempC = 23.0,
            wiperMode = WiperMode.CONTINUOUS,
            wiperRunning = true,
            lowBeam = LampInfo(switch = LampSwitch.AUTO, lit = true, controllable = true),
            frontFog = LampInfo(switch = LampSwitch.OFF, lit = false, controllable = true),
            mirrorHeat = HeaterInfo(level = 0, controllable = true),
            steeringHeat = HeaterInfo(level = 0, controllable = true),
            climateAuto = true,
            frontDefrost = false,
            washerLow = false,
            tireLow = false,
        )
        fun ctx(clock: String, km: Double, remain: Int, w: RoadWeather = rain, arrived: Boolean = false) =
            DriveContext(clock, km, remain, w, listOf(storm), arrived = arrived, routeLengthKm = 28.0)

        return DemoScenario(
            id = "monsoon",
            title = "7월 퇴근길 · 장마철 폭우",
            persona = "면허 1년 차 사회초년생 · 꼼꼼히 안내",
            careful = true,
            frames = listOf(
                DemoFrame("19:09 퇴근 출발", 0, car, ctx("19:09", 0.0, 37)),
                DemoFrame("19:21 와이퍼 12분째", 12 * 60, car.copy(gear = Gear.DRIVE, speedKmh = 52.0, wiperRunningMin = 12), ctx("19:21", 9.0, 25)),
                DemoFrame("19:24 폭우 구간 1.5km 전", 15 * 60, car.copy(gear = Gear.DRIVE, speedKmh = 68.0, wiperRunningMin = 15), ctx("19:24", 10.5, 22)),
                DemoFrame(
                    "19:27 앞차들이 급하게 느려짐", 18 * 60,
                    car.copy(gear = Gear.DRIVE, speedKmh = 38.0, wiperRunningMin = 18), ctx("19:27", 12.6, 19),
                    brakeFromKmh = 70.0,
                ),
                DemoFrame("19:30 속도 회복", 21 * 60, car.copy(gear = Gear.DRIVE, speedKmh = 55.0, wiperRunningMin = 21), ctx("19:30", 14.6, 16)),
                DemoFrame(
                    "19:40 비 그침", 31 * 60,
                    car.copy(gear = Gear.DRIVE, speedKmh = 60.0, wiperMode = WiperMode.AUTO, wiperRunning = false, wiperRunningMin = 0),
                    ctx("19:40", 22.0, 6, dry),
                ),
            ),
        )
    }

    // ---------------- 시나리오 3: 10월, 새벽 강변 도로의 짙은 안개 ----------------

    private fun foggyDawn(): DemoScenario {
        val fog = RouteSpot("fog", "강변 도로 안개 구간", SpotKind.RIVERSIDE, atKm = 6.0, lengthKm = 4.0, usualSpeedKmh = 60.0, visibilityM = 90)
        val clear = RoadWeather(minTempTodayC = 8.0, maxTempTodayC = 19.0, daysSincePrecip = null)
        val car = VehicleState(
            ignitionOn = true,
            gear = Gear.PARK,
            outsideTempC = 9.0,
            wiperMode = WiperMode.AUTO,
            lowBeam = LampInfo(switch = LampSwitch.AUTO, lit = true, controllable = false),
            frontFog = LampInfo(switch = LampSwitch.OFF, lit = false, controllable = false),
            mirrorHeat = HeaterInfo(level = 0, controllable = false),
            steeringHeat = HeaterInfo(level = 0, controllable = false),
            climateAuto = true,
            frontDefrost = false,
            washerLow = false,
            tireLow = false,
        )
        fun ctx(clock: String, km: Double, remain: Int, arrived: Boolean = false) =
            DriveContext(clock, km, remain, clear, listOf(fog), arrived = arrived, routeLengthKm = 21.0)

        return DemoScenario(
            id = "fog",
            title = "10월 새벽 · 강변 도로 짙은 안개",
            persona = "70대 운전자 · 새벽 시장 가는 길 · 꼼꼼히 안내",
            careful = true,
            frames = listOf(
                DemoFrame("06:40 시동", 0, car, ctx("06:40", 0.0, 38)),
                DemoFrame("06:45 출발", 5 * 60, car.copy(gear = Gear.DRIVE, speedKmh = 45.0), ctx("06:45", 1.0, 35)),
                DemoFrame("06:52 안개 구간 1.5km 전", 12 * 60, car.copy(gear = Gear.DRIVE, speedKmh = 60.0), ctx("06:52", 4.5, 29)),
                DemoFrame("06:55 안개 구간 진입", 15 * 60, car.copy(gear = Gear.DRIVE, speedKmh = 30.0), ctx("06:55", 6.4, 26)),
                DemoFrame("07:04 안개 걷힘", 24 * 60, car.copy(gear = Gear.DRIVE, speedKmh = 60.0), ctx("07:04", 10.6, 17)),
                DemoFrame("07:21 도착", 41 * 60, car.copy(gear = Gear.PARK, speedKmh = 0.0), ctx("07:21", 21.0, 0, arrived = true)),
            ),
        )
    }
}

/**
 * 시연 진행: 장면을 넘기면서, 앱이 켠 열선·등화와 운전자가 안내대로 누른 스위치를 차량 상태에 반영한다.
 */
class DemoRun(val scenario: DemoScenario) {
    var index: Int = 0
        private set

    private val heaters = mutableMapOf<Heater, Boolean>()
    private val lamps = mutableMapOf<Lamp, Boolean>()
    private val pendingManual = mutableMapOf<Lamp, Boolean>()

    val frame: DemoFrame get() = scenario.frames[index]
    val isLast: Boolean get() = index == scenario.frames.lastIndex
    val nowMs: Long get() = frame.atSec * 1000L

    fun next(): Boolean {
        if (isLast) return false
        index++
        // 운전자가 안내대로 스위치를 눌렀다고 보고 다음 장면부터 반영
        lamps.putAll(pendingManual)
        pendingManual.clear()
        return true
    }

    fun previousIndexAllowed(): Boolean = index > 0

    /** 앱의 차량 제어 (시연 차량은 승인된 제어가 모두 성공한다) */
    fun apply(command: ControlCommand): Boolean {
        when (command) {
            is ControlCommand.SetHeater -> heaters[command.heater] = command.on
            is ControlCommand.SetLamp -> lamps[command.lamp] = command.on
        }
        return true
    }

    /** 앱이 스위치 위치를 안내한 등화: 운전자가 다음 장면까지 누른다 */
    fun guided(lamp: Lamp, on: Boolean) {
        pendingManual[lamp] = on
    }

    fun vehicle(): VehicleState {
        val base = frame.vehicle
        fun heater(info: HeaterInfo, h: Heater): HeaterInfo = heaters[h]?.let { info.copy(level = if (it) 1 else 0) } ?: info
        fun lamp(info: LampInfo, l: Lamp): LampInfo = lamps[l]?.let {
            info.copy(lit = it, switch = if (it) LampSwitch.ON else if (info.switch == LampSwitch.ON) LampSwitch.OFF else info.switch)
        } ?: info
        return base.copy(
            mirrorHeat = heater(base.mirrorHeat, Heater.MIRROR),
            steeringHeat = heater(base.steeringHeat, Heater.STEERING),
            frontDefrost = heaters[Heater.DEFROST] ?: base.frontDefrost,
            lowBeam = lamp(base.lowBeam, Lamp.LOW_BEAM),
            frontFog = lamp(base.frontFog, Lamp.FRONT_FOG),
        )
    }

    fun context(): DriveContext = frame.context
}
