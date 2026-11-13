package com.halo.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.halo.domain.AlertPlanner
import com.halo.domain.HaloSnapshot
import com.halo.domain.HazardKind
import com.halo.domain.Phrases
import com.halo.domain.RiskEngine
import com.halo.domain.RiskLevel
import com.halo.domain.RouteHazard
import com.halo.domain.SpotKind
import com.halo.ui.components.HIcon
import com.halo.ui.components.HairlineV
import com.halo.ui.components.HaloIcons
import com.halo.ui.components.KText
import com.halo.ui.components.LevelTag
import com.halo.ui.components.RoadAhead
import com.halo.ui.components.SectionLabel
import com.halo.ui.state.GuideNote
import com.halo.ui.theme.HaloColors
import com.halo.ui.theme.HaloSpace
import com.halo.ui.theme.HaloType
import kotlin.math.max

/**
 * 주행 중: 왼쪽에 지금 알릴 것(경보 제목은 가장 크게), 가운데 앞으로의 길, 오른쪽에 질문·근거.
 * 운전 중에 읽을 글은 짧게: 제목 한 줄과 할 일 한 줄.
 */
@Composable
fun DrivingBody(snap: HaloSnapshot, guide: GuideNote?) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val compact = maxWidth < 1300.dp
        Row(Modifier.fillMaxSize().padding(vertical = HaloSpace.s)) {
            Column(Modifier.weight(1f).fillMaxHeight().padding(top = HaloSpace.m)) {
                AlertBlock(snap)
                Spacer(Modifier.weight(1f))
                TripFacts(snap)
            }
            Spacer(Modifier.width(HaloSpace.l))
            RoadAhead(snap.ahead, snap.progressKm, Modifier.width(if (compact) 300.dp else 380.dp).fillMaxHeight())
            Spacer(Modifier.width(HaloSpace.l))
            HairlineV(Modifier.padding(vertical = HaloSpace.s))
            Spacer(Modifier.width(HaloSpace.xl))
            Box(Modifier.width(if (compact) 440.dp else 520.dp).fillMaxHeight()) {
                SidePanel(snap, guide)
            }
        }
    }
}

