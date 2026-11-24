package com.morningboard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

/** 보드 화면 전체 상태 + 네트워크 갱신 로직 (MainActivity 이식) */
class BoardModel(val prefs: Prefs) {
    var weather by mutableStateOf<Weather?>(null)
    var weatherError by mutableStateOf<String?>(null)
    var stops by mutableStateOf<List<StopArrivals>>(emptyList())
    var nowMs by mutableLongStateOf(System.currentTimeMillis())
    var updatedText by mutableStateOf("")

    private var lastBusFetch = 0L
    private var lastWeatherFetch = 0L
    private var loadingBus = false
    private var loadingWeather = false

    val mood: Mood? get() = weather?.let { moodOf(it.code, it.isDay) }

    fun forceRefresh(scope: CoroutineScope) { lastBusFetch = 0; lastWeatherFetch = 0; refreshBuses(scope); refreshWeather(scope) }

    fun refreshWeather(scope: CoroutineScope) {
        if (loadingWeather) return
        loadingWeather = true
        lastWeatherFetch = System.currentTimeMillis()
        scope.launch {
            val (w, err) = withContext(Dispatchers.IO) { Board.loadWeather(prefs) }
            loadingWeather = false
            if (w != null || weather == null) { weather = w; weatherError = err } else weatherError = err
            if (err == Board.OFFLINE) {
                val interval = if (weather == null) 60_000L else 15 * 60_000L
                lastWeatherFetch = System.currentTimeMillis() - interval + 5_000
            }
        }
    }

    fun refreshBuses(scope: CoroutineScope) {
        if (loadingBus) return
        loadingBus = true
        lastBusFetch = System.currentTimeMillis()
        scope.launch {
            val result = withContext(Dispatchers.IO) { Board.loadBuses(prefs) }
            loadingBus = false
            val offline = result.isNotEmpty() && result.all { it.error == Board.OFFLINE }
            if (offline) {
                lastBusFetch = System.currentTimeMillis() - 25_000
                if (stops.isEmpty() || stops.all { it.error != null }) stops = result
                updatedText = "인터넷 연결 대기 중…"
            } else {
                stops = result
                updatedText = "버스 ${Board.clock(System.currentTimeMillis())} 갱신"
            }
        }
    }

    fun tick(scope: CoroutineScope) {
        val now = System.currentTimeMillis()
        nowMs = now
        if (now - lastBusFetch > 30_000) refreshBuses(scope)
        val wInterval = if (weather == null) 60_000L else 15 * 60_000L
        if (now - lastWeatherFetch > wInterval) refreshWeather(scope)
    }
}

@Composable
private fun T(
    text: String,
    size: TextUnit,
    color: Color = C.TEXT,
    bold: Boolean = false,
    modifier: Modifier = Modifier,
    maxLines: Int = Int.MAX_VALUE,
) = Text(
    text = text,
    modifier = modifier,
    color = color,
    fontSize = size,
    fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
    lineHeight = size * 1.15f,
    maxLines = maxLines,
    overflow = TextOverflow.Ellipsis,
)

@Composable
fun App(prefs: Prefs, alwaysOnTop: Boolean, onToggleTop: () -> Unit, onMinimize: () -> Unit, onClose: () -> Unit, dragArea: @Composable (@Composable () -> Unit) -> Unit) {
    val model = remember { BoardModel(prefs) }
    val scope = rememberCoroutineScope()
    var showSettings by remember { mutableStateOf(false) }
    var settingsEpoch by remember { mutableStateOf(0) } // 설정 저장 후 보드 강제 반영

    // 매초 tick
    LaunchedEffect(Unit) {
        model.forceRefresh(scope)
        while (true) { model.tick(scope); delay(1000) }
    }

    Box(Modifier.fillMaxSize().background(C.BG)) {
        WeatherBackdrop(model.mood, LocalDensity.current.density, Modifier.fillMaxSize())

        Column(Modifier.fillMaxSize()) {
            // ── 상단 바 (드래그 영역) ──
            dragArea {
                Row(
                    Modifier.fillMaxWidth().height(38.dp).padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    T("☀  아침보드", 14.sp, C.DIM)
                    Spacer(Modifier.weight(1f))
                    TopBtn(if (alwaysOnTop) "📌" else "📍", if (alwaysOnTop) "항상 위 (켜짐)" else "항상 위 (꺼짐)") { onToggleTop() }
                    TopBtn(if (showSettings) "🖥" else "⚙", if (showSettings) "보드로" else "설정") {
                        if (showSettings) { settingsEpoch++; model.forceRefresh(scope) }
                        showSettings = !showSettings
                    }
                    TopBtn("—", "최소화") { onMinimize() }
                    TopBtn("✕", "닫기") { onClose() }
                }
            }

            if (showSettings) {
                SettingsScreen(prefs) { settingsEpoch++ }
            } else {
                androidx.compose.runtime.key(settingsEpoch) { BoardContent(model, scope) }
            }
        }
    }
}

