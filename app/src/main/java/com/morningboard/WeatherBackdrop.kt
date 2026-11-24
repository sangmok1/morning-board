package com.morningboard

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.os.SystemClock
import android.view.View
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

/** 날씨 분위기 */
enum class Mood { CLEAR_DAY, CLEAR_NIGHT, CLOUDY, RAIN, SNOW, FOG, THUNDER }

fun moodOf(code: Int, isDay: Boolean): Mood = when (code) {
    0, 1 -> if (isDay) Mood.CLEAR_DAY else Mood.CLEAR_NIGHT
    2 -> if (isDay) Mood.CLEAR_DAY else Mood.CLEAR_NIGHT
    3 -> Mood.CLOUDY
    45, 48 -> Mood.FOG
    in 71..77, 85, 86 -> Mood.SNOW
    in 95..99 -> Mood.THUNDER
    in 51..67, in 80..82 -> Mood.RAIN
    else -> Mood.CLOUDY
}

/**
 * 화면 전체 뒤에 깔리는 날씨 배경. 어두운 톤 유지(벽에 거는 보드라 눈부시지 않게),
 * 애니메이션은 초당 ~24프레임으로 가볍게.
 */
class WeatherBackdrop(ctx: Context) : View(ctx) {
    var mood: Mood? = null
        set(v) {
            if (field == v) return
            field = v; shaders = null; particles.clear(); invalidate()
        }

    private var running = false
    private val rnd = Random(7)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var shaders: List<Paint>? = null
    private var lastFrame = 0L
    private var flashUntil = 0L
    private var nextFlash = 0L

    /** x,y 는 0..1 비율, speed 는 화면높이/초, size 는 dp, phase 는 흔들림/반짝임용 */
    private class P(var x: Float, var y: Float, val speed: Float, val size: Float, val phase: Float)
    private val particles = mutableListOf<P>()

