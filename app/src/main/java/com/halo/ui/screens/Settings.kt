package com.halo.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.border
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.halo.demo.DemoScenarios
import com.halo.ui.components.HairlineH
import com.halo.ui.components.HaloIcons
import com.halo.ui.components.KText
import com.halo.ui.components.IconAction
import com.halo.ui.components.SectionLabel
import com.halo.ui.state.HaloUiState
import com.halo.ui.state.Source
import com.halo.ui.theme.HaloColors
import com.halo.ui.theme.HaloSpace
import com.halo.ui.theme.HaloType

/** 오른쪽에서 여는 설정. 바깥을 누르면 닫힌다. */
@Composable
fun SettingsPanel(state: HaloUiState, actions: HaloActions) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.55f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClickLabel = "설정 닫기") {
                actions.openSettings(false)
            },
    ) {
        Column(
            Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
                .width(600.dp)
                .background(HaloColors.Surface)
                // 패널 안을 눌러도 닫히지 않게
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                .verticalScroll(rememberScrollState())
                .padding(horizontal = HaloSpace.xl, vertical = HaloSpace.l),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                KText("설정", style = HaloType.Title, color = HaloColors.Text, modifier = Modifier.weight(1f))
                IconAction(HaloIcons.Close, "설정 닫기", onClick = { actions.openSettings(false) })
            }
            Spacer(Modifier.height(HaloSpace.l))

            SectionLabel("안내 방식")
            val careful = state.snapshot?.settings?.careful == true
            Row(
                Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = HaloSpace.touch)
                    .toggleable(value = careful, role = Role.Switch, onValueChange = { actions.setCareful(it) })
                    .padding(vertical = HaloSpace.m),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    KText("꼼꼼히 안내", style = HaloType.Subtitle, color = HaloColors.Text)
                    Spacer(Modifier.height(4.dp))
                    KText(
                        "위험 구간을 1.5km 전부터 이유와 함께 알려요. 대답을 못 들으면 안전한 때 한 번 더 물어요. 처음 운전하거나 밤·새벽 운전이 낯설 때 좋아요.",
                        style = HaloType.Small,
                        color = HaloColors.TextDim,
                    )
                }
                Spacer(Modifier.width(HaloSpace.l))
                Switch(
                    checked = careful,
                    onCheckedChange = null,
                    enabled = state.snapshot != null,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = HaloColors.OnIce,
                        checkedTrackColor = HaloColors.Ice,
                        uncheckedThumbColor = HaloColors.TextDim,
                        uncheckedTrackColor = HaloColors.SurfaceHigh,
                        uncheckedBorderColor = HaloColors.Line,
                    ),
                )
            }
            HairlineH(Modifier.padding(vertical = HaloSpace.m))

            SectionLabel("보기")
            Spacer(Modifier.height(HaloSpace.s))
            Choice(
                title = "실제 차량",
                note = if (state.isAutomotive) "차량 센서와 내비로 판단해요" else "이 기기는 차량이 아니라 연결할 수 없어요",
                selected = state.source == Source.LIVE,
                onClick = actions.useLive,
            )
            DemoScenarios.all.forEach { scenario ->
                Choice(
                    title = "시연 · ${scenario.title}",
                    note = scenario.persona,
                    selected = state.source == Source.DEMO && state.demo?.scenarioId == scenario.id,
                    onClick = { actions.startDemo(scenario.id) },
                )
            }
            HairlineH(Modifier.padding(vertical = HaloSpace.m))

            SectionLabel("음성")
            Spacer(Modifier.height(HaloSpace.s))
            KText(
                if (state.voice.canListen) "질문 뒤에 “응” 또는 “아니”라고 말하면 돼요. 화면 버튼으로도 답할 수 있어요."
                else "지금은 음성 인식을 쓸 수 없어요. 질문은 읽어 드리고, 답은 화면 버튼으로 받아요.",
                style = HaloType.Body,
                color = HaloColors.TextDim,
            )
            Spacer(Modifier.height(HaloSpace.s))
            KText(
                "대답이 없으면 아무것도 바꾸지 않아요. Gleo에게 ‘헤일로, 눈 모드 켜 줘’처럼 말해도 돼요.",
                style = HaloType.Small,
                color = HaloColors.TextMute,
            )
            if (state.snapshot != null) {
                Spacer(Modifier.height(HaloSpace.m))
                Box(
                    Modifier
                        .clip(RoundedCornerShape(HaloSpace.radiusSmall))
                        .clickable(role = Role.Button) { actions.replayBriefing() }
                        .defaultMinSize(minHeight = 56.dp)
                        .padding(horizontal = HaloSpace.s),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    KText("오늘 길 브리핑 다시 듣기", style = HaloType.BodyStrong, color = HaloColors.Ice)
                }
            }
            HairlineH(Modifier.padding(vertical = HaloSpace.m))

            KText("글꼴: Pretendard (SIL Open Font License 1.1)", style = HaloType.Small, color = HaloColors.TextMute)
            KText("시연 시나리오의 기상·도로 값은 예시예요.", style = HaloType.Small, color = HaloColors.TextMute)
        }
    }
}

@Composable
private fun Choice(title: String, note: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = HaloSpace.touch)
            .clip(RoundedCornerShape(HaloSpace.radiusSmall))
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(vertical = HaloSpace.s, horizontal = HaloSpace.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(HaloSpace.m),
    ) {
        Box(
            Modifier
                .size(26.dp)
                .clip(CircleShape)
                .border(2.dp, if (selected) HaloColors.Ice else HaloColors.TextMute, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) Box(Modifier.size(12.dp).clip(CircleShape).background(HaloColors.Ice))
        }
        Column(Modifier.weight(1f)) {
            KText(title, style = HaloType.Body, color = if (selected) HaloColors.Text else HaloColors.TextDim)
            KText(note, style = HaloType.Small, color = HaloColors.TextMute)
        }
    }
}
