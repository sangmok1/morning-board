package com.morningboard

import android.util.Xml
import org.json.JSONArray
import org.json.JSONObject
import org.xmlpull.v1.XmlPullParser
import java.io.StringReader
import java.net.URLEncoder

/** 한 대의 도착 예정 정보. secs 가 있으면 카운트다운 가능, 없으면 text 표시 */
data class Eta(val secs: Int?, val stopsAway: Int?, val text: String)

data class Arrival(
    val route: String,
    val dest: String,
    val first: Eta?,
    val second: Eta?,
)

data class StopArrivals(
    val stop: SavedStop,
    val stopName: String,
    val arrivals: List<Arrival>,
    val error: String?,
    val fetchedAt: Long,
)

data class StationHit(val stationId: String, val name: String, val stopNo: String, val routes: List<String>, val area: String = "")

class BusError(msg: String) : Exception(msg)

object BusApi {
    private fun enc(key: String) = if (key.contains('%')) key else URLEncoder.encode(key, "UTF-8")
    private fun q(s: String) = URLEncoder.encode(s, "UTF-8")

    fun fetchStop(key: String, stop: SavedStop): StopArrivals {
        val now = System.currentTimeMillis()
        return try {
            val (name, list) = when (stop.region) {
                "seoul" -> seoul(key, stop.stationId)
                else -> stop.stopName to gyeonggi(key, stop.stationId)
            }
            val filtered = if (stop.routes.isEmpty()) list else {
                val order = stop.routes.withIndex().associate { it.value to it.index }
                list.filter { it.route in order }.sortedBy { order[it.route] }
            }
            StopArrivals(stop, name.ifBlank { stop.stopName }, filtered, null, now)
        } catch (e: Exception) {
            StopArrivals(stop, stop.stopName, emptyList(), Board.friendly(e), now)
        }
    }

    // ───────────── 서울: 정류소정보조회 서비스 getStationByUid (arsId = 5자리 정류소 번호) ─────────────
    fun seoul(key: String, arsId: String): Pair<String, List<Arrival>> {
        val url = "http://ws.bus.go.kr/api/rest/stationinfo/getStationByUid?serviceKey=${enc(key)}&arsId=${q(arsId)}"
        val items = parseXmlItems(Http.get(url), "itemList")
        val header = items.header
        val cd = header["headerCd"]
        if (cd != null && cd != "0" && cd != "4") throw BusError(header["headerMsg"] ?: "서울 API 오류($cd)")
        header["returnAuthMsg"]?.let { throw BusError("API 키 오류: $it") }
        header["errMsg"]?.let { if (it.isNotBlank() && cd == null) throw BusError("API 오류: $it") }
        var stopName = ""
        val list = items.rows.map { r ->
            if (stopName.isBlank()) stopName = r["stNm"].orEmpty()
            Arrival(
                route = r["rtNm"].orEmpty(),
                dest = r["adirection"].orEmpty().let { if (it.isNotBlank()) "$it 방면" else "" },
                first = parseSeoulMsg(r["arrmsg1"]),
                second = parseSeoulMsg(r["arrmsg2"]),
            )
        }
        return stopName to list
    }

    private val seoulRe = Regex("""(?:(\d+)분)?\s*(?:(\d+)초)?\s*후\s*\[(\d+)번째\s*전]""")

    fun parseSeoulMsg(msg: String?): Eta? {
        if (msg.isNullOrBlank()) return null
        val m = seoulRe.find(msg)
        if (m != null) {
            val min = m.groupValues[1].toIntOrNull() ?: 0
            val sec = m.groupValues[2].toIntOrNull() ?: 0
            return Eta(min * 60 + sec, m.groupValues[3].toIntOrNull(), msg)
        }
        if (msg.contains("곧 도착")) return Eta(0, null, "곧 도착")
        return Eta(null, null, msg) // 출발대기, 운행종료 등
    }

