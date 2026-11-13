package com.halo.data.pleos;

import android.util.Log;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import ai.pleos.playground.vehicle.Vehicle;
import ai.pleos.playground.vehicle.api.model.area.MirrorArea;
import ai.pleos.playground.vehicle.api.model.area.SeatArea;
import ai.pleos.playground.vehicle.api.model.area.WheelArea;
import ai.pleos.playground.vehicle.api.model.area.WindowArea;
import ai.pleos.playground.vehicle.api.model.capability.AccessType;
import ai.pleos.playground.vehicle.api.model.capability.AreaBounds;
import ai.pleos.playground.vehicle.api.model.capability.CapabilityConfig;
import ai.pleos.playground.vehicle.constant.area.MirrorValue;
import ai.pleos.playground.vehicle.constant.area.SeatValue;
import ai.pleos.playground.vehicle.constant.area.TurnSignalValue;
import ai.pleos.playground.vehicle.constant.area.WheelValue;
import ai.pleos.playground.vehicle.constant.area.WindowValue;
import ai.pleos.playground.vehicle.constant.control.LightState;
import ai.pleos.playground.vehicle.constant.control.LightSwitch;
import ai.pleos.playground.vehicle.constant.control.VehicleGear;
import ai.pleos.playground.vehicle.constant.control.WiperState;
import ai.pleos.playground.vehicle.listener.GearSelectionListener;
import ai.pleos.playground.vehicle.listener.HvacAutoStateListener;
import ai.pleos.playground.vehicle.listener.HvacDefrosterStateListener;
import ai.pleos.playground.vehicle.listener.HvacSideMirrorHeatStateListener;
import ai.pleos.playground.vehicle.listener.HvacSteeringWheelHeatStateListener;
import ai.pleos.playground.vehicle.listener.LightStateListener;
import ai.pleos.playground.vehicle.listener.LowWasherFluidWaringListener;
import ai.pleos.playground.vehicle.listener.TurnSignalStateListener;
import ai.pleos.playground.vehicle.listener.WiperStateListener;
import kotlin.Unit;

/**
 * Pleos Vehicle SDK 의 리스너·조회·제어를 자바에서 감싼다.
 * SDK 는 Kotlin 으로 작성돼 있어, 자바 쪽에서 붙이면 SDK 버전이 바뀌어도 (널 가능 여부 같은) 세부에 덜 민감하다.
 * 차종마다 지원하는 기능이 다르므로 하나가 실패해도 나머지는 계속한다.
 */
public final class HaloVehicleBridge {
    private static final String TAG = "HaloVehicleBridge";

    private static final WindowArea FRONT = new WindowArea(WindowValue.FRONT_WINDSHIELD);
    private static final SeatArea DRIVER = new SeatArea(SeatValue.ROW_1_LEFT);
    private static final WheelValue[] WHEELS = {WheelValue.LEFT_FRONT, WheelValue.RIGHT_FRONT, WheelValue.LEFT_REAR, WheelValue.RIGHT_REAR};

    /** 차량 값을 단순한 값으로 넘긴다 (열거형은 이름 문자열로). 바인더 스레드에서 불릴 수 있다. */
    public interface Sink {
        void onGear(String name);

        void onTurnSignal(String name);

        void onWiperState(String name);

        void onWiperSwitch(String name);

        void onLowBeamState(String name);

        void onLowBeamSwitch(String name);

        void onLowBeamCapability(boolean available, boolean writable);

        void onFrontFogState(String name);

        void onFrontFogSwitch(String name);

        void onFrontFogCapability(boolean available, boolean writable);

        void onMirrorHeat(Integer level);

        void onMirrorHeatCapability(boolean available, boolean writable);

        void onSteeringHeat(Integer level);

        void onSteeringHeatCapability(boolean available, boolean writable);

        void onClimateAuto(Boolean on);

        void onDefrost(Boolean on);

        void onDefrostCapability(boolean available, boolean writable);

        void onWasherLow(Boolean low);

        void onTireLow(Boolean low);
    }

    public interface Done {
        void accept(boolean ok);
    }