@Composable
private fun TopBtn(label: String, tip: String, onClick: () -> Unit) {
    Box(
        Modifier.padding(start = 6.dp).clip(RoundedCornerShape(8.dp)).clickable { onClick() }
            .background(C.GLASS2).size(26.dp),
        contentAlignment = Alignment.Center,
    ) { T(label, 13.sp, C.TEXT) }
}

@Composable
private fun BoardContent(model: BoardModel, scope: CoroutineScope) {
    androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxSize().padding(start = 24.dp, end = 24.dp, bottom = 22.dp)) {
        val landscape = maxWidth > maxHeight
        Column(Modifier.fillMaxSize()) {
            Header(model)
            Spacer(Modifier.height(14.dp))
            if (landscape) {
                Row(Modifier.fillMaxSize()) {
                    Card(Modifier.weight(1f).fillMaxHeight()) { WeatherPane(model, compact = true) }
                    Spacer(Modifier.width(18.dp))
                    Card(Modifier.weight(1.25f).fillMaxHeight(), onClick = { model.refreshBuses(scope) }) { BusPane(model) }
                }
            } else {
                Card(Modifier.fillMaxWidth()) { WeatherPane(model, compact = false) }
                Spacer(Modifier.height(16.dp))
                Card(Modifier.fillMaxWidth().weight(1f), onClick = { model.refreshBuses(scope) }) { BusPane(model) }
            }
        }
    }
}

@Composable
private fun Header(model: BoardModel) {
    val now = Date(model.nowMs)
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
        Row(Modifier.weight(1f), verticalAlignment = Alignment.Bottom) {
            T(SimpleDateFormat("HH:mm", Locale.KOREA).format(now), 92.sp, C.TEXT, bold = true)
            Spacer(Modifier.width(18.dp))
            T(SimpleDateFormat("M월 d일 EEEE", Locale.KOREA).format(now), 24.sp, C.SUB, modifier = Modifier.padding(bottom = 16.dp))
        }
        T(model.updatedText, 14.sp, C.DIM, modifier = Modifier.padding(bottom = 10.dp))
    }
}

@Composable
private fun Card(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, content: @Composable () -> Unit) {
    val base = modifier.clip(RoundedCornerShape(26.dp)).background(C.GLASS)
    val m = if (onClick != null) base.clickable { onClick() } else base
    Box(m.padding(22.dp)) { content() }
}

@Composable
private fun WeatherPane(model: BoardModel, compact: Boolean) {
    val w = model.weather
    val scroll = rememberScrollState()
    if (w == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            T(model.weatherError ?: "날씨 불러오는 중…", 20.sp, C.SUB)
        }
        return
    }
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.weight(1f, fill = false).verticalScroll(scroll)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PixelSprite(WeatherApi.icon(w), if (compact) 100.dp else 128.dp)
                Spacer(Modifier.width(22.dp))
                Column {
                    T("${w.temp.roundToInt()}°", if (compact) 60.sp else 72.sp, C.TEXT, bold = true)
                    T("${WeatherApi.desc(w.code)} · 체감 ${w.feels.roundToInt()}°", 20.sp, C.SUB)
                    T("최저 ${w.min.roundToInt()}° / 최고 ${w.max.roundToInt()}° · 강수 ${w.rainProb12h}%", 16.sp, C.DIM)
                }
            }
            val outfit = WeatherApi.outfit(w)
            Spacer(Modifier.height(if (compact) 14.dp else 22.dp))
            T("오늘의 옷차림", 16.sp, C.SUB)
            Spacer(Modifier.height(8.dp))
            Row {
                for (item in outfit.items) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(end = 14.dp)) {
                        PixelSprite(item.sprite, if (compact) 88.dp else 104.dp)
                        Spacer(Modifier.height(6.dp))
                        T(item.label, 15.sp, C.TEXT)
                    }
                }
            }
            Spacer(Modifier.height(if (compact) 10.dp else 16.dp))
            T(outfit.headline, if (compact) 26.sp else 30.sp, C.ACCENT, bold = true)
            for (tip in outfit.tips) T("• $tip", 18.sp, C.TEXT, modifier = Modifier.padding(top = 4.dp))
            model.weatherError?.let { T(it, 14.sp, C.RED, modifier = Modifier.padding(top = 10.dp)) }
        }
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.KOREA).format(Date())
        val week = w.week.filter { it.date >= today }.take(7)
        if (week.isNotEmpty()) {
            Spacer(Modifier.height(14.dp))
            WeekRow(week, today)
        }
    }
}

