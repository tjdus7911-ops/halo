package com.halo.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.halo.domain.ActiveMode
import com.halo.domain.Detour
import com.halo.domain.HaloSnapshot
import com.halo.domain.HazardKind
import com.halo.domain.Heater
import com.halo.domain.KeptAuto
import com.halo.domain.Lamp
import com.halo.domain.LampSwitch
import com.halo.domain.ModeProposal
import com.halo.domain.Phrases
import com.halo.domain.Question
import com.halo.domain.QuestionKind
import com.halo.domain.Reason
import com.halo.domain.RiskEngine
import com.halo.domain.RiskLevel
import com.halo.domain.RouteHazard
import com.halo.domain.SpotKind
import com.halo.domain.VehicleState
import com.halo.domain.WeatherMode
import com.halo.domain.WiperMode
import com.halo.ui.components.HIcon
import com.halo.ui.components.HairlineH
import com.halo.ui.components.HaloIcons
import com.halo.ui.components.InfoRow
import com.halo.ui.components.SectionLabel
import com.halo.ui.state.GuideNote
import com.halo.ui.theme.HaloColors
import com.halo.ui.theme.HaloSpace
import com.halo.ui.theme.HaloType

/**
 * 오른쪽 칸: 지금 가장 필요한 것 하나만.
 * 묻는 중인 질문의 내용 → 직접 눌러 달라는 안내 → 경보의 판단 근거 → 켜 둔 날씨 모드 → 지금 차 상태.
 */
@Composable
fun SidePanel(snap: HaloSnapshot, guide: GuideNote?) {
    val q = snap.question
    val alert = snap.alert
    val mode = snap.activeMode
    when {
        q != null -> QuestionDetail(q, snap)
        guide != null -> GuidePanel(guide)
        alert != null -> BasisPanel(alert.hazard)
        mode != null -> ModePanel(mode, snap)
        else -> CarStatePanel(snap.vehicle)
    }
}

// ---------------- 질문 내용 ----------------

@Composable
fun QuestionDetail(q: Question, snap: HaloSnapshot) {
    Column(Modifier.fillMaxSize().padding(top = HaloSpace.m)) {
        when (val k = q.kind) {
            is QuestionKind.ModeOn -> ModeOnDetail(k.proposal)
            is QuestionKind.ModeOff -> ModeOffDetail(k.mode, snap.activeMode)
            is QuestionKind.LampOn -> LampDetail(k.lamp, on = true, snap)
            is QuestionKind.LampOff -> LampDetail(k.lamp, on = false, snap)
            is QuestionKind.Route -> RouteDetail(k.detour, snap)
            QuestionKind.CarefulOffer -> CarefulDetail()
        }
    }
}

@Composable
private fun DetailHeader(icon: ImageVector, title: String, subtitle: String?, tint: Color = HaloColors.Ice) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(64.dp)
                .clip(RoundedCornerShape(HaloSpace.radius))
                .background(tint.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center,
        ) {
            HIcon(icon, tint, 34.dp)
        }
        Spacer(Modifier.width(HaloSpace.m))
        Column {
            Text(title, style = HaloType.Title, color = HaloColors.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) Text(subtitle, style = HaloType.Body, color = HaloColors.TextDim, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
    Spacer(Modifier.height(HaloSpace.l))
}

private fun modeIcon(mode: WeatherMode) = if (mode == WeatherMode.SNOW) HaloIcons.Snow else HaloIcons.Rain

private fun heaterIcon(h: Heater) = when (h) {
    Heater.MIRROR -> HaloIcons.MirrorHeat
    Heater.STEERING -> HaloIcons.SteeringWheel
    Heater.DEFROST -> HaloIcons.Defrost
}

private fun keptIcon(k: KeptAuto) = if (k.label.contains("전조등")) HaloIcons.LowBeam else HaloIcons.Defrost

private fun lampIcon(l: Lamp) = when (l) {
    Lamp.LOW_BEAM -> HaloIcons.LowBeam
    Lamp.FRONT_FOG -> HaloIcons.FogLight
    Lamp.HAZARD -> HaloIcons.Hazard
}

@Composable
private fun ModeOnDetail(p: ModeProposal) {
    DetailHeader(modeIcon(p.mode), "${p.mode.label} 켤까요?", p.evidence.joinToString(" · ").ifEmpty { null })
    Text(
        buildAnnotatedString {
            append("자동으로 도는 기능은 ")
            withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = HaloColors.Text)) { append("그대로 두고") }
            append(", 자동이 아닌 ")
            withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = HaloColors.Text)) { append("${p.turnOn.size}가지만") }
            append(" 켤게요")
        },
        style = HaloType.Body,
        color = HaloColors.TextDim,
    )
    Spacer(Modifier.height(HaloSpace.s))
    p.turnOn.forEach {
        InfoRow(heaterIcon(it.heater), it.heater.label, "켤게요", HaloColors.Ok, note = it.note, iconTint = HaloColors.Ice)
        HairlineH()
    }
    p.keep.forEach {
        InfoRow(keptIcon(it), it.label, "그대로", HaloColors.TextMute, note = it.note)
        HairlineH()
    }
}

