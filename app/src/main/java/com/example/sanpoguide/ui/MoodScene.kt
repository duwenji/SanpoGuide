package com.example.sanpoguide.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.example.sanpoguide.mood.Mood
import com.example.sanpoguide.mood.Place
import com.example.sanpoguide.mood.Season
import com.example.sanpoguide.mood.Sky
import com.example.sanpoguide.mood.TimeOfDay
import kotlin.random.Random

/**
 * A small landscape for the current mood — sky for the time and weather, a silhouette for the
 * place, a touch of the season. Drawn rather than bundled as pictures: no image licenses, no
 * download, and any combination of time, weather and place has a picture.
 */
@Composable
fun MoodScene(mood: Mood, modifier: Modifier = Modifier) {
    Canvas(modifier) { drawScene(mood) }
}

private fun DrawScope.drawScene(mood: Mood) {
    val dark = mood.time.isDark
    val (top, bottom) = skyColors(mood)
    drawRect(Brush.verticalGradient(listOf(top, bottom)))

    val clear = mood.sky == null || mood.sky == Sky.CLEAR
    if (dark && clear) drawStars()
    if (clear || mood.sky == Sky.CLOUDY) drawSunOrMoon(mood.time)
    if (mood.sky != null && mood.sky != Sky.CLEAR && mood.sky != Sky.FOG) drawClouds(dark, heavy = mood.sky.isWet)

    val ground = groundColor(mood)
    val horizon = size.height * 0.78f
    when (mood.place) {
        Place.SHRINE_TEMPLE -> { drawTrees(ground, horizon, count = 4, scale = 0.8f); drawTorii(ground, horizon, dark) }
        Place.PARK, null -> drawTrees(ground, horizon, count = 7, scale = 1f)
        Place.WATERSIDE -> drawWater(ground, horizon, dark)
        Place.HISTORIC -> { drawTrees(ground, horizon, count = 3, scale = 0.7f); drawPagoda(ground, horizon) }
        Place.TOWN -> drawTown(ground, horizon, dark)
    }
    if (mood.place != Place.WATERSIDE) drawRect(ground, Offset(0f, horizon), Size(size.width, size.height - horizon))

    when (mood.sky) {
        Sky.RAIN -> drawRain()
        Sky.THUNDER -> { drawRain(); drawLightning() }
        Sky.SNOW -> drawSnow()
        Sky.FOG -> drawFog()
        else -> drawSeasonAccent(mood.season, horizon)
    }
}

private fun skyColors(mood: Mood): Pair<Color, Color> = when {
    mood.sky == Sky.FOG -> if (mood.time.isDark) Color(0xFF2A3038) to Color(0xFF454D57) else Color(0xFFC9CFD4) to Color(0xFFE3E6E8)
    mood.sky == Sky.SNOW -> if (mood.time.isDark) Color(0xFF1B2330) to Color(0xFF39475A) else Color(0xFFB9C4CF) to Color(0xFFE8EDF2)
    mood.sky?.isWet == true -> if (mood.time.isDark) Color(0xFF141A26) to Color(0xFF2B3444) else Color(0xFF7D8D9B) to Color(0xFFBCC6CF)
    else -> when (mood.time) {
        TimeOfDay.MORNING -> Color(0xFF9FD3F0) to Color(0xFFFFE6C2)
        TimeOfDay.DAY -> if (mood.season == Season.SUMMER) Color(0xFF3E97DD) to Color(0xFFBFE2F8) else Color(0xFF6FAEDF) to Color(0xFFD2E8F5)
        TimeOfDay.EVENING -> Color(0xFF4F5F9A) to Color(0xFFF3A05A)
        TimeOfDay.NIGHT -> Color(0xFF0B1030) to Color(0xFF2A3264)
        TimeOfDay.LATE_NIGHT -> Color(0xFF05071A) to Color(0xFF161C40)
    }.let { if (mood.sky == Sky.CLOUDY) it.first.muted() to it.second.muted() else it }
}

/** Clouds take the color out of the sky. */
private fun Color.muted() = Color(
    red = red * 0.6f + 0.55f * 0.4f, green = green * 0.6f + 0.58f * 0.4f, blue = blue * 0.6f + 0.62f * 0.4f, alpha = alpha,
)

private fun groundColor(mood: Mood): Color = when {
    mood.time.isDark -> Color(0xFF06080F)
    mood.time == TimeOfDay.EVENING -> Color(0xFF2B1E2A)
    mood.sky?.isWet == true || mood.sky == Sky.FOG -> Color(0xFF3B4650)
    else -> when (mood.season) {
        Season.SPRING -> Color(0xFF3F5E3A)
        Season.SUMMER -> Color(0xFF2B5A2A)
        Season.AUTUMN -> Color(0xFF5C3B21)
        Season.WINTER -> Color(0xFF46525E)
    }
}

private fun DrawScope.drawStars() {
    val rnd = Random(7)
    repeat(28) {
        val alpha = 0.4f + rnd.nextFloat() * 0.6f
        drawCircle(Color.White.copy(alpha = alpha), radius = (0.6f + rnd.nextFloat() * 1.2f) * density,
            center = Offset(rnd.nextFloat() * size.width, rnd.nextFloat() * size.height * 0.6f))
    }
}

