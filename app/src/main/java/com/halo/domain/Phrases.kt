package com.halo.domain

import kotlin.math.abs
import kotlin.math.roundToInt

/** 음성·화면 문장. 한 문장에 하나만, “응 / 아니”로 답할 수 있게 묻는다. */
object Phrases {

    // ---------------- 거리·온도 ----------------

    /** 화면용: 1km, 1.5km, 300m (1km 가까이부터는 0.5km 단위로 말한다) */
    fun distance(km: Double): String = when {
        km < 0.95 -> "${roundTo50(km * 1000)}m"
        else -> halfKm(km).let { if (it % 1.0 == 0.0) "${it.toInt()}km" else "${it}km" }
    }

    /** 음성용: 1킬로미터, 1.5킬로미터, 300미터 */
    fun spokenDistance(km: Double): String = when {
        km < 0.95 -> "${roundTo50(km * 1000)}미터"
        else -> halfKm(km).let { if (it % 1.0 == 0.0) "${it.toInt()}킬로미터" else "${it}킬로미터" }
    }

    private fun halfKm(km: Double): Double = (km * 2).roundToInt() / 2.0

    private fun roundTo50(m: Double): Int = ((m / 50.0).roundToInt() * 50).coerceAtLeast(50)

    /** 음성용 기온: -4 → "영하 4도", 0.5 → "0.5도" */
    fun spokenTemp(c: Double): String {
        val r = (c * 10).roundToInt() / 10.0
        val body = if (r % 1.0 == 0.0) abs(r).toInt().toString() else abs(r).toString()
        return if (r < 0) "영하 ${body}도" else "${body}도"
    }

    // ---------------- 조사 ----------------

    private fun hasBatchim(word: String): Boolean {
        val c = word.trim().lastOrNull() ?: return false
        if (c in '가'..'힣') return (c - '가') % 28 != 0
        return c in "013678" // 영·일·삼·육·칠·팔
    }

    /** josa("열선", "은/는") → "열선은" */
    fun josa(word: String, pair: String): String {
        val (a, b) = pair.split("/")
        return word + if (hasBatchim(word)) a else b
    }

    // ---------------- 브리핑 ----------------

    fun briefing(
        v: VehicleState,
        ctx: DriveContext,
        hazards: List<RouteHazard>,
        delayMin: Int,
        checks: List<CheckItem>,
        full: Boolean,
        careful: Boolean,
        firstFrost: Boolean,
    ): String {
        val s = mutableListOf<String>()
        val temp = v.outsideTempC
        val frosty = temp != null && temp <= 0.0
        if (full) {
            s += if (frosty) "앞유리가 녹는 동안 오늘 길 알려 드릴게요." else "오늘 길 알려 드릴게요."
            if (firstFrost) s += "올겨울 첫 영하 아침이에요."
        }
        val w = ctx.weather
        val days = w.daysSincePrecip
        val refrozen = days != null && days in 1..RiskEngine.RECENT_PRECIP_DAYS && w.minTempTodayC <= 0.0
        when {
            temp != null && refrozen -> s += "지금 ${spokenTemp(temp)}, ${josa(RiskEngine.recentPrecipText(days!!, w), "이/가")} 와서 녹았다 언 곳이 있을 수 있어요."
            temp != null && w.precipNow != Precip.NONE -> s += "지금 ${spokenTemp(temp)}, ${josa(RiskEngine.precipLabel(w.precipNow), "이/가")} 와요."
            temp != null -> s += "지금 ${spokenTemp(temp)}예요."
        }
        if (w.precipNow == Precip.NONE && w.precipForecast != Precip.NONE && full) {
            s += "${josa(RiskEngine.precipLabel(w.precipForecast), "이/가")} 예보돼 있어요."
        }
        val summary = hazardSummary(hazards)
        if (summary != null) {
            s += if (delayMin > 0) "$summary, 천천히 가면 ${delayMin}분 늦어요." else "$summary 있어요."
            if (careful) reasonFor(hazards)?.let { s += it }
        } else if (ctx.routeKnown) {
            s += "경로에 살얼음이나 안개 구간은 없어요."
        }
        if (full) {
            checks.filter { it.level == CheckLevel.WARN }.forEach { c ->
                s += when (c.id) {
                    "tire" -> "타이어 공기압을 확인해 주세요."
                    "wiper" -> "와이퍼가 AUTO라 유리에 얼어붙지 않았는지 먼저 봐 주세요."
                    "washer" -> "워셔액이 부족해요."
                    else -> "${josa(c.label, "을/를")} 확인해 주세요."
                }
            }
        }
        return s.joinToString(" ")
    }

