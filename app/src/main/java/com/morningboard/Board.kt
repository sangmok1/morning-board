package com.morningboard

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Callable
import java.util.concurrent.Executors

data class Snapshot(
    val weather: Weather?,
    val weatherError: String?,
    val stops: List<StopArrivals>,
)

/** 날씨 + 버스 한 번에 불러오기 (백그라운드 스레드에서 호출) */
object Board {
    private val pool = Executors.newFixedThreadPool(4)

    fun loadWeather(ctx: Context): Pair<Weather?, String?> {
        val p = Prefs(ctx)
        val lat = p.lat
        val lon = p.lon
        if (lat == null || lon == null) return null to "설정에서 위치를 정해주세요"
        return try {
            WeatherApi.fetch(lat, lon) to null
        } catch (e: Exception) {
            null to friendly(e, "날씨를 못 불러왔어요")
        }
    }

    fun loadBuses(ctx: Context): List<StopArrivals> {
        val p = Prefs(ctx)
        val key = p.apiKey
        val stops = p.stops
        if (key.isBlank() || stops.isEmpty()) return emptyList()
        return stops.map { s -> pool.submit(Callable { BusApi.fetchStop(key, s) }) }.map { it.get() }
    }

    fun loadAll(ctx: Context): Snapshot {
        val w = pool.submit(Callable { loadWeather(ctx) })
        val b = loadBuses(ctx)
        val (weather, err) = w.get()
        return Snapshot(weather, err, b)
    }

    const val OFFLINE = "인터넷 연결 대기 중… 곧 다시 시도해요"

    /** 네트워크 계열 에러는 한글 문구로 */
    fun friendly(e: Exception, prefix: String = ""): String = when (e) {
        is BusError -> e.message ?: "오류"
        is java.net.UnknownHostException, is java.net.ConnectException,
        is java.net.SocketTimeoutException, is java.net.NoRouteToHostException -> OFFLINE
        is java.io.IOException -> OFFLINE
        else -> listOf(prefix, e.message ?: e.javaClass.simpleName).filter { it.isNotBlank() }.joinToString(": ")
    }

    private val hm = SimpleDateFormat("HH:mm", Locale.KOREA)
    fun clock(ms: Long): String = hm.format(Date(ms))

    /** 남은 초 (가져온 시각 이후 경과시간 반영) */
    fun remaining(eta: Eta, fetchedAt: Long, now: Long = System.currentTimeMillis()): Int? =
        eta.secs?.let { it - ((now - fetchedAt) / 1000).toInt() }

    /** 짧은 표기: "곧 도착", "7분", "운행종료" 등 */
    fun etaShort(eta: Eta?, fetchedAt: Long, withSec: Boolean = false, now: Long = System.currentTimeMillis()): String {
        if (eta == null) return "—"
        val r = remaining(eta, fetchedAt, now) ?: return eta.text.ifBlank { "정보 없음" }
        if (r <= 60) return "곧 도착"
        val m = r / 60
        val s = r % 60
        return if (withSec) "${m}분 ${"%02d".format(s)}초" else "${m}분"
    }

    fun etaClock(eta: Eta?, fetchedAt: Long): String? {
        val s = eta?.secs ?: return null
        return clock(fetchedAt + s * 1000L)
    }

    fun stopsText(eta: Eta?): String = eta?.stopsAway?.let { if (it <= 0) "" else "${it}정거장 전" } ?: ""
}