    private final Vehicle vehicle;
    private final List<Runnable> unsubscribe = new ArrayList<>();
    private final Map<WheelValue, Boolean> lowWheels = new ConcurrentHashMap<>();
    private volatile MirrorArea mirrorArea = new MirrorArea(MirrorValue.DRIVER_LEFT, MirrorValue.DRIVER_RIGHT);
    private volatile boolean mirrorSubscribed = false;
    private volatile int mirrorLevel = 1;
    private volatile int steeringLevel = 1;

    public HaloVehicleBridge(Vehicle vehicle) {
        this.vehicle = vehicle;
    }

    /** 리스너를 붙이고 지금 값을 한 번 읽는다 */
    public void subscribe(Sink sink) {
        registerListeners(sink);
        checkCapabilities(sink);
        readNow(sink);
    }

    /**
     * 지원 여부와 지금 값을 다시 읽는다. 연결 직후에는 첫 조회가 실패할 수 있고,
     * 공기압·스위치 위치처럼 리스너가 없는 값도 있어 가끔 다시 읽는다.
     */
    public void refresh(Sink sink) {
        checkCapabilities(sink);
        readNow(sink);
    }

    public void release() {
        for (Runnable r : unsubscribe) safely("unsubscribe", r);
        unsubscribe.clear();
    }

    // ---------------- 제어 ----------------

    public void setMirrorHeat(boolean on, Done done) {
        call("mirror heat", done, () -> vehicle.getSideMirror().setHvacSideMirrorHeat(mirrorArea, on ? mirrorLevel : 0,
                (area, value) -> ok(done),
                e -> fail(done, e)));
    }

    public void setSteeringHeat(boolean on, Done done) {
        call("steering heat", done, () -> vehicle.getSteeringWheel().setHvacSteeringWheelHeat(on ? steeringLevel : 0,
                value -> ok(done),
                e -> fail(done, e)));
    }

    public void setDefrost(boolean on, Done done) {
        call("defrost", done, () -> vehicle.getHVAC().setHvacDefroster(FRONT, on,
                (area, value) -> ok(done),
                e -> fail(done, e)));
    }

    /** 켤 때는 ON, 끌 때는 AUTO 로 되돌린다 */
    public void setLowBeam(boolean on, Done done) {
        call("low beam", done, () -> vehicle.getLight().setLowBeamLightSwitch(on ? LightSwitch.ON : LightSwitch.AUTOMATIC,
                value -> ok(done),
                e -> fail(done, e)));
    }

    public void setFrontFog(boolean on, Done done) {
        call("front fog", done, () -> vehicle.getLight().setFrontFogLightSwitch(on ? LightSwitch.ON : LightSwitch.OFF,
                value -> ok(done),
                e -> fail(done, e)));
    }

    // ---------------- 리스너 ----------------