    /** "살얼음 주의 2곳", "살얼음 위험 1곳, 짙은 안개 1곳" (관심은 말하지 않는다) */
    fun hazardSummary(hazards: List<RouteHazard>): String? {
        val parts = mutableListOf<String>()
        val ice = hazards.filter { it.kind == HazardKind.ICE && it.level != RiskLevel.WATCH }
        val iceDanger = ice.count { it.level == RiskLevel.DANGER }
        val iceCaution = ice.count { it.level == RiskLevel.CAUTION }
        if (iceDanger > 0) parts += "살얼음 위험 ${iceDanger}곳"
        if (iceCaution > 0) parts += "살얼음 주의 ${iceCaution}곳"
        val fog = hazards.count { it.kind == HazardKind.FOG && it.level != RiskLevel.WATCH }
        if (fog > 0) parts += "짙은 안개 ${fog}곳"
        val rain = hazards.count { it.kind == HazardKind.HEAVY_RAIN && it.level == RiskLevel.DANGER }
        if (rain > 0) parts += "폭우 구간 ${rain}곳"
        return if (parts.isEmpty()) null else parts.joinToString(", ")
    }

    private fun reasonFor(hazards: List<RouteHazard>): String? {
        val serious = hazards.filter { it.level != RiskLevel.WATCH }
        val kinds = serious.filter { it.kind == HazardKind.ICE }.map { it.spot.kind }.toSet()
        return when {
            SpotKind.BRIDGE in kinds && SpotKind.TUNNEL_EXIT in kinds -> "다리 위와 터널 출구는 땅보다 먼저 얼어요."
            SpotKind.BRIDGE in kinds -> "다리 위는 땅보다 먼저 얼어요."
            SpotKind.TUNNEL_EXIT in kinds -> "터널 출구는 그늘이라 먼저 얼어요."
            serious.any { it.kind == HazardKind.HEAVY_RAIN } -> "빗길에서는 제동거리가 길어져요."
            serious.any { it.kind == HazardKind.FOG } -> "안개 속에서는 앞차가 갑자기 나타나요. 안개 구간에 가까워지면 다시 알려 드릴게요."
            else -> null
        }
    }

    // ---------------- 레이더 경보 ----------------

    private fun place(kind: SpotKind): String = when (kind) {
        SpotKind.BRIDGE -> "다리 위"
        SpotKind.TUNNEL_EXIT -> "터널 출구"
        SpotKind.ICE_HOTSPOT -> "결빙 사고가 잦은 곳"
        SpotKind.SLIPPERY -> "미끄럼 주의 구간"
        SpotKind.RIVERSIDE -> "강변 도로"
        SpotKind.ROAD -> "도로"
    }

    /** 화면 제목: "1km 앞 다리 위 살얼음 주의" (spoken = true 면 "1킬로미터 앞 …") */
    fun alertTitle(h: RouteHazard, distanceKm: Double, spoken: Boolean = false): String {
        val where = when {
            distanceKm <= 0.05 -> "지금"
            spoken -> "${spokenDistance(distanceKm)} 앞"
            else -> "${distance(distanceKm)} 앞"
        }
        return when (h.kind) {
            HazardKind.ICE -> "$where ${place(h.spot.kind)} 살얼음 ${h.level.label}"
            HazardKind.FOG -> if (h.level == RiskLevel.WATCH) "$where 안개" else "$where 짙은 안개"
            HazardKind.HEAVY_RAIN -> "$where 폭우 구간"
        }
    }

