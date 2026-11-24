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

    /** 폰처럼 좁은 화면(짧은 변 600dp 미만)인지 */
    private var phone = false
    /** 화면 크기에 맞춘 배율: 태블릿 1.0, 폰은 더 작게 */
    private var k = 1f
    private fun s(v: Number) = v.toFloat() * k
    private fun d(v: Number) = dp(v.toFloat() * k)

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
        val cfg = resources.configuration
        val landscape = cfg.orientation == Configuration.ORIENTATION_LANDSCAPE
        phone = cfg.smallestScreenWidthDp < 600
        // 폰 세로: 짧은 변 기준(360~430dp)으로 0.55~0.65배, 폰 가로는 세로 공간이 더 빠듯해서 조금 더 작게
        k = when {
            !phone -> 1f
            landscape -> 0.52f
            else -> (cfg.smallestScreenWidthDp / 640f).coerceIn(0.55f, 0.66f)
        }
        // 폰 세로는 화면 전체를 한 줄로 쌓고 통째로 스크롤
        val stacked = phone && !landscape
        val root = vbox().apply {
            if (phone) setPadding(dp(16), dp(12), dp(16), dp(16))
            else setPadding(dp(28), dp(20), dp(28), dp(24))
        }

        // 헤더: 시계 / 날짜 / 갱신시각 / 설정
        val header = hbox()
        // 시계는 크게, 날짜는 옆에 붙여서 세로 공간 절약
        val clockRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.BOTTOM }
        clockText = text("--:--", if (stacked) 64f else s(104), C.TEXT, true).apply {
            includeFontPadding = false
            letterSpacing = -0.02f
        }
        dateText = text("", if (stacked) 17f else s(26).coerceAtLeast(15f), C.SUB)
        updatedText = text("", if (phone) 12f else 15f, C.DIM)
        if (stacked) {
            // 폰 세로: 시계 아래에 날짜·갱신시각을 두 줄로
            val col = vbox()
            col.addView(clockText)
            col.addView(dateText)
            col.addView(updatedText)
            header.addView(col, lp(0, weight = 1f))
        } else {
            dateText.setPadding(d(20), 0, 0, d(14))
            clockRow.addView(clockText); clockRow.addView(dateText)
            header.addView(clockRow, lp(0, weight = 1f))
            updatedText.setPadding(0, 0, dp(if (phone) 10 else 16), 0)
            header.addView(updatedText, lp(LinearLayout.LayoutParams.WRAP_CONTENT))
        }
        if (stacked) header.gravity = Gravity.TOP
        val settings = text(if (phone) "⚙" else "⚙  설정", if (phone) 22f else 20f, C.TEXT).apply {
            background = rounded(C.GLASS2, dp(16).toFloat())
            if (phone) setPadding(dp(14), dp(8), dp(14), dp(8))
            else setPadding(dp(20), dp(12), dp(20), dp(12))
            setOnClickListener { startActivity(Intent(this@MainActivity, SettingsActivity::class.java)) }
        }
        header.addView(settings, lp(LinearLayout.LayoutParams.WRAP_CONTENT))
        root.addView(header, lp())

        val body = LinearLayout(this).apply {
            orientation = if (landscape) LinearLayout.HORIZONTAL else LinearLayout.VERTICAL
        }
        val cardPad = if (phone) 16 else 26
        weatherBox = vbox().apply {
            background = rounded(C.GLASS, dp(if (phone) 20 else 28).toFloat())
            setPadding(dp(cardPad), dp(cardPad - 2), dp(cardPad), dp(cardPad - 2))
        }
        val busCard = vbox().apply {
            background = rounded(C.GLASS, dp(if (phone) 20 else 28).toFloat())
            setPadding(dp(cardPad - 2), dp(cardPad - 4), dp(cardPad - 2), dp(cardPad - 4))
        }
        val busTitle = hbox()
        busTitle.addView(text("🚌  버스 도착", if (phone) 17f else 22f, C.SUB, true), lp(0, weight = 1f))
        busTitle.addView(text("탭하면 새로고침", if (phone) 12f else 14f, C.DIM), lp(LinearLayout.LayoutParams.WRAP_CONTENT))
        busCard.addView(busTitle, lp())
        busBox = vbox()
        if (stacked) {
            busCard.addView(busBox, lp()) // 바깥 스크롤이 담당
        } else {
            val scroll = ScrollView(this).apply { addView(busBox, lp()); isVerticalScrollBarEnabled = false }
            busCard.addView(scroll, lp(h = 0, weight = 1f))
        }
        busCard.setOnClickListener { lastBusFetch = 0; refreshBuses() }
        busBox.setOnClickListener { lastBusFetch = 0; refreshBuses() }

        val gap = if (phone) 12 else 20
        when {
            landscape -> {
                body.addView(weatherBox, lp(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f))
                body.addView(busCard, lp(0, LinearLayout.LayoutParams.MATCH_PARENT, 1.25f).apply { marginStart = dp(gap) })
            }
            stacked -> {
                // 폰 세로: 버스를 먼저(아침에 제일 급한 정보), 날씨는 그 아래
                body.addView(busCard, lp())
                body.addView(weatherBox, lp().apply { topMargin = dp(gap) })
            }
            else -> {
                body.addView(weatherBox, lp())
                body.addView(busCard, lp(h = 0, weight = 1f).apply { topMargin = dp(gap) })
            }
        }
        if (stacked) root.addView(body, lp().apply { topMargin = dp(12) })
        else root.addView(body, lp(h = 0, weight = 1f).apply { topMargin = dp(16) })
        // 날씨 배경 위에 화면 내용을 얹음
        val frame = android.widget.FrameLayout(this).apply { setBackgroundColor(C.BG) }
        val bd = WeatherBackdrop(this)
        backdrop?.stop()
        backdrop = bd
        frame.addView(bd, android.widget.FrameLayout.LayoutParams(-1, -1))
        val content: View = if (stacked) ScrollView(this).apply {
            isVerticalScrollBarEnabled = false
            isFillViewport = true
            addView(root, android.widget.FrameLayout.LayoutParams(-1, -2))
        } else root
        frame.addView(content, android.widget.FrameLayout.LayoutParams(-1, -1))
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
            weatherBox.addView(text(weatherError ?: "날씨 불러오는 중…", s(22).coerceAtLeast(15f), C.SUB).apply { gravity = Gravity.CENTER })
            return
        }
        weatherBox.gravity = Gravity.NO_GRAVITY
        val land = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        // 가로 화면은 세로 공간이 빠듯해서 조금 작게, 폰은 배율 k 만큼 더 작게
        val iconDp = ((if (land) 100 else 128) * k).roundToInt()
        val tileDp = ((if (land) 92 else 112) * k).roundToInt()
        val minSp = 12f

        val upper = vbox()
        val top = hbox()
        top.addView(pixelView(WeatherApi.icon(w), iconDp), lp(dp(iconDp), dp(iconDp)))
        val col = vbox().apply { setPadding(d(24), 0, 0, 0) }
        col.addView(text("${w.temp.roundToInt()}°", s(if (land) 64 else 76), C.TEXT, true).apply { includeFontPadding = false })
        col.addView(text("${WeatherApi.desc(w.code)} · 체감 ${w.feels.roundToInt()}°", s(22).coerceAtLeast(15f), C.SUB))
        col.addView(text("최저 ${w.min.roundToInt()}° / 최고 ${w.max.roundToInt()}° · 강수 ${w.rainProb12h}%", s(18).coerceAtLeast(minSp), C.DIM))
        top.addView(col, lp(0, weight = 1f))
        upper.addView(top, lp())

        val outfit = WeatherApi.outfit(w)
        upper.addView(text("오늘의 옷차림", s(18).coerceAtLeast(13f), C.SUB).apply { setPadding(0, d(if (land) 16 else 24), 0, d(8)) })
        val row = hbox()
        for (item in outfit.items) {
            val cell = vbox().apply { gravity = Gravity.CENTER_HORIZONTAL; setPadding(0, 0, d(14), 0) }
            cell.addView(pixelTile(item.sprite, tileDp), lp(dp(tileDp), dp(tileDp)))
            cell.addView(text(item.label, s(17).coerceAtLeast(minSp), C.TEXT).apply { gravity = Gravity.CENTER; setPadding(0, d(6), 0, 0) },
                lp(dp(tileDp)))
            row.addView(cell)
        }
        upper.addView(row, lp())
        upper.addView(text(outfit.headline, s(if (land) 28 else 30).coerceAtLeast(18f), C.ACCENT, true).apply { setPadding(0, d(if (land) 10 else 16), 0, 0) })
        for (tip in outfit.tips) upper.addView(text("• $tip", s(if (land) 18 else 19).coerceAtLeast(13f), C.TEXT).apply { setPadding(0, d(4), 0, 0) })
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
        if (week.isNotEmpty()) weatherBox.addView(weekRow(week, today, land || phone), lp().apply { topMargin = d(if (land) 10 else 18) })
    }

    private val ymd = SimpleDateFormat("yyyy-MM-dd", Locale.KOREA)
    private val dow = SimpleDateFormat("E", Locale.KOREA)
    private val cal = java.util.Calendar.getInstance()

    /** 주간 날씨: 요일 / 아이콘 / 최고·최저 / 강수확률 */
    private fun weekRow(days: List<DayForecast>, today: String, compact: Boolean = false): View {
        val box = vbox()
        box.addView(text("주간 날씨", s(18).coerceAtLeast(13f), C.SUB).apply { setPadding(0, 0, 0, dp(if (compact) 6 else 8)) })
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            background = rounded(C.GLASS2, dp(18).toFloat())
            setPadding(dp(if (phone) 2 else 6), dp(if (compact) 8 else 12), dp(if (phone) 2 else 6), dp(if (compact) 8 else 12))
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
            val ic = if (phone) 30 else 48
            cell.addView(text(if (isToday) "오늘" else dow.format(date), if (phone) 13f else 16f, labelColor, true).apply { gravity = Gravity.CENTER })
            cell.addView(pixelView(WeatherApi.iconFor(d.code), ic, 0),
                lp(dp(ic), dp(ic)).apply { topMargin = dp(4) })
            // 폰은 칸이 좁아서 최고/최저를 위아래로
            val temps = (if (phone) vbox() else hbox()).apply { gravity = Gravity.CENTER; setPadding(0, dp(if (phone) 2 else 6), 0, 0) }
            temps.addView(text("${d.max.roundToInt()}°", if (phone) 13f else 17f, C.TEXT, true).apply { gravity = Gravity.CENTER })
            temps.addView(text(if (phone) "${d.min.roundToInt()}°" else " ${d.min.roundToInt()}°", if (phone) 12f else 15f, C.DIM).apply { gravity = Gravity.CENTER })
            cell.addView(temps, lp())
            val rain = if (d.rainProb >= 30) (if (phone) "${d.rainProb}%" else "☂ ${d.rainProb}%") else " "
            if (anyRain) cell.addView(text(rain, if (phone) 11f else 13f, C.BLUE).apply { gravity = Gravity.CENTER })
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
            busBox.addView(text("${s.stopName}  ·  ${s.stop.stopNo}", if (phone) 14f else 18f, C.SUB, true).apply {
                setPadding(dp(4), dp(if (phone) 10 else 14), 0, dp(4))
            })
            if (s.error != null) {
                busBox.addView(text(s.error, if (phone) 13f else 16f, C.RED).apply { setPadding(dp(4), dp(4), 0, dp(4)) }); continue
            }
            if (s.arrivals.isEmpty()) {
                val msg = if (s.stop.routes.isEmpty()) "도착 예정 버스 없음" else "${s.stop.routes.joinToString(", ")} 도착 정보 없음"
                busBox.addView(emptyText(msg)); continue
            }
            for (a in s.arrivals) busBox.addView(busRow(a, s.fetchedAt), lp().apply { topMargin = dp(if (phone) 8 else 10) })
        }
    }

    private fun busRow(a: Arrival, fetchedAt: Long): View {
        val row = hbox().apply {
            background = rounded(C.GLASS2, dp(if (phone) 14 else 18).toFloat())
            if (phone) setPadding(dp(12), dp(10), dp(12), dp(10))
            else setPadding(dp(20), dp(14), dp(20), dp(14))
        }
        val routeCol = vbox()
        routeCol.addView(text(a.route, if (phone) 22f else 34f, C.TEXT, true))
        if (a.dest.isNotBlank()) routeCol.addView(text(a.dest, if (phone) 11f else 13f, C.DIM).apply {
            maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END
        })
        row.addView(routeCol, lp(dp(if (phone) 82 else 150)).apply { marginEnd = dp(if (phone) 8 else 0) })

        val first = vbox()
        val eta1 = if (a.first == null) text("도착 정보 없음", if (phone) 15f else 22f, C.DIM)
        else text(Board.etaShort(a.first, fetchedAt, true), if (phone) 21f else 32f, C.ACCENT, true).also {
            live += Live(it, a.first, fetchedAt, true)
        }
        first.addView(eta1)
        val sub = listOfNotNull(
            Board.etaClock(a.first, fetchedAt)?.let { "$it 도착" },
            Board.stopsText(a.first).ifBlank { null },
        ).joinToString(" · ")
        if (sub.isNotBlank()) first.addView(text(sub, if (phone) 12f else 16f, C.SUB))
        row.addView(first, lp(0, weight = 1f))

        val second = vbox().apply { gravity = Gravity.END }
        second.addView(text("다음", if (phone) 11f else 14f, C.DIM))
        val eta2 = text(Board.etaShort(a.second, fetchedAt), if (phone) 15f else 22f, C.TEXT)
        live += Live(eta2, a.second, fetchedAt, false)
        second.addView(eta2)
        Board.stopsText(a.second).takeIf { it.isNotBlank() }?.let { second.addView(text(it, if (phone) 11f else 13f, C.DIM)) }
        row.addView(second, lp(LinearLayout.LayoutParams.WRAP_CONTENT))
        return row
    }

    private fun emptyText(s: String) = text(s, if (phone) 15f else 20f, C.DIM).apply { setPadding(dp(4), dp(16), 0, dp(8)) }

    private fun pixelView(sprite: Array<String>, sizeDp: Int, bg: Int = 0xFFE9EDF3.toInt()) = ImageView(this).apply {
        val bmp = Pixel.tile(sprite, dp(sizeDp), bg)
        setImageDrawable(BitmapDrawable(resources, bmp).apply { isFilterBitmap = false })
    }

    private fun pixelTile(sprite: Array<String>, sizeDp: Int) = pixelView(sprite, sizeDp)
}
