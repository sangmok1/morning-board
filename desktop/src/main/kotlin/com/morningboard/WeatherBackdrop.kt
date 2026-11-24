package com.morningboard

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.layout.onSizeChanged
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

/** x,y 는 0..1 비율, speed 는 화면높이/초, size 는 dp, phase 는 흔들림/반짝임용 */
private class Particle(var x: Float, var y: Float, val speed: Float, val size: Float, val phase: Float)

private class BackdropState(val mood: Mood?) {
    val rnd = Random(7)
    val particles = ArrayList<Particle>()
    var lastFrame = 0L
    var flashUntil = 0L
    var nextFlash = 0L
    var spawned = false

    fun spawn() {
        val n = when (mood) {
            Mood.RAIN -> 90; Mood.THUNDER -> 110; Mood.SNOW -> 70
            Mood.CLEAR_NIGHT -> 80; Mood.CLOUDY -> 5; Mood.FOG -> 4; Mood.CLEAR_DAY -> 24
            null -> 0
        }
        repeat(n) {
            val (speed, size) = when (mood) {
                Mood.RAIN, Mood.THUNDER -> (1.1f + rnd.nextFloat() * 0.6f) to (14f + rnd.nextFloat() * 14f)
                Mood.SNOW -> (0.05f + rnd.nextFloat() * 0.07f) to (1.5f + rnd.nextFloat() * 2.5f)
                Mood.CLOUDY, Mood.FOG -> (0.006f + rnd.nextFloat() * 0.01f) to (180f + rnd.nextFloat() * 160f)
                Mood.CLEAR_DAY -> (0.015f + rnd.nextFloat() * 0.02f) to (1f + rnd.nextFloat() * 1.8f)
                else -> 0f to (0.8f + rnd.nextFloat() * 1.4f)
            }
            particles += Particle(rnd.nextFloat(), rnd.nextFloat(), speed, size, rnd.nextFloat() * 2f * PI.toFloat())
        }
        spawned = true
    }
}

/**
 * 화면 전체 뒤에 깔리는 날씨 배경. 어두운 톤 유지(벽에 거는 보드라 눈부시지 않게).
 */
@Composable
fun WeatherBackdrop(mood: Mood?, density: Float, modifier: Modifier = Modifier) {
    val state = remember(mood) { BackdropState(mood) }
    var tick by remember(mood) { mutableStateOf(0L) }
    var sizePx by remember { mutableStateOf(Size.Zero) }

    LaunchedEffect(mood, sizePx) {
        if (mood == null || sizePx.width <= 0f) return@LaunchedEffect
        if (!state.spawned) state.spawn()
        state.lastFrame = 0L
        while (true) {
            withFrameNanos { nowNs ->
                val now = nowNs / 1_000_000L
                val dt = if (state.lastFrame == 0L) 0f else ((now - state.lastFrame).coerceAtMost(200)) / 1000f
                state.lastFrame = now
                advance(state, mood, dt, sizePx)
                tick = now
            }
        }
    }

    Canvas(modifier.onSizeChanged { sizePx = Size(it.width.toFloat(), it.height.toFloat()) }) {
        @Suppress("UNUSED_EXPRESSION") tick // 매 프레임 재그리기 트리거
        drawBackdrop(this, state, mood, density, tick / 1000f)
    }
}

private fun advance(state: BackdropState, mood: Mood?, dt: Float, size: Size) {
    val w = size.width
    val h = size.height
    if (w <= 0f || h <= 0f) return
    val rnd = state.rnd
    when (mood) {
        Mood.RAIN, Mood.THUNDER -> {
            val slant = 0.18f
            for (p in state.particles) {
                p.y += p.speed * dt; p.x += p.speed * dt * slant * h / w
                if (p.y > 1.05f) { p.y = -0.05f; p.x = rnd.nextFloat() * 1.1f - 0.1f }
            }
        }
        Mood.SNOW -> for (p in state.particles) {
            p.y += p.speed * dt
            if (p.y > 1.03f) { p.y = -0.03f; p.x = rnd.nextFloat() }
        }
        Mood.CLEAR_DAY -> for (p in state.particles) {
            p.y -= p.speed * dt
            if (p.y < -0.02f) { p.y = 1.02f; p.x = rnd.nextFloat() }
        }
        Mood.CLOUDY, Mood.FOG -> for (p in state.particles) {
            p.x += p.speed * dt
            val r = p.size * 1f
            if (p.x * w - r > w) { p.x = -r / w; p.y = rnd.nextFloat() }
        }
        else -> {}
    }
}