@Composable
private fun ModeOffDetail(mode: WeatherMode, active: ActiveMode?) {
    DetailHeader(modeIcon(mode), "${mode.label} 끌까요?", "헤일로가 켠 것만 원래대로 돌려요")
    active?.turnedOn?.forEach {
        InfoRow(heaterIcon(it), it.label, "끌게요", HaloColors.TextDim, iconTint = HaloColors.Ice)
        HairlineH()
    }
    active?.kept?.forEach {
        InfoRow(keptIcon(it), it.label, "그대로", HaloColors.TextMute, note = it.note)
        HairlineH()
    }
}

@Composable
private fun LampDetail(lamp: Lamp, on: Boolean, snap: HaloSnapshot) {
    val v = snap.vehicle
    val fog = snap.ahead.firstOrNull { it.hazard.kind == HazardKind.FOG }
    val subtitle = when {
        lamp == Lamp.FRONT_FOG && on -> fog?.let { "짙은 안개 구간 약 ${Phrases.distance(it.hazard.spot.lengthKm)}" } ?: "앞쪽에 짙은 안개"
        lamp == Lamp.FRONT_FOG -> "안개가 걷혔어요"
        lamp == Lamp.HAZARD && on -> "앞차들이 갑자기 느려지고 있어요"
        lamp == Lamp.HAZARD -> "속도가 돌아왔어요"
        else -> "비·눈·안개 낮에는 오토 라이트가 켜지지 않을 수 있어요"
    }
    val tint = if (lamp == Lamp.HAZARD && on) HaloColors.Caution else HaloColors.Ice
    DetailHeader(lampIcon(lamp), "${lamp.label} ${if (on) "켤까요?" else "끌까요?"}", subtitle, tint)
    val tips = when {
        lamp == Lamp.FRONT_FOG && on -> listOf(
            HaloIcons.LowBeam to "상향등은 안개에 반사돼요 · 하향등 유지",
            HaloIcons.Fog to "안개가 걷히면 끌지 다시 물어볼게요",
        )
        lamp == Lamp.FRONT_FOG -> listOf(HaloIcons.Visibility to "안개등을 켜 두면 맞은편 차가 눈부셔요")
        lamp == Lamp.HAZARD && on -> listOf(
            HaloIcons.Car to "뒤차가 미리 알 수 있게 잠깐 켜요",
            HaloIcons.Clock to "속도가 돌아오면 끌지 물어볼게요",
        )
        lamp == Lamp.HAZARD -> emptyList()
        else -> listOf(HaloIcons.Visibility to "다른 차가 나를 먼저 보게 해요")
    }
    tips.forEach { (icon, text) ->
        InfoRow(icon, text, null)
        HairlineH()
    }
    val controllable = when (lamp) {
        Lamp.LOW_BEAM -> v.lowBeam.controllable
        Lamp.FRONT_FOG -> v.frontFog.controllable
        Lamp.HAZARD -> false
    }
    Spacer(Modifier.height(HaloSpace.m))
    Text(
        when {
            controllable -> "“응” 하면 바로 ${if (on) "켤게요" else "끌게요"}."
            lamp == Lamp.HAZARD -> "비상등은 앱에서 켤 수 없어 버튼 위치를 알려 드릴게요."
            else -> "이 차는 앱에서 ${lamp.label}을 바꿀 수 없어, 스위치 위치를 알려 드릴게요."
        },
        style = HaloType.Small,
        color = HaloColors.TextDim,
    )
}