    private void registerListeners(Sink sink) {
        GearSelectionListener gear = new GearSelectionListener() {
            @Override
            public void onSelected(VehicleGear value) {
                sink.onGear(nameOf(value));
            }

            @Override
            public void onFailed(Exception error) {
                failed(error);
            }
        };
        register("gear", () -> vehicle.getGear().registerGearSelection(gear),
                () -> vehicle.getGear().unregisterGearSelection(gear));

        TurnSignalStateListener turn = new TurnSignalStateListener() {
            @Override
            public void onTurnSignalStateUpdated(TurnSignalValue value) {
                sink.onTurnSignal(nameOf(value));
            }

            @Override
            public void onFailed(Exception error) {
                failed(error);
            }
        };
        register("turn signal", () -> vehicle.getTurnSignal().registerTurnSignalState(turn),
                () -> vehicle.getTurnSignal().unregisterTurnSignalState(turn));

        WiperStateListener wiper = new WiperStateListener() {
            @Override
            public void onWiperStateUpdated(WiperState value) {
                sink.onWiperState(nameOf(value));
                // 스위치 위치는 리스너가 없어 작동 상태가 바뀔 때 함께 읽는다
                readWiperSwitch(sink);
            }

            @Override
            public void onFailed(Exception error) {
                failed(error);
            }
        };
        register("wiper", () -> vehicle.getWiper().registerWiperState(FRONT, wiper),
                () -> vehicle.getWiper().unregisterWiperState(FRONT, wiper));

        LightStateListener lowBeam = new LightStateListener() {
            @Override
            public void onLightStateUpdated(LightState value) {
                sink.onLowBeamState(nameOf(value));
                safely("low beam switch", () -> vehicle.getLight().getLowBeamLightSwitch(
                        v -> { sink.onLowBeamSwitch(nameOf(v)); return Unit.INSTANCE; }, HaloVehicleBridge.this::failed));
            }

            @Override
            public void onFailed(Exception error) {
                failed(error);
            }
        };
        register("low beam", () -> vehicle.getLight().registerLowBeamLightState(lowBeam),
                () -> vehicle.getLight().unregisterLowBeamLightState(lowBeam));

        LightStateListener fog = new LightStateListener() {
            @Override
            public void onLightStateUpdated(LightState value) {
                sink.onFrontFogState(nameOf(value));
                safely("fog switch", () -> vehicle.getLight().getFrontFogLightSwitch(
                        v -> { sink.onFrontFogSwitch(nameOf(v)); return Unit.INSTANCE; }, HaloVehicleBridge.this::failed));
            }

            @Override
            public void onFailed(Exception error) {
                failed(error);
            }
        };
        register("fog", () -> vehicle.getLight().registerFrontFogLightState(fog),
                () -> vehicle.getLight().unregisterFrontFogLightState(fog));

        HvacSteeringWheelHeatStateListener steering = new HvacSteeringWheelHeatStateListener() {
            @Override
            public void onHvacSteeringWheelHeatStateUpdated(Integer value) {
                sink.onSteeringHeat(value);
            }

            @Override
            public void onFailed(Exception error) {
                failed(error);
            }
        };
        register("steering heat", () -> vehicle.getSteeringWheel().registerHvacSteeringWheelHeatState(steering),
                () -> vehicle.getSteeringWheel().unregisterHvacSteeringWheelHeatState(steering));

        HvacAutoStateListener auto = new HvacAutoStateListener() {
            @Override
            public void onHvacAutoStateUpdated(Boolean value) {
                sink.onClimateAuto(value);
            }

            @Override
            public void onFailed(Exception error) {
                failed(error);
            }
        };
        register("climate auto", () -> vehicle.getHVAC().registerHvacAutoState(DRIVER, auto),
                () -> vehicle.getHVAC().unregisterHvacAutoState(DRIVER, auto));

        HvacDefrosterStateListener defrost = new HvacDefrosterStateListener() {
            @Override
            public void onHvacDefrosterStateUpdated(Boolean value) {
                sink.onDefrost(value);
            }

            @Override
            public void onFailed(Exception error) {
                failed(error);
            }
        };
        register("defrost", () -> vehicle.getHVAC().registerHvacDefrosterState(FRONT, defrost),
                () -> vehicle.getHVAC().unregisterHvacDefrosterState(FRONT, defrost));

        LowWasherFluidWaringListener washer = new LowWasherFluidWaringListener() {
            @Override
            public void onLowWasherFluidWaringUpdated(Boolean value) {
                sink.onWasherLow(value);
            }

            @Override
            public void onFailed(Exception error) {
                failed(error);
            }
        };
        register("washer", () -> vehicle.getSafety().registerLowWasherFluidWaringState(washer),
                () -> vehicle.getSafety().unregisterLowWasherFluidWaringState(washer));
    }

