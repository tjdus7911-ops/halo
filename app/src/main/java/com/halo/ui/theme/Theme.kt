package com.halo.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.halo.R
import com.halo.domain.CheckLevel
import com.halo.domain.RiskLevel

/**
 * 색: 어두운 남색 바탕 하나에 면을 한 단계만 올리고, 색은 의미가 있을 때만 쓴다.
 * 얼음색(Ice)은 헤일로가 하는 일과 누를 곳, 노랑·빨강은 위험 단계, 초록은 정상·완료.
 * 그라데이션·그림자·빛 번짐은 쓰지 않는다 (밤 운전 중 눈부심과 시선 분산을 줄인다).
 */
object HaloColors {
    val Bg = Color(0xFF0A1220)
    val Surface = Color(0xFF111B2C)
    val SurfaceHigh = Color(0xFF182438)
    val Line = Color(0xFF233149)
    val Text = Color(0xFFEEF3FA)
    val TextDim = Color(0xFFA9B5C7)
    val TextMute = Color(0xFF6F7C91)
    val Ice = Color(0xFF5BD8FF)
    val OnIce = Color(0xFF04131F)
    val Watch = Color(0xFF8EA8CC)
    val Caution = Color(0xFFF5B43C)
    val Danger = Color(0xFFFF6B66)
    val Ok = Color(0xFF52D69E)

    /** 시연 바: 제품 화면과 구분되는 바탕 */
    val DemoBg = Color(0xFF060B15)

    fun of(level: RiskLevel): Color = when (level) {
        RiskLevel.WATCH -> Watch
        RiskLevel.CAUTION -> Caution
        RiskLevel.DANGER -> Danger
    }

    fun of(level: CheckLevel): Color = when (level) {
        CheckLevel.OK -> Ok
        CheckLevel.WARN -> Caution
        CheckLevel.INFO -> TextDim
    }
}

val Pretendard = FontFamily(
    Font(R.font.pretendard_regular, FontWeight.Normal),
    Font(R.font.pretendard_medium, FontWeight.Medium),
    Font(R.font.pretendard_semibold, FontWeight.SemiBold),
    Font(R.font.pretendard_bold, FontWeight.Bold),
)

/**
 * 글자: 운전석에서 한눈에 읽히도록 크기 차이를 크게 둔다.
 * 기온 숫자 > 경보 제목 > 질문·패널 제목 > 본문 > 라벨 순.
 */
object HaloType {
    /** 한국어는 글자 사이가 아니라 띄어쓰기에서 줄을 바꾼다 (Android 13 이상, 그 아래는 기본 줄바꿈) */
    private val KoreanBreak = LineBreak(LineBreak.Strategy.HighQuality, LineBreak.Strictness.Normal, LineBreak.WordBreak.Phrase)

    private fun style(weight: FontWeight, size: Int, line: Int, spacing: Double = 0.0) = TextStyle(
        fontFamily = Pretendard,
        fontWeight = weight,
        fontSize = size.sp,
        lineHeight = line.sp,
        letterSpacing = spacing.sp,
        lineBreak = KoreanBreak,
    )

    val Display = style(FontWeight.Bold, 112, 112, -4.0)
    val Headline = style(FontWeight.Bold, 46, 56, -1.0)
    val Title = style(FontWeight.Bold, 32, 40, -0.5)
    val Subtitle = style(FontWeight.SemiBold, 26, 34)
    val Body = style(FontWeight.Medium, 22, 30)
    val BodyStrong = Body.copy(fontWeight = FontWeight.SemiBold)
    val Label = style(FontWeight.Medium, 19, 26)
    val Small = style(FontWeight.Medium, 17, 24)
    val Button = style(FontWeight.Bold, 26, 32)
}

/** 간격: 8 단위 */
object HaloSpace {
    val xs = 4.dp
    val s = 8.dp
    val m = 16.dp
    val l = 24.dp
    val xl = 32.dp
    val xxl = 48.dp

    /** 차량 화면 터치 최소 크기 */
    val touch = 76.dp
    val radius = 10.dp
    val radiusSmall = 6.dp
}

@Composable
fun HaloTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = HaloColors.Ice,
            onPrimary = HaloColors.OnIce,
            background = HaloColors.Bg,
            onBackground = HaloColors.Text,
            surface = HaloColors.Surface,
            onSurface = HaloColors.Text,
            surfaceVariant = HaloColors.SurfaceHigh,
            onSurfaceVariant = HaloColors.TextDim,
            outline = HaloColors.Line,
            error = HaloColors.Danger,
        ),
        content = content,
    )
}
