package com.halo.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
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
    val Display = TextStyle(fontFamily = Pretendard, fontWeight = FontWeight.Bold, fontSize = 112.sp, lineHeight = 112.sp, letterSpacing = (-4).sp)
    val Headline = TextStyle(fontFamily = Pretendard, fontWeight = FontWeight.Bold, fontSize = 46.sp, lineHeight = 56.sp, letterSpacing = (-1).sp)
    val Title = TextStyle(fontFamily = Pretendard, fontWeight = FontWeight.Bold, fontSize = 32.sp, lineHeight = 40.sp, letterSpacing = (-0.5).sp)
    val Subtitle = TextStyle(fontFamily = Pretendard, fontWeight = FontWeight.SemiBold, fontSize = 26.sp, lineHeight = 34.sp)
    val Body = TextStyle(fontFamily = Pretendard, fontWeight = FontWeight.Medium, fontSize = 22.sp, lineHeight = 30.sp)
    val BodyStrong = Body.copy(fontWeight = FontWeight.SemiBold)
    val Label = TextStyle(fontFamily = Pretendard, fontWeight = FontWeight.Medium, fontSize = 19.sp, lineHeight = 26.sp)
    val Small = TextStyle(fontFamily = Pretendard, fontWeight = FontWeight.Medium, fontSize = 17.sp, lineHeight = 24.sp)
    val Button = TextStyle(fontFamily = Pretendard, fontWeight = FontWeight.Bold, fontSize = 26.sp, lineHeight = 32.sp)
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
