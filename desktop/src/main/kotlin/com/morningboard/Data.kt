package com.morningboard

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
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

/**
 * macOS 로컬 설정 저장소.
 * Android 의 SharedPreferences 를 ~/Library/Application Support/MorningBoard/prefs.json 으로 대체.
 */
class Prefs {
    private val file: File by lazy {
        val dir = File(System.getProperty("user.home"), "Library/Application Support/MorningBoard")
        dir.mkdirs()
        File(dir, "prefs.json")
    }

    private var data: JSONObject = load()

    private fun load(): JSONObject = try {
        if (file.exists()) JSONObject(file.readText(Charsets.UTF_8)) else JSONObject()
    } catch (e: Exception) {
        JSONObject()
    }

    private fun save() {
        runCatching { file.writeText(data.toString(2), Charsets.UTF_8) }
    }

    var apiKey: String
        get() = data.optString("apiKey", "")
        set(v) { data.put("apiKey", v.trim()); save() }

    var locName: String
        get() = data.optString("locName", "")
        set(v) { data.put("locName", v); save() }

    var lat: Double?
        get() = if (data.has("lat") && !data.isNull("lat")) data.optDouble("lat") else null
        set(v) { if (v == null) data.remove("lat") else data.put("lat", v); save() }

    var lon: Double?
        get() = if (data.has("lon") && !data.isNull("lon")) data.optDouble("lon") else null
        set(v) { if (v == null) data.remove("lon") else data.put("lon", v); save() }

    var keepScreenOn: Boolean
        get() = data.optBoolean("keepScreenOn", true)
        set(v) { data.put("keepScreenOn", v); save() }

    var alwaysOnTop: Boolean
        get() = data.optBoolean("alwaysOnTop", true)
        set(v) { data.put("alwaysOnTop", v); save() }

    var stops: List<SavedStop>
        get() {
            val a = JSONArray(data.optString("stops", "[]"))
            return (0 until a.length()).map { SavedStop.fromJson(a.getJSONObject(it)) }
        }
        set(v) { data.put("stops", JSONArray(v.map { it.toJson() }).toString()); save() }
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