private fun DrawScope.drawSunOrMoon(time: TimeOfDay) {
    val h = size.height
    val r = h * 0.16f
    when (time) {
        TimeOfDay.MORNING -> drawSun(Offset(size.width * 0.15f, h * 0.5f), r, Color(0xFFFFE08A))
        TimeOfDay.DAY -> drawSun(Offset(size.width * 0.8f, h * 0.28f), r, Color(0xFFFFF3B0))
        TimeOfDay.EVENING -> drawSun(Offset(size.width * 0.82f, h * 0.7f), r * 1.3f, Color(0xFFFF8A4C))
        TimeOfDay.NIGHT, TimeOfDay.LATE_NIGHT -> {
            val c = Offset(size.width * 0.82f, h * 0.3f)
            drawCircle(Color(0xFFF4F1D8), r, c)
            // A crescent: cover most of the disc with the sky.
            drawCircle(Color(0xFF151B45), r * 0.92f, c + Offset(r * 0.45f, -r * 0.2f))
        }
    }
}

private fun DrawScope.drawSun(center: Offset, r: Float, color: Color) {
    drawCircle(Brush.radialGradient(listOf(color.copy(alpha = 0.45f), Color.Transparent), center, r * 3), r * 3, center)
    drawCircle(color, r, center)
}

private fun DrawScope.drawClouds(dark: Boolean, heavy: Boolean) {
    val color = when {
        dark -> Color(0xFF2E3548)
        heavy -> Color(0xFF9AA5AF)
        else -> Color(0xFFF2F4F6)
    }.copy(alpha = 0.92f)
    val h = size.height
    listOf(0.12f to 0.25f, 0.45f to 0.18f, 0.75f to 0.3f).forEachIndexed { i, (x, y) ->
        val c = Offset(size.width * x, h * y)
        val r = h * (if (heavy) 0.2f else 0.14f) * (1f + i * 0.1f)
        drawCircle(color, r, c)
        drawCircle(color, r * 0.8f, c + Offset(-r * 1.1f, r * 0.3f))
        drawCircle(color, r * 0.85f, c + Offset(r * 1.1f, r * 0.25f))
        drawRect(color, Offset(c.x - r * 1.6f, c.y + r * 0.2f), Size(r * 3.2f, r * 0.8f))
    }
}

private fun DrawScope.drawTrees(color: Color, horizon: Float, count: Int, scale: Float) {
    val rnd = Random(count)
    val h = size.height
    repeat(count) { i ->
        val x = size.width * (i + 0.5f) / count + (rnd.nextFloat() - 0.5f) * size.width / count * 0.6f
        val r = h * (0.1f + rnd.nextFloat() * 0.07f) * scale
        drawRect(color, Offset(x - r * 0.12f, horizon - r * 1.2f), Size(r * 0.24f, r * 1.2f))
        drawCircle(color, r, Offset(x, horizon - r * 1.6f))
        drawCircle(color, r * 0.75f, Offset(x - r * 0.6f, horizon - r * 1.1f))
        drawCircle(color, r * 0.75f, Offset(x + r * 0.6f, horizon - r * 1.1f))
    }
}

private fun DrawScope.drawTorii(ground: Color, horizon: Float, dark: Boolean) {
    // Vermilion by day; a silhouette once it's dark.
    val color = if (dark) ground else Color(0xFFC0392B)
    val h = size.height
    val cx = size.width * 0.3f
    val gateH = h * 0.5f
    val span = gateH * 1.0f
    val post = gateH * 0.08f
    listOf(-1, 1).forEach { side ->
        drawRect(color, Offset(cx + side * span / 2 - post / 2, horizon - gateH), Size(post, gateH))
    }
    // Nuki (lower beam) and the curved kasagi on top.
    drawRect(color, Offset(cx - span * 0.62f, horizon - gateH * 0.78f), Size(span * 1.24f, post * 0.8f))
    val top = Path().apply {
        moveTo(cx - span * 0.8f, horizon - gateH * 1.02f)
        quadraticTo(cx, horizon - gateH * 0.9f, cx + span * 0.8f, horizon - gateH * 1.02f)
        lineTo(cx + span * 0.72f, horizon - gateH * 0.9f)
        quadraticTo(cx, horizon - gateH * 0.8f, cx - span * 0.72f, horizon - gateH * 0.9f)
        close()
    }
    drawPath(top, color)
}