    /** 화면 부제: 지금 할 일 */
    fun alertAdvice(h: RouteHazard): String = when (h.kind) {
        HazardKind.ICE -> if (h.level == RiskLevel.DANGER) "속도를 절반으로 · 급제동·급조향 금지" else "지금부터 천천히 감속 · 급제동·급조향 금지"
        HazardKind.FOG -> {
            val vis = h.spot.visibilityM
            listOfNotNull(
                vis?.let { "가시거리 ${it}m" },
                if (h.slowDownPercent > 0) "${h.slowDownPercent}% 감속" else null,
                "차간 거리를 평소의 2배로",
            ).joinToString(" · ")
        }
        HazardKind.HEAVY_RAIN -> listOfNotNull(
            h.spot.rainMmPerHour?.let { "시간당 ${it}mm" },
            "${h.slowDownPercent}% 감속",
            "차간 거리를 평소의 2배로",
        ).joinToString(" · ")
    }

    fun alertSpeech(due: AlertPlanner.Due, careful: Boolean): String {
        val h = due.hazard
        val d = due.distanceKm
        val where = if (d <= 0.05) "지금" else "${spokenDistance(d)} 앞"
        val p = place(h.spot.kind)
        if (h.kind == HazardKind.ICE && h.spot.kind == SpotKind.SLIPPERY) return "$where 미끄럼 주의 구간이에요. 기온이 낮아 살얼음이 있을 수 있어요. 천천히 가세요."
        if (h.spot.navAnnounced && h.kind == HazardKind.ICE) return "$where $p, 내비가 알린 살얼음 구간이에요. 천천히 가세요."
        val main = when (h.kind) {
            HazardKind.ICE -> when {
                due.stage == AlertPlanner.Stage.SECOND -> "$where ${josa(p, "이에요/예요")}. 급제동하지 말고 그대로 지나가세요."
                h.level == RiskLevel.DANGER -> "$where $p 살얼음 위험이에요. 속도를 절반으로 줄이세요."
                else -> "$where $p 살얼음 주의예요. 지금부터 천천히 줄이세요."
            }
            HazardKind.FOG -> {
                val vis = h.spot.visibilityM?.let { " 가시거리 ${it}미터," } ?: ""
                val act = if (h.slowDownPercent >= 50) "속도를 절반으로 줄이세요." else "속도를 줄이고 차간 거리를 넉넉히 두세요."
                "$where 짙은 안개예요.$vis $act"
            }
            HazardKind.HEAVY_RAIN -> "$where 폭우 구간이에요. 평소보다 절반으로 줄이고 차간 거리를 두 배로 두세요."
        }
        if (!careful || due.stage == AlertPlanner.Stage.SECOND) return main
        val reason = when {
            h.kind == HazardKind.ICE && h.spot.kind == SpotKind.BRIDGE -> "다리 위는 땅보다 먼저 얼어요."
            h.kind == HazardKind.ICE && h.spot.kind == SpotKind.TUNNEL_EXIT -> "터널 출구는 그늘이라 먼저 얼어요."
            h.kind == HazardKind.HEAVY_RAIN -> "빗길은 미끄러워요."
            h.kind == HazardKind.FOG -> "안개 속에서는 앞차가 갑자기 나타나요."
            else -> null
        }
        return if (reason == null) main else "$main $reason"
    }

    /** 경로 정보가 없을 때(차만으로 판단): 기온이 빠르게 떨어지면 */
    fun genericIceWarning(temp: Double, drop: Double, careful: Boolean): String {
        val base = "기온이 10분 새 ${spokenTemp(drop).removePrefix("영하 ")} 떨어져 지금 ${spokenTemp(temp)}예요. 다리 위와 터널 출구를 조심하세요."
        return if (careful) "$base 다리 위는 땅보다 먼저 얼어요." else base
    }

    /** Gleo “오늘 길 위험 다시 듣기” */
    fun routeRiskSummary(ahead: List<Pair<RouteHazard, Double>>, routeKnown: Boolean): String {
        if (!routeKnown) return "지금 경로의 날씨 정보는 아직 없어요. 기온이 4도 이하면 다리 위와 터널 출구를 조심하세요."
        val list = ahead.filter { it.first.level != RiskLevel.WATCH }
        if (list.isEmpty()) return "남은 길에 살얼음이나 안개 구간은 없어요."
        return list.take(3).joinToString(" ") { (h, d) -> alertTitle(h, d, spoken = true) + "." }
    }

    // ---------------- 질문 ----------------

    fun detourPrompt(d: Detour): String = "${d.label}로 가면 ${d.extraMin}분 더 걸려요. 그 길로 갈까요?"

