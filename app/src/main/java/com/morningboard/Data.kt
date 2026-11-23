package com.morningboard

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** 저장된 정류장. routes 가 비어 있으면 그 정류장의 모든 노선을 보여줌 */
data class SavedStop(
    val region: String,      // "seoul" | "gyeonggi"
    val stopNo: String,      // 정류소 번호(5자리, 네이버 지도에 보이는 번호)
    val stationId: String,   // 서울: arsId 그대로 / 경기: 내부 stationId
    val stopName: String,
    val routes: List<String>,
) {
    fun toJson() = JSONObject().apply {
        put("region", region); put("stopNo", stopNo); put("stationId", stationId)
        put("stopName", stopName); put("routes", JSONArray(routes))
    }

    companion object {
        fun fromJson(o: JSONObject) = SavedStop(
            o.getString("region"), o.getString("stopNo"), o.getString("stationId"),
            o.optString("stopName"),
            o.optJSONArray("routes")?.let { a -> (0 until a.length()).map { a.getString(it) } } ?: emptyList(),
        )
    }
}

class Prefs(ctx: Context) {
    private val sp = ctx.getSharedPreferences("board", Context.MODE_PRIVATE)

    var apiKey: String
        get() = sp.getString("apiKey", "") ?: ""
        set(v) = sp.edit().putString("apiKey", v.trim()).apply()

    var locName: String
        get() = sp.getString("locName", "") ?: ""
        set(v) = sp.edit().putString("locName", v).apply()

    var lat: Double?
        get() = sp.getString("lat", null)?.toDoubleOrNull()
        set(v) = sp.edit().putString("lat", v?.toString()).apply()

    var lon: Double?
        get() = sp.getString("lon", null)?.toDoubleOrNull()
        set(v) = sp.edit().putString("lon", v?.toString()).apply()

    var keepScreenOn: Boolean
        get() = sp.getBoolean("keepScreenOn", true)
        set(v) = sp.edit().putBoolean("keepScreenOn", v).apply()

    var stops: List<SavedStop>
        get() {
            val a = JSONArray(sp.getString("stops", "[]"))
            return (0 until a.length()).map { SavedStop.fromJson(a.getJSONObject(it)) }
        }
        set(v) = sp.edit().putString("stops", JSONArray(v.map { it.toJson() }).toString()).apply()
}

object Http {
    fun get(url: String): String {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 8000
        c.readTimeout = 10000
        c.setRequestProperty("Accept", "application/json, application/xml, */*")
        try {
            val code = c.responseCode
            val stream = if (code in 200..299) c.inputStream else (c.errorStream ?: c.inputStream)
            val body = stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            if (code !in 200..299 && body.isBlank()) throw RuntimeException("HTTP $code")
            return body
        } finally {
            c.disconnect()
        }
    }
}