    // ───────────── 경기: 정류소 조회 v2 + 버스도착정보 v2 ─────────────
    fun ggFindStation(key: String, keyword: String): List<StationHit> {
        val url = "https://apis.data.go.kr/6410000/busstationservice/v2/getBusStationListv2" +
            "?serviceKey=${enc(key)}&keyword=${q(keyword)}&format=json"
        val body = ggBody(Http.get(url)) ?: return emptyList()
        return arr(body, "busStationList").map {
            StationHit(
                stationId = it.optString("stationId"),
                name = it.optString("stationName"),
                stopNo = it.optString("mobileNo").trim(),
                routes = emptyList(),
                area = it.optString("regionName"),
            )
        }
    }

    fun gyeonggi(key: String, stationId: String): List<Arrival> {
        val url = "https://apis.data.go.kr/6410000/busarrivalservice/v2/getBusArrivalListv2" +
            "?serviceKey=${enc(key)}&stationId=${q(stationId)}&format=json"
        val body = ggBody(Http.get(url)) ?: return emptyList()
        return arr(body, "busArrivalList").map { r ->
            Arrival(
                route = r.optString("routeName").ifBlank { r.optString("routeId") },
                dest = r.optString("routeDestName").let { if (it.isNotBlank()) "$it 방면" else "" },
                first = ggEta(r, "1"),
                second = ggEta(r, "2"),
            )
        }
    }

    private fun ggEta(r: JSONObject, n: String): Eta? {
        val sec = r.optString("predictTimeSec$n").toIntOrNull()
        val min = r.optString("predictTime$n").toIntOrNull()
        val stops = r.optString("locationNo$n").toIntOrNull()
        if (sec == null && min == null) {
            return if (n == "1" && r.optString("flag") == "WAIT") Eta(null, null, "회차지 대기") else null
        }
        val s = sec ?: (min!! * 60)
        return Eta(s, stops, "")
    }

    /** 경기 응답에서 msgBody 꺼내기 (결과 없음이면 null, 오류면 예외) */
    private fun ggBody(text: String): JSONObject? {
        val t = text.trim()
        if (!t.startsWith("{")) {
            val x = parseXmlItems(t, "__none__").header
            throw BusError("API 키 오류: " + (x["returnAuthMsg"] ?: x["errMsg"] ?: t.take(80)))
        }
        val o = JSONObject(t)
        o.optJSONObject("OpenAPI_ServiceResponse")?.let {
            val h = it.optJSONObject("cmmMsgHeader")
            throw BusError("API 키 오류: " + (h?.optString("returnAuthMsg") ?: "알 수 없음"))
        }
        val resp = o.optJSONObject("response") ?: throw BusError("알 수 없는 응답")
        val header = resp.optJSONObject("msgHeader")
        val code = header?.optString("resultCode") ?: "0"
        if (code == "4") return null
        if (code != "0") throw BusError(header?.optString("resultMessage") ?: "경기 API 오류($code)")
        return resp.optJSONObject("msgBody")
    }

    private fun arr(body: JSONObject, name: String): List<JSONObject> {
        body.optJSONArray(name)?.let { a -> return (0 until a.length()).mapNotNull { a.optJSONObject(it) } }
        body.optJSONObject(name)?.let { return listOf(it) }
        return emptyList()
    }

    // ───────────── 간단 XML 파서 ─────────────
    class XmlResult(val header: Map<String, String>, val rows: List<Map<String, String>>)

    fun parseXmlItems(xml: String, rowTag: String): XmlResult {
        val p = Xml.newPullParser()
        p.setInput(StringReader(xml))
        val header = HashMap<String, String>()
        val rows = ArrayList<Map<String, String>>()
        var cur: HashMap<String, String>? = null
        var tag: String? = null
        while (true) {
            when (p.next()) {
                XmlPullParser.END_DOCUMENT -> break
                XmlPullParser.START_TAG -> {
                    tag = p.name
                    if (p.name == rowTag) cur = HashMap()
                }
                XmlPullParser.TEXT -> {
                    val txt = p.text?.trim().orEmpty()
                    val t = tag
                    if (t != null && txt.isNotEmpty()) (cur ?: header)[t] = txt
                }
                XmlPullParser.END_TAG -> {
                    if (p.name == rowTag && cur != null) { rows += cur; cur = null }
                    tag = null
                }
            }
        }
        return XmlResult(header, rows)
    }

    @Suppress("unused")
    private fun JSONArray.objects() = (0 until length()).mapNotNull { optJSONObject(it) }
}
