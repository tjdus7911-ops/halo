package com.halo.domain

import kotlin.math.roundToInt

/**
 * Gleo AI(Pleos Connect 차량 음성 비서)가 부르는 헤일로 기능. 이름은 assets/schema.json 과 같다.
 *
 * 운전자가 “헤일로, 눈 모드 켜 줘”처럼 말하면 Gleo 가 함수를 고르고
 * Intent(action = 함수 이름, extra "ACTION_PARAM" = 매개변수 JSON)를 앱에 보낸다 (component = "service" → sendBroadcast).
 * 앱은 처리한 결과를 Gleo 로 돌려주고([GleoResponse]), Gleo 가 그 결과로 운전자에게 답한다.
 */
sealed interface GleoCommand {
    /** 남은 길의 위험 다시 듣기 */
    data object RouteRisk : GleoCommand

    /** 오늘 길 브리핑 다시 듣기 */
    data object Briefing : GleoCommand

    /** 눈·비 모드 켜기, [mode] 가 null 이면 켜 둔 모드 끄기 */
    data class SetWeatherMode(val mode: WeatherMode?) : GleoCommand

    companion object {
        const val CHECK_ROAD_RISK = "CHECK_ROAD_RISK"
        const val START_BRIEFING = "START_BRIEFING"
        const val SET_WEATHER_MODE = "SET_WEATHER_MODE"

        /** schema.json 의 actionCalls (Gleo 규칙상 최대 3개) */
        val FUNCTIONS = listOf(CHECK_ROAD_RISK, START_BRIEFING, SET_WEATHER_MODE)

        /** [params] 는 ACTION_PARAM 을 풀어 놓은 값 (또는 extra 하나씩 들어온 값). 매개변수가 비었거나 모르는 값이면 null */
        fun parse(action: String?, params: Map<String, String?>): GleoCommand? = when (action) {
            CHECK_ROAD_RISK -> RouteRisk
            START_BRIEFING -> Briefing
            SET_WEATHER_MODE -> when (params["mode"]?.trim()?.uppercase()) {
                "SNOW" -> SetWeatherMode(WeatherMode.SNOW)
                "RAIN" -> SetWeatherMode(WeatherMode.RAIN)
                "OFF" -> SetWeatherMode(null)
                else -> null
            }
            else -> null
        }

        /** 함수는 알지만 매개변수가 비었거나 모르는 값일 때: Gleo 가 운전자에게 되묻게 한다 (status = asking) */
        fun askingFor(action: String?): GleoResult = when (action) {
            SET_WEATHER_MODE -> GleoResult.asking("Should Halo turn on snow mode, turn on rain mode, or turn the weather mode off?")
            else -> GleoResult.fail("This request is not supported by Halo.")
        }
    }
}

/**
 * 앱이 처리한 결과 (Gleo Callback).
 * - [message]: Gleo 가 답을 만들 때 쓰는 한 문장. Pleos 가이드대로 영어 한 문장으로 쓴다
 * - [screenText]: 차량 화면에 보여 줄 한국어 문장
 * - [data]: Gleo 가 답을 만들 때 참고할 값 (JSON 배열 문자열로 보낸다)
 */
data class GleoResult(
    val status: Status,
    val message: String,
    val screenText: String? = null,
    val data: Map<String, String> = emptyMap(),
) {
    enum class Status(val value: String) { SUCCESS("success"), ASKING("asking"), FAIL("fail") }

    companion object {
        fun success(message: String, screenText: String? = null, data: Map<String, String> = emptyMap()) =
            GleoResult(Status.SUCCESS, message, screenText, data)

        fun fail(message: String, screenText: String? = null) = GleoResult(Status.FAIL, message, screenText)

        fun asking(message: String) = GleoResult(Status.ASKING, message)
    }
}

/** Gleo 에 돌려줄 영어 문장 */
object GleoReplies {