    /** 사이드미러는 차마다 영역(좌우)이 달라, 지원 영역을 알아낸 뒤에 붙인다 */
    private void registerMirror(Sink sink) {
        if (mirrorSubscribed) return;
        mirrorSubscribed = true;
        MirrorArea area = mirrorArea;
        HvacSideMirrorHeatStateListener mirror = new HvacSideMirrorHeatStateListener() {
            @Override
            public void onHvacSideMirrorHeatStateUpdated(Integer value) {
                sink.onMirrorHeat(value);
            }

            @Override
            public void onFailed(Exception error) {
                failed(error);
            }
        };
        register("mirror heat", () -> vehicle.getSideMirror().registerHvacSideMirrorHeatState(area, mirror),
                () -> vehicle.getSideMirror().unregisterHvacSideMirrorHeatState(area, mirror));
        safely("mirror heat now", () -> vehicle.getSideMirror().getHvacSideMirrorHeatState(area,
                v -> { sink.onMirrorHeat(v); return Unit.INSTANCE; }, this::failed));
    }

    // ---------------- 지원 여부 ----------------

    /** 지원하지 않는 기능은 화면과 질문에서 뺀다. 조회가 실패하면 (연결 전일 수 있어) 이전 값을 그대로 둔다. */
    private void checkCapabilities(Sink sink) {
        safely("low beam capability", () -> vehicle.getLight().checkLowBeamLightSwitchCapabilities(
                (config, list) -> {
                    sink.onLowBeamCapability(list != null && !list.isEmpty(), writable(config));
                    return Unit.INSTANCE;
                }, this::failed));
        safely("fog capability", () -> vehicle.getLight().checkFrontFogLightSwitchCapabilities(
                (config, list) -> {
                    sink.onFrontFogCapability(list != null && !list.isEmpty(), writable(config));
                    return Unit.INSTANCE;
                }, this::failed));
        safely("mirror capability", () -> vehicle.getSideMirror().checkHvacSideMirrorHeatCapabilities(
                (config, areas) -> {
                    boolean available = areas != null && !areas.isEmpty();
                    if (available) {
                        Map.Entry<MirrorArea, AreaBounds<Integer>> first = areas.entrySet().iterator().next();
                        if (!mirrorSubscribed) mirrorArea = first.getKey();
                        mirrorLevel = lowestOn(first.getValue());
                        registerMirror(sink);
                    }
                    sink.onMirrorHeatCapability(available, writable(config));
                    return Unit.INSTANCE;
                }, this::failed));
        safely("steering capability", () -> vehicle.getSteeringWheel().checkHvacSteeringWheelHeatCapabilities(
                (config, bounds) -> {
                    steeringLevel = lowestOn(bounds);
                    sink.onSteeringHeatCapability(bounds != null, writable(config));
                    return Unit.INSTANCE;
                }, this::failed));
        safely("defrost capability", () -> vehicle.getHVAC().checkHvacDefrosterCapabilities(
                (config, areas) -> {
                    sink.onDefrostCapability(areas != null && !areas.isEmpty(), writable(config));
                    return Unit.INSTANCE;
                }, this::failed));
    }

    // ---------------- 지금 값 ----------------

    private void readNow(Sink sink) {
        safely("gear now", () -> vehicle.getGear().getCurrentGearSelectionState(
                v -> { sink.onGear(nameOf(v)); return Unit.INSTANCE; }, this::failed));
        safely("turn signal now", () -> vehicle.getTurnSignal().getCurrentTurnSignalState(
                v -> { sink.onTurnSignal(nameOf(v)); return Unit.INSTANCE; }, this::failed));
        safely("wiper now", () -> vehicle.getWiper().getCurrentWiperState(FRONT,
                v -> { sink.onWiperState(nameOf(v)); return Unit.INSTANCE; }, this::failed));
        readWiperSwitch(sink);
        safely("low beam now", () -> vehicle.getLight().getCurrentLowBeamLightState(
                v -> { sink.onLowBeamState(nameOf(v)); return Unit.INSTANCE; }, this::failed));
        safely("low beam switch", () -> vehicle.getLight().getLowBeamLightSwitch(
                v -> { sink.onLowBeamSwitch(nameOf(v)); return Unit.INSTANCE; }, this::failed));
        safely("fog now", () -> vehicle.getLight().getCurrentFrontFogLightState(
                v -> { sink.onFrontFogState(nameOf(v)); return Unit.INSTANCE; }, this::failed));
        safely("fog switch", () -> vehicle.getLight().getFrontFogLightSwitch(
                v -> { sink.onFrontFogSwitch(nameOf(v)); return Unit.INSTANCE; }, this::failed));
        safely("steering heat now", () -> vehicle.getSteeringWheel().getHvacSteeringWheelHeatState(
                v -> { sink.onSteeringHeat(v); return Unit.INSTANCE; }, this::failed));
        if (mirrorSubscribed) {
            MirrorArea area = mirrorArea;
            safely("mirror heat now", () -> vehicle.getSideMirror().getHvacSideMirrorHeatState(area,
                    v -> { sink.onMirrorHeat(v); return Unit.INSTANCE; }, this::failed));
        }
        safely("climate auto now", () -> vehicle.getHVAC().getHvacAutoState(DRIVER,
                v -> { sink.onClimateAuto(v); return Unit.INSTANCE; }, this::failed));
        safely("defrost now", () -> vehicle.getHVAC().getHvacDefrosterState(FRONT,
                v -> { sink.onDefrost(v); return Unit.INSTANCE; }, this::failed));
        safely("washer now", () -> vehicle.getSafety().hasLowWasherFluidWarning(
                v -> { sink.onWasherLow(v); return Unit.INSTANCE; }, this::failed));
        readTires(sink);
    }