private fun DrawScope.drawPagoda(color: Color, horizon: Float) {
    val h = size.height
    val cx = size.width * 0.7f
    val tiers = 4
    val tierH = h * 0.13f
    for (i in 0 until tiers) {
        val base = horizon - i * tierH
        val w = h * (0.42f - i * 0.07f)
        drawRect(color, Offset(cx - w * 0.3f, base - tierH), Size(w * 0.6f, tierH))
        val roof = Path().apply {
            moveTo(cx - w * 0.6f, base - tierH * 0.55f)
            lineTo(cx + w * 0.6f, base - tierH * 0.55f)
            lineTo(cx + w * 0.3f, base - tierH * 0.95f)
            lineTo(cx - w * 0.3f, base - tierH * 0.95f)
            close()
        }
        drawPath(roof, color)
    }
    drawLine(color, Offset(cx, horizon - tiers * tierH), Offset(cx, horizon - tiers * tierH - h * 0.12f), strokeWidth = h * 0.02f)
}

private fun DrawScope.drawWater(ground: Color, horizon: Float, dark: Boolean) {
    val water = if (dark) Color(0xFF0E1734) else Color(0xFF3F7FB5)
    val top = size.height * 0.68f
    drawRect(Brush.verticalGradient(listOf(water.copy(alpha = 0.85f), water), top, size.height), Offset(0f, top), Size(size.width, size.height - top))
    val wave = Color.White.copy(alpha = if (dark) 0.25f else 0.5f)
    val rnd = Random(3)
    repeat(10) {
        val y = top + (size.height - top) * (0.15f + rnd.nextFloat() * 0.8f)
        val x = rnd.nextFloat() * size.width
        val len = size.width * (0.04f + rnd.nextFloat() * 0.06f)
        drawLine(wave, Offset(x, y), Offset(x + len, y), strokeWidth = 1.5f * density, cap = StrokeCap.Round)
    }
    // A far shore on the left.
    drawRect(ground, Offset(0f, top - size.height * 0.05f), Size(size.width * 0.25f, size.height * 0.05f))
}

private fun DrawScope.drawTown(color: Color, horizon: Float, dark: Boolean) {
    val rnd = Random(11)
    val h = size.height
    var x = 0f
    while (x < size.width) {
        val w = size.width * (0.05f + rnd.nextFloat() * 0.06f)
        val bh = h * (0.18f + rnd.nextFloat() * 0.3f)
        drawRect(color, Offset(x, horizon - bh), Size(w - density, bh))
        if (dark) {
            // Lit windows.
            repeat(3) {
                if (rnd.nextFloat() < 0.6f) {
                    drawRect(Color(0xFFFFD27A).copy(alpha = 0.8f),
                        Offset(x + w * (0.2f + rnd.nextFloat() * 0.5f), horizon - bh * (0.2f + rnd.nextFloat() * 0.7f)),
                        Size(2.5f * density, 2.5f * density))
                }
            }
        }
        x += w
    }
}

private fun DrawScope.drawRain() {
    val rnd = Random(5)
    val color = Color.White.copy(alpha = 0.45f)
    repeat(45) {
        val x = rnd.nextFloat() * size.width
        val y = rnd.nextFloat() * size.height
        val len = size.height * 0.08f
        drawLine(color, Offset(x, y), Offset(x - len * 0.3f, y + len), strokeWidth = 1.2f * density)
    }
}

private fun DrawScope.drawLightning() {
    val h = size.height
    val x = size.width * 0.58f
    val bolt = Path().apply {
        moveTo(x, h * 0.2f)
        lineTo(x - h * 0.08f, h * 0.45f)
        lineTo(x + h * 0.02f, h * 0.45f)
        lineTo(x - h * 0.06f, h * 0.72f)
    }
    drawPath(bolt, Color(0xFFFFF176), style = Stroke(width = 2.5f * density, cap = StrokeCap.Round))
}

private fun DrawScope.drawSnow() {
    val rnd = Random(9)
    repeat(40) {
        drawCircle(Color.White.copy(alpha = 0.85f), radius = (1f + rnd.nextFloat() * 1.8f) * density,
            center = Offset(rnd.nextFloat() * size.width, rnd.nextFloat() * size.height))
    }
}

private fun DrawScope.drawFog() {
    val h = size.height
    listOf(0.35f, 0.55f, 0.75f).forEach { y ->
        drawRect(Color.White.copy(alpha = 0.28f), Offset(0f, h * y), Size(size.width, h * 0.12f))
    }
}

/** Petals in spring, leaves in autumn, a thin snow line in winter. */
private fun DrawScope.drawSeasonAccent(season: Season, horizon: Float) {
    val rnd = Random(13)
    when (season) {
        Season.SPRING -> repeat(14) {
            drawOval(Color(0xFFFFC1D6).copy(alpha = 0.9f),
                Offset(rnd.nextFloat() * size.width, rnd.nextFloat() * size.height * 0.9f), Size(4f * density, 2.6f * density))
        }
        Season.AUTUMN -> repeat(12) {
            val c = if (rnd.nextBoolean()) Color(0xFFE0632C) else Color(0xFFF2B233)
            drawCircle(c.copy(alpha = 0.9f), 2f * density, Offset(rnd.nextFloat() * size.width, rnd.nextFloat() * size.height * 0.9f))
        }
        Season.WINTER -> drawRect(Color.White.copy(alpha = 0.55f), Offset(0f, horizon), Size(size.width, 2.5f * density))
        Season.SUMMER -> Unit
    }
}