@Composable
private fun RouteDetail(d: Detour, snap: HaloSnapshot) {
    DetailHeader(HaloIcons.Pin, "${d.label}로 갈까요?", "${d.extraMin}분 더 걸려요")
    snap.ahead.filter { it.hazard.spot.id in d.avoidsSpotIds }.forEach {
        InfoRow(spotIcon(it.hazard), it.hazard.spot.name, "피해요", HaloColors.Ok, note = "살얼음 ${it.hazard.level.label} · ${Phrases.distance(it.hazard.spot.atKm)} 지점")
        HairlineH()
    }
    Spacer(Modifier.height(HaloSpace.m))
    Text("지금 길로 가도 위험한 곳은 미리 알려 드려요.", style = HaloType.Small, color = HaloColors.TextDim)
}

@Composable
private fun CarefulDetail() {
    DetailHeader(HaloIcons.Check, "꼼꼼히 안내를 켤까요?", "처음 운전하거나 밤·새벽 운전이 낯설 때")
    listOf(
        HaloIcons.Pin to "위험 구간을 1.5km 전부터 알려요",
        HaloIcons.Speaker to "왜 위험한지 이유를 덧붙여요",
        HaloIcons.Mic to "대답을 못 들으면 한 번 더 물어요",
    ).forEach { (icon, text) ->
        InfoRow(icon, text, null)
        HairlineH()
    }
}

// ---------------- 직접 눌러 달라는 안내 ----------------

@Composable
fun GuidePanel(guide: GuideNote) {
    Column(Modifier.fillMaxSize().padding(top = HaloSpace.m)) {
        DetailHeader(
            lampIcon(guide.lamp),
            "${guide.lamp.label}을 ${if (guide.on) "켜" else "꺼"} 주세요",
            if (guide.lamp == Lamp.HAZARD) "비상등은 앱에서 바꿀 수 없어요" else "이 차는 앱에서 바꿀 수 없어요",
            HaloColors.Caution,
        )
        Text(guide.text, style = HaloType.Subtitle, color = HaloColors.Text)
        if (guide.lamp != Lamp.HAZARD) {
            Spacer(Modifier.height(HaloSpace.m))
            Text("${if (guide.on) "켜지면" else "꺼지면"} 알려 드릴게요.", style = HaloType.Body, color = HaloColors.TextDim)
        }
    }
}

// ---------------- 판단 근거 ----------------

@Composable
fun BasisPanel(h: RouteHazard) {
    Column(Modifier.fillMaxSize().padding(top = HaloSpace.m)) {
        SectionLabel(if (h.observed) "판단 근거 · 기상청 관측" else "판단 근거 · 내 차가 직접 판단")
        Spacer(Modifier.height(HaloSpace.s))
        h.reasons.forEach { r ->
            InfoRow(reasonIcon(r), r.label, r.value, if (r.warn) HaloColors.Caution else HaloColors.Text)
            HairlineH()
        }
        Spacer(Modifier.height(HaloSpace.l))
        LevelScale(h.level)
    }
}

/** 관심 · 주의 · 위험 가운데 지금 단계 */
@Composable
fun LevelScale(level: RiskLevel) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(HaloSpace.s)) {
        RiskLevel.entries.forEach { l ->
            val on = l == level
            val color = HaloColors.of(l)
            Box(
                Modifier
                    .weight(1f)
                    .height(56.dp)
                    .clip(RoundedCornerShape(HaloSpace.radiusSmall))
                    .background(if (on) color.copy(alpha = 0.16f) else HaloColors.Surface)
                    .then(if (on) Modifier.border(2.dp, color, RoundedCornerShape(HaloSpace.radiusSmall)) else Modifier),
                contentAlignment = Alignment.Center,
            ) {
                Text(l.label, style = HaloType.BodyStrong, color = if (on) color else HaloColors.TextMute)
            }
        }
    }
}

