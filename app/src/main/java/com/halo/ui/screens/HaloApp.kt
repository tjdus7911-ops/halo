package com.halo.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.halo.data.FeedStatus
import com.halo.domain.HaloSnapshot
import com.halo.domain.Lamp
import com.halo.domain.Phase
import com.halo.domain.Question
import com.halo.domain.QuestionKind
import com.halo.domain.WeatherMode
import com.halo.ui.components.HIcon
import com.halo.ui.components.HaloButton
import com.halo.ui.components.HaloIcons
import com.halo.ui.components.HaloMark
import com.halo.ui.components.IconAction
import com.halo.ui.components.ListeningBars
import com.halo.ui.components.StatusItem
import com.halo.ui.state.DemoUi
import com.halo.ui.state.HaloUiState
import com.halo.ui.state.Source
import com.halo.ui.state.VoicePhase
import com.halo.ui.state.VoiceUi
import com.halo.ui.theme.HaloColors
import com.halo.ui.theme.HaloSpace
import com.halo.ui.theme.HaloType

/** 화면에서 부르는 동작. 미리보기·스크린샷에서는 기본값(아무것도 안 함)을 쓴다. */
class HaloActions(
    val answer: (questionId: String, yes: Boolean) -> Unit = { _, _ -> },
    val dismiss: (questionId: String) -> Unit = {},
    val setCareful: (Boolean) -> Unit = {},
    val openSettings: (Boolean) -> Unit = {},
    val replayBriefing: () -> Unit = {},
    val useLive: () -> Unit = {},
    val startDemo: (scenarioId: String) -> Unit = {},
    val demoNext: () -> Unit = {},
    val demoRestart: () -> Unit = {},
)

/** 헤일로 화면 전체: 위 상태 줄 · 가운데 본문 · 아래 음성 줄 (시연이면 맨 아래 시연 바) */
@Composable
fun HaloApp(state: HaloUiState, actions: HaloActions) {
    Box(Modifier.fillMaxSize().background(HaloColors.Bg)) {
        Column(Modifier.fillMaxSize()) {
            Header(state, actions)
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = HaloSpace.xl),
            ) {
                Body(state, actions)
            }
            VoiceBar(state, actions)
            state.demo?.let { DemoBar(it, actions) }
        }
        AnimatedVisibility(visible = state.settingsOpen, enter = fadeIn(tween(160)), exit = fadeOut(tween(160))) {
            SettingsPanel(state, actions)
        }
    }
}

@Composable
private fun Body(state: HaloUiState, actions: HaloActions) {
    val snap = state.snapshot
    if (snap == null) {
        StatusMessage(state, actions)
        return
    }
    Crossfade(targetState = snap.phase, animationSpec = tween(220), label = "phase") { phase ->
        when (phase) {
            Phase.BRIEFING -> BriefingBody(snap, state.guide)
            Phase.DRIVING -> DrivingBody(snap, state.guide)
            Phase.ARRIVED -> ArrivedBody(snap, state.guide)
        }
    }
}

// ---------------- 상태 줄 ----------------

fun contextLabel(state: HaloUiState): String {
    val snap = state.snapshot ?: return when (state.feed) {
        FeedStatus.CONNECTING -> "차량에 연결하는 중"
        else -> "차량 연결 안 됨"
    }
    val q = snap.question?.kind
    val mode = snap.activeMode
    return when {
        snap.phase == Phase.ARRIVED -> "도착"
        snap.phase == Phase.BRIEFING -> "시동 브리핑"
        q is QuestionKind.ModeOn -> "날씨 변화 감지"
        q is QuestionKind.LampOn || q is QuestionKind.LampOff -> "날씨 맞춤 등화"
        mode != null -> mode.mode.label
        else -> "살얼음·안개 레이더"
    }
}

@Composable
private fun Header(state: HaloUiState, actions: HaloActions) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(88.dp)
            .padding(start = HaloSpace.xl, end = HaloSpace.m),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HaloMark(40.dp)
        Spacer(Modifier.width(12.dp))
        Text("헤일로", style = HaloType.Title, color = HaloColors.Text)
        Spacer(Modifier.width(HaloSpace.m))
        Box(Modifier.width(1.dp).height(24.dp).background(HaloColors.Line))
        Spacer(Modifier.width(HaloSpace.m))
        Text(contextLabel(state), style = HaloType.Body, color = HaloColors.TextDim, maxLines = 1)
        if (state.source == Source.DEMO) {
            state.snapshot?.clock?.let { clock ->
                Spacer(Modifier.width(HaloSpace.m))
                Text(clock, style = HaloType.Body, color = HaloColors.TextMute)
            }
        }
        Spacer(Modifier.weight(1f))
        Row(horizontalArrangement = Arrangement.spacedBy(HaloSpace.l), verticalAlignment = Alignment.CenterVertically) {
            if (state.snapshot?.settings?.careful == true) StatusItem(HaloIcons.Check, "꼼꼼히 안내", HaloColors.Ice)
            VoiceStatus(state.voice)
            SensorStatus(state)
        }
        Spacer(Modifier.width(HaloSpace.s))
        IconAction(HaloIcons.Settings, "설정", onClick = { actions.openSettings(true) })
    }
}

