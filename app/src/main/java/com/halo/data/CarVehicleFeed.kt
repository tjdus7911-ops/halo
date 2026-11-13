package com.halo.data

import ai.pleos.playground.vehicle.Vehicle
import android.car.Car
import android.car.VehiclePropertyIds
import android.car.hardware.CarPropertyValue
import android.car.hardware.property.CarPropertyManager
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import com.halo.data.pleos.HaloVehicleBridge
import com.halo.domain.ControlCommand
import com.halo.domain.Gear
import com.halo.domain.Heater
import com.halo.domain.HeaterInfo
import com.halo.domain.Lamp
import com.halo.domain.LampInfo
import com.halo.domain.LampSwitch
import com.halo.domain.TurnSignal
import com.halo.domain.VehicleState
import com.halo.domain.WiperMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 실제 차량: AAOS 표준 속성(시동·기어·속도·외기온도)과 Pleos Vehicle SDK(등화·와이퍼·열선·공조·워셔액·공기압)를 합친다.
 * 차종마다 지원하는 값이 달라 없는 값은 비워 두고, 판단 엔진은 있는 값만으로 판단한다.
 */
class CarVehicleFeed(context: Context) : VehicleFeed, HaloVehicleBridge.Sink {

    private val app = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private val raw = MutableStateFlow(Raw())
    private val _status = MutableStateFlow(FeedStatus.CONNECTING)
    override val status: StateFlow<FeedStatus> = _status.asStateFlow()

    private val temps = ArrayDeque<Pair<Long, Double>>()
    private val wiperLock = Any()
    private var wiperOnSince: Long? = null
    private var wiperOffSince: Long? = null

    private var car: Car? = null
    private var properties: CarPropertyManager? = null
    private var vehicle: Vehicle? = null
    private var bridge: HaloVehicleBridge? = null

    private val carCallback = object : CarPropertyManager.CarPropertyEventCallback {
        override fun onChangeEvent(value: CarPropertyValue<*>) = applyCar(value)

        override fun onErrorEvent(propertyId: Int, areaId: Int) {
            Log.w(TAG, "property error $propertyId/$areaId")
        }
    }

    private val refresher = object : Runnable {
        override fun run() {
            bridge?.refresh(this@CarVehicleFeed)
            main.postDelayed(this, REFRESH_MS)
        }
    }

    init {
        connectCar()
        connectPleos()
        main.postDelayed(refresher, FIRST_REFRESH_MS)
        main.postDelayed({ if (_status.value == FeedStatus.CONNECTING) _status.value = FeedStatus.UNAVAILABLE }, CONNECT_TIMEOUT_MS)
    }

    // ---------------- 읽기 ----------------

    override fun current(nowMs: Long): VehicleState {
        val r = raw.value
        val temp = r.outsideTempC
        val drop = synchronized(temps) {
            val recent = temps.filter { nowMs - it.first <= TEMP_WINDOW_MS }
            val peak = recent.maxOfOrNull { it.second }
            if (peak == null || temp == null) 0.0 else (peak - temp).coerceAtLeast(0.0)
        }
        val (running, minutes) = synchronized(wiperLock) {
            val on = r.wiperState == "ON"
            val pausedShortly = !on && wiperOnSince != null && wiperOffSince?.let { nowMs - it < WIPER_PAUSE_MS } == true
            val running = on || pausedShortly
            val since = wiperOnSince
            running to if (running && since != null) ((nowMs - since) / 60_000L).toInt() else 0
        }
        return VehicleState(
            ignitionOn = r.ignitionOn ?: true,
            gear = r.gear,
            speedKmh = r.speedKmh ?: 0.0,
            outsideTempC = temp,
            tempDrop10MinC = drop,
            turnSignal = r.turnSignal,
            wiperMode = wiperModeOf(r.wiperSwitch, running),
            wiperRunning = running,
            wiperRunningMin = minutes,
            lowBeam = LampInfo(r.lowBeamAvailable, lampSwitchOf(r.lowBeamSwitch), r.lowBeamState?.let { it == "ON" }, r.lowBeamWritable),
            frontFog = LampInfo(r.fogAvailable, lampSwitchOf(r.fogSwitch), r.fogState?.let { it == "ON" }, r.fogWritable),
            mirrorHeat = HeaterInfo(r.mirrorAvailable, r.mirrorLevel, r.mirrorWritable),
            steeringHeat = HeaterInfo(r.steeringAvailable, r.steeringLevel, r.steeringWritable),
            climateAuto = r.climateAuto,
            frontDefrost = r.defrost,
            defrostControllable = r.defrostWritable,
            washerLow = r.washerLow,
            tireLow = r.tireLow,
        )
    }

