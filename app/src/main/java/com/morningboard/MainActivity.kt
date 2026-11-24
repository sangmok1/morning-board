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
    private var backdrop: WeatherBackdrop? = null
    /** 미리보기용: adb shell am start ... --es mood RAIN */
    private var moodOverride: Mood? = null

    private var lastBusFetch = 0L
    private var lastWeatherFetch = 0L
    private var loadingBus = false
    private var loadingWeather = false
    private var weather: Weather? = null
    private var weatherError: String? = null
    private var stops: List<StopArrivals> = emptyList()
    private var dayKey: String? = null

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
        moodOverride = intent.getStringExtra("mood")?.let { runCatching { Mood.valueOf(it) }.getOrNull() }
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
        backdrop?.start()
    }

    override fun onPause() {
        super.onPause()
        main.removeCallbacks(ticker)
        backdrop?.stop()
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
        val dayChanged = dayKey != null && dayKey != SimpleDateFormat("yyyyMMdd", Locale.KOREA).format(Date(now))
        dayKey = SimpleDateFormat("yyyyMMdd", Locale.KOREA).format(Date(now))
        if (dayChanged) renderWeather() // 자정 지나면 주간 예보의 '오늘' 갱신
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
            setPadding(dp(28), dp(20), dp(28), dp(24))
        }

        // 헤더: 시계 / 날짜 / 갱신시각 / 설정
        val header = hbox()
        // 시계는 크게, 날짜는 옆에 붙여서 세로 공간 절약
        val clockRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.BOTTOM }
        clockText = text("--:--", 104f, C.TEXT, true).apply {
            includeFontPadding = false
            letterSpacing = -0.02f
        }
        dateText = text("", 26f, C.SUB).apply { setPadding(dp(20), 0, 0, dp(14)) }
        clockRow.addView(clockText); clockRow.addView(dateText)
        header.addView(clockRow, lp(0, weight = 1f))
        updatedText = text("", 15f, C.DIM).apply { setPadding(0, 0, dp(16), 0) }
        header.addView(updatedText, lp(LinearLayout.LayoutParams.WRAP_CONTENT))
        val settings = text("⚙  설정", 20f, C.TEXT).apply {
            background = rounded(C.GLASS2, dp(16).toFloat())
            setPadding(dp(20), dp(12), dp(20), dp(12))
            setOnClickListener { startActivity(Intent(this@MainActivity, SettingsActivity::class.java)) }
        }
        header.addView(settings, lp(LinearLayout.LayoutParams.WRAP_CONTENT))
        root.addView(header, lp())

        val body = LinearLayout(this).apply {
            orientation = if (landscape) LinearLayout.HORIZONTAL else LinearLayout.VERTICAL
        }
        weatherBox = vbox().apply {
            background = rounded(C.GLASS, dp(28).toFloat())
            setPadding(dp(28), dp(24), dp(28), dp(24))
        }
        val busCard = vbox().apply {
            background = rounded(C.GLASS, dp(28).toFloat())
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
        // 날씨 배경 위에 화면 내용을 얹음
        val frame = android.widget.FrameLayout(this).apply { setBackgroundColor(C.BG) }
        val bd = WeatherBackdrop(this)
        backdrop?.stop()
        backdrop = bd
        frame.addView(bd, android.widget.FrameLayout.LayoutParams(-1, -1))
        frame.addView(root, android.widget.FrameLayout.LayoutParams(-1, -1))
        setContentView(frame)
        updateMood()
        bd.start()
        tick()
    }

    private fun updateMood() {
        backdrop?.mood = moodOverride ?: weather?.let { moodOf(it.code, it.isDay) }
    }

    private fun renderWeather() {
        updateMood()
        weatherBox.removeAllViews()
        val w = weather
        if (w == null) {
            weatherBox.gravity = Gravity.CENTER
            weatherBox.addView(text(weatherError ?: "날씨 불러오는 중…", 22f, C.SUB).apply { gravity = Gravity.CENTER })
            return
        }
        weatherBox.gravity = Gravity.NO_GRAVITY
        val land = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        // 가로 화면은 세로 공간이 빠듯해서 조금 작게
        val iconDp = if (land) 100 else 128
        val tileDp = if (land) 92 else 112

        val upper = vbox()
        val top = hbox()
        top.addView(pixelView(WeatherApi.icon(w), iconDp), lp(dp(iconDp), dp(iconDp)))
        val col = vbox().apply { setPadding(dp(24), 0, 0, 0) }
        col.addView(text("${w.temp.roundToInt()}°", if (land) 64f else 76f, C.TEXT, true).apply { includeFontPadding = false })
        col.addView(text("${WeatherApi.desc(w.code)} · 체감 ${w.feels.roundToInt()}°", 22f, C.SUB))
        col.addView(text("최저 ${w.min.roundToInt()}° / 최고 ${w.max.roundToInt()}° · 강수 ${w.rainProb12h}%", 18f, C.DIM))
        top.addView(col, lp(0, weight = 1f))
        upper.addView(top, lp())

        val outfit = WeatherApi.outfit(w)
        upper.addView(text("오늘의 옷차림", 18f, C.SUB).apply { setPadding(0, dp(if (land) 16 else 24), 0, dp(8)) })
        val row = hbox()
        for (item in outfit.items) {
            val cell = vbox().apply { gravity = Gravity.CENTER_HORIZONTAL; setPadding(0, 0, dp(14), 0) }
            cell.addView(pixelTile(item.sprite, tileDp), lp(dp(tileDp), dp(tileDp)))
            cell.addView(text(item.label, 17f, C.TEXT).apply { gravity = Gravity.CENTER; setPadding(0, dp(6), 0, 0) },
                lp(dp(tileDp)))
            row.addView(cell)
        }
        upper.addView(row, lp())
        upper.addView(text(outfit.headline, if (land) 28f else 30f, C.ACCENT, true).apply { setPadding(0, dp(if (land) 10 else 16), 0, 0) })
        for (tip in outfit.tips) upper.addView(text("• $tip", if (land) 18f else 19f, C.TEXT).apply { setPadding(0, dp(4), 0, 0) })
        weatherError?.let { upper.addView(text(it, 14f, C.RED).apply { setPadding(0, dp(10), 0, 0) }) }

        val today = SimpleDateFormat("yyyy-MM-dd", Locale.KOREA).format(Date())
        val week = w.week.filter { it.date >= today }.take(7)
        if (land) {
            // 가로: 위쪽 내용이 넘치면 위쪽만 스크롤되고, 주간 날씨는 항상 카드 바닥에 고정
            val sv = ScrollView(this).apply { addView(upper, lp()); isVerticalScrollBarEnabled = false }
            weatherBox.addView(sv, lp(h = 0, weight = 1f))
        } else {
            weatherBox.addView(upper, lp())
        }
        if (week.isNotEmpty()) weatherBox.addView(weekRow(week, today, land), lp().apply { topMargin = dp(if (land) 10 else 18) })
    }

    private val ymd = SimpleDateFormat("yyyy-MM-dd", Locale.KOREA)
    private val dow = SimpleDateFormat("E", Locale.KOREA)
    private val cal = java.util.Calendar.getInstance()

    /** 주간 날씨: 요일 / 아이콘 / 최고·최저 / 강수확률 */
    private fun weekRow(days: List<DayForecast>, today: String, compact: Boolean = false): View {
        val box = vbox()
        box.addView(text("주간 날씨", 18f, C.SUB).apply { setPadding(0, 0, 0, dp(if (compact) 6 else 8)) })
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            background = rounded(C.GLASS2, dp(18).toFloat())
            setPadding(dp(6), dp(if (compact) 8 else 12), dp(6), dp(if (compact) 8 else 12))
        }
        val anyRain = days.any { it.rainProb >= 30 }
        for (d in days) {
            val date = ymd.parse(d.date) ?: continue
            cal.time = date
            val weekday = cal.get(java.util.Calendar.DAY_OF_WEEK)
            val isToday = d.date == today
            val labelColor = when {
                isToday -> C.ACCENT
                weekday == java.util.Calendar.SUNDAY -> C.RED
                weekday == java.util.Calendar.SATURDAY -> C.BLUE
                else -> C.SUB
            }
            val cell = vbox().apply { gravity = Gravity.CENTER_HORIZONTAL }
            cell.addView(text(if (isToday) "오늘" else dow.format(date), 16f, labelColor, true).apply { gravity = Gravity.CENTER })
            cell.addView(pixelView(WeatherApi.iconFor(d.code), 48, 0),
                lp(dp(48), dp(48)).apply { topMargin = dp(4) })
            val temps = hbox().apply { gravity = Gravity.CENTER; setPadding(0, dp(6), 0, 0) }
            temps.addView(text("${d.max.roundToInt()}°", 17f, C.TEXT, true))
            temps.addView(text(" ${d.min.roundToInt()}°", 15f, C.DIM))
            cell.addView(temps, lp())
            val rain = if (d.rainProb >= 30) "☂ ${d.rainProb}%" else " "
            if (anyRain) cell.addView(text(rain, 13f, C.BLUE).apply { gravity = Gravity.CENTER })
            row.addView(cell, lp(0, weight = 1f))
        }
        box.addView(row, lp())
        return box
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
            background = rounded(C.GLASS2, dp(18).toFloat())
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

    private fun pixelView(sprite: Array<String>, sizeDp: Int, bg: Int = 0xFFE9EDF3.toInt()) = ImageView(this).apply {
        val bmp = Pixel.tile(sprite, dp(sizeDp), bg)
        setImageDrawable(BitmapDrawable(resources, bmp).apply { isFilterBitmap = false })
    }

    private fun pixelTile(sprite: Array<String>, sizeDp: Int) = pixelView(sprite, sizeDp)
}