@Composable
private fun VoiceStatus(voice: VoiceUi) {
    when {
        voice.phase == VoicePhase.LISTENING -> StatusItem(HaloIcons.Mic, "듣고 있어요", HaloColors.Ice)
        voice.canListen -> StatusItem(HaloIcons.Mic, "말로 답하면 돼요", HaloColors.TextDim)
        else -> StatusItem(HaloIcons.Mic, "버튼으로 답해요", HaloColors.TextMute)
    }
}

@Composable
private fun SensorStatus(state: HaloUiState) {
    when {
        state.source == Source.DEMO -> StatusItem(HaloIcons.Shield, "시연 시나리오", HaloColors.TextDim)
        state.feed == FeedStatus.CONNECTED -> StatusItem(HaloIcons.Shield, "차량 센서 연결됨", HaloColors.TextDim)
        state.feed == FeedStatus.CONNECTING -> StatusItem(HaloIcons.Shield, "연결 중", HaloColors.TextMute)
        else -> StatusItem(HaloIcons.Shield, "차량 연결 안 됨", HaloColors.Caution)
    }
}

// ---------------- 음성 줄 ----------------

/**
 * 헤일로가 지금 하는 말과, 질문이 있으면 “응 / 아니” 버튼. 버튼은 동승자·음성 인식을 못 쓸 때를 위한 보조.
 * 질문은 언제나 이 자리에서 답한다 (본문에 무엇이 떠 있든 같은 곳).
 */
@Composable
private fun VoiceBar(state: HaloUiState, actions: HaloActions) {
    val snap = state.snapshot
    val question = snap?.question
    val voice = state.voice
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = HaloSpace.xl, end = HaloSpace.xl, top = HaloSpace.m, bottom = HaloSpace.l)
            .heightIn(min = 112.dp)
            .clip(RoundedCornerShape(HaloSpace.radius))
            .background(HaloColors.Surface)
            .padding(horizontal = HaloSpace.l, vertical = HaloSpace.m),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
            when (voice.phase) {
                VoicePhase.LISTENING -> ListeningBars(active = true)
                VoicePhase.SPEAKING -> HIcon(HaloIcons.Speaker, HaloColors.Ice, 32.dp)
                else -> HaloMark(36.dp, if (question != null) HaloColors.Ice else HaloColors.TextMute)
            }
        }
        Spacer(Modifier.width(HaloSpace.m))
        Column(Modifier.weight(1f)) {
            voiceHint(voice, question, snap)?.let { hint ->
                Text(hint, style = HaloType.Small, color = if (voice.phase == VoicePhase.LISTENING) HaloColors.Ice else HaloColors.TextDim, maxLines = 1)
            }
            val caption = voice.caption
            val line = when {
                question != null -> "“${question.prompt}”"
                caption != null -> caption
                else -> idleLine(snap)
            }
            Text(
                line,
                style = HaloType.Subtitle,
                color = if (question != null || caption != null) HaloColors.Text else HaloColors.TextDim,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (question != null) {
            Spacer(Modifier.width(HaloSpace.l))
            HaloButton(question.yesLabel, onClick = { actions.answer(question.id, true) }, primary = true, icon = yesIcon(question), modifier = Modifier.width(232.dp))
            Spacer(Modifier.width(HaloSpace.m))
            HaloButton(question.noLabel, onClick = { actions.answer(question.id, false) }, primary = false, modifier = Modifier.width(196.dp))
            if (snap?.asking == false) {
                Spacer(Modifier.width(HaloSpace.s))
                IconAction(HaloIcons.Close, "질문 닫기", onClick = { actions.dismiss(question.id) })
            }
        }
    }
}

private fun voiceHint(voice: VoiceUi, question: Question?, snap: HaloSnapshot?): String? = when {
    question == null -> null
    voice.phase == VoicePhase.LISTENING -> "듣고 있어요 · “응” 한마디면 돼요"
    voice.phase == VoicePhase.WAITING_TAP -> "음성 인식을 쓸 수 없어요 · 버튼으로 답해 주세요"
    snap?.asking == false -> "대답을 못 들었어요 · 아무것도 바꾸지 않았어요"
    else -> "헤일로가 묻고 있어요"
}