    private void readWiperSwitch(Sink sink) {
        safely("wiper switch", () -> vehicle.getWiper().getCurrentWiperSwitch(FRONT,
                v -> { sink.onWiperSwitch(nameOf(v)); return Unit.INSTANCE; }, this::failed));
    }

    /** 바퀴마다 지금 공기압이 차량의 저압 경고 기준보다 낮은지 */
    private void readTires(Sink sink) {
        for (WheelValue wheel : WHEELS) {
            WheelArea area = new WheelArea(wheel);
            safely("tire " + wheel, () -> vehicle.getTire().getLowTirePressure(area,
                    low -> {
                        safely("tire pressure " + wheel, () -> vehicle.getTire().getTirePressure(area,
                                now -> {
                                    if (low != null && now != null && low > 0f) {
                                        lowWheels.put(wheel, now < low);
                                        sink.onTireLow(lowWheels.containsValue(Boolean.TRUE));
                                    }
                                    return Unit.INSTANCE;
                                }, this::failed));
                        return Unit.INSTANCE;
                    }, this::failed));
        }
    }

    // ---------------- 도움 ----------------

    private void register(String what, Runnable add, Runnable remove) {
        try {
            add.run();
            unsubscribe.add(remove);
        } catch (Throwable t) {
            Log.w(TAG, "Pleos vehicle " + what + " unavailable", t);
        }
    }

    private static boolean writable(CapabilityConfig config) {
        if (config == null) return false;
        AccessType type = config.getAccessType();
        return type == AccessType.WRITE || type == AccessType.READ_WRITE;
    }

    /** 0(꺼짐)을 뺀 가장 낮은 단계 */
    private static int lowestOn(AreaBounds<Integer> bounds) {
        if (bounds == null) return 1;
        Integer min = bounds.getMinValue();
        Integer max = bounds.getMaxValue();
        int low = min == null ? 1 : Math.max(1, min);
        return max == null ? low : Math.min(low, Math.max(1, max));
    }

    private static String nameOf(Enum<?> value) {
        return value == null ? null : value.name();
    }

    private static Unit ok(Done done) {
        done.accept(true);
        return Unit.INSTANCE;
    }

    private Unit fail(Done done, Exception error) {
        failed(error);
        done.accept(false);
        return Unit.INSTANCE;
    }

    private Unit failed(Exception error) {
        Log.w(TAG, "vehicle call failed", error);
        return Unit.INSTANCE;
    }

    private void call(String what, Done done, Runnable action) {
        try {
            action.run();
        } catch (Throwable t) {
            Log.w(TAG, "Pleos vehicle " + what + " failed", t);
            done.accept(false);
        }
    }

    private static void safely(String what, Runnable action) {
        try {
            action.run();
        } catch (Throwable t) {
            Log.w(TAG, "Pleos vehicle " + what + " unavailable", t);
        }
    }
}
