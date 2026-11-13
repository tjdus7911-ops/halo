package com.halo.domain

/** 질문 종류 */
sealed interface QuestionKind {
    data class Route(val detour: Detour) : QuestionKind
    data class ModeOn(val proposal: ModeProposal) : QuestionKind
    data class ModeOff(val mode: WeatherMode) : QuestionKind
    data class LampOn(val lamp: Lamp) : QuestionKind
    data class LampOff(val lamp: Lamp) : QuestionKind
    data object CarefulOffer : QuestionKind
}

/** 한 번에 하나만, “응 / 아니”로 답하는 질문 */
data class Question(
    val id: String,
    /** 같은 상황에서 다시 묻지 않기 위한 키 */
    val key: String,
    val kind: QuestionKind,
    /** 음성으로 묻는 문장 */
    val prompt: String,
    /** 화면 제목 */
    val title: String,
    val yesLabel: String,
    val noLabel: String,
    /** 바로 물어야 하는 질문 (방향지시등·급감속 중에도) */
    val urgent: Boolean = false,
    /** 못 들었을 때 한 번 더 묻는 중인지 */
    val reAsked: Boolean = false,
)

sealed interface ControlCommand {
    data class SetHeater(val heater: Heater, val on: Boolean) : ControlCommand
    data class SetLamp(val lamp: Lamp, val on: Boolean) : ControlCommand
}

sealed interface Effect {
    /** 읽어 주기 */
    data class Speak(val text: String) : Effect

    /** 묻고 대답 듣기 */
    data class Ask(val question: Question) : Effect

    /** 차량 제어 */
    data class Control(val command: ControlCommand) : Effect

    /** 앱이 직접 켤 수 없는 등화: 스위치 위치를 안내하고, 켜졌는지 상태 조회로 확인한다 */
    data class Guide(val lamp: Lamp, val on: Boolean, val text: String) : Effect

    /** 설정 저장 */
    data class SaveSettings(val settings: HaloSettings) : Effect
}

enum class Phase { BRIEFING, DRIVING, ARRIVED }

data class ActiveAlert(val hazard: RouteHazard, val stage: AlertPlanner.Stage, val firedAtKm: Double)

/** 켜 둔 비·눈 모드: 앱이 켠 것만 기억했다가 끌 때 되돌린다 */
data class ActiveMode(val mode: WeatherMode, val turnedOn: List<Heater>, val kept: List<KeptAuto>)

data class HazardAhead(val hazard: RouteHazard, val distanceKm: Double)

/** 화면이 그리는 상태 */
data class HaloSnapshot(
    val phase: Phase,
    val clock: String,
    val vehicle: VehicleState,
    val weather: RoadWeather,
    val settings: HaloSettings,
    val routeKnown: Boolean,
    val progressKm: Double,
    val briefing: Briefing?,
    /** 남은 길의 위험 (가까운 순) */
    val ahead: List<HazardAhead>,
    val alert: ActiveAlert?,
    /** 지금 묻는 질문, 없으면 대답을 못 들어 화면에 남겨 둔 질문 */
    val question: Question?,
    /** 음성으로 묻는 중인지 (false 면 화면에만 남아 있음) */
    val asking: Boolean,
    val activeMode: ActiveMode?,
    val lampsOnByApp: Set<Lamp>,
    val arrivalClock: String?,
    val delayMin: Int,
    val detourTaken: Boolean,
    /** 경로 전체 길이 (모르면 null) */
    val routeLengthKm: Double? = null,
)

/**
 * 헤일로의 판단을 한곳에서: 차량 상태와 경로·날씨를 받아 읽을 말, 물을 질문, 차량 제어를 낸다.
 * 안드로이드에 의존하지 않아 시연 시나리오와 단위 테스트에서 실제 차와 같은 로직으로 돈다.
 *
 * 원칙: 먼저 알아챈다 → 먼저 묻는다 → “응” 한마디로 끝낸다. 대답이 없으면 아무것도 바꾸지 않는다.
 */