    fun start() { running = true; lastFrame = 0; invalidate() }
    fun stop() { running = false }

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) { shaders = null }

    private fun d(v: Float) = v * resources.displayMetrics.density

    private fun gradientPaints(w: Float, h: Float): List<Paint> {
        fun lin(top: Int, bottom: Int) = Paint().apply {
            shader = LinearGradient(0f, 0f, 0f, h, top, bottom, Shader.TileMode.CLAMP)
        }
        fun glow(cx: Float, cy: Float, r: Float, color: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(cx, cy, r, color, Color.TRANSPARENT, Shader.TileMode.CLAMP)
        }
        return when (mood) {
            Mood.CLEAR_DAY -> listOf(
                lin(0xFF15325A.toInt(), 0xFF0E1622.toInt()),
                glow(w * 0.92f, h * 0.02f, w * 0.55f, 0x66FFB84D),
            )
            Mood.CLEAR_NIGHT -> listOf(
                lin(0xFF0B1030.toInt(), 0xFF07080F.toInt()),
                glow(w * 0.85f, h * 0.08f, w * 0.3f, 0x33AFC6FF),
            )
            Mood.CLOUDY -> listOf(lin(0xFF2A303B.toInt(), 0xFF12151B.toInt()))
            Mood.RAIN -> listOf(lin(0xFF1B2838.toInt(), 0xFF0B0F15.toInt()))
            Mood.SNOW -> listOf(
                lin(0xFF2C3A4E.toInt(), 0xFF141A24.toInt()),
                glow(w * 0.5f, h * 1.1f, w * 0.7f, 0x22FFFFFF),
            )
            Mood.FOG -> listOf(lin(0xFF33373E.toInt(), 0xFF181A1E.toInt()))
            Mood.THUNDER -> listOf(lin(0xFF1E1830.toInt(), 0xFF0A0910.toInt()))
            null -> listOf(lin(C.BG, C.BG))
        }
    }

    private fun spawn() {
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
            particles += P(rnd.nextFloat(), rnd.nextFloat(), speed, size, rnd.nextFloat() * 2f * PI.toFloat())
        }
    }

    override fun onDraw(c: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        if (w <= 0f || h <= 0f) return
        val sh = shaders ?: gradientPaints(w, h).also { shaders = it }
        for (p in sh) c.drawRect(0f, 0f, w, h, p)
        if (particles.isEmpty()) spawn()

        val now = SystemClock.uptimeMillis()
        val dt = if (lastFrame == 0L) 0f else ((now - lastFrame).coerceAtMost(200)) / 1000f
        lastFrame = now
        val t = now / 1000f

        when (mood) {
            Mood.RAIN, Mood.THUNDER -> {
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = d(1.3f)
                paint.color = 0x55A9C8FF
                val slant = 0.18f
                for (p in particles) {
                    p.y += p.speed * dt; p.x += p.speed * dt * slant * h / w
                    if (p.y > 1.05f) { p.y = -0.05f; p.x = rnd.nextFloat() * 1.1f - 0.1f }
                    val len = d(p.size)
                    val x = p.x * w; val y = p.y * h
                    c.drawLine(x, y, x - len * slant, y - len, paint)
                }
                if (mood == Mood.THUNDER) {
                    if (nextFlash == 0L) nextFlash = now + 4000 + rnd.nextLong(8000)
                    if (now >= nextFlash) { flashUntil = now + 160; nextFlash = now + 6000 + rnd.nextLong(12000) }
                    if (now < flashUntil) { paint.style = Paint.Style.FILL; paint.color = 0x2EDDE6FF; c.drawRect(0f, 0f, w, h, paint) }
                }
            }
            Mood.SNOW -> {
                paint.style = Paint.Style.FILL
                for (p in particles) {
                    p.y += p.speed * dt
                    if (p.y > 1.03f) { p.y = -0.03f; p.x = rnd.nextFloat() }
                    val x = (p.x + 0.015f * sin(t * 0.8f + p.phase)) * w
                    paint.color = Color.argb(150, 235, 242, 255)
                    c.drawCircle(x, p.y * h, d(p.size), paint)
                }
            }
            Mood.CLEAR_NIGHT -> {
                paint.style = Paint.Style.FILL
                for (p in particles) {
                    val a = (110 + 100 * sin(t * 1.3f + p.phase * 3f)).toInt().coerceIn(20, 220)
                    paint.color = Color.argb(a, 220, 228, 255)
                    c.drawCircle(p.x * w, p.y * h * 0.75f, d(p.size), paint)
                }
            }
            Mood.CLEAR_DAY -> {
                // 햇빛 속에 떠다니는 먼지처럼 아주 은은한 반짝임
                paint.style = Paint.Style.FILL
                for (p in particles) {
                    p.y -= p.speed * dt
                    if (p.y < -0.02f) { p.y = 1.02f; p.x = rnd.nextFloat() }
                    val a = (60 + 50 * sin(t * 0.9f + p.phase)).toInt().coerceIn(10, 120)
                    paint.color = Color.argb(a, 255, 214, 140)
                    c.drawCircle((p.x + 0.01f * sin(t * 0.5f + p.phase)) * w, p.y * h, d(p.size), paint)
                }
            }
            Mood.CLOUDY, Mood.FOG -> {
                // 크고 흐릿한 구름 덩어리가 천천히 흘러감
                paint.style = Paint.Style.FILL
                val base = if (mood == Mood.FOG) 0x1CFFFFFF else 0x14FFFFFF
                for (p in particles) {
                    p.x += p.speed * dt
                    val r = d(p.size)
                    if (p.x * w - r > w) { p.x = -r / w; p.y = rnd.nextFloat() }
                    paint.shader = RadialGradient(p.x * w, p.y * h, r, base, Color.TRANSPARENT, Shader.TileMode.CLAMP)
                    c.drawCircle(p.x * w, p.y * h, r, paint)
                    paint.shader = null
                }
            }
            null -> {}
        }

        if (running && mood != null) postInvalidateDelayed(42) // ~24fps
    }
}
