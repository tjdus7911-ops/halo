package com.halo

import android.app.Application
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.halo.data.CarVehicleFeed
import com.halo.data.FeedStatus
import com.halo.data.HandlerScheduler
import com.halo.data.NaviVoiceMonitor
import com.halo.data.PleosNaviFeed
import com.halo.data.SettingsStore
import com.halo.data.VehicleFeed
import com.halo.data.speech.AndroidAnswerListener
import com.halo.data.speech.AndroidSpeaker
import com.halo.data.speech.PleosAnswerListener
import com.halo.data.speech.PleosSpeaker
import com.halo.demo.DemoRun
import com.halo.demo.DemoScenarios
import com.halo.domain.Answer
import com.halo.domain.ControlCommand
import com.halo.domain.Effect
import com.halo.domain.GleoCommand
import com.halo.domain.GleoReplies
import com.halo.domain.GleoResult
import com.halo.domain.HaloEngine
import com.halo.domain.HaloSettings
import com.halo.domain.Lamp
import com.halo.domain.LiveTrip
import com.halo.gleo.GleoBridge
import com.halo.ui.state.DemoUi
import com.halo.ui.state.GuideNote
import com.halo.ui.state.HaloUiState
import com.halo.ui.state.Source
import com.halo.ui.state.VoiceUi
import com.halo.voice.AnswerListener
import com.halo.voice.FirstReadyListener
import com.halo.voice.Speaker
import com.halo.voice.VoiceFlow
import com.halo.voice.VoiceState
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * 헤일로 화면의 상태. 판단은 [HaloEngine] 이 하고, 여기서는
 * 차량·내비 값을 1초마다 엔진에 넣고, 엔진이 낸 효과(읽기·묻기·제어·안내)를 처리한다.
 * 음성은 [VoiceFlow] 가 한 번에 하나씩 차례로 읽고 듣는다.
 */
class HaloViewModel(app: Application) : AndroidViewModel(app) {

