package com.halo.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.foundation.clickable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.halo.domain.RiskLevel
import com.halo.ui.theme.HaloColors
import com.halo.ui.theme.HaloSpace
import com.halo.ui.theme.HaloType

/** 헤일로 로고: 기울어진 고리 (런처 아이콘과 같은 그림) */
@Composable
fun HaloMark(size: Dp = 40.dp, color: Color = HaloColors.Ice) {
    Canvas(Modifier.size(size)) {
        val stroke = this.size.minDimension * 0.11f
        val w = this.size.width * 0.84f
        val h = this.size.height * 0.38f
        rotate(-14f) {
            drawOval(
                color = color,
                topLeft = Offset((this.size.width - w) / 2f, (this.size.height - h) / 2f),
                size = Size(w, h),
                style = Stroke(width = stroke),
            )
        }
    }
}

@Composable
fun HIcon(icon: ImageVector, tint: Color, size: Dp = 28.dp, modifier: Modifier = Modifier) {
    Icon(imageVector = icon, contentDescription = null, tint = tint, modifier = modifier.size(size))
}

/** 위험 단계 표시: 관심 · 주의 · 위험 */
@Composable
fun LevelTag(level: RiskLevel, large: Boolean = false) {
    val color = HaloColors.of(level)
    Box(
        Modifier
            .clip(RoundedCornerShape(HaloSpace.radiusSmall))
            .background(color.copy(alpha = 0.16f))
            .padding(horizontal = if (large) 14.dp else 10.dp, vertical = if (large) 5.dp else 3.dp),
    ) {
        KText(level.label, style = if (large) HaloType.Subtitle else HaloType.Label, color = color)
    }
}

/** 칸 위의 작은 제목 */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    KText(text, style = HaloType.Label, color = HaloColors.TextDim, modifier = modifier, maxLines = 1, overflow = TextOverflow.Ellipsis)
}

/** 아이콘 · 이름 · 값 한 줄 (값은 상태 색) */
@Composable
fun InfoRow(
    icon: ImageVector?,
    label: String,
    value: String?,
    valueColor: Color = HaloColors.Text,
    note: String? = null,
    iconTint: Color = HaloColors.TextDim,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 60.dp)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            HIcon(icon, iconTint, 28.dp)
            Box(Modifier.width(HaloSpace.m))
        }
        Column(Modifier.weight(1f)) {
            KText(label, style = HaloType.Body, color = HaloColors.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (note != null) KText(note, style = HaloType.Small, color = HaloColors.TextDim, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        if (value != null) {
            Box(Modifier.width(HaloSpace.m))
            KText(value, style = HaloType.BodyStrong, color = valueColor, maxLines = 1)
        }
    }
}

@Composable
fun HairlineH(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(1.dp).background(HaloColors.Line))
}

@Composable
fun HairlineV(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxHeight().width(1.dp).background(HaloColors.Line))
}

/** 누르는 버튼. 주 버튼은 얼음색 면, 보조 버튼은 한 단계 올린 면. 누르면 살짝 어두워진다. */
@Composable
fun HaloButton(
    text: String,
    onClick: () -> Unit,
    primary: Boolean,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    height: Dp = 80.dp,
    textStyle: TextStyle = HaloType.Button,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val base = if (primary) HaloColors.Ice else HaloColors.SurfaceHigh
    val bg = when {
        !enabled -> HaloColors.Surface
        pressed -> if (primary) Color(0xFF3FB6DD) else HaloColors.Line
        else -> base
    }
    val fg = when {
        !enabled -> HaloColors.TextMute
        primary -> HaloColors.OnIce
        else -> HaloColors.Text
    }
    Row(
        modifier
            .height(height)
            .defaultMinSize(minWidth = 160.dp)
            .clip(RoundedCornerShape(HaloSpace.radius))
            .background(bg)
            .then(if (!primary && enabled) Modifier.border(1.dp, HaloColors.Line, RoundedCornerShape(HaloSpace.radius)) else Modifier)
            .clickable(
                interactionSource = interaction,
                indication = ripple(color = fg),
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            )
            .padding(horizontal = HaloSpace.l),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            HIcon(icon, fg, 28.dp)
            Box(Modifier.width(10.dp))
        }
        KText(text, style = textStyle, color = fg, maxLines = 1)
    }
}