private val ymd = SimpleDateFormat("yyyy-MM-dd", Locale.KOREA)
private val dow = SimpleDateFormat("E", Locale.KOREA)

@Composable
private fun WeekRow(days: List<DayForecast>, today: String) {
    Column {
        T("주간 날씨", 16.sp, C.SUB)
        Spacer(Modifier.height(8.dp))
        val anyRain = days.any { it.rainProb >= 30 }
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(C.GLASS2).padding(vertical = 12.dp, horizontal = 6.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            val cal = Calendar.getInstance()
            for (d in days) {
                val date = ymd.parse(d.date) ?: continue
                cal.time = date
                val weekday = cal.get(Calendar.DAY_OF_WEEK)
                val isToday = d.date == today
                val labelColor = when {
                    isToday -> C.ACCENT
                    weekday == Calendar.SUNDAY -> C.RED
                    weekday == Calendar.SATURDAY -> C.BLUE
                    else -> C.SUB
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                    T(if (isToday) "오늘" else dow.format(date), 16.sp, labelColor, bold = true)
                    Spacer(Modifier.height(4.dp))
                    PixelSprite(WeatherApi.iconFor(d.code), 46.dp, tileBg = null)
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.Bottom) {
                        T("${d.max.roundToInt()}°", 17.sp, C.TEXT, bold = true)
                        T(" ${d.min.roundToInt()}°", 15.sp, C.DIM)
                    }
                    if (anyRain) T(if (d.rainProb >= 30) "☂ ${d.rainProb}%" else " ", 13.sp, C.BLUE)
                }
            }
        }
    }
}

@Composable
private fun BusPane(model: BoardModel) {
    val scroll = rememberScrollState()
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            T("🚌  버스 도착", 22.sp, C.SUB, bold = true, modifier = Modifier.weight(1f))
            T("탭하면 새로고침", 14.sp, C.DIM)
        }
        Spacer(Modifier.height(6.dp))
        Column(Modifier.weight(1f).verticalScroll(scroll)) {
            when {
                model.prefs.apiKey.isBlank() -> EmptyLine("설정에서 공공데이터포털 API 키를 넣어주세요")
                model.prefs.stops.isEmpty() -> EmptyLine("설정에서 정류장과 버스를 추가하세요")
                model.stops.isEmpty() -> EmptyLine("버스 정보 불러오는 중…")
                else -> for (s in model.stops) {
                    T("${s.stopName}  ·  ${s.stop.stopNo}", 18.sp, C.SUB, bold = true, modifier = Modifier.padding(top = 14.dp, bottom = 4.dp))
                    when {
                        s.error != null -> T(s.error, 16.sp, C.RED, modifier = Modifier.padding(vertical = 4.dp))
                        s.arrivals.isEmpty() -> EmptyLine(
                            if (s.stop.routes.isEmpty()) "도착 예정 버스 없음" else "${s.stop.routes.joinToString(", ")} 도착 정보 없음"
                        )
                        else -> for (a in s.arrivals) { Spacer(Modifier.height(10.dp)); BusRow(a, s.fetchedAt, model.nowMs) }
                    }
                }
            }
        }
    }
}

@Composable
private fun BusRow(a: Arrival, fetchedAt: Long, now: Long) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(C.GLASS2).padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.width(150.dp)) {
            T(a.route, 34.sp, C.TEXT, bold = true, maxLines = 1)
            if (a.dest.isNotBlank()) T(a.dest, 13.sp, C.DIM, maxLines = 1)
        }
        Column(Modifier.weight(1f)) {
            if (a.first == null) T("도착 정보 없음", 22.sp, C.DIM)
            else T(Board.etaShort(a.first, fetchedAt, true, now), 32.sp, C.ACCENT, bold = true)
            val sub = listOfNotNull(
                Board.etaClock(a.first, fetchedAt)?.let { "$it 도착" },
                Board.stopsText(a.first).ifBlank { null },
            ).joinToString(" · ")
            if (sub.isNotBlank()) T(sub, 16.sp, C.SUB)
        }
        Column(horizontalAlignment = Alignment.End) {
            T("다음", 14.sp, C.DIM)
            T(Board.etaShort(a.second, fetchedAt, false, now), 22.sp, C.TEXT)
            Board.stopsText(a.second).takeIf { it.isNotBlank() }?.let { T(it, 13.sp, C.DIM) }
        }
    }
}

@Composable
private fun EmptyLine(s: String) = T(s, 20.sp, C.DIM, modifier = Modifier.padding(top = 16.dp, bottom = 8.dp))
