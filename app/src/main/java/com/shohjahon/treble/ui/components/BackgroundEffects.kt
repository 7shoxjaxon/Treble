package com.shohjahon.treble.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.shohjahon.treble.ui.theme.Ink

/** Futbol maydoni chiziqlari: o'rta chiziq, markaz doirasi va tepadagi jarima maydoni. */
fun DrawScope.drawPitchLines(color: Color, centerYFraction: Float, radius: Float) {
    val sw = 1.5.dp.toPx()
    val w = size.width
    val h = size.height
    val cy = h * centerYFraction
    drawLine(color, Offset(0f, cy), Offset(w, cy), sw)
    drawCircle(color, radius, Offset(w / 2f, cy), style = Stroke(sw))
    drawCircle(color, sw * 2.5f, Offset(w / 2f, cy))
    drawRect(color, topLeft = Offset(w * 0.24f, -sw), size = Size(w * 0.52f, h * 0.1f + sw), style = Stroke(sw))
}

fun Modifier.pitchLines(color: Color = Color(0x26FFFFFF), centerYFraction: Float = 0.5f, radiusFraction: Float = 0.3f): Modifier =
    drawBehind { drawPitchLines(color, centerYFraction, size.width * radiusFraction) }

/** Aurora (Okean): qora asos ustida ko'k, firuza va binafsha-ko'k yorug'lik. */
private fun DrawScope.drawAurora(glowRight: Boolean, pitch: Boolean) {
    val w = size.width
    val h = size.height
    val topX = if (glowRight) w * 0.95f else w * 0.05f
    drawRect(
        Brush.radialGradient(
            colors = listOf(Color(0xCC1478C8), Color(0x551478C8), Color.Transparent),
            center = Offset(topX, h * 0.05f),
            radius = w * 1.05f
        )
    )
    drawRect(
        Brush.radialGradient(
            colors = listOf(Color(0x990EAAAA), Color.Transparent),
            center = Offset(if (glowRight) w * 0.02f else w * 0.98f, h * 0.60f),
            radius = w * 0.9f
        )
    )
    drawRect(
        Brush.radialGradient(
            colors = listOf(Color(0x993240B4), Color.Transparent),
            center = Offset(if (glowRight) w * 0.9f else w * 0.15f, h * 0.97f),
            radius = w * 0.95f
        )
    )
    if (pitch) drawPitchLines(Color(0x1FFFFFFF), 0.355f, w * 0.38f)
}

/** Ekran ildizi uchun fon: eski `.background(BackgroundDark)` o'rniga qo'yiladi. */
fun Modifier.auroraBackground(glowRight: Boolean = false, pitch: Boolean = false): Modifier =
    this.background(Ink).drawBehind { drawAurora(glowRight, pitch) }

/**
 * Aurora fon.
 * [glowRight] = tepadagi yorug'lik o'ng tomonda. [pitch] = orqada futbol maydoni chiziqlari.
 */
@Composable
fun BackgroundAmbientGlow(
    modifier: Modifier = Modifier,
    glowRight: Boolean = false,
    pitch: Boolean = false
) {
    Box(modifier = modifier.fillMaxSize().auroraBackground(glowRight, pitch))
}