    fun routeRisk(ahead: List<HazardAhead>, routeKnown: Boolean): GleoResult {
        val korean = Phrases.routeRiskSummary(ahead.map { it.hazard to it.distanceKm }, routeKnown)
        val serious = ahead.filter { it.hazard.level != RiskLevel.WATCH }
        val message = when {
            serious.isNotEmpty() -> {
                val nearest = serious.first()
                val count = if (serious.size == 1) "There is one risky spot ahead" else "There are ${serious.size} risky spots ahead"
                "$count; the nearest is ${hazardName(nearest.hazard)} ${distance(nearest.distanceKm)} ahead (${levelName(nearest.hazard.level)})."
            }
            !routeKnown -> "Road weather for this route is not connected, so Halo is judging icy roads from the car's outside temperature only."
            else -> "There are no icy, foggy or heavy-rain spots left on this route."
        }
        return GleoResult.success(message, korean, mapOf("summary" to korean))
    }

    fun briefing(): GleoResult = GleoResult.success("Halo is reading today's weather briefing aloud.")

    fun modeOn(mode: WeatherMode, heaters: List<Heater>, korean: String): GleoResult {
        val name = if (mode == WeatherMode.SNOW) "Snow mode" else "Rain mode"
        val list = heaters.map { heaterName(it) }
        val turned = when (list.size) {
            0 -> "nothing new needed to be turned on"
            1 -> "Halo turned on the ${list[0]}"
            else -> "Halo turned on the ${list.dropLast(1).joinToString(", ")} and the ${list.last()}"
        }
        return GleoResult.success("$name is on; $turned.", korean)
    }

    fun modeOff(korean: String): GleoResult =
        GleoResult.success("The weather mode is off and the heaters Halo turned on are back to their previous settings.", korean)

    fun noModeOn(korean: String): GleoResult = GleoResult.success("No weather mode is on right now.", korean)

    fun nothingToTurnOn(korean: String): GleoResult = GleoResult.success("Every heater for this mode is already on.", korean)

    private fun hazardName(h: RouteHazard): String = when (h.kind) {
        HazardKind.ICE -> "possible black ice at the ${placeName(h.spot.kind)}"
        HazardKind.FOG -> "dense fog"
        HazardKind.HEAVY_RAIN -> "heavy rain"
    }

    private fun placeName(kind: SpotKind): String = when (kind) {
        SpotKind.BRIDGE -> "bridge"
        SpotKind.TUNNEL_EXIT -> "tunnel exit"
        SpotKind.ICE_HOTSPOT -> "frequent icing spot"
        SpotKind.SLIPPERY -> "slippery-road section"
        SpotKind.RIVERSIDE -> "riverside road"
        SpotKind.ROAD -> "road"
    }

    private fun levelName(level: RiskLevel): String = when (level) {
        RiskLevel.WATCH -> "watch"
        RiskLevel.CAUTION -> "caution"
        RiskLevel.DANGER -> "danger"
    }

    private fun heaterName(h: Heater): String = when (h) {
        Heater.MIRROR -> "side mirror heater"
        Heater.STEERING -> "steering wheel heater"
        Heater.DEFROST -> "windshield defroster"
    }

    private fun distance(km: Double): String = when {
        km <= 0.05 -> "right"
        km < 0.95 -> "${((km * 1000) / 50.0).roundToInt() * 50} m"
        else -> ((km * 2).roundToInt() / 2.0).let { if (it % 1.0 == 0.0) "${it.toInt()} km" else "$it km" }
    }
}

/**
 * Gleo 로 돌려줄 응답. Intent action = [ACTION], extra "result" = 상태, "packageName" = 앱 패키지,
 * "response" = {"functionName","status","message","data"} JSON (data 는 JSON 배열을 문자열로 담는다).
 * Gleo 는 7초가 넘게 걸리는 처리를 취소하므로 받자마자 바로 답한다.
 */
object GleoResponse {
    const val ACTION = "ai.pleos.playground.intent.action.APP_ACTION_RESPONSE"

    fun json(functionName: String, result: GleoResult): String = buildString {
        append("{\"functionName\":").append(quote(functionName))
        append(",\"status\":").append(quote(result.status.value))
        append(",\"message\":").append(quote(result.message))
        if (result.data.isNotEmpty()) append(",\"data\":").append(quote(dataArray(result.data)))
        append('}')
    }

