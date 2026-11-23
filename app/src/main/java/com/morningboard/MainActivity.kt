package com.morningboard

import android.app.Activity
import android.content.Intent
import android.content.res.Configuration
import android.graphics.drawable.BitmapDrawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

class MainActivity : Activity() {
    private val main = Handler(Looper.getMainLooper())
    private lateinit var prefs: Prefs

    private lateinit var clockText: TextView
    private lateinit var dateText: TextView
    private lateinit var updatedText: TextView
    private lateinit var weatherBox: LinearLayout
    private lateinit var busBox: LinearLayout

    private var lastBusFetch = 0L
    private var lastWeatherFetch = 0L
    private var loadingBus = false
    private var loadingWeather = false
    private var weather: Weather? = null
    private var weatherError: String? = null
    private var stops: List<StopArrivals> = emptyList()

    /** 매초 갱신할 카운트다운 텍스트 */
    private class Live(val view: TextView, val eta: Eta?, val fetchedAt: Long, val withSec: Boolean)
    private val live = mutableListOf<Live>()

    private val ticker = object : Runnable {
        override fun run() {
            tick()
            main.postDelayed(this, 1000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        buildUi()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        buildUi()
        renderWeather(); renderBuses()
    }

    override fun onResume() {
        super.onResume()
        if (prefs.keepScreenOn) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        hideSystemBars()
        refreshWeather(); refreshBuses()
        main.post(ticker)
    }

    override fun onPause() {
        super.onPause()
        main.removeCallbacks(ticker)
    }

    private fun hideSystemBars() {
        if (Build.VERSION.SDK_INT >= 30) {
            window.insetsController?.let {
                it.hide(WindowInsets.Type.systemBars())
                it.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION)
        }
    }

    private fun tick() {
        val now = System.currentTimeMillis()
        clockText.text = SimpleDateFormat("HH:mm", Locale.KOREA).format(Date(now))
        dateText.text = SimpleDateFormat("M월 d일 EEEE", Locale.KOREA).format(Date(now))
        for (l in live) l.view.text = Board.etaShort(l.eta, l.fetchedAt, l.withSec, now)
        if (now - lastBusFetch > 30_000) refreshBuses()
        val wInterval = if (weather == null) 60_000L else 15 * 60_000L
        if (now - lastWeatherFetch > wInterval) refreshWeather()
    }

    private fun refreshWeather() {
        if (loadingWeather) return
        loadingWeather = true
        lastWeatherFetch = System.currentTimeMillis()
        Thread {
            val (w, err) = Board.loadWeather(this)
            main.post {
                loadingWeather = false
                if (w != null || weather == null) { weather = w; weatherError = err }
                else weatherError = err // 실패해도 이전 날씨는 유지
                if (err == Board.OFFLINE) { // 와이파이 붙는 중이면 5초 뒤 재시도
                    val interval = if (weather == null) 60_000L else 15 * 60_000L
                    lastWeatherFetch = System.currentTimeMillis() - interval + 5_000
                }
                renderWeather(); pushWidget()
            }
        }.start()
    }

    private fun refreshBuses() {
        if (loadingBus) return
        loadingBus = true
        lastBusFetch = System.currentTimeMillis()
        Thread {
            val result = Board.loadBuses(this)
            main.post {
                loadingBus = false
                val offline = result.isNotEmpty() && result.all { it.error == Board.OFFLINE }
                if (offline) {
                    lastBusFetch = System.currentTimeMillis() - 25_000 // 5초 뒤 재시도
                    // 이전에 받은 정보가 있으면 그대로 두고 카운트다운 계속
                    if (stops.isEmpty() || stops.all { it.error != null }) { stops = result; renderBuses() }
                    updatedText.text = "인터넷 연결 대기 중…"
                } else {
                    stops = result
                    renderBuses(); pushWidget()
                    updatedText.text = "버스 ${Board.clock(System.currentTimeMillis())} 갱신"
                }
            }
        }.start()
    }

    private fun pushWidget() = BoardWidget.render(this, Snapshot(weather, weatherError, stops))

    // ───────────────────────── 화면 구성 ─────────────────────────
    private fun buildUi() {
        val landscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        val root = vbox().apply {
            setBackgroundColor(C.BG)
            setPadding(dp(28), dp(20), dp(28), dp(24))
        }

        // 헤더: 시계 / 날짜 / 갱신시각 / 설정
        val header = hbox()
        val clockCol = vbox()
        clockText = text("--:--", 60f, C.TEXT, true)
        dateText = text("", 22f, C.SUB)
        clockCol.addView(clockText); clockCol.addView(dateText)
        header.addView(clockCol, lp(0, weight = 1f))
        updatedText = text("", 15f, C.DIM).apply { setPadding(0, 0, dp(16), 0) }
        header.addView(updatedText, lp(LinearLayout.LayoutParams.WRAP_CONTENT))
        val settings = text("⚙  설정", 20f, C.TEXT).apply {
            background = rounded(C.CARD2, dp(16).toFloat())
            setPadding(dp(20), dp(12), dp(20), dp(12))
            setOnClickListener { startActivity(Intent(this@MainActivity, SettingsActivity::class.java)) }
        }
        header.addView(settings, lp(LinearLayout.LayoutParams.WRAP_CONTENT))
        root.addView(header, lp())

        val body = LinearLayout(this).apply {
            orientation = if (landscape) LinearLayout.HORIZONTAL else LinearLayout.VERTICAL
        }
        weatherBox = vbox().apply {
            background = rounded(C.CARD, dp(28).toFloat())
            setPadding(dp(28), dp(24), dp(28), dp(24))
        }
        val busCard = vbox().apply {
            background = rounded(C.CARD, dp(28).toFloat())
            setPadding(dp(24), dp(20), dp(24), dp(20))
        }
        val busTitle = hbox()
        busTitle.addView(text("🚌  버스 도착", 22f, C.SUB, true), lp(0, weight = 1f))
        busTitle.addView(text("탭하면 새로고침", 14f, C.DIM), lp(LinearLayout.LayoutParams.WRAP_CONTENT))
        busCard.addView(busTitle, lp())
        busBox = vbox()
        val scroll = ScrollView(this).apply { addView(busBox, lp()); isVerticalScrollBarEnabled = false }
        busCard.addView(scroll, lp(h = 0, weight = 1f))
        busCard.setOnClickListener { lastBusFetch = 0; refreshBuses() }
        busBox.setOnClickListener { lastBusFetch = 0; refreshBuses() }

        if (landscape) {
            body.addView(weatherBox, lp(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f))
            body.addView(busCard, lp(0, LinearLayout.LayoutParams.MATCH_PARENT, 1.25f).apply { marginStart = dp(20) })
        } else {
            body.addView(weatherBox, lp())
            body.addView(busCard, lp(h = 0, weight = 1f).apply { topMargin = dp(20) })
        }
        root.addView(body, lp(h = 0, weight = 1f).apply { topMargin = dp(16) })
        setContentView(root)
        tick()
    }

    private fun renderWeather() {
        weatherBox.removeAllViews()
        val w = weather
        if (w == null) {
            weatherBox.gravity = Gravity.CENTER
            weatherBox.addView(text(weatherError ?: "날씨 불러오는 중…", 22f, C.SUB).apply { gravity = Gravity.CENTER })
            return
        }
        weatherBox.gravity = Gravity.NO_GRAVITY
        val top = hbox()
        top.addView(pixelView(WeatherApi.icon(w), 128), lp(dp(128), dp(128)))
        val col = vbox().apply { setPadding(dp(24), 0, 0, 0) }
        col.addView(text("${w.temp.roundToInt()}°", 76f, C.TEXT, true))
        col.addView(text("${WeatherApi.desc(w.code)} · 체감 ${w.feels.roundToInt()}°", 22f, C.SUB))
        col.addView(text("최저 ${w.min.roundToInt()}° / 최고 ${w.max.roundToInt()}° · 강수 ${w.rainProb12h}%", 18f, C.DIM))
        top.addView(col, lp(0, weight = 1f))
        weatherBox.addView(top, lp())

        val outfit = WeatherApi.outfit(w)
        weatherBox.addView(text("오늘의 옷차림", 18f, C.SUB).apply { setPadding(0, dp(24), 0, dp(10)) })
        val row = hbox()
        for (item in outfit.items) {
            val cell = vbox().apply { gravity = Gravity.CENTER_HORIZONTAL; setPadding(0, 0, dp(14), 0) }
            cell.addView(pixelTile(item.sprite, 112), lp(dp(112), dp(112)))
            cell.addView(text(item.label, 17f, C.TEXT).apply { gravity = Gravity.CENTER; setPadding(0, dp(6), 0, 0) },
                lp(dp(112)))
            row.addView(cell)
        }
        weatherBox.addView(row, lp())
        weatherBox.addView(text(outfit.headline, 30f, C.ACCENT, true).apply { setPadding(0, dp(16), 0, 0) })
        for (tip in outfit.tips) weatherBox.addView(text("• $tip", 19f, C.TEXT).apply { setPadding(0, dp(6), 0, 0) })
        weatherError?.let { weatherBox.addView(text(it, 14f, C.RED).apply { setPadding(0, dp(10), 0, 0) }) }
    }

    private fun renderBuses() {
        busBox.removeAllViews()
        live.clear()
        if (prefs.apiKey.isBlank()) {
            busBox.addView(emptyText("설정에서 공공데이터포털 API 키를 넣어주세요")); return
        }
        if (prefs.stops.isEmpty()) {
            busBox.addView(emptyText("설정에서 정류장과 버스를 추가하세요")); return
        }
        if (stops.isEmpty()) {
            busBox.addView(emptyText("버스 정보 불러오는 중…")); return
        }
        for (s in stops) {
            busBox.addView(text("${s.stopName}  ·  ${s.stop.stopNo}", 18f, C.SUB, true).apply {
                setPadding(dp(4), dp(14), 0, dp(4))
            })
            if (s.error != null) {
                busBox.addView(text(s.error, 16f, C.RED).apply { setPadding(dp(4), dp(4), 0, dp(4)) }); continue
            }
            if (s.arrivals.isEmpty()) {
                val msg = if (s.stop.routes.isEmpty()) "도착 예정 버스 없음" else "${s.stop.routes.joinToString(", ")} 도착 정보 없음"
                busBox.addView(emptyText(msg)); continue
            }
            for (a in s.arrivals) busBox.addView(busRow(a, s.fetchedAt), lp().apply { topMargin = dp(10) })
        }
    }

    private fun busRow(a: Arrival, fetchedAt: Long): View {
        val row = hbox().apply {
            background = rounded(C.CARD2, dp(18).toFloat())
            setPadding(dp(20), dp(14), dp(20), dp(14))
        }
        val routeCol = vbox()
        routeCol.addView(text(a.route, 34f, C.TEXT, true))
        if (a.dest.isNotBlank()) routeCol.addView(text(a.dest, 13f, C.DIM).apply { maxLines = 1 })
        row.addView(routeCol, lp(dp(150)))

        val first = vbox()
        val eta1 = if (a.first == null) text("도착 정보 없음", 22f, C.DIM)
        else text(Board.etaShort(a.first, fetchedAt, true), 32f, C.ACCENT, true).also {
            live += Live(it, a.first, fetchedAt, true)
        }
        first.addView(eta1)
        val sub = listOfNotNull(
            Board.etaClock(a.first, fetchedAt)?.let { "$it 도착" },
            Board.stopsText(a.first).ifBlank { null },
        ).joinToString(" · ")
        if (sub.isNotBlank()) first.addView(text(sub, 16f, C.SUB))
        row.addView(first, lp(0, weight = 1f))

        val second = vbox().apply { gravity = Gravity.END }
        second.addView(text("다음", 14f, C.DIM))
        val eta2 = text(Board.etaShort(a.second, fetchedAt), 22f, C.TEXT)
        live += Live(eta2, a.second, fetchedAt, false)
        second.addView(eta2)
        Board.stopsText(a.second).takeIf { it.isNotBlank() }?.let { second.addView(text(it, 13f, C.DIM)) }
        row.addView(second, lp(LinearLayout.LayoutParams.WRAP_CONTENT))
        return row
    }

    private fun emptyText(s: String) = text(s, 20f, C.DIM).apply { setPadding(dp(4), dp(16), 0, dp(8)) }

    private fun pixelView(sprite: Array<String>, sizeDp: Int) = ImageView(this).apply {
        val bmp = Pixel.tile(sprite, dp(sizeDp))
        setImageDrawable(BitmapDrawable(resources, bmp).apply { isFilterBitmap = false })
    }

    private fun pixelTile(sprite: Array<String>, sizeDp: Int) = pixelView(sprite, sizeDp)
}