    fun modeOnPrompt(p: ModeProposal, w: RoadWeather): String {
        val lead = when {
            w.precipNow == Precip.SNOW -> "눈이 와요."
            w.precipNow == Precip.SLEET -> "진눈깨비가 와요."
            w.precipNow == Precip.RAIN -> "비가 계속 와요."
            w.precipForecast == Precip.SNOW -> "눈이 예보돼 있어요."
            w.precipForecast == Precip.SLEET -> "진눈깨비가 예보돼 있어요."
            else -> "와이퍼가 계속 움직이고 있어요."
        }
        return "$lead ${p.mode.label} 켤까요?"
    }

    /** "사이드미러와 핸들 열선을 켰어요." */
    fun modeOnDone(p: ModeProposal, careful: Boolean): String {
        val all = p.turnOn.map { it.heater }
        val heat = all.filter { it != Heater.DEFROST }
        val defrost = Heater.DEFROST in all
        val parts = mutableListOf<String>()
        when {
            heat.size == 1 -> parts += heat[0].label
            heat.size == 2 && !defrost -> parts += josa(heat[0].label.removeSuffix(" 열선"), "과/와") + " " + heat[1].label
            heat.size >= 2 -> parts += heat.joinToString("·") { it.label.removeSuffix(" 열선") } + " 열선"
        }
        if (defrost) parts += "김서림 방지"
        val obj = if (parts.size == 1) parts[0] else josa(parts[0], "과/와") + " " + parts[1]
        val main = "${josa(obj, "을/를")} 켰어요."
        if (!careful || p.keep.isEmpty()) return main
        val kept = p.keep.joinToString(", ") { it.label }
        return "$main ${josa(kept, "은/는")} 자동으로 작동 중이라 그대로 둘게요."
    }

    fun modeOffPrompt(mode: WeatherMode, arrived: Boolean): String =
        if (arrived) "도착했어요. ${mode.label} 끌까요?" else "날이 갰어요. ${mode.label} 끌까요?"

    fun lampOnPrompt(lamp: Lamp, w: RoadWeather, fog: Boolean): String = when (lamp) {
        Lamp.FRONT_FOG -> "앞쪽에 짙은 안개예요. 안개등 켤까요?"
        Lamp.LOW_BEAM -> when {
            fog -> "안개가 껴요. 전조등 켤까요?"
            w.precipNow == Precip.SNOW -> "눈이 와요. 전조등 켤까요?"
            w.precipNow == Precip.SLEET -> "진눈깨비가 와요. 전조등 켤까요?"
            else -> "비가 와요. 전조등 켤까요?"
        }
        Lamp.HAZARD -> "앞차들이 갑자기 느려지고 있어요. 뒤차가 알 수 있게 비상등 켤까요?"
    }

    fun lampOffPrompt(lamp: Lamp): String = when (lamp) {
        Lamp.FRONT_FOG -> "안개가 걷혔어요. 안개등 끌까요?"
        Lamp.HAZARD -> "속도가 돌아왔어요. 비상등 끌까요?"
        Lamp.LOW_BEAM -> "전조등 끌까요?"
    }

    fun lampDone(lamp: Lamp, on: Boolean): String = "${lamp.label} ${if (on) "켰어요" else "껐어요"}."

    fun lampConfirmed(lamp: Lamp, on: Boolean): String = "${lamp.label} ${if (on) "켜졌어요" else "꺼졌어요"}."

    const val FOG_LIGHT_TIP = "상향등은 안개에 반사돼 앞이 더 안 보여요. 하향등을 유지하세요."

    const val CAREFUL_OFFER = "안내를 더 일찍, 더 자세히 들으시겠어요?"
    const val CAREFUL_ON = "꼼꼼히 안내를 켰어요. 위험 구간을 1.5킬로미터 전부터, 이유와 함께 알려 드릴게요."
    const val MODE_OFF_DONE = "원래 설정으로 돌려놨어요."
    const val LATER_HINT = "필요하면 ‘헤일로, 눈 모드 켜 줘’라고 말해 주세요."

    fun controlFailed(name: String): String = "${josa(name, "은/는")} 이 차에서 앱이 켤 수 없어요. 직접 켜 주세요."
}