class HaloEngine(
    initialSettings: HaloSettings,
    /** 오늘 날짜 (yyyy-MM-dd): 하루 첫 운행에 전체 브리핑 */
    private val today: String,
    /** 이번 겨울 (예: "2026-27"): 올겨울 첫 영하 안내 */
    private val season: String,
    /** 첫 실행 때 꼼꼼히 안내를 권할지 (시연 모드에서는 끈다) */
    private val offerCareful: Boolean = true,
) {
    var settings: HaloSettings = initialSettings
        private set

    private var briefing: Briefing? = null
    private var briefed = false
    private val fired = mutableSetOf<String>()
    private var alert: ActiveAlert? = null
    private val queue = mutableListOf<Question>()
    private var current: Question? = null
    private var onScreen: Question? = null
    private var reAsk: Pair<Question, Long>? = null
    private val asked = mutableSetOf<String>()
    private var activeMode: ActiveMode? = null
    private val lampsOn = mutableSetOf<Lamp>()
    private val awaiting = mutableMapOf<Lamp, Pair<Boolean, Long>>()
    private var detourTaken = false
    private val speeds = SpeedHistory()
    private var genericIceAt: Long? = null
    private var seq = 0

    private var vehicle = VehicleState()
    private var ctx = DriveContext(clock = "00:00", weather = RoadWeather(0.0, 0.0, null))
    private var hazards: List<RouteHazard> = emptyList()

    fun setCareful(on: Boolean): List<Effect> {
        settings = settings.copy(careful = on, carefulOffered = true)
        return listOf(Effect.SaveSettings(settings))
    }

    fun update(v: VehicleState, context: DriveContext, nowMs: Long, naviSpeaking: Boolean = false): List<Effect> {
        val fx = mutableListOf<Effect>()
        speeds.add(nowMs, v.speedKmh)
        val braking = speeds.hardBraking(nowMs)
        vehicle = v
        ctx = context
        hazards = RiskEngine.evaluateRoute(routeSpots(), v, ctx.weather)

        // 1) 시동 브리핑: 주행 한 번에 한 번
        if (!briefed && v.ignitionOn) {
            briefed = true
            fx += brief(nowMs)
        }

        // 2) 살얼음·안개 레이더: 경로 위 지점(시나리오·내비 주의 지점)이 있으면 지점마다,
        //    경로의 날씨 정보가 없으면 차량 외기온도가 빠르게 떨어질 때 한 번 더 알린다
        if (ctx.spots.isNotEmpty()) {
            alert?.let { if (!AlertPlanner.stillRelevant(it.hazard, ctx.progressKm)) alert = null }
            val due = AlertPlanner.due(hazards, ctx.progressKm, settings.careful, fired)
            due.firstOrNull()?.let { d ->
                fired += d.key
                alert = ActiveAlert(d.hazard, d.stage, ctx.progressKm)
                fx += Effect.Speak(Phrases.alertSpeech(d, settings.careful))
            }
        } else {
            alert = null
        }
        if (!ctx.routeKnown) genericIce(nowMs)?.let { fx += it }

        // 3) 비·눈 모드
        val trigger = WeatherModePlanner.trigger(v, ctx.weather)
        if (trigger == null) asked.removeAll { it.startsWith("mode-on") }
        if (activeMode == null && trigger != null && !ctx.arrived) {
            val key = "mode-on:$trigger"
            if (key !in asked) WeatherModePlanner.propose(trigger, v, ctx.weather)?.let { enqueue(modeOnQuestion(it, key)) }
        }
        activeMode?.let { m ->
            val key = "mode-off:${m.mode}"
            if (key !in asked && WeatherModePlanner.cleared(m.mode, v, ctx.weather, ctx.arrived)) enqueue(modeOffQuestion(m.mode, key))
        }

        // 4) 날씨 맞춤 등화
        val firstKm = AlertPlanner.firstAlertKm(settings.careful)
        val fog = hazards.firstOrNull {
            it.kind == HazardKind.FOG && it.level != RiskLevel.WATCH &&
                it.spot.atKm - ctx.progressKm <= firstKm && ctx.progressKm < it.spot.endKm
        }
        if (v.isDriving && LightPlanner.headlightNeeded(v, ctx.weather, fog != null)) {
            enqueueOnce(lampOnQuestion(Lamp.LOW_BEAM, "lamp-on:LOW_BEAM", fog != null))
        }
        if (!v.wiperRunning && ctx.weather.precipNow == Precip.NONE && fog == null) asked.remove("lamp-on:LOW_BEAM")
        if (fog != null && LightPlanner.fogLightNeeded(v, fog)) {
            enqueueOnce(lampOnQuestion(Lamp.FRONT_FOG, "lamp-on:FRONT_FOG:${fog.spot.id}", true))
        }
        if (Lamp.FRONT_FOG in lampsOn && fog == null && v.frontFog.isOn) enqueueOnce(lampOffQuestion(Lamp.FRONT_FOG))

        val badWeather = v.wiperRunning || ctx.weather.precipNow != Precip.NONE || fog != null ||
            hazards.any { it.kind == HazardKind.HEAVY_RAIN && it.level == RiskLevel.DANGER && ctx.progressKm in (it.spot.atKm - 1.0)..it.spot.endKm }
        if (braking && badWeather && Lamp.HAZARD !in lampsOn) {
            enqueueOnce(lampOnQuestion(Lamp.HAZARD, "lamp-on:HAZARD", false).copy(urgent = true), front = true)
        }
        if (Lamp.HAZARD in lampsOn && !braking && v.speedKmh >= 40.0) enqueueOnce(lampOffQuestion(Lamp.HAZARD))

        // 5) 스위치를 직접 눌러 달라고 한 등화가 켜졌는지 확인
        val confirmed = awaiting.filter { (lamp, want) -> lampIsOn(lamp, v) == want.first }.keys
        confirmed.forEach { lamp ->
            val on = awaiting.remove(lamp)!!.first
            fx += Effect.Speak(Phrases.lampConfirmed(lamp, on))
        }
        awaiting.entries.removeAll { nowMs - it.value.second > 30_000L }

        // 6) 질문: 한 번에 하나, 물어도 되는 순간에만
        reAsk?.let { (q, at) ->
            if (nowMs >= at && current == null) {
                reAsk = null
                if (stillValid(q)) queue.add(0, q)
            }
        }
        queue.removeAll { !stillValid(it) }
        if (onScreen?.let { !stillValid(it) } == true) onScreen = null
        if (current == null) {
            val next = queue.firstOrNull { QuestionPolicy.canAskNow(v, braking, naviSpeaking, it.urgent) }
            if (next != null) {
                queue.remove(next)
                current = next
                onScreen = null
                fx += Effect.Ask(next)
            }
        }
        return fx
    }

    /** 대답 (음성 또는 화면 버튼) */
    fun answer(questionId: String, answer: Answer, nowMs: Long): List<Effect> {
        val q = when (questionId) {
            current?.id -> current.also { current = null }
            onScreen?.id -> onScreen.also { onScreen = null }
            else -> null
        } ?: return emptyList()
        if (reAsk?.first?.key == q.key) reAsk = null
        val fx = mutableListOf<Effect>()
        val yes = answer == Answer.YES
        when (val k = q.kind) {
            is QuestionKind.Route -> {
                if (yes) {
                    detourTaken = true
                    fx += Effect.Speak("${k.detour.label}로 안내할게요.")
                } else {
                    fx += Effect.Speak("지금 길로 갈게요. 위험한 곳은 미리 알려 드릴게요.")
                }
            }
            is QuestionKind.ModeOn -> if (yes) fx += turnModeOn(k.proposal) else fx += Effect.Speak(Phrases.LATER_HINT.replace("눈 모드", k.proposal.mode.label))
            is QuestionKind.ModeOff -> if (yes) fx += turnModeOff()
            is QuestionKind.LampOn -> if (yes) fx += setLamp(k.lamp, true, nowMs)
            is QuestionKind.LampOff -> if (yes) {
                fx += setLamp(k.lamp, false, nowMs)
                if (k.lamp == Lamp.HAZARD) asked.remove("lamp-on:HAZARD")
            }
            QuestionKind.CarefulOffer -> {
                settings = settings.copy(careful = yes, carefulOffered = true)
                fx += Effect.SaveSettings(settings)
                if (yes) fx += Effect.Speak(Phrases.CAREFUL_ON)
            }
        }
        return fx
    }

    /** 대답을 못 들었을 때: 아무것도 바꾸지 않고 화면에 남겨 둔다. 꼼꼼히 안내는 안전한 순간에 한 번 더 묻는다. */
    fun noAnswer(questionId: String, nowMs: Long): List<Effect> {
        val q = current?.takeIf { it.id == questionId } ?: return emptyList()
        current = null
        onScreen = q
        if (settings.careful && !q.reAsked) reAsk = q.copy(id = nextId(), reAsked = true) to (nowMs + RE_ASK_DELAY_MS)
        return emptyList()
    }

    /** 화면에 남은 질문을 닫는다 (답하지 않고) */
    fun dismiss(questionId: String) {
        if (onScreen?.id == questionId) onScreen = null
    }

    /** 차량 제어 결과: 못 켰으면 스위치 위치를 안내하거나 직접 켜 달라고 한다 */
    fun controlResult(command: ControlCommand, ok: Boolean, nowMs: Long): List<Effect> {
        if (ok) return emptyList()
        return when (command) {
            is ControlCommand.SetLamp -> {
                awaiting[command.lamp] = command.on to nowMs
                listOf(Effect.Guide(command.lamp, command.on, LightPlanner.guide(command.lamp, command.on)))
            }
            is ControlCommand.SetHeater -> {
                activeMode = activeMode?.let { m -> m.copy(turnedOn = m.turnedOn - command.heater) }
                if (command.on) listOf(Effect.Speak(Phrases.controlFailed(command.heater.label))) else emptyList()
            }
        }
    }

    // ---------------- Gleo 음성 명령 ----------------

    fun gleoRouteRisk(): String = Phrases.routeRiskSummary(ahead().map { it.hazard to it.distanceKm }, ctx.routeKnown)

    fun gleoBriefing(nowMs: Long): List<Effect> = brief(nowMs, again = true)

    /** “헤일로, 눈 모드 켜 줘”: 직접 요청이라 묻지 않고 바로 한다 */
    fun gleoWeatherMode(mode: WeatherMode?): Pair<String, List<Effect>> {
        if (mode == null) {
            val m = activeMode ?: return "켜져 있는 날씨 모드가 없어요." to emptyList()
            asked += "mode-off:${m.mode}"
            val fx = turnModeOff()
            return Phrases.MODE_OFF_DONE to fx
        }
        val p = WeatherModePlanner.propose(mode, vehicle, ctx.weather)
            ?: return "켤 수 있는 열선이 이미 모두 켜져 있어요." to emptyList()
        asked += "mode-on:$mode"
        val fx = turnModeOn(p)
        val text = (fx.firstOrNull { it is Effect.Speak } as? Effect.Speak)?.text ?: ""
        return text to fx
    }

    // ---------------- 화면 상태 ----------------

    fun snapshot(): HaloSnapshot {
        val ahead = ahead()
        val delayAhead = if (ctx.routeKnown) BriefingPlanner.delayMinutes(ahead.map { it.hazard }, ctx.baseRemainingMin, ctx.weather) else 0
        val extra = if (detourTaken) ctx.detour?.extraMin ?: 0 else 0
        val arrival = ctx.baseRemainingMin?.let { Clock.plus(ctx.clock, it + delayAhead + extra) }
        val phase = when {
            ctx.arrived -> Phase.ARRIVED
            vehicle.isDriving -> Phase.DRIVING
            else -> Phase.BRIEFING
        }
        return HaloSnapshot(
            phase = phase,
            clock = ctx.clock,
            vehicle = vehicle,
            weather = ctx.weather,
            settings = settings,
            routeKnown = ctx.routeKnown,
            progressKm = ctx.progressKm,
            briefing = briefing,
            ahead = ahead,
            alert = alert,
            question = current ?: onScreen,
            asking = current != null,
            activeMode = activeMode,
            lampsOnByApp = lampsOn.toSet(),
            arrivalClock = arrival,
            delayMin = delayAhead + extra,
            detourTaken = detourTaken,
            routeLengthKm = ctx.routeLengthKm,
        )
    }

    // ---------------- 내부 ----------------

    private fun routeSpots(): List<RouteSpot> {
        val d = ctx.detour
        return if (detourTaken && d != null) ctx.spots.filter { it.id !in d.avoidsSpotIds } else ctx.spots
    }

    private fun ahead(): List<HazardAhead> = hazards
        .filter { ctx.progressKm < it.spot.endKm }
        .map { HazardAhead(it, (it.spot.atKm - ctx.progressKm).coerceAtLeast(0.0)) }
        .sortedBy { it.distanceKm }

    private fun brief(nowMs: Long, again: Boolean = false): List<Effect> {
        val fx = mutableListOf<Effect>()
        val temp = vehicle.outsideTempC
        val firstFrost = !again && temp != null && temp <= 0.0 && settings.firstFrostSeason != season
        val full = again || settings.careful || settings.lastFullBriefingDay != today
        val b = BriefingPlanner.build(vehicle, ctx, hazards, full, settings.careful, firstFrost)
        briefing = b
        fx += Effect.Speak(b.speech)
        if (!again) {
            var next = settings.copy(lastFullBriefingDay = if (full) today else settings.lastFullBriefingDay)
            if (firstFrost) next = next.copy(firstFrostSeason = season)
            if (next != settings) {
                settings = next
                fx += Effect.SaveSettings(settings)
            }
            ctx.detour?.let { d ->
                if (hazards.any { it.spot.id in d.avoidsSpotIds && it.level != RiskLevel.WATCH }) enqueue(routeQuestion(d))
            }
            if (offerCareful && !settings.carefulOffered && !settings.careful) enqueue(carefulQuestion())
        }
        return fx
    }

    private fun genericIce(nowMs: Long): Effect? {
        val temp = vehicle.outsideTempC ?: return null
        if (temp > RiskEngine.ICE_TEMP_C || vehicle.tempDrop10MinC < RiskEngine.SHARP_DROP_C || !vehicle.isDriving) return null
        val last = genericIceAt
        if (last != null && nowMs - last < 30 * 60_000L) return null
        genericIceAt = nowMs
        return Effect.Speak(Phrases.genericIceWarning(temp, vehicle.tempDrop10MinC, settings.careful))
    }

    private fun turnModeOn(p: ModeProposal): List<Effect> {
        activeMode = ActiveMode(p.mode, p.turnOn.map { it.heater }, p.keep)
        return p.turnOn.map { Effect.Control(ControlCommand.SetHeater(it.heater, true)) } +
            Effect.Speak(Phrases.modeOnDone(p, settings.careful))
    }

    private fun turnModeOff(): List<Effect> {
        val m = activeMode ?: return emptyList()
        activeMode = null
        return m.turnedOn.map { Effect.Control(ControlCommand.SetHeater(it, false)) } + Effect.Speak(Phrases.MODE_OFF_DONE)
    }

    private fun setLamp(lamp: Lamp, on: Boolean, nowMs: Long): List<Effect> {
        if (on) lampsOn += lamp else lampsOn -= lamp
        val fx = mutableListOf<Effect>()
        val controllable = when (lamp) {
            Lamp.LOW_BEAM -> vehicle.lowBeam.controllable
            Lamp.FRONT_FOG -> vehicle.frontFog.controllable
            Lamp.HAZARD -> false // 비상등 제어 API 없음: 버튼 위치만 안내
        }
        if (controllable) {
            fx += Effect.Control(ControlCommand.SetLamp(lamp, on))
            fx += Effect.Speak(Phrases.lampDone(lamp, on))
        } else {
            fx += Effect.Guide(lamp, on, LightPlanner.guide(lamp, on))
            if (lamp != Lamp.HAZARD) awaiting[lamp] = on to nowMs
        }
        if (on && lamp == Lamp.FRONT_FOG) fx += Effect.Speak(Phrases.FOG_LIGHT_TIP)
        return fx
    }

    private fun lampIsOn(lamp: Lamp, v: VehicleState): Boolean = when (lamp) {
        Lamp.LOW_BEAM -> v.lowBeam.isOn
        Lamp.FRONT_FOG -> v.frontFog.isOn
        Lamp.HAZARD -> false
    }

    private fun stillValid(q: Question): Boolean = when (val k = q.kind) {
        is QuestionKind.LampOn -> !lampIsOn(k.lamp, vehicle) || k.lamp == Lamp.HAZARD
        is QuestionKind.LampOff -> k.lamp == Lamp.HAZARD || lampIsOn(k.lamp, vehicle)
        is QuestionKind.ModeOn -> activeMode == null
        is QuestionKind.ModeOff -> activeMode?.mode == k.mode
        is QuestionKind.Route -> !vehicle.isDriving || ctx.progressKm < 0.5
        QuestionKind.CarefulOffer -> !settings.carefulOffered
    }

    private fun enqueue(q: Question, front: Boolean = false) {
        asked += q.key
        if (front) queue.add(0, q) else queue.add(q)
    }

    private fun enqueueOnce(q: Question, front: Boolean = false) {
        if (q.key in asked) return
        enqueue(q, front)
    }

    private fun nextId(): String = "q${++seq}"

    private fun routeQuestion(d: Detour) = Question(
        id = nextId(), key = "detour", kind = QuestionKind.Route(d),
        prompt = Phrases.detourPrompt(d), title = "${d.label}(+${d.extraMin}분)로 갈까요?",
        yesLabel = "다른 길로", noLabel = "이대로 갈게",
    )

    private fun modeOnQuestion(p: ModeProposal, key: String) = Question(
        id = nextId(), key = key, kind = QuestionKind.ModeOn(p),
        prompt = Phrases.modeOnPrompt(p, ctx.weather), title = "${p.mode.label} 켤까요?",
        yesLabel = "켜기", noLabel = "나중에",
    )

    private fun modeOffQuestion(mode: WeatherMode, key: String) = Question(
        id = nextId(), key = key, kind = QuestionKind.ModeOff(mode),
        prompt = Phrases.modeOffPrompt(mode, ctx.arrived), title = "${mode.label} 끌까요?",
        yesLabel = "끄기", noLabel = "그대로",
    )

    private fun lampOnQuestion(lamp: Lamp, key: String, fog: Boolean) = Question(
        id = nextId(), key = key, kind = QuestionKind.LampOn(lamp),
        prompt = Phrases.lampOnPrompt(lamp, ctx.weather, fog), title = "${lamp.label} 켤까요?",
        yesLabel = "응, 켜 줘", noLabel = "괜찮아",
    )

    private fun lampOffQuestion(lamp: Lamp) = Question(
        id = nextId(), key = "lamp-off:$lamp", kind = QuestionKind.LampOff(lamp),
        prompt = Phrases.lampOffPrompt(lamp), title = "${lamp.label} 끌까요?",
        yesLabel = "응, 꺼 줘", noLabel = "그대로",
    )

    private fun carefulQuestion() = Question(
        id = nextId(), key = "careful-offer", kind = QuestionKind.CarefulOffer,
        prompt = Phrases.CAREFUL_OFFER, title = "꼼꼼히 안내를 켤까요?",
        yesLabel = "켜기", noLabel = "괜찮아",
    )

    private companion object {
        const val RE_ASK_DELAY_MS = 20_000L
    }
}