private fun reasonIcon(r: Reason): ImageVector? = when {
    r.label.contains("기상청") -> HaloIcons.Cloud
    r.label.contains("외기") || r.label.contains("일교차") -> HaloIcons.Thermometer
    r.label.contains("와이퍼") -> HaloIcons.Wiper
    r.label.contains("가시거리") -> HaloIcons.Visibility
    r.label.contains("교량") -> HaloIcons.Bridge
    r.label.contains("터널") -> HaloIcons.Tunnel
    r.label.contains("내비") -> HaloIcons.Pin
    r.label.contains("비") || r.label.contains("눈") || r.label.contains("강수") -> HaloIcons.Rain
    else -> null
}

private fun spotIcon(h: RouteHazard): ImageVector = when {
    h.kind == HazardKind.FOG -> HaloIcons.Fog
    h.kind == HazardKind.HEAVY_RAIN -> HaloIcons.Rain
    h.spot.kind == SpotKind.BRIDGE -> HaloIcons.Bridge
    h.spot.kind == SpotKind.TUNNEL_EXIT -> HaloIcons.Tunnel
    else -> HaloIcons.Warning
}

// ---------------- 켜 둔 날씨 모드 ----------------

@Composable
fun ModePanel(mode: ActiveMode, snap: HaloSnapshot) {
    Column(Modifier.fillMaxSize().padding(top = HaloSpace.m)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            HIcon(modeIcon(mode.mode), HaloColors.Ice, 34.dp)
            Spacer(Modifier.width(12.dp))
            Text("${mode.mode.label} 켜짐", style = HaloType.Title, color = HaloColors.Ice)
        }
        Text("자동 기능은 그대로, 빈 곳만 채웠어요", style = HaloType.Body, color = HaloColors.TextDim)
        Spacer(Modifier.height(HaloSpace.m))
        mode.kept.forEach {
            InfoRow(keptIcon(it), it.label, "자동 작동 중", HaloColors.Ice)
            HairlineH()
        }
        mode.turnedOn.forEach {
            InfoRow(heaterIcon(it), it.label, "켰어요", HaloColors.Ok)
            HairlineH()
        }
        snap.lampsOnByApp.filter { it != Lamp.HAZARD }.forEach { lamp ->
            val lit = if (lamp == Lamp.LOW_BEAM) snap.vehicle.lowBeam.isOn else snap.vehicle.frontFog.isOn
            InfoRow(lampIcon(lamp), lamp.label, if (lit) "켜짐 확인" else "확인 중", if (lit) HaloColors.Ok else HaloColors.TextDim)
            HairlineH()
        }
    }
}

// ---------------- 지금 차 상태 ----------------

@Composable
fun CarStatePanel(v: VehicleState) {
    Column(Modifier.fillMaxSize().padding(top = HaloSpace.m)) {
        SectionLabel("지금 차 상태")
        Spacer(Modifier.height(HaloSpace.s))
        val temp = v.outsideTempC
        InfoRow(
            HaloIcons.Thermometer,
            "외기온도",
            temp?.let { t -> RiskEngine.celsius(t) + if (v.tempDrop10MinC >= 0.5) " (10분 새 −${RiskEngine.celsius(v.tempDrop10MinC)})" else "" } ?: "정보 없음",
            if (v.tempDrop10MinC >= RiskEngine.SHARP_DROP_C) HaloColors.Caution else HaloColors.Text,
        )
        HairlineH()
        InfoRow(
            HaloIcons.Wiper,
            "와이퍼",
            when {
                v.wiperRunning -> "작동 중 · ${v.wiperRunningMin}분째"
                v.wiperMode == WiperMode.AUTO -> "AUTO"
                else -> "꺼짐"
            },
        )
        HairlineH()
        if (v.lowBeam.available) {
            InfoRow(HaloIcons.LowBeam, "전조등", if (v.lowBeam.isOn) "켜짐" else if (v.lowBeam.switch == LampSwitch.AUTO) "오토" else "꺼짐")
            HairlineH()
        }
        v.climateAuto?.let {
            InfoRow(HaloIcons.Defrost, "오토 디포그", if (it) "작동 중" else "꺼짐", if (it) HaloColors.Ok else HaloColors.TextDim)
            HairlineH()
        }
        if (v.mirrorHeat.available && v.mirrorHeat.level != null) {
            InfoRow(HaloIcons.MirrorHeat, "사이드미러 열선", if (v.mirrorHeat.isOn) "켜짐" else "꺼짐")
            HairlineH()
        }
    }
}