private fun idleLine(snap: HaloSnapshot?): String = when (snap?.phase) {
    Phase.DRIVING -> "날씨와 길을 보고 있어요. 필요할 때 먼저 물어볼게요."
    Phase.ARRIVED -> "다음 운행 때 다시 브리핑할게요."
    else -> "‘헤일로’라고 부르면 오늘 길 위험을 다시 들을 수 있어요."
}

private fun yesIcon(q: Question): ImageVector? = when (val k = q.kind) {
    is QuestionKind.LampOn -> when (k.lamp) {
        Lamp.FRONT_FOG -> HaloIcons.FogLight
        Lamp.LOW_BEAM -> HaloIcons.LowBeam
        Lamp.HAZARD -> HaloIcons.Hazard
    }
    is QuestionKind.Route -> HaloIcons.Pin
    is QuestionKind.ModeOn -> if (k.proposal.mode == WeatherMode.SNOW) HaloIcons.Snow else HaloIcons.Rain
    else -> null
}

// ---------------- 연결 상태 ----------------

@Composable
private fun StatusMessage(state: HaloUiState, actions: HaloActions) {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
        if (state.feed == FeedStatus.CONNECTING) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(color = HaloColors.Ice, strokeWidth = 3.dp, modifier = Modifier.size(32.dp))
                Spacer(Modifier.width(HaloSpace.m))
                Text("차량에 연결하는 중이에요", style = HaloType.Title, color = HaloColors.Text)
            }
            Spacer(Modifier.height(HaloSpace.m))
            Text("외기온도·와이퍼·등화 상태를 받는 대로 오늘 길을 알려 드릴게요.", style = HaloType.Body, color = HaloColors.TextDim)
        } else {
            Text("차량 데이터를 받을 수 없어요", style = HaloType.Headline, color = HaloColors.Text)
            Spacer(Modifier.height(HaloSpace.m))
            Text(
                if (state.isAutomotive) "차량 서비스 연결과 앱 권한(속도·외기온도)을 확인해 주세요. 연결되면 바로 시작해요."
                else "이 기기는 차량이 아니에요. 기획서의 장면을 시연 시나리오로 볼 수 있어요.",
                style = HaloType.Body,
                color = HaloColors.TextDim,
            )
            Spacer(Modifier.height(HaloSpace.xl))
            HaloButton("시연 시나리오 보기", onClick = { actions.startDemo("winter") }, primary = true, modifier = Modifier.width(320.dp))
        }
    }
}

// ---------------- 시연 바 ----------------

/** 발표·시연용: 장면을 넘긴다. 제품 화면과 구분되게 더 어두운 바탕에 작은 글씨로 둔다. */
@Composable
private fun DemoBar(demo: DemoUi, actions: HaloActions) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(76.dp)
            .background(HaloColors.DemoBg)
            .padding(horizontal = HaloSpace.xl),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("시연", style = HaloType.Label.copy(fontWeight = FontWeight.Bold), color = HaloColors.Ice)
        Spacer(Modifier.width(HaloSpace.m))
        Text(
            buildAnnotatedString {
                withStyle(SpanStyle(color = HaloColors.Text)) { append(demo.title) }
                withStyle(SpanStyle(color = HaloColors.TextMute)) { append("   ${demo.index + 1}/${demo.count}   ") }
                withStyle(SpanStyle(color = HaloColors.TextDim)) { append(demo.frameLabel) }
            },
            style = HaloType.Label,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(HaloSpace.m))
        Box(
            Modifier
                .height(56.dp)
                .clip(RoundedCornerShape(HaloSpace.radiusSmall))
                .clickable(onClickLabel = "시연 처음부터", onClick = actions.demoRestart)
                .padding(horizontal = HaloSpace.m),
            contentAlignment = Alignment.Center,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                HIcon(HaloIcons.Restart, HaloColors.TextDim, 22.dp)
                Spacer(Modifier.width(6.dp))
                Text("처음부터", style = HaloType.Label, color = HaloColors.TextDim)
            }
        }
        Spacer(Modifier.width(HaloSpace.s))
        HaloButton(
            if (demo.isLast) "마지막 장면" else "다음 장면",
            onClick = actions.demoNext,
            primary = false,
            enabled = !demo.isLast,
            icon = if (demo.isLast) null else HaloIcons.Next,
            height = 56.dp,
            textStyle = HaloType.BodyStrong,
            modifier = Modifier.width(200.dp),
        )
    }
}
