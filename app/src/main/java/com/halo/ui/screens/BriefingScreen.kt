package com.halo.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.halo.domain.Briefing
import com.halo.domain.CheckItem
import com.halo.domain.CheckLevel
import com.halo.domain.HaloSnapshot
import com.halo.domain.HazardKind
import com.halo.domain.Precip
import com.halo.domain.QuestionKind
import com.halo.domain.RiskEngine
import com.halo.domain.RiskLevel
import com.halo.domain.RouteHazard
import com.halo.ui.components.HIcon
import com.halo.ui.components.HairlineH
import com.halo.ui.components.HairlineV
import com.halo.ui.components.HaloIcons
import com.halo.ui.components.KText
import com.halo.ui.components.InfoRow
import com.halo.ui.components.LevelTag
import com.halo.ui.components.RouteStrip
import com.halo.ui.components.SectionLabel
import com.halo.ui.state.GuideNote
import com.halo.ui.theme.HaloColors
import com.halo.ui.theme.HaloSpace
import com.halo.ui.theme.HaloType

/**
 * 시동 브리핑: 바깥 · 오늘 경로 · 차 상태를 세 칸으로. 칸은 상자 대신 가는 선으로 나눈다.
 * 비·눈 모드나 등화를 묻는 중이면 오른쪽 칸에 그 내용을 보여 준다 (점검 항목은 이미 읽어 드렸다).
 */
@Composable
fun BriefingBody(snap: HaloSnapshot, guide: GuideNote?) {
    val b = snap.briefing
    val q = snap.question
    val detailed = q != null && q.kind !is QuestionKind.Route && q.kind !is QuestionKind.CarefulOffer
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val compact = maxWidth < 1200.dp
        Row(Modifier.fillMaxSize().padding(vertical = HaloSpace.s)) {
            WeatherColumn(snap, b, Modifier.weight(if (compact) 0.85f else 0.9f).fillMaxHeight())
            ColumnGap()
            RouteColumn(snap, b, Modifier.weight(1.25f).fillMaxHeight())
            ColumnGap()
            Box(Modifier.weight(1f).fillMaxHeight()) {
                when {
                    detailed && q != null -> QuestionDetail(q, snap)
                    guide != null -> GuidePanel(guide)
                    else -> ChecksColumn(b?.checks.orEmpty(), Modifier.fillMaxSize())
                }
            }
        }
    }
}

@Composable
private fun ColumnGap() {
    Spacer(Modifier.width(HaloSpace.xl))
    HairlineV(Modifier.padding(vertical = HaloSpace.s))
    Spacer(Modifier.width(HaloSpace.xl))
}

// ---------------- 바깥 ----------------

@Composable
private fun WeatherColumn(snap: HaloSnapshot, b: Briefing?, modifier: Modifier) {
    val temp = b?.tempC ?: snap.vehicle.outsideTempC
    val w = snap.weather
    Column(modifier.padding(top = HaloSpace.m)) {
        SectionLabel("지금 바깥 · 차량 외기 센서")
        Spacer(Modifier.height(HaloSpace.s))
        if (temp != null) {
            Text(
                buildAnnotatedString {
                    append(RiskEngine.celsius(temp).removeSuffix("℃"))
                    withStyle(SpanStyle(fontSize = 52.sp, color = HaloColors.TextDim)) { append("℃") }
                },
                style = HaloType.Display,
                color = if (temp <= 0.0) HaloColors.Ice else HaloColors.Text,
                maxLines = 1,
            )
        } else {
            KText("—", style = HaloType.Display, color = HaloColors.TextMute)
            KText("외기온도를 받지 못했어요", style = HaloType.Body, color = HaloColors.TextDim)
        }
        if (snap.routeKnown) {
            KText(
                "최저 ${RiskEngine.celsius(w.minTempTodayC)} · 최고 ${RiskEngine.celsius(w.maxTempTodayC)}",
                style = HaloType.Body,
                color = HaloColors.TextDim,
            )
        }
        Spacer(Modifier.weight(1f))
        WeatherNotes(snap, b)
    }
}

/** 날씨 근거: 지금 내리는 것 · 예보 · 최근 강수 · 올겨울 첫 영하 */
@Composable
private fun WeatherNotes(snap: HaloSnapshot, b: Briefing?) {
    val w = snap.weather
    Column(verticalArrangement = Arrangement.spacedBy(HaloSpace.s)) {
        if (w.precipNow != Precip.NONE) {
            Note(precipIcon(w.precipNow), "지금 ${RiskEngine.precipLabel(w.precipNow)}", HaloColors.Ice)
        } else if (w.precipForecast != Precip.NONE) {
            Note(precipIcon(w.precipForecast), "${RiskEngine.precipLabel(w.precipForecast)} 예보", HaloColors.Ice)
        }
        val days = w.daysSincePrecip
        if (days != null && days in 1..RiskEngine.RECENT_PRECIP_DAYS && w.minTempTodayC <= 0.0) {
            Note(HaloIcons.Rain, "${RiskEngine.recentPrecipText(days, w)} · 녹았다 언 곳 주의", HaloColors.Caution)
        }
        if (b?.firstFrost == true) Note(HaloIcons.Snow, "올겨울 첫 영하", HaloColors.Ice)
        if (!snap.routeKnown) Note(HaloIcons.Thermometer, "경로 날씨 정보 없이 차량 센서로 판단해요", HaloColors.TextDim)
    }
}