    // ---------------- 제어 ----------------

    override fun control(command: ControlCommand, done: (Boolean) -> Unit) {
        val b = bridge
        if (b == null) {
            main.post { done(false) }
            return
        }
        val answered = AtomicBoolean(false)
        val finish = HaloVehicleBridge.Done { ok -> if (answered.compareAndSet(false, true)) main.post { done(ok) } }
        main.postDelayed({ finish.accept(false) }, CONTROL_TIMEOUT_MS)
        when (command) {
            is ControlCommand.SetHeater -> when (command.heater) {
                Heater.MIRROR -> b.setMirrorHeat(command.on, finish)
                Heater.STEERING -> b.setSteeringHeat(command.on, finish)
                Heater.DEFROST -> b.setDefrost(command.on, finish)
            }
            is ControlCommand.SetLamp -> when (command.lamp) {
                Lamp.LOW_BEAM -> b.setLowBeam(command.on, finish)
                Lamp.FRONT_FOG -> b.setFrontFog(command.on, finish)
                // 비상등은 제어 API 가 없다: 버튼 위치를 안내한다
                Lamp.HAZARD -> finish.accept(false)
            }
        }
    }

    override fun release() {
        main.removeCallbacksAndMessages(null)
        try {
            properties?.unregisterCallback(carCallback)
        } catch (t: Throwable) {
            Log.w(TAG, "unregister failed", t)
        }
        try {
            car?.disconnect()
        } catch (t: Throwable) {
            Log.w(TAG, "car disconnect failed", t)
        }
        bridge?.release()
        try {
            vehicle?.release()
        } catch (t: Throwable) {
            Log.w(TAG, "vehicle release failed", t)
        }
    }

    // ---------------- AAOS ----------------