/** 아이콘만 있는 버튼 (설정·닫기). 터치 영역은 차량 기준 이상으로 둔다. */
@Composable
fun IconAction(icon: ImageVector, description: String, onClick: () -> Unit, tint: Color = HaloColors.TextDim, size: Dp = 64.dp) {
    Box(
        Modifier
            .size(size)
            .clip(RoundedCornerShape(HaloSpace.radius))
            .clickable(role = Role.Button, onClickLabel = description, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = description, tint = tint, modifier = Modifier.size(30.dp))
    }
}

/** 듣는 중: 막대 세 개가 천천히 오르내린다. 듣지 않을 때는 멈춘 막대. */
@Composable
fun ListeningBars(active: Boolean, color: Color = HaloColors.Ice, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "listening")
    val phases = (0 until 3).map { i ->
        transition.animateFloat(
            initialValue = 0.35f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(durationMillis = 520, delayMillis = i * 140), RepeatMode.Reverse),
            label = "bar$i",
        )
    }
    Canvas(modifier.size(width = 28.dp, height = 24.dp)) {
        val barW = size.width / 5f
        for (i in 0 until 3) {
            val f = if (active) phases[i].value else 0.4f
            val h = size.height * f
            val x = barW * (i * 2) + barW / 2f
            drawLine(
                color = color,
                start = Offset(x, (size.height - h) / 2f),
                end = Offset(x, (size.height + h) / 2f),
                strokeWidth = barW,
                cap = StrokeCap.Round,
            )
        }
    }
}

/** 상태 줄의 한 항목: 아이콘 + 짧은 글 */
@Composable
fun StatusItem(icon: ImageVector, text: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        HIcon(icon, color, 24.dp)
        Box(Modifier.width(8.dp))
        KText(text, style = HaloType.Label, color = color, maxLines = 1)
    }
}

/** 근거·상태를 짧게 보여 주는 낱말 (면 위 작은 글) */
@Composable
fun Chip(text: String, icon: ImageVector? = null, color: Color = HaloColors.TextDim) {
    Row(
        Modifier
            .clip(RoundedCornerShape(HaloSpace.radiusSmall))
            .background(HaloColors.SurfaceHigh)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            HIcon(icon, color, 20.dp)
            Box(Modifier.width(6.dp))
        }
        KText(text, style = HaloType.Label, color = color, maxLines = 1)
    }
}

private const val WORD_JOINER = '\u2060'

/**
 * 한국어 낱말 안에서 줄이 바뀌지 않게 한다: 띄어쓰기가 아닌 한글 글자 사이에 줄바꿈 금지 문자(U+2060)를 넣는다.
 * 화면 표시에만 쓴다 (음성으로 읽는 문장에는 넣지 않는다).
 */
fun keepWords(text: String): String {
    if (text.length < 2) return text
    val sb = StringBuilder(text.length * 2)
    for (i in text.indices) {
        val c = text[i]
        sb.append(c)
        val next = text.getOrNull(i + 1) ?: break
        if (!c.isWhitespace() && !next.isWhitespace() && (c.isHangul() || next.isHangul())) sb.append(WORD_JOINER)
    }
    return sb.toString()
}

private fun Char.isHangul(): Boolean = this in '가'..'힣'

/**
 * 화면 낭독기와 UI 자동화 도구에는 줄바꿈 금지 문자를 뺀 원래 문장을 준다.
 * Text가 스스로 넣는 글자 정보를 지우고 이 문장으로 바꾼다.
 */
fun Modifier.accessibleText(sentence: String): Modifier {
    val plain = sentence.replace(WORD_JOINER.toString(), "")
    return clearAndSetSemantics { text = AnnotatedString(plain) }
}

/** 한국어 문장: 낱말 단위로 줄을 바꾼다 */
@Composable
fun KText(
    text: String,
    style: TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
    textAlign: TextAlign? = null,
) {
    Text(keepWords(text), modifier = modifier.accessibleText(text), color = color, style = style, maxLines = maxLines, overflow = overflow, textAlign = textAlign)
}