@Composable
private fun AlertBlock(snap: HaloSnapshot) {
    val alert = snap.alert
    if (alert != null) {
        val h = alert.hazard
        val d = max(0.0, h.spot.atKm - snap.progressKm)
        Row(verticalAlignment = Alignment.CenterVertically) {
            LevelTag(h.level, large = true)
            Spacer(Modifier.width(HaloSpace.m))
            KText(sourceLine(h), style = HaloType.Body, color = HaloColors.TextDim, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.height(HaloSpace.m))
        KText(Phrases.alertTitle(h, d), style = HaloType.Headline, color = HaloColors.Text)
        Spacer(Modifier.height(HaloSpace.s))
        KText(Phrases.alertAdvice(h), style = HaloType.Body, color = HaloColors.TextDim)
        return
    }
    val next = snap.ahead.firstOrNull { it.hazard.level != RiskLevel.WATCH } ?: snap.ahead.firstOrNull()
    when {
        next != null -> {
            SectionLabel("다음 위험")
            Spacer(Modifier.height(HaloSpace.s))
            Row(verticalAlignment = Alignment.CenterVertically) {
                LevelTag(next.hazard.level)
                Spacer(Modifier.width(12.dp))
                KText(sourceLine(next.hazard), style = HaloType.Label, color = HaloColors.TextDim, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.height(HaloSpace.s))
            KText(Phrases.alertTitle(next.hazard, next.distanceKm), style = HaloType.Title, color = HaloColors.Text)
            Spacer(Modifier.height(HaloSpace.xs))
            val km = AlertPlanner.firstAlertKm(snap.settings.careful)
            KText(
                if (next.hazard.level == RiskLevel.WATCH) "관심 단계는 화면으로만 알려 드려요" else "${Phrases.distance(km)} 앞에서 말로 알려 드릴게요",
                style = HaloType.Body,
                color = HaloColors.TextDim,
            )
        }
        snap.routeKnown -> {
            KText("남은 길에 위험 구간이 없어요", style = HaloType.Title, color = HaloColors.Text)
            Spacer(Modifier.height(HaloSpace.s))
            KText("날씨가 바뀌면 바로 알려 드릴게요.", style = HaloType.Body, color = HaloColors.TextDim)
        }
        else -> {
            KText("차량 센서로 보고 있어요", style = HaloType.Title, color = HaloColors.Text)
            Spacer(Modifier.height(HaloSpace.s))
            KText(
                "경로 날씨 정보 없이 외기온도와 와이퍼로 판단해요. 기온이 빠르게 떨어지면 다리 위·터널 출구를 조심하라고 알려 드릴게요.",
                style = HaloType.Body,
                color = HaloColors.TextDim,
            )
        }
    }
}

/** 경보 출처: 기상청 관측인지, 내 차가 판단했는지 */
fun sourceLine(h: RouteHazard): String = when {
    h.kind == HazardKind.FOG -> if (h.spot.visibilityM != null) "기상청 도로 가시거리 정보" else h.spot.name
    h.kind == HazardKind.HEAVY_RAIN -> listOfNotNull(h.spot.rainMmPerHour?.let { "시간당 ${it}mm" }, "구간 ${Phrases.distance(h.spot.lengthKm)}").joinToString(" · ")
    h.observed -> "${h.spot.name} · 기상청 관측 구간"
    h.spot.kind == SpotKind.SLIPPERY -> "내비 주의 지점 · 내 차가 판단"
    else -> "${h.spot.name} · 관측소 없는 구간"
}

/** 도착 시각과 지금 판단에 쓰는 값 몇 가지 */
@Composable
private fun TripFacts(snap: HaloSnapshot) {
    val v = snap.vehicle
    Row(horizontalArrangement = Arrangement.spacedBy(HaloSpace.xl), verticalAlignment = Alignment.CenterVertically) {
        snap.arrivalClock?.let { arrival ->
            Fact(HaloIcons.Clock, if (snap.delayMin > 0) "도착 $arrival (+${snap.delayMin}분)" else "도착 $arrival")
        }
        v.outsideTempC?.let { Fact(HaloIcons.Thermometer, "외기 ${RiskEngine.celsius(it)}") }
        if (v.wiperRunning) Fact(HaloIcons.Wiper, "와이퍼 ${v.wiperRunningMin}분째")
    }
}

@Composable
private fun Fact(icon: ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        HIcon(icon, HaloColors.TextDim, 24.dp)
        Spacer(Modifier.width(10.dp))
        KText(text, style = HaloType.BodyStrong, color = HaloColors.TextDim, maxLines = 1)
    }
}

/** 도착: 켜 둔 날씨 모드가 있으면 끌지 묻고, 없으면 짧게 마친다 */
@Composable
fun ArrivedBody(snap: HaloSnapshot, guide: GuideNote?) {
    Row(Modifier.fillMaxSize().padding(vertical = HaloSpace.s)) {
        Column(Modifier.weight(1f).fillMaxHeight().padding(top = HaloSpace.m)) {
            SectionLabel("도착 ${snap.clock}")
            Spacer(Modifier.height(HaloSpace.s))
            KText("도착했어요", style = HaloType.Headline, color = HaloColors.Text)
            Spacer(Modifier.height(HaloSpace.s))
            KText(
                if (snap.activeMode != null) "켜 둔 ${snap.activeMode.mode.label}를 정리할게요." else "다음 운행 때 다시 브리핑할게요.",
                style = HaloType.Body,
                color = HaloColors.TextDim,
            )
        }
        Spacer(Modifier.width(HaloSpace.l))
        HairlineV(Modifier.padding(vertical = HaloSpace.s))
        Spacer(Modifier.width(HaloSpace.xl))
        Box(Modifier.width(520.dp).fillMaxHeight()) {
            SidePanel(snap, guide)
        }
    }
}
