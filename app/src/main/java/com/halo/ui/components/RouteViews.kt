package com.halo.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.halo.domain.HazardAhead
import com.halo.domain.HazardKind
import com.halo.domain.Phrases
import com.halo.domain.RouteHazard
import com.halo.ui.theme.HaloColors
import com.halo.ui.theme.HaloType
import kotlin.math.max
import kotlin.math.min

/** 위험 구간의 짧은 이름: "강변 교량 · 살얼음" (이름에 이미 있으면 덧붙이지 않는다) */
fun hazardName(h: RouteHazard): String {
    val word = when (h.kind) {
        HazardKind.ICE -> "살얼음"
        HazardKind.FOG -> "안개"
        HazardKind.HEAVY_RAIN -> "폭우"
    }
    return if (h.spot.name.contains(word)) h.spot.name else "${h.spot.name} · $word"
}

/** 출발점에서의 위치: 7.2km, 16km */
fun spotPosition(km: Double): String {
    val rounded = Math.round(km * 10) / 10.0
    return if (rounded % 1.0 == 0.0) "${rounded.toInt()}km" else "${rounded}km"
}

/**
 * 오늘 경로를 한 줄로: 출발에서 도착까지, 위험 구간을 단계 색으로 표시한다.
 * 지도 대신 “어디쯤, 얼마나 긴 구간인지”만 보여 준다.
 */
