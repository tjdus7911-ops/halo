package com.halo.domain

import com.halo.demo.DemoRun
import com.halo.demo.DemoScenario

/**
 * 시연 시나리오를 판단 엔진에 넣고, 화면 앱이 하듯 효과(제어·안내)를 처리한다.
 * [answers] 는 질문 키의 앞부분 → 대답. 없는 질문은 대답하지 않은 것으로 본다.
 */
class TestDrive(
    scenario: DemoScenario,
    private val answers: Map<String, Answer?> = emptyMap(),
    careful: Boolean = scenario.careful,
    /** 이 키로 시작하는 질문은 대답하지 않고 묻는 중으로 둔다 (화면 확인용) */
    private val hold: Set<String> = emptySet(),
) {
    val run = DemoRun(scenario)
    val engine = HaloEngine(HaloSettings(careful = careful, carefulOffered = true, firstFrostSeason = "2026-27"), today = "2027-01-12", season = "2026-27", offerCareful = false)
    val spoken = mutableListOf<String>()
    val asked = mutableListOf<Question>()
    val controls = mutableListOf<ControlCommand>()
    val guides = mutableListOf<Effect.Guide>()

    /** 지금 장면을 엔진에 넣고, 나온 질문에 대답하면서 더 나올 게 없을 때까지 돈다 */
    fun step(): HaloSnapshot {
        val f = run.frame
        f.brakeFromKmh?.let { engine.update(run.vehicle().copy(speedKmh = it), run.context(), run.nowMs - 2_000L) }
        var effects = engine.update(run.vehicle(), run.context(), run.nowMs)
        repeat(10) {
            val next = handle(effects)
            if (next.isEmpty()) return engine.snapshot()
            effects = next + engine.update(run.vehicle(), run.context(), run.nowMs)
        }
        return engine.snapshot()
    }

    fun next(): Boolean = run.next()

    fun playAll(): List<HaloSnapshot> {
        val out = mutableListOf(step())
        while (next()) out += step()
        return out
    }

    private fun handle(effects: List<Effect>): List<Effect> {
        val more = mutableListOf<Effect>()
        for (e in effects) when (e) {
            is Effect.Speak -> spoken += e.text
            is Effect.Guide -> {
                spoken += e.text
                guides += e
                run.guided(e.lamp, e.on)
            }
            is Effect.Control -> {
                controls += e.command
                more += engine.controlResult(e.command, run.apply(e.command), run.nowMs)
            }
            is Effect.Ask -> {
                asked += e.question
                spoken += e.question.prompt
                if (hold.any { e.question.key.startsWith(it) }) continue
                val a = answers.entries.firstOrNull { e.question.key.startsWith(it.key) }?.value
                more += if (a == null) engine.noAnswer(e.question.id, run.nowMs) else engine.answer(e.question.id, a, run.nowMs)
            }
            is Effect.SaveSettings -> Unit
        }
        return more
    }
}