private fun drawBackdrop(scope: DrawScope, state: BackdropState, mood: Mood?, density: Float, t: Float) = with(scope) {
    val w = size.width
    val h = size.height
    if (w <= 0f || h <= 0f) return
    fun d(v: Float) = v * density

    // 배경 그라데이션
    fun lin(top: Long, bottom: Long) = Brush.verticalGradient(listOf(Color(top), Color(bottom)), startY = 0f, endY = h)
    fun glow(cx: Float, cy: Float, r: Float, color: Long) =
        Brush.radialGradient(listOf(Color(color), Color.Transparent), center = Offset(cx, cy), radius = r)

    when (mood) {
        Mood.CLEAR_DAY -> {
            drawRect(lin(0xFF15325A, 0xFF0E1622))
            drawRect(glow(w * 0.92f, h * 0.02f, w * 0.55f, 0x66FFB84D))
        }
        Mood.CLEAR_NIGHT -> {
            drawRect(lin(0xFF0B1030, 0xFF07080F))
            drawRect(glow(w * 0.85f, h * 0.08f, w * 0.3f, 0x33AFC6FF))
        }
        Mood.CLOUDY -> drawRect(lin(0xFF2A303B, 0xFF12151B))
        Mood.RAIN -> drawRect(lin(0xFF1B2838, 0xFF0B0F15))
        Mood.SNOW -> {
            drawRect(lin(0xFF2C3A4E, 0xFF141A24))
            drawRect(glow(w * 0.5f, h * 1.1f, w * 0.7f, 0x22FFFFFF))
        }
        Mood.FOG -> drawRect(lin(0xFF33373E, 0xFF181A1E))
        Mood.THUNDER -> drawRect(lin(0xFF1E1830, 0xFF0A0910))
        null -> drawRect(C.BG)
    }

    when (mood) {
        Mood.RAIN, Mood.THUNDER -> {
            val slant = 0.18f
            val color = Color(0x55A9C8FF)
            for (p in state.particles) {
                val len = d(p.size)
                val x = p.x * w; val y = p.y * h
                drawLine(color, Offset(x, y), Offset(x - len * slant, y - len), strokeWidth = d(1.3f))
            }
            if (mood == Mood.THUNDER) {
                val now = (t * 1000f).toLong()
                if (state.nextFlash == 0L) state.nextFlash = now + 4000 + state.rnd.nextLong(8000)
                if (now >= state.nextFlash) { state.flashUntil = now + 160; state.nextFlash = now + 6000 + state.rnd.nextLong(12000) }
                if (now < state.flashUntil) drawRect(Color(0x2EDDE6FF))
            }
        }
        Mood.SNOW -> for (p in state.particles) {
            val x = (p.x + 0.015f * sin(t * 0.8f + p.phase)) * w
            drawCircle(Color(red = 235, green = 242, blue = 255, alpha = 150), radius = d(p.size), center = Offset(x, p.y * h))
        }
        Mood.CLEAR_NIGHT -> for (p in state.particles) {
            val a = (110 + 100 * sin(t * 1.3f + p.phase * 3f)).toInt().coerceIn(20, 220)
            drawCircle(Color(red = 220, green = 228, blue = 255, alpha = a), radius = d(p.size), center = Offset(p.x * w, p.y * h * 0.75f))
        }
        Mood.CLEAR_DAY -> for (p in state.particles) {
            val a = (60 + 50 * sin(t * 0.9f + p.phase)).toInt().coerceIn(10, 120)
            val x = (p.x + 0.01f * sin(t * 0.5f + p.phase)) * w
            drawCircle(Color(red = 255, green = 214, blue = 140, alpha = a), radius = d(p.size), center = Offset(x, p.y * h))
        }
        Mood.CLOUDY, Mood.FOG -> {
            val base = if (mood == Mood.FOG) 0x1CFFFFFF else 0x14FFFFFF
            for (p in state.particles) {
                val r = d(p.size)
                drawCircle(
                    brush = Brush.radialGradient(listOf(Color(base), Color.Transparent), center = Offset(p.x * w, p.y * h), radius = r),
                    radius = r, center = Offset(p.x * w, p.y * h),
                )
            }
        }
        null -> {}
    }
}