    private val main = Handler(Looper.getMainLooper())
    private val settingsStore = SettingsStore(app)
    val isAutomotive: Boolean = app.packageManager.hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE)

    private val speaker: Speaker = PleosSpeaker(app, AndroidSpeaker(app))
    private val listener: AnswerListener = FirstReadyListener(listOf(PleosAnswerListener(app), AndroidAnswerListener(app)))
    private val naviVoice = NaviVoiceMonitor(app)

    // 실제 차량 (권한을 물은 뒤 연결한다)
    private var feed: VehicleFeed? = null
    private var navi: PleosNaviFeed? = null
    private var trip = LiveTrip()
    private var liveConnectedAt: Long? = null
    private var lastIgnition: Boolean? = null

    // 시연
    private var demo: DemoRun? = null
    private var demoFrameStartedAt = 0L
    private var demoLastNow = 0L

    private var source: Source = if (isAutomotive) Source.LIVE else Source.DEMO
    private var engine: HaloEngine? = null
    private var guide: GuideNote? = null
    private var settingsOpen = false

    private val voice = VoiceFlow(speaker, listener, HandlerScheduler(main), object : VoiceFlow.Callbacks {
        override fun isAsking(questionId: String): Boolean {
            val snap = engine?.snapshot() ?: return false
            return snap.asking && snap.question?.id == questionId
        }

        override fun onAnswer(questionId: String, answer: Answer, heard: String?) {
            engine?.let { handle(it.answer(questionId, answer, now())) }
            publish()
        }

        override fun onNoAnswer(questionId: String) {
            engine?.let { handle(it.noAnswer(questionId, now())) }
            publish()
        }

        override fun onStateChanged(state: VoiceState) = publish()

        override fun naviSpeaking(): Boolean = source == Source.LIVE && naviVoice.guidancePlaying.value
    })

    private val _ui = MutableStateFlow(HaloUiState(source = source, feed = FeedStatus.CONNECTING, isAutomotive = isAutomotive))
    val ui: StateFlow<HaloUiState> = _ui.asStateFlow()

    private val gleoHandler: (GleoCommand) -> GleoResult = { handleGleo(it) }

    init {
        GleoBridge.handler = gleoHandler
        if (source == Source.DEMO) startDemo(DemoScenarios.all.first().id)
        viewModelScope.launch {
            while (isActive) {
                tick()
                delay(TICK_MS)
            }
        }
    }

    // ---------------- 화면에서 부르는 동작 ----------------

    /** 권한을 물은 뒤 (허용 여부와 상관없이): 차량이면 실제 차량에 연결한다 */
    fun onVehiclePermissionResult() {
        if (!isAutomotive || feed != null) return
        feed = CarVehicleFeed(getApplication())
        navi = PleosNaviFeed(getApplication())
        publish()
    }

    /** 화면 버튼으로 답하기 */
    fun answer(questionId: String, yes: Boolean) {
        val e = engine ?: return
        voice.answeredElsewhere(questionId) {
            handle(e.answer(questionId, if (yes) Answer.YES else Answer.NO, now()))
        }
        publish()
    }

    /** 대답하지 않고 화면에 남은 질문을 닫는다 */
    fun dismiss(questionId: String) {
        engine?.dismiss(questionId)
        publish()
    }

    fun setCareful(on: Boolean) {
        val e = engine
        if (e != null) {
            handle(e.setCareful(on))
        } else if (source == Source.LIVE) {
            settingsStore.save(settingsStore.load().copy(careful = on, carefulOffered = true))
        }
        publish()
    }

    fun openSettings(open: Boolean) {
        settingsOpen = open
        publish()
    }

    /** 오늘 길 브리핑을 다시 듣는다 */
    fun replayBriefing() {
        val e = engine ?: return
        settingsOpen = false
        handle(e.gleoBriefing(now()))
        publish()
    }

    fun useLive() {
        if (source == Source.LIVE) return
        voice.clear()
        demo = null
        source = Source.LIVE
        engine = null
        trip = LiveTrip()
        liveConnectedAt = null
        lastIgnition = null
        guide = null
        onVehiclePermissionResult()
        publish()
    }

    fun startDemo(scenarioId: String) {
        voice.clear()
        source = Source.DEMO
        val scenario = DemoScenarios.byId(scenarioId)
        val season = seasonOf(LocalDate.now())
        demo = DemoRun(scenario)
        demoFrameStartedAt = SystemClock.elapsedRealtime()
        demoLastNow = 0L
        guide = null
        // 시연은 저장된 설정과 섞지 않는다: 시나리오의 꼼꼼히 안내로 시작하고, 첫 실행 안내는 하지 않는다
        engine = HaloEngine(
            HaloSettings(careful = scenario.careful, carefulOffered = true, firstFrostSeason = season),
            today = DEMO_DAY,
            season = season,
            offerCareful = false,
        )
        tick()
    }

    fun demoNext() {
        val run = demo ?: return
        if (run.isLast) return
        voice.interrupt()
        run.next()
        demoFrameStartedAt = SystemClock.elapsedRealtime()
        val e = engine ?: return
        // 급감속 장면: 2초 전 속도를 먼저 넣는다
        run.frame.brakeFromKmh?.let { from ->
            handle(e.update(run.vehicle().copy(speedKmh = from), run.context(), demoNow() - BRAKE_LEAD_MS))
        }
        tick()
    }

    fun demoRestart() {
        val run = demo ?: return
        startDemo(run.scenario.id)
    }

    // ---------------- 1초마다 ----------------

    private fun tick() {
        when (source) {
            Source.DEMO -> demoTick()
            Source.LIVE -> liveTick()
        }
        clearGuideIfDone()
        publish()
    }

    private fun demoTick() {
        val run = demo ?: return
        val e = engine ?: return
        handle(e.update(run.vehicle(), run.context(), demoNow(), naviSpeaking = false))
    }

    private fun liveTick() {
        val f = feed ?: return
        if (f.status.value != FeedStatus.CONNECTED) return
        val nowMs = SystemClock.elapsedRealtime()
        val connectedAt = liveConnectedAt ?: nowMs.also { liveConnectedAt = it }
        val v = f.current(nowMs)
        // 첫 값들이 다 들어올 때까지 잠깐 기다린다 (브리핑에 외기온도가 빠지지 않게)
        if (v.outsideTempC == null && nowMs - connectedAt < LIVE_SETTLE_MS) return

        // 시동을 다시 걸면 새 주행으로 본다
        if (lastIgnition == false && v.ignitionOn) {
            engine = null
            trip = LiveTrip()
        }
        lastIgnition = v.ignitionOn

        val e = engine ?: newLiveEngine().also { engine = it }
        val ctx = trip.update(nowMs, LocalTime.now().format(CLOCK), v, navi?.info?.value)
        handle(e.update(v, ctx, nowMs, naviSpeaking = naviVoice.guidancePlaying.value))
    }

    private fun newLiveEngine(): HaloEngine {
        val today = LocalDate.now()
        return HaloEngine(settingsStore.load(), today.toString(), seasonOf(today), offerCareful = true)
    }

    private fun now(): Long = if (source == Source.DEMO) demoNow() else SystemClock.elapsedRealtime()

    /** 시연 시각: 장면의 시각 + 장면을 띄운 뒤 지난 시간 (뒤로 가지 않게) */
    private fun demoNow(): Long {
        val run = demo ?: return 0L
        val t = maxOf(run.nowMs + (SystemClock.elapsedRealtime() - demoFrameStartedAt), demoLastNow)
        demoLastNow = t
        return t
    }

    // ---------------- 엔진 효과 처리 ----------------

    private fun handle(effects: List<Effect>) {
        for (e in effects) when (e) {
            is Effect.Speak -> voice.say(e.text)
            is Effect.Ask -> voice.ask(e.question)
            is Effect.Control -> control(e.command)
            is Effect.Guide -> {
                guide = GuideNote(e.lamp, e.on, e.text, SystemClock.elapsedRealtime())
                voice.say(e.text)
                demo?.guided(e.lamp, e.on)
            }
            is Effect.SaveSettings -> if (source == Source.LIVE) settingsStore.save(e.settings)
        }
    }

    private fun control(command: ControlCommand) {
        val e = engine ?: return
        when (source) {
            Source.DEMO -> handle(e.controlResult(command, demo?.apply(command) ?: false, now()))
            Source.LIVE -> {
                val f = feed
                if (f == null) {
                    handle(e.controlResult(command, false, now()))
                    return
                }
                f.control(command) { ok ->
                    // 그사이 엔진이 바뀌었으면 (시연으로 바꿈 등) 결과를 버린다
                    if (engine === e) {
                        handle(e.controlResult(command, ok, now()))
                        publish()
                    }
                }
            }
        }
    }

    private fun clearGuideIfDone() {
        val g = guide ?: return
        val snap = engine?.snapshot()
        val done = snap != null && when (g.lamp) {
            Lamp.LOW_BEAM -> snap.vehicle.lowBeam.isOn == g.on
            Lamp.FRONT_FOG -> snap.vehicle.frontFog.isOn == g.on
            Lamp.HAZARD -> false
        }
        if (done || SystemClock.elapsedRealtime() - g.shownAt > GUIDE_SHOW_MS) guide = null
    }

    // ---------------- Gleo AI ----------------
    // 운전자가 Gleo 에게 말하면 Gleo 가 헤일로 기능을 부른다 (schema.json). Gleo 가 결과를 말로 답하므로
    // 날씨 모드 결과는 헤일로가 다시 읽지 않고 화면에만 보여 준다. 브리핑은 길어서 헤일로가 읽는다.

    private fun handleGleo(command: GleoCommand): GleoResult {
        val e = engine ?: return GleoResult.fail("Halo is still connecting to the car.", "차량에 연결하는 중이에요")
        val result = when (command) {
            GleoCommand.RouteRisk -> {
                val snap = e.snapshot()
                GleoReplies.routeRisk(snap.ahead, snap.routeKnown)
            }
            GleoCommand.Briefing -> {
                // Gleo 의 대답과 겹치지 않게 조금 뒤에 읽는다
                main.postDelayed({ if (engine === e) handle(e.gleoBriefing(now())) }, GLEO_SPEAK_DELAY_MS)
                GleoReplies.briefing()
            }
            is GleoCommand.SetWeatherMode -> {
                val before = e.snapshot().activeMode
                val (text, effects) = e.gleoWeatherMode(command.mode)
                handle(effects.filterNot { it is Effect.Speak })
                val mode = command.mode
                when {
                    mode == null && before == null -> GleoReplies.noModeOn(text)
                    mode == null -> GleoReplies.modeOff(text)
                    effects.isEmpty() -> GleoReplies.nothingToTurnOn(text)
                    else -> GleoReplies.modeOn(mode, e.snapshot().activeMode?.turnedOn.orEmpty(), text)
                }
            }
        }
        result.screenText?.let { voice.showCaption(it, 4_000L + it.length * 80L) }
        publish()
        return result
    }

    // ---------------- 화면 상태 ----------------

    private fun publish() {
        val f = feed
        val feedStatus = when {
            source == Source.DEMO -> FeedStatus.CONNECTED
            f == null -> if (isAutomotive) FeedStatus.CONNECTING else FeedStatus.UNAVAILABLE
            else -> f.status.value
        }
        val run = demo
        val v = voice.state
        _ui.value = HaloUiState(
            source = source,
            feed = feedStatus,
            snapshot = engine?.snapshot(),
            voice = VoiceUi(v.phase, v.caption, canListen = listener.ready, heard = v.heard),
            demo = if (source == Source.DEMO && run != null) {
                DemoUi(
                    scenarioId = run.scenario.id,
                    title = run.scenario.title,
                    persona = run.scenario.persona,
                    frameLabel = run.frame.label,
                    index = run.index,
                    count = run.scenario.frames.size,
                )
            } else {
                null
            },
            guide = guide,
            settingsOpen = settingsOpen,
            isAutomotive = isAutomotive,
        )
    }

    override fun onCleared() {
        if (GleoBridge.handler === gleoHandler) GleoBridge.handler = null
        main.removeCallbacksAndMessages(null)
        voice.clear()
        speaker.release()
        listener.release()
        naviVoice.release()
        feed?.release()
        navi?.release()
        super.onCleared()
    }

    private companion object {
        const val TICK_MS = 1_000L
        const val LIVE_SETTLE_MS = 4_000L
        const val GUIDE_SHOW_MS = 30_000L
        const val BRAKE_LEAD_MS = 2_000L
        const val GLEO_SPEAK_DELAY_MS = 2_500L
        const val DEMO_DAY = "demo"
        val CLOCK: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

        /** 겨울 기준 (7월부터 다음 해 6월까지): 2026-27 */
        fun seasonOf(day: LocalDate): String {
            val start = if (day.monthValue >= 7) day.year else day.year - 1
            return "$start-${((start + 1) % 100).toString().padStart(2, '0')}"
        }
    }
}