    /** [{"key":"value", …}] */
    fun dataArray(data: Map<String, String>): String =
        data.entries.joinToString(separator = ",", prefix = "[{", postfix = "}]") { (k, v) -> "${quote(k)}:${quote(v)}" }

    fun quote(s: String): String = buildString {
        append('"')
        for (c in s) {
            when (c) {
                '"' -> append("\\\"")
                '\\' -> append("\\\\")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> if (c < ' ') append(String.format("\\u%04x", c.code)) else append(c)
            }
        }
        append('"')
    }
}

/**
 * ACTION_PARAM 읽기. 매개변수가 평평한 JSON 객체라서 작은 읽개로 읽는다 (JVM 단위 테스트에서도 같은 코드가 돈다).
 * 객체를 따옴표로 한 번 더 감싸 오거나, JSON 문자열로 한 번 더 감싸 와도 읽는다.
 * 문자열·숫자·true/false 는 글자로 받고, null 과 중첩 객체·배열은 건너뛴다. 읽지 못하면 빈 맵.
 */
object GleoParams {
    fun parse(raw: String?): Map<String, String> {
        val text = unwrap(raw ?: return emptyMap()) ?: return emptyMap()
        return try {
            FlatJsonReader(text).readObject()
        } catch (e: RuntimeException) {
            emptyMap()
        }
    }

    internal fun unwrap(raw: String): String? {
        var s = raw.trim()
        repeat(3) {
            if (s.startsWith("{")) return s
            if (s.length < 2 || s.first() != '"' || s.last() != '"') return null
            val inner = s.substring(1, s.length - 1).trim()
            // "{\"mode\":…}" 처럼 JSON 문자열로 감싼 경우는 이스케이프를 풀고, "{"mode":…}" 는 바깥 따옴표만 벗긴다
            s = if (inner.drop(1).trimStart().startsWith("\\\"")) {
                try {
                    FlatJsonReader(s).readString()
                } catch (e: RuntimeException) {
                    return null
                }
            } else {
                inner
            }
        }
        return s.takeIf { it.startsWith("{") }
    }
}

private class FlatJsonReader(private val s: String) {
    private var i = 0

    fun readObject(): Map<String, String> {
        val out = LinkedHashMap<String, String>()
        skipWs()
        expect('{')
        skipWs()
        if (peek() == '}') return out
        while (true) {
            skipWs()
            val key = readString()
            skipWs()
            expect(':')
            skipWs()
            val value = readValue()
            if (value != null) out[key] = value
            skipWs()
            when (next()) {
                ',' -> Unit
                '}' -> return out
                else -> error("expected , or }")
            }
        }
    }

    fun readString(): String {
        expect('"')
        val sb = StringBuilder()
        while (true) {
            when (val c = next()) {
                '"' -> return sb.toString()
                '\\' -> when (val e = next()) {
                    '"', '\\', '/' -> sb.append(e)
                    'b' -> sb.append('\b')
                    'f' -> sb.append('\u000C')
                    'n' -> sb.append('\n')
                    'r' -> sb.append('\r')
                    't' -> sb.append('\t')
                    'u' -> {
                        sb.append(s.substring(i, i + 4).toInt(16).toChar())
                        i += 4
                    }
                    else -> error("bad escape")
                }
                else -> sb.append(c)
            }
        }
    }

    private fun readValue(): String? = when (peek()) {
        '"' -> readString()
        '{', '[' -> {
            skipNested()
            null
        }
        else -> {
            val start = i
            while (i < s.length && s[i] !in ",}] \t\r\n") i++
            val token = s.substring(start, i)
            require(token.isNotEmpty()) { "empty value" }
            token.takeUnless { it == "null" }
        }
    }

    private fun skipNested() {
        var depth = 0
        do {
            when (peek()) {
                '"' -> {
                    readString()
                    continue
                }
                '{', '[' -> depth++
                '}', ']' -> depth--
            }
            i++
        } while (depth > 0)
    }

    private fun skipWs() {
        while (i < s.length && s[i].isWhitespace()) i++
    }

    private fun peek(): Char = s[i]

    private fun next(): Char = s[i++]

    private fun expect(c: Char) {
        val got = next()
        require(got == c) { "expected $c but was $got" }
    }
}