@Composable
fun RouteStrip(
    hazards: List<RouteHazard>,
    progressKm: Double,
    lengthKm: Double?,
    endLabel: String?,
    modifier: Modifier = Modifier,
) {
    val total = max(lengthKm ?: ((hazards.maxOfOrNull { it.spot.endKm } ?: 8.0) + 3.0), 1.0)
    val sorted = hazards.sortedBy { it.spot.atKm }
    BoxWithConstraints(modifier.fillMaxWidth().height(176.dp)) {
        val pad = 14.dp
        val trackY = 46.dp
        val inner = maxWidth - pad * 2
        fun x(km: Double): Dp = pad + inner * (km / total).coerceIn(0.0, 1.0).toFloat()

        Canvas(Modifier.matchParentSize()) {
            val y = trackY.toPx()
            val x0 = pad.toPx()
            val x1 = size.width - pad.toPx()
            drawLine(HaloColors.Line, Offset(x0, y), Offset(x1, y), strokeWidth = 8.dp.toPx(), cap = StrokeCap.Round)
            if (progressKm > 0.0) {
                drawLine(HaloColors.Ice, Offset(x0, y), Offset(x(progressKm).toPx(), y), strokeWidth = 8.dp.toPx(), cap = StrokeCap.Round)
            }
            sorted.forEach { h ->
                val a = x(h.spot.atKm).toPx()
                val b = max(x(h.spot.endKm).toPx(), a + 12.dp.toPx())
                drawLine(HaloColors.of(h.level), Offset(a, y), Offset(min(b, x1), y), strokeWidth = 18.dp.toPx(), cap = StrokeCap.Round)
            }
            drawCircle(HaloColors.Ice, radius = 10.dp.toPx(), center = Offset(x0, y))
            drawCircle(HaloColors.Bg, radius = 4.dp.toPx(), center = Offset(x0, y))
            drawCircle(HaloColors.Text, radius = 9.dp.toPx(), center = Offset(x1, y), style = Stroke(3.dp.toPx()))
            if (progressKm > 0.0) drawCircle(HaloColors.Ice, radius = 9.dp.toPx(), center = Offset(x(progressKm).toPx(), y))
        }

        Text("출발", style = HaloType.Small, color = HaloColors.TextMute, modifier = Modifier.offset(x = pad - 6.dp, y = 0.dp))
        Text(
            endLabel ?: "도착",
            style = HaloType.Small,
            color = HaloColors.TextDim,
            textAlign = TextAlign.End,
            modifier = Modifier.width(220.dp).offset(x = maxWidth - 220.dp, y = 0.dp),
        )

        // 구간 이름은 길 아래에. 겹치면 한 줄 내린다.
        val labelW = 190.dp
        var lastEnd = listOf((-1000).dp, (-1000).dp)
        sorted.forEach { h ->
            val start = (x(h.spot.atKm) - 8.dp).coerceAtMost(maxWidth - labelW).coerceAtLeast(0.dp)
            val row = if (start >= lastEnd[0]) 0 else if (start >= lastEnd[1]) 1 else 0
            lastEnd = lastEnd.toMutableList().also { it[row] = start + labelW + 8.dp }
            Column(Modifier.width(labelW).offset(x = start, y = trackY + 24.dp + 56.dp * row)) {
                Text(hazardName(h), style = HaloType.Label, color = HaloColors.of(h.level), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${spotPosition(h.spot.atKm)} 지점", style = HaloType.Small, color = HaloColors.TextDim, maxLines = 1)
            }
        }
    }
}

/**
 * 앞으로의 길 (아래가 내 차, 위로 갈수록 멀리). 가까운 위험 구간이 어디서 시작해 얼마나 이어지는지 본다.
 * 보여 주는 거리는 가장 가까운 위험에 맞춰 2~8km 로 늘었다 줄었다 한다.
 */
@Composable
fun RoadAhead(ahead: List<HazardAhead>, progressKm: Double, modifier: Modifier = Modifier) {
    val nearest = ahead.firstOrNull()
    val range = if (nearest == null) 3.0 else (nearest.distanceKm + min(nearest.hazard.spot.lengthKm, 2.0) + 0.6).coerceIn(2.0, 8.0)
    val step = if (range <= 3.0) 0.5 else 1.0
    BoxWithConstraints(modifier) {
        val roadX = 120.dp
        val bandW = 72.dp
        val topY = 36.dp
        val carY = maxHeight - 64.dp
        fun y(km: Double): Dp = carY - (carY - topY) * (km / range).coerceIn(0.0, 1.0).toFloat()

        Canvas(Modifier.matchParentSize()) {
            val cx = roadX.toPx()
            val half = bandW.toPx() / 2f
            val top = topY.toPx() - 16.dp.toPx()
            val bottom = carY.toPx() + 40.dp.toPx()
            drawRoundRect(
                HaloColors.SurfaceHigh,
                topLeft = Offset(cx - half, top),
                size = Size(half * 2, bottom - top),
                cornerRadius = CornerRadius(10.dp.toPx()),
            )
            drawLine(
                HaloColors.TextMute.copy(alpha = 0.5f),
                Offset(cx, top + 8.dp.toPx()),
                Offset(cx, carY.toPx() - 30.dp.toPx()),
                strokeWidth = 2.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(14.dp.toPx(), 12.dp.toPx())),
            )
            // 거리 눈금
            var km = step
            while (km < range - 0.05) {
                val ty = y(km).toPx()
                drawLine(HaloColors.Line, Offset(cx - half - 18.dp.toPx(), ty), Offset(cx - half - 6.dp.toPx(), ty), strokeWidth = 2.dp.toPx())
                km += step
            }
            // 위험 구간
            ahead.filter { it.distanceKm < range }.forEach { a ->
                val from = y(a.distanceKm).toPx()
                val len = if (a.distanceKm <= 0.0) a.hazard.spot.endKm - progressKm else a.hazard.spot.lengthKm
                val to = y(min(a.distanceKm + len, range)).toPx()
                drawRoundRect(
                    HaloColors.of(a.hazard.level),
                    topLeft = Offset(cx - half + 8.dp.toPx(), to),
                    size = Size(half * 2 - 16.dp.toPx(), max(from - to, 10.dp.toPx())),
                    cornerRadius = CornerRadius(6.dp.toPx()),
                )
            }
            // 내 차
            val cy = carY.toPx()
            drawCircle(HaloColors.Bg, radius = 26.dp.toPx(), center = Offset(cx, cy))
            drawCircle(HaloColors.Ice, radius = 26.dp.toPx(), center = Offset(cx, cy), style = Stroke(3.dp.toPx()))
            val arrow = Path().apply {
                moveTo(cx, cy - 13.dp.toPx())
                lineTo(cx + 10.dp.toPx(), cy + 11.dp.toPx())
                lineTo(cx, cy + 5.dp.toPx())
                lineTo(cx - 10.dp.toPx(), cy + 11.dp.toPx())
                close()
            }
            drawPath(arrow, HaloColors.Ice)
        }

        // 눈금 글씨 (길 왼쪽)
        var km = step
        while (km < range - 0.05) {
            Text(
                Phrases.distance(km),
                style = HaloType.Small,
                color = HaloColors.TextMute,
                textAlign = TextAlign.End,
                modifier = Modifier.width(roadX - bandW / 2 - 26.dp).offset(y = y(km) - 12.dp),
            )
            km += step
        }

        // 위험 구간 이름 (길 오른쪽)
        val labelX = roadX + bandW / 2 + 16.dp
        ahead.filter { it.distanceKm < range }.take(3).forEach { a ->
            Column(Modifier.width(maxWidth - labelX).offset(x = labelX, y = y(a.distanceKm) - 30.dp)) {
                Text(
                    if (a.distanceKm <= 0.05) "지금 구간 안" else Phrases.distance(a.distanceKm),
                    style = HaloType.Subtitle,
                    color = HaloColors.Text,
                    maxLines = 1,
                )
                Text(hazardName(a.hazard), style = HaloType.Label, color = HaloColors.of(a.hazard.level), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        val beyond = ahead.firstOrNull { it.distanceKm >= range }
        if (nearest == null || beyond != null && ahead.none { it.distanceKm < range }) {
            Box(Modifier.width(maxWidth - labelX).offset(x = labelX, y = topY - 14.dp)) {
                Text(
                    if (beyond != null) "다음 위험 ${Phrases.distance(beyond.distanceKm)} 앞" else "앞 ${Phrases.distance(range)} 위험 없음",
                    style = HaloType.Label,
                    color = HaloColors.TextDim,
                )
            }
        }
    }
}
