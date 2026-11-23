package com.morningboard

import org.json.JSONObject
import kotlin.math.roundToInt

data class Weather(
    val temp: Double,
    val feels: Double,
    val code: Int,
    val isDay: Boolean,
    val min: Double,
    val max: Double,
    val rainProb12h: Int,     // 앞으로 12시간 최대 강수확률
    val rainSoon: Boolean,    // 앞으로 12시간 안에 비/눈 예보
    val fetchedAt: Long,
)

data class OutfitItem(val sprite: Array<String>, val label: String)

data class Outfit(val items: List<OutfitItem>, val headline: String, val tips: List<String>)

object WeatherApi {
    /** Open-Meteo (무료, 키 불필요) */
    fun fetch(lat: Double, lon: Double): Weather {
        val url = "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon" +
            "&current=temperature_2m,apparent_temperature,weather_code,is_day" +
            "&hourly=precipitation_probability,weather_code" +
            "&daily=temperature_2m_max,temperature_2m_min" +
            "&forecast_hours=12&forecast_days=1&timezone=Asia%2FSeoul"
        val o = JSONObject(Http.get(url))
        val cur = o.getJSONObject("current")
        val daily = o.getJSONObject("daily")
        val hourly = o.getJSONObject("hourly")
        val probs = hourly.getJSONArray("precipitation_probability")
        val codes = hourly.getJSONArray("weather_code")
        var maxProb = 0
        var wetSoon = false
        for (i in 0 until probs.length()) {
            val p = probs.optInt(i, 0)
            maxProb = maxOf(maxProb, p)
            if (isWet(codes.optInt(i, 0)) && p >= 40) wetSoon = true
        }
        val code = cur.getInt("weather_code")
        return Weather(
            temp = cur.getDouble("temperature_2m"),
            feels = cur.getDouble("apparent_temperature"),
            code = code,
            isDay = cur.optInt("is_day", 1) == 1,
            min = daily.getJSONArray("temperature_2m_min").getDouble(0),
            max = daily.getJSONArray("temperature_2m_max").getDouble(0),
            rainProb12h = maxProb,
            rainSoon = isWet(code) || wetSoon || maxProb >= 50,
            fetchedAt = System.currentTimeMillis(),
        )
    }

    fun isWet(code: Int) = code in 51..67 || code in 71..77 || code in 80..86 || code in 95..99
    fun isSnow(code: Int) = code in 71..77 || code == 85 || code == 86

    fun icon(w: Weather): Array<String> = when (w.code) {
        0, 1 -> if (w.isDay) Pixel.SUN else Pixel.MOON
        2 -> if (w.isDay) Pixel.PARTLY else Pixel.CLOUD
        3 -> Pixel.CLOUD
        45, 48 -> Pixel.FOG
        in 71..77, 85, 86 -> Pixel.SNOW
        in 95..99 -> Pixel.THUNDER
        in 51..67, in 80..82 -> Pixel.RAIN
        else -> Pixel.CLOUD
    }

    fun desc(code: Int): String = when (code) {
        0 -> "맑음"
        1 -> "대체로 맑음"
        2 -> "구름 조금"
        3 -> "흐림"
        45, 48 -> "안개"
        in 51..57 -> "이슬비"
        in 61..67 -> "비"
        in 71..77 -> "눈"
        in 80..82 -> "소나기"
        85, 86 -> "소낙눈"
        in 95..99 -> "뇌우"
        else -> "—"
    }

    /** 체감온도 기준 옷차림 */
    fun outfit(w: Weather): Outfit {
        val t = w.feels
        val items = mutableListOf<OutfitItem>()
        val headline: String
        when {
            t <= 0 -> { items += OutfitItem(Pixel.LONG_PADDING, "롱패딩"); headline = "롱패딩 입으세요" }
            t <= 6 -> { items += OutfitItem(Pixel.SHORT_PADDING, "숏패딩"); headline = "숏패딩이면 OK" }
            t <= 12 -> { items += OutfitItem(Pixel.HOODIE, "후디·경량패딩"); headline = "후디나 경량패딩" }
            t <= 19 -> {
                items += OutfitItem(Pixel.SWEATSHIRT, "맨투맨·가디건")
                items += OutfitItem(Pixel.PANTS, "긴바지")
                headline = "맨투맨·가디건"
            }
            t < 25 -> {
                items += OutfitItem(Pixel.TSHIRT, "반팔")
                items += OutfitItem(Pixel.PANTS, "긴바지")
                headline = "반팔에 긴바지"
            }
            else -> {
                items += OutfitItem(Pixel.TSHIRT, "반팔")
                items += OutfitItem(Pixel.SHORTS, "반바지")
                headline = "반팔·반바지"
            }
        }
        val tips = mutableListOf<String>()
        if (w.rainSoon) {
            items += OutfitItem(Pixel.UMBRELLA, "우산")
            tips += if (isSnow(w.code)) "눈 와요, 우산 챙기세요" else "우산 챙기세요 (최대 강수확률 ${w.rainProb12h}%)"
        }
        if (w.max - w.min >= 10) tips += "일교차 ${(w.max - w.min).roundToInt()}° — 겉옷 하나 더"
        return Outfit(items, headline, tips)
    }
}