    private fun connectCar() {
        try {
            val c = Car.createCar(app) ?: return
            val manager = c.getCarManager(Car.PROPERTY_SERVICE) as? CarPropertyManager
            car = c
            properties = manager
            if (manager == null) return
            val subscriptions = listOf(
                VehiclePropertyIds.IGNITION_STATE to CarPropertyManager.SENSOR_RATE_ONCHANGE,
                VehiclePropertyIds.GEAR_SELECTION to CarPropertyManager.SENSOR_RATE_ONCHANGE,
                VehiclePropertyIds.PERF_VEHICLE_SPEED to CarPropertyManager.SENSOR_RATE_NORMAL,
                VehiclePropertyIds.ENV_OUTSIDE_TEMPERATURE to CarPropertyManager.SENSOR_RATE_NORMAL,
            )
            for ((propertyId, rate) in subscriptions) {
                try {
                    if (manager.getCarPropertyConfig(propertyId) == null) continue // 이 차는 지원하지 않음
                    @Suppress("DEPRECATION")
                    manager.registerCallback(carCallback, propertyId, rate)
                } catch (e: SecurityException) {
                    Log.w(TAG, "no permission for property $propertyId")
                } catch (e: IllegalArgumentException) {
                    Log.w(TAG, "unsupported property $propertyId")
                }
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Car service unavailable", t)
        }
    }

    private fun applyCar(v: CarPropertyValue<*>) {
        val value = v.value ?: return
        when (v.propertyId) {
            VehiclePropertyIds.IGNITION_STATE -> set { it.copy(ignitionOn = ((value as? Int) ?: 0) >= IGNITION_STATE_ON) }
            VehiclePropertyIds.GEAR_SELECTION -> set { it.copy(gear = gearOfAaos((value as? Int) ?: 0)) }
            VehiclePropertyIds.PERF_VEHICLE_SPEED -> set { it.copy(speedKmh = ((value as? Float) ?: 0f) * 3.6) }
            VehiclePropertyIds.ENV_OUTSIDE_TEMPERATURE -> (value as? Float)?.toDouble()?.let { t ->
                recordTemp(t)
                set { it.copy(outsideTempC = t) }
            }
        }
    }

    private fun recordTemp(t: Double) {
        val now = SystemClock.elapsedRealtime()
        synchronized(temps) {
            if (temps.isEmpty() || now - temps.last().first >= TEMP_SAMPLE_MS) temps.addLast(now to t)
            while (temps.isNotEmpty() && now - temps.first().first > TEMP_WINDOW_MS) temps.removeFirst()
        }
    }

    // ---------------- Pleos Vehicle SDK ----------------

    private fun connectPleos() {
        try {
            val v = Vehicle(app).also { it.initialize() }
            vehicle = v
            bridge = HaloVehicleBridge(v).also { it.subscribe(this) }
        } catch (t: Throwable) {
            Log.w(TAG, "Pleos Vehicle SDK unavailable", t)
        }
    }

    override fun onGear(name: String?) {
        val gear = gearOfPleos(name) ?: return
        set { it.copy(gear = gear) }
    }

    override fun onTurnSignal(name: String?) = set {
        it.copy(turnSignal = when (name) {
            "LEFT" -> TurnSignal.LEFT
            "RIGHT" -> TurnSignal.RIGHT
            else -> TurnSignal.OFF
        })
    }

    override fun onWiperState(name: String?) {
        val now = SystemClock.elapsedRealtime()
        synchronized(wiperLock) {
            if (name == "ON") {
                val off = wiperOffSince
                if (wiperOnSince == null || (off != null && now - off >= WIPER_PAUSE_MS)) wiperOnSince = now
                wiperOffSince = null
            } else if (wiperOnSince != null && wiperOffSince == null) {
                wiperOffSince = now
            }
        }
        set { it.copy(wiperState = name) }
    }

    override fun onWiperSwitch(name: String?) = set { it.copy(wiperSwitch = name) }

    override fun onLowBeamState(name: String?) = set { it.copy(lowBeamState = name) }

    override fun onLowBeamSwitch(name: String?) = set { it.copy(lowBeamSwitch = name) }

    override fun onLowBeamCapability(available: Boolean, writable: Boolean) =
        set { it.copy(lowBeamAvailable = available, lowBeamWritable = writable) }

    override fun onFrontFogState(name: String?) = set { it.copy(fogState = name) }

    override fun onFrontFogSwitch(name: String?) = set { it.copy(fogSwitch = name) }

    override fun onFrontFogCapability(available: Boolean, writable: Boolean) =
        set { it.copy(fogAvailable = available, fogWritable = writable) }

    override fun onMirrorHeat(level: Int?) = set { it.copy(mirrorLevel = level) }

    override fun onMirrorHeatCapability(available: Boolean, writable: Boolean) =
        set { it.copy(mirrorAvailable = available, mirrorWritable = writable) }

    override fun onSteeringHeat(level: Int?) = set { it.copy(steeringLevel = level) }

    override fun onSteeringHeatCapability(available: Boolean, writable: Boolean) =
        set { it.copy(steeringAvailable = available, steeringWritable = writable) }

    override fun onClimateAuto(on: Boolean?) = set { it.copy(climateAuto = on) }

    override fun onDefrost(on: Boolean?) = set { it.copy(defrost = on) }

    override fun onDefrostCapability(available: Boolean, writable: Boolean) =
        set { it.copy(defrostWritable = available && writable) }

    override fun onWasherLow(low: Boolean?) = set { it.copy(washerLow = low) }

    override fun onTireLow(low: Boolean?) = set { it.copy(tireLow = low) }

    // ---------------- 도움 ----------------

    private fun set(change: (Raw) -> Raw) {
        raw.update(change)
        if (_status.value != FeedStatus.CONNECTED) _status.value = FeedStatus.CONNECTED
    }

    /** 차량에서 받은 값 그대로 (열거형 이름 문자열 포함) */
    private data class Raw(
        val ignitionOn: Boolean? = null,
        val gear: Gear = Gear.UNKNOWN,
        val speedKmh: Double? = null,
        val outsideTempC: Double? = null,
        val turnSignal: TurnSignal = TurnSignal.OFF,
        val wiperState: String? = null,
        val wiperSwitch: String? = null,
        val lowBeamState: String? = null,
        val lowBeamSwitch: String? = null,
        val lowBeamAvailable: Boolean = true,
        val lowBeamWritable: Boolean = false,
        val fogState: String? = null,
        val fogSwitch: String? = null,
        val fogAvailable: Boolean = true,
        val fogWritable: Boolean = false,
        val mirrorLevel: Int? = null,
        val mirrorAvailable: Boolean = true,
        val mirrorWritable: Boolean = false,
        val steeringLevel: Int? = null,
        val steeringAvailable: Boolean = true,
        val steeringWritable: Boolean = false,
        val climateAuto: Boolean? = null,
        val defrost: Boolean? = null,
        val defrostWritable: Boolean = false,
        val washerLow: Boolean? = null,
        val tireLow: Boolean? = null,
    )

    private fun gearOfAaos(raw: Int): Gear = when {
        raw == GEAR_PARK -> Gear.PARK
        raw == GEAR_REVERSE -> Gear.REVERSE
        raw == GEAR_NEUTRAL -> Gear.NEUTRAL
        raw >= GEAR_DRIVE -> Gear.DRIVE // D 및 수동 1~9단
        else -> Gear.UNKNOWN
    }

    private fun gearOfPleos(name: String?): Gear? = when (name) {
        null, "GEAR_UNKNOWN" -> null
        "GEAR_PARK" -> Gear.PARK
        "GEAR_REVERSE" -> Gear.REVERSE
        "GEAR_NEUTRAL" -> Gear.NEUTRAL
        else -> Gear.DRIVE // D 및 1~9단
    }

    private fun lampSwitchOf(name: String?): LampSwitch = when (name) {
        "OFF" -> LampSwitch.OFF
        "ON" -> LampSwitch.ON
        "AUTOMATIC" -> LampSwitch.AUTO
        "DAYTIME_RUNNING" -> LampSwitch.DAYTIME
        else -> LampSwitch.UNKNOWN
    }

    private fun wiperModeOf(name: String?, running: Boolean): WiperMode = when {
        name == "OFF" -> WiperMode.OFF
        name == "AUTO" -> WiperMode.AUTO
        name == "MIST" || name?.startsWith("INTERMITTENT") == true -> WiperMode.INTERMITTENT
        name?.startsWith("CONTINUOUS") == true -> WiperMode.CONTINUOUS
        name == null && !running -> WiperMode.OFF
        else -> WiperMode.OTHER
    }

    private companion object {
        const val TAG = "CarVehicleFeed"
        const val FIRST_REFRESH_MS = 3_000L
        const val REFRESH_MS = 60_000L
        const val CONNECT_TIMEOUT_MS = 8_000L
        const val CONTROL_TIMEOUT_MS = 4_000L
        const val TEMP_WINDOW_MS = 10 * 60_000L
        const val TEMP_SAMPLE_MS = 10_000L

        /** 와이퍼가 이만큼 안 쉬고 다시 움직이면 계속 작동 중으로 본다 (간헐 와이퍼) */
        const val WIPER_PAUSE_MS = 60_000L

        // android.car.VehicleIgnitionState / VehicleGear 값
        const val IGNITION_STATE_ON = 4
        const val GEAR_NEUTRAL = 1
        const val GEAR_REVERSE = 2
        const val GEAR_PARK = 4
        const val GEAR_DRIVE = 8
    }
}
