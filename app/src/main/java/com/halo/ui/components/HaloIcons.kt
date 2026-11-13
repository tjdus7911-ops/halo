package com.halo.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/**
 * 헤일로 아이콘 (24×24 선 아이콘). 화면시안과 같은 그림을 쓴다.
 * 색은 쓰는 곳에서 tint 로 정한다.
 */
object HaloIcons {
    val Snow: ImageVector by lazy { line("Snow", "M12 2v20M3.3 7l17.4 10M3.3 17L20.7 7 M9.5 3.5L12 6l2.5-2.5M9.5 20.5L12 18l2.5 2.5M3.6 10.4l3.4-.9-.9-3.4M20.4 13.6l-3.4.9.9 3.4M3.6 13.6l3.4.9-.9 3.4M20.4 10.4l-3.4-.9.9-3.4") }
    val Rain: ImageVector by lazy { line("Rain", "M7 15a4.5 4.5 0 1 1 1.6-8.7A6 6 0 0 1 19.5 9 3.5 3.5 0 0 1 18 15.9 M9 18l-1 3M13 18l-1 3M17 18l-1 3") }
    val Thermometer: ImageVector by lazy { line("Thermometer", "M10 14.8V5a2 2 0 1 1 4 0v9.8a4 4 0 1 1-4 0z M12 9v7") }
    val Warning: ImageVector by lazy { line("Warning", "M12 3.5L2.8 19.5h18.4z M12 10v4.2M12 17.2v.3") }
    val Bridge: ImageVector by lazy { line("Bridge", "M2 15h20M4 15v4M20 15v4M2 15c3-6 7-8 10-8s7 2 10 8 M8 15v-4.6M12 15V7M16 15v-4.6") }
    val Tire: ImageVector by lazy { line("Tire", "M3 12a9 9 0 1 0 18 0a9 9 0 1 0 -18 0z M8 12a4 4 0 1 0 8 0a4 4 0 1 0 -8 0z M12 3v3M12 18v3M3 12h3M18 12h3") }
    val Washer: ImageVector by lazy { line("Washer", "M4 18h16M6 18c0-5 2.5-9 6-9s6 4 6 9 M12 9V5M9 6l-2-2M15 6l2-2") }
    val LowBeam: ImageVector by lazy { line("LowBeam", "M14 6c3.5 0 6 2.7 6 6s-2.5 6-6 6c-1.4 0-2.2-.6-2.2-1.6V7.6C11.8 6.6 12.6 6 14 6z M9 7.6L3.5 9.4M9 11.6L3.5 13.4M9 15.6L3.5 17.4") }
    val FogLight: ImageVector by lazy { line("FogLight", "M14 6c3.5 0 6 2.7 6 6s-2.5 6-6 6c-1.4 0-2.2-.6-2.2-1.6V7.6C11.8 6.6 12.6 6 14 6z M3 8h6M3 12h6M3 16h6 M6.2 4.6c-1.1 1.2 1.1 2.4 0 3.6s1.1 2.4 0 3.6 1.1 2.4 0 3.6 1.1 2.4 0 3.6") }
    val Hazard: ImageVector by lazy { line("Hazard", "M12 3.2L2.4 20.2h19.2z M12 9.6l-4.3 7.3h8.6z") }
    val Fog: ImageVector by lazy { line("Fog", "M7 13a4.5 4.5 0 1 1 1.6-8.7A6 6 0 0 1 19.5 7 3.5 3.5 0 0 1 18 13.9 M3 17.5h18M6 21h12") }
    val Visibility: ImageVector by lazy { line("Visibility", "M2.5 12S6 5.5 12 5.5 21.5 12 21.5 12 18 18.5 12 18.5 2.5 12 2.5 12z M9 12a3 3 0 1 0 6 0a3 3 0 1 0 -6 0z") }
    val Mic: ImageVector by lazy { line("Mic", "M12 3h0a3 3 0 0 1 3 3v5a3 3 0 0 1 -3 3h-0a3 3 0 0 1 -3 -3v-5a3 3 0 0 1 3 -3z M5.5 11a6.5 6.5 0 0 0 13 0M12 17.5V21") }
    val Check: ImageVector by lazy { line("Check", "M4.5 12.5l4.5 4.5L19.5 6.5") }
    val Defrost: ImageVector by lazy { line("Defrost", "M3 15c3-5 15-5 18 0 M8 7.5c0 1 1 1.4 1 2.4s-1 1.4-1 2.4M12 6.5c0 1 1 1.4 1 2.4s-1 1.4-1 2.4M16 7.5c0 1 1 1.4 1 2.4s-1 1.4-1 2.4 M5 19h14") }
    val MirrorHeat: ImageVector by lazy { line("MirrorHeat", "M7 6h6a4 4 0 0 1 4 4v2a4 4 0 0 1 -4 4h-6a4 4 0 0 1 -4 -4v-2a4 4 0 0 1 4 -4z M17 11h4 M8 9c0 .8.8 1.1.8 2s-.8 1.2-.8 2M12 9c0 .8.8 1.1.8 2s-.8 1.2-.8 2") }
    val SteeringWheel: ImageVector by lazy { line("SteeringWheel", "M3.5 12a8.5 8.5 0 1 0 17 0a8.5 8.5 0 1 0 -17 0z M9.8 12a2.2 2.2 0 1 0 4.4 0a2.2 2.2 0 1 0 -4.4 0z M3.8 11.2l6.1.8M14.1 12l6.1-.8M12 14.2v6.3") }
    val Clock: ImageVector by lazy { line("Clock", "M3.5 12a8.5 8.5 0 1 0 17 0a8.5 8.5 0 1 0 -17 0z M12 7.5V12l3 2") }
    val Cloud: ImageVector by lazy { line("Cloud", "M7 18a4.5 4.5 0 1 1 1.6-8.7A6 6 0 0 1 19.5 12 3.5 3.5 0 0 1 18 18.9z") }
    val Shield: ImageVector by lazy { line("Shield", "M12 3l7.5 3v5.5c0 4.6-3.2 8.3-7.5 9.5-4.3-1.2-7.5-4.9-7.5-9.5V6z") }
    val Wiper: ImageVector by lazy { line("Wiper", "M3 18a12 12 0 0 1 18 0 M12 18l6.5-9") }
    val Pin: ImageVector by lazy { line("Pin", "M12 21s7-6.2 7-11.5A7 7 0 0 0 5 9.5C5 14.8 12 21 12 21z M9.5 9.5a2.5 2.5 0 1 0 5 0a2.5 2.5 0 1 0 -5 0z") }
    val Drop: ImageVector by lazy { line("Drop", "M12 3c-3.5 5-6 8.3-6 11.2A6 6 0 0 0 18 14.2C18 11.3 15.5 8 12 3z") }
    val Car: ImageVector by lazy { line("Car", "M5 16v3M19 16v3M3.5 16V12l2.2-5.2A2 2 0 0 1 7.5 5.5h9a2 2 0 0 1 1.8 1.3L20.5 12v4z M3.5 12h17M7 14h1.5M15.5 14H17") }
    val Tunnel: ImageVector by lazy { line("Tunnel", "M3 20v-9a9 9 0 0 1 18 0v9 M7.5 20v-7a4.5 4.5 0 0 1 9 0v7 M2 20h20") }
    val Speaker: ImageVector by lazy { line("Speaker", "M4 9.5h3.5L12 5.5v13l-4.5-4H4z M15.5 9a4 4 0 0 1 0 6 M18 6.5a7.5 7.5 0 0 1 0 11") }
    val Close: ImageVector by lazy { line("Close", "M6 6l12 12M18 6L6 18") }
    val Next: ImageVector by lazy { line("Next", "M9 5l7 7-7 7") }
    val Restart: ImageVector by lazy { line("Restart", "M4.5 12a7.5 7.5 0 1 0 2.2-5.3 M4.5 4.5v4h4") }
    val Settings: ImageVector by lazy { line("Settings", "M12.00 4.80 L13.81 2.88 L15.56 3.41 L16.00 6.01 L17.09 6.91 L19.73 6.83 L20.59 8.44 L19.06 10.60 L19.20 12.00 L21.12 13.81 L20.59 15.56 L17.99 16.00 L17.09 17.09 L17.17 19.73 L15.56 20.59 L13.40 19.06 L12.00 19.20 L10.19 21.12 L8.44 20.59 L8.00 17.99 L6.91 17.09 L4.27 17.17 L3.41 15.56 L4.94 13.40 L4.80 12.00 L2.88 10.19 L3.41 8.44 L6.01 8.00 L6.91 6.91 L6.83 4.27 L8.44 3.41 L10.60 4.94z M9 12a3 3 0 1 0 6 0a3 3 0 1 0 -6 0z") }

    private fun line(name: String, pathData: String): ImageVector =
        ImageVector.Builder(name = name, defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f)
            .addPath(
                pathData = addPathNodes(pathData),
                fill = null,
                stroke = SolidColor(Color.White),
                strokeLineWidth = 1.9f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            )
            .build()
}