@Composable
private fun Note(icon: ImageVector, text: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        HIcon(icon, color, 26.dp)
        Spacer(Modifier.width(12.dp))
        KText(text, style = HaloType.BodyStrong, color = color, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

private fun precipIcon(p: Precip) = when (p) {
    Precip.SNOW, Precip.SLEET -> HaloIcons.Snow
    Precip.RAIN -> HaloIcons.Rain
    Precip.NONE -> HaloIcons.Cloud
}

// ---------------- 오늘 경로 ----------------

@Composable
private fun RouteColumn(snap: HaloSnapshot, b: Briefing?, modifier: Modifier) {
    val hazards = snap.ahead.map { it.hazard }
    Column(modifier.padding(top = HaloSpace.m)) {
        val remain = b?.baseRemainingMin
        SectionLabel(if (remain != null) "오늘 경로 · 평소 ${remain}분" else "오늘 경로")
        Spacer(Modifier.height(HaloSpace.m))
        if (!snap.routeKnown && hazards.isEmpty()) {
            KText("경로 날씨 정보 없음", style = HaloType.Title, color = HaloColors.Text)
            Spacer(Modifier.height(HaloSpace.s))
            KText(
                "내비의 미끄럼 주의 지점과 차량 외기온도로 판단해요. 기온이 빠르게 떨어지면 바로 알려 드릴게요.",
                style = HaloType.Body,
                color = HaloColors.TextDim,
            )
            return@Column
        }
        RouteHeadline(hazards)
        Spacer(Modifier.height(HaloSpace.m))
        RouteStrip(
            hazards = hazards,
            progressKm = snap.progressKm,
            lengthKm = snap.routeLengthKm,
            endLabel = snap.arrivalClock?.let { "도착 $it" },
        )
        Spacer(Modifier.weight(1f))
        ArrivalLine(snap)
    }
}

@Composable
private fun RouteHeadline(hazards: List<RouteHazard>) {
    val serious = hazards.filter { it.level != RiskLevel.WATCH }
    if (serious.isEmpty()) {
        KText("경로에 위험 구간이 없어요", style = HaloType.Title, color = HaloColors.Text)
        return
    }
    val top = serious.maxBy { it.level.ordinal }
    val kind = when (top.kind) {
        HazardKind.ICE -> "살얼음"
        HazardKind.FOG -> "짙은 안개"
        HazardKind.HEAVY_RAIN -> "폭우"
    }
    val count = serious.count { it.kind == top.kind }
    Row(verticalAlignment = Alignment.CenterVertically) {
        KText("$kind ${top.level.label} ", style = HaloType.Title, color = HaloColors.Text)
        KText("${count}곳", style = HaloType.Title, color = HaloColors.of(top.level))
        val others = serious.size - count
        if (others > 0) KText("  외 ${others}곳", style = HaloType.Subtitle, color = HaloColors.TextDim)
    }
}

@Composable
private fun ArrivalLine(snap: HaloSnapshot) {
    val arrival = snap.arrivalClock ?: return
    Row(verticalAlignment = Alignment.CenterVertically) {
        HIcon(HaloIcons.Clock, HaloColors.TextDim, 26.dp)
        Spacer(Modifier.width(12.dp))
        KText(if (snap.delayMin > 0) "천천히 가면 도착 $arrival " else "도착 $arrival", style = HaloType.BodyStrong, color = HaloColors.Text)
        if (snap.delayMin > 0) KText("(+${snap.delayMin}분)", style = HaloType.BodyStrong, color = HaloColors.Caution)
        if (snap.detourTaken) KText("  · 우회 경로", style = HaloType.Body, color = HaloColors.TextDim)
    }
}

// ---------------- 차 상태 ----------------

@Composable
private fun ChecksColumn(checks: List<CheckItem>, modifier: Modifier) {
    Column(modifier.padding(top = HaloSpace.m)) {
        SectionLabel("오늘의 악천후 차 상태")
        Spacer(Modifier.height(HaloSpace.s))
        if (checks.isEmpty()) {
            KText("차량에서 받은 점검 항목이 없어요", style = HaloType.Body, color = HaloColors.TextDim, modifier = Modifier.padding(top = HaloSpace.s))
            return@Column
        }
        checks.forEachIndexed { i, c ->
            InfoRow(
                icon = checkIcon(c.id),
                label = c.label,
                value = c.status,
                valueColor = HaloColors.of(c.level),
                iconTint = if (c.level == CheckLevel.WARN) HaloColors.Caution else HaloColors.TextDim,
            )
            if (i < checks.lastIndex) HairlineH()
        }
        Spacer(Modifier.weight(1f))
        checks.firstOrNull { it.level == CheckLevel.WARN && it.note != null }?.let {
            KText(it.note!!, style = HaloType.Small, color = HaloColors.TextDim, modifier = Modifier.fillMaxWidth())
        }
    }
}

private fun checkIcon(id: String) = when (id) {
    "snow" -> HaloIcons.Snow
    "battery" -> HaloIcons.Battery
    "tire" -> HaloIcons.Tire
    "wiper" -> HaloIcons.Wiper
    "washer" -> HaloIcons.Washer
    "defog" -> HaloIcons.Defrost
    "light" -> HaloIcons.LowBeam
    else -> HaloIcons.Car
}
