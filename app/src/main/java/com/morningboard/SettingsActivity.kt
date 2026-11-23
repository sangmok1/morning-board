package com.morningboard

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.view.Gravity
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import org.json.JSONObject
import java.net.URLEncoder
import java.util.Locale

class SettingsActivity : Activity() {
    private val main = Handler(Looper.getMainLooper())
    private lateinit var prefs: Prefs
    private lateinit var locLabel: TextView
    private lateinit var locResults: LinearLayout
    private lateinit var stopList: LinearLayout
    private lateinit var lookupBox: LinearLayout
    private var region = "seoul"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        val root = vbox().apply { setPadding(dp(32), dp(24), dp(32), dp(48)); setBackgroundColor(C.BG) }
        val scroll = ScrollView(this).apply { setBackgroundColor(C.BG); addView(root) }
        setContentView(scroll)

        val top = hbox()
        top.addView(text("설정", 32f, C.TEXT, true), lp(0, weight = 1f))
        top.addView(button("완료") { finish() })
        root.addView(top)

        // ── 1. API 키 ──
        val keyCard = card(root, "① 버스 API 키 (공공데이터포털)")
        keyCard.addView(text(
            "data.go.kr 에서 아래 서비스를 '활용신청'하면 마이페이지에 인증키가 나와요. " +
                "일반 인증키(Decoding)를 붙여넣으세요. 한 키로 모든 서비스가 됩니다.\n" +
                "• 서울: 서울특별시_정류소정보조회 서비스\n" +
                "• 경기: 경기도_버스도착정보 조회 + 경기도_정류소 조회", 15f, C.SUB))
        val keyEdit = EditText(this).apply {
            setText(prefs.apiKey); hint = "인증키 붙여넣기"; isSingleLine = true
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
        }
        keyCard.addView(keyEdit, lp().apply { topMargin = dp(8) })
        keyCard.addView(button("키 저장") { prefs.apiKey = keyEdit.text.toString(); toast("저장했어요") })

        // ── 2. 날씨 위치 ──
        val locCard = card(root, "② 날씨 위치")
        locLabel = text(locText(), 18f, C.TEXT)
        locCard.addView(locLabel)
        locCard.addView(button("📍 현재 위치 사용") { useCurrentLocation() })
        val searchRow = hbox()
        val searchEdit = EditText(this).apply { hint = "동네 이름 검색 (예: 분당구 정자동)"; isSingleLine = true }
        searchRow.addView(searchEdit, lp(0, weight = 1f))
        searchRow.addView(button("검색") { searchPlace(searchEdit.text.toString()) })
        locCard.addView(searchRow)
        locResults = vbox()
        locCard.addView(locResults)

        // ── 3. 버스 정류장 ──
        val busCard = card(root, "③ 버스 정류장")
        busCard.addView(text(
            "네이버 지도에서 정류장을 누르면 5자리 번호가 있어요. 번호가 두 개(예: 19119, 35370)면 경기 번호와 서울 번호예요. " +
                "서울을 고르면 서울 번호, 경기를 고르면 경기 번호를 넣고 볼 버스를 체크해 저장하세요.", 15f, C.SUB))
        stopList = vbox()
        busCard.addView(stopList, lp().apply { topMargin = dp(8) })
        renderStops()

        busCard.addView(text("정류장 추가", 18f, C.TEXT, true).apply { setPadding(0, dp(20), 0, dp(4)) })
        val regionRow = hbox()
        val seoulBtn = text("서울", 18f)
        val ggBtn = text("경기", 18f)
        fun paintRegion() {
            for ((v, r) in listOf(seoulBtn to "seoul", ggBtn to "gyeonggi")) {
                v.background = rounded(if (region == r) C.BLUE else C.CARD2, dp(12).toFloat())
                v.setPadding(dp(24), dp(10), dp(24), dp(10))
            }
        }
        seoulBtn.setOnClickListener { region = "seoul"; paintRegion() }
        ggBtn.setOnClickListener { region = "gyeonggi"; paintRegion() }
        paintRegion()
        regionRow.addView(seoulBtn)
        regionRow.addView(ggBtn, lp(LinearLayout.LayoutParams.WRAP_CONTENT).apply { marginStart = dp(10) })
        busCard.addView(regionRow, lp().apply { topMargin = dp(6) })
        busCard.addView(text("※ 경기 정류장은 번호 대신 정류장 이름으로도 검색돼요", 13f, C.DIM).apply { setPadding(0, dp(6), 0, 0) })

        val noRow = hbox()
        val noEdit = EditText(this).apply { hint = "정류소 번호 5자리"; isSingleLine = true }
        noRow.addView(noEdit, lp(0, weight = 1f))
        noRow.addView(button("조회") { lookup(noEdit.text.toString().replace("-", "").trim()) })
        busCard.addView(noRow)
        lookupBox = vbox()
        busCard.addView(lookupBox)

        // ── 4. 화면 ──
        val screenCard = card(root, "④ 화면")
        val keep = CheckBox(this).apply {
            text = "보드 화면 켜져 있는 동안 꺼지지 않게"; isChecked = prefs.keepScreenOn; textSize = 17f
            setOnCheckedChangeListener { _, b -> prefs.keepScreenOn = b }
        }
        screenCard.addView(keep)
        screenCard.addView(text(
            "팁: 보드 앱을 띄워둔 채 태블릿을 덮으면, 다시 열 때 이 화면이 바로 보이고 자동으로 새로고침돼요. " +
                "홈 화면 위젯(길게 누르기 → 위젯 → 아침보드)도 쓸 수 있어요.", 14f, C.DIM))
    }

    private fun card(root: LinearLayout, title: String): LinearLayout {
        val c = vbox().apply {
            background = rounded(C.CARD, dp(20).toFloat())
            setPadding(dp(24), dp(20), dp(24), dp(20))
        }
        c.addView(text(title, 20f, C.TEXT, true).apply { setPadding(0, 0, 0, dp(8)) })
        root.addView(c, lp().apply { topMargin = dp(20) })
        return c
    }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()

    private fun locText() = if (prefs.lat == null) "아직 위치가 없어요"
    else "현재: ${prefs.locName.ifBlank { "지정 위치" }}  (${"%.3f".format(prefs.lat)}, ${"%.3f".format(prefs.lon)})"

    private fun setLocation(name: String, lat: Double, lon: Double) {
        prefs.locName = name; prefs.lat = lat; prefs.lon = lon
        locLabel.text = locText()
        locResults.removeAllViews()
        toast("위치 저장: $name")
    }

    // ───────── 위치 ─────────
    private fun useCurrentLocation() {
        if (checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION), 1)
            return
        }
        fetchLocation()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) fetchLocation()
        else toast("위치 권한이 없어요. 이름으로 검색해 주세요")
    }

    @SuppressLint("MissingPermission")
    private fun fetchLocation() {
        val lm = getSystemService(LocationManager::class.java)
        val last = lm.getProviders(true).mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }
            .maxByOrNull { it.time }
        if (last != null && System.currentTimeMillis() - last.time < 6 * 3600_000L) { onLocation(last); return }
        val provider = when {
            lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
            lm.isProviderEnabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
            else -> null
        }
        if (provider == null) {
            if (last != null) onLocation(last) else toast("위치 서비스를 켜주세요")
            return
        }
        toast("위치 찾는 중…")
        if (Build.VERSION.SDK_INT >= 30) {
            lm.getCurrentLocation(provider, null, mainExecutor) { loc ->
                if (loc != null) onLocation(loc) else if (last != null) onLocation(last) else toast("위치를 못 찾았어요")
            }
        } else {
            @Suppress("DEPRECATION")
            lm.requestSingleUpdate(provider, { loc -> onLocation(loc) }, Looper.getMainLooper())
        }
    }

    private fun onLocation(loc: Location) {
        Thread {
            val name = runCatching {
                @Suppress("DEPRECATION")
                Geocoder(this, Locale.KOREA).getFromLocation(loc.latitude, loc.longitude, 1)?.firstOrNull()?.let {
                    listOfNotNull(it.subLocality ?: it.locality, it.thoroughfare).joinToString(" ")
                }
            }.getOrNull()
            main.post { setLocation(name?.ifBlank { null } ?: "현재 위치", loc.latitude, loc.longitude) }
        }.start()
    }

    private fun searchPlace(query: String) {
        if (query.isBlank()) return
        locResults.removeAllViews()
        locResults.addView(text("검색 중…", 15f, C.DIM))
        Thread {
            val hits = mutableListOf<Triple<String, Double, Double>>()
            // 1) 안드로이드 기본 Geocoder (한국어 주소에 강함)
            runCatching {
                @Suppress("DEPRECATION")
                Geocoder(this, Locale.KOREA).getFromLocationName(query, 5)?.forEach {
                    val label = (0..it.maxAddressLineIndex).joinToString(" ") { i -> it.getAddressLine(i) }
                        .removePrefix("대한민국 ")
                    hits += Triple(label, it.latitude, it.longitude)
                }
            }
            // 2) 보조: Open-Meteo 지명 검색
            if (hits.isEmpty()) runCatching {
                val o = JSONObject(Http.get("https://geocoding-api.open-meteo.com/v1/search?name=" +
                    URLEncoder.encode(query, "UTF-8") + "&count=8&language=ko&countryCode=KR"))
                val a = o.optJSONArray("results")
                if (a != null) for (i in 0 until a.length()) {
                    val r = a.getJSONObject(i)
                    val label = listOf(r.optString("name"), r.optString("admin2"), r.optString("admin1"))
                        .filter { it.isNotBlank() }.joinToString(", ")
                    hits += Triple(label, r.getDouble("latitude"), r.getDouble("longitude"))
                }
            }
            main.post {
                locResults.removeAllViews()
                if (hits.isEmpty()) locResults.addView(text("결과가 없어요. 다른 이름으로 검색하거나 현재 위치를 써보세요", 15f, C.DIM))
                for ((label, lat, lon) in hits) {
                    locResults.addView(text("→ $label", 17f, C.BLUE).apply {
                        setPadding(0, dp(10), 0, dp(10))
                        setOnClickListener { setLocation(query.trim(), lat, lon) }
                    })
                }
            }
        }.start()
    }

    // ───────── 정류장 ─────────
    private fun renderStops() {
        stopList.removeAllViews()
        val stops = prefs.stops
        if (stops.isEmpty()) {
            stopList.addView(text("저장된 정류장이 없어요", 16f, C.DIM)); return
        }
        stops.forEachIndexed { i, s ->
            val row = hbox().apply {
                background = rounded(C.CARD2, dp(14).toFloat())
                setPadding(dp(16), dp(12), dp(12), dp(12))
            }
            val col = vbox()
            col.addView(text("${s.stopName} (${s.stopNo}) · ${if (s.region == "seoul") "서울" else "경기"}", 17f, C.TEXT, true))
            col.addView(text(if (s.routes.isEmpty()) "모든 버스" else s.routes.joinToString(", "), 15f, C.ACCENT))
            row.addView(col, lp(0, weight = 1f))
            if (i > 0) row.addView(smallBtn("▲") { move(i, -1) })
            row.addView(smallBtn("삭제") {
                prefs.stops = prefs.stops.filterIndexed { j, _ -> j != i }; renderStops()
            })
            stopList.addView(row, lp().apply { topMargin = dp(8) })
        }
    }

    private fun move(i: Int, d: Int) {
        val l = prefs.stops.toMutableList()
        val t = l[i]; l[i] = l[i + d]; l[i + d] = t
        prefs.stops = l; renderStops()
    }

    private fun smallBtn(label: String, onClick: () -> Unit) = text(label, 15f, C.TEXT).apply {
        background = rounded(C.CARD, dp(10).toFloat())
        setPadding(dp(14), dp(8), dp(14), dp(8))
        setOnClickListener { onClick() }
        layoutParams = lp(LinearLayout.LayoutParams.WRAP_CONTENT).apply { marginStart = dp(8) }
    }

    private fun lookup(input: String) {
        val key = prefs.apiKey
        lookupBox.removeAllViews()
        if (key.isBlank()) { lookupBox.addView(text("먼저 ① API 키를 저장해 주세요", 16f, C.RED)); return }
        if (input.isBlank()) return
        lookupBox.addView(text("조회 중…", 15f, C.DIM))
        val r = region
        Thread {
            try {
                if (r == "seoul") {
                    val (name, list) = BusApi.seoul(key, input)
                    main.post {
                        if (list.isEmpty()) showError("정류장을 찾지 못했어요. 번호를 확인하거나 '경기'로 바꿔보세요")
                        else showRoutePicker(r, input, input, name, list.map { it.route })
                    }
                } else {
                    val hits = BusApi.ggFindStation(key, input)
                    val exact = hits.filter { it.stopNo == input }
                    val pick = if (exact.size == 1) exact else hits
                    if (pick.size == 1) {
                        val h = pick[0]
                        val routes = BusApi.gyeonggi(key, h.stationId).map { it.route }
                        main.post { showRoutePicker(r, h.stopNo.ifBlank { input }, h.stationId, h.name, routes) }
                    } else main.post { showStationChoices(pick) }
                }
            } catch (e: Exception) {
                main.post { showError(Board.friendly(e, "조회 실패")) }
            }
        }.start()
    }

    private fun showError(msg: String) {
        lookupBox.removeAllViews()
        lookupBox.addView(text(msg, 16f, C.RED).apply { setPadding(0, dp(8), 0, 0) })
    }

    private fun showStationChoices(hits: List<StationHit>) {
        lookupBox.removeAllViews()
        if (hits.isEmpty()) { showError("정류장을 찾지 못했어요. 번호/이름을 확인하거나 '서울'로 바꿔보세요"); return }
        lookupBox.addView(text("정류장을 골라주세요", 16f, C.SUB).apply { setPadding(0, dp(8), 0, 0) })
        for (h in hits.take(15)) {
            lookupBox.addView(text("→ ${if (h.area.isNotBlank()) "[${h.area}] " else ""}${h.name} (${h.stopNo.ifBlank { "번호 없음" }})", 17f, C.BLUE).apply {
                setPadding(0, dp(10), 0, dp(10))
                setOnClickListener {
                    lookupBox.removeAllViews(); lookupBox.addView(text("노선 불러오는 중…", 15f, C.DIM))
                    Thread {
                        val routes = runCatching { BusApi.gyeonggi(prefs.apiKey, h.stationId).map { it.route } }
                        main.post {
                            routes.onSuccess { showRoutePicker("gyeonggi", h.stopNo, h.stationId, h.name, it) }
                                .onFailure { showError(it.message ?: "조회 실패") }
                        }
                    }.start()
                }
            })
        }
    }

    private fun showRoutePicker(region: String, stopNo: String, stationId: String, name: String, routes: List<String>) {
        lookupBox.removeAllViews()
        lookupBox.addView(text("✔ $name ($stopNo)", 19f, C.TEXT, true).apply { setPadding(0, dp(12), 0, dp(4)) })
        val boxes = mutableListOf<CheckBox>()
        val distinct = routes.filter { it.isNotBlank() }.distinct()
        if (distinct.isEmpty()) {
            lookupBox.addView(text("지금 도착 정보가 있는 버스가 없어요. 아래에 버스 번호를 직접 적어주세요", 15f, C.DIM))
        } else {
            lookupBox.addView(text("볼 버스를 체크하세요 (아무것도 안 고르면 전부 표시)", 15f, C.SUB))
            if (region == "gyeonggi") lookupBox.addView(text("※ 경기는 지금 운행 중인 노선만 보여요. 없는 건 아래에 직접 적어주세요", 13f, C.DIM))
            val grid = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            var line: LinearLayout? = null
            distinct.forEachIndexed { i, rt ->
                if (i % 4 == 0) { line = hbox(); grid.addView(line) }
                val cb = CheckBox(this).apply { text = rt; textSize = 18f }
                boxes += cb
                line!!.addView(cb, lp(0, weight = 1f))
            }
            val rem = distinct.size % 4
            if (rem != 0) repeat(4 - rem) { line!!.addView(TextView(this), lp(0, weight = 1f)) }
            lookupBox.addView(grid)
        }
        val manual = EditText(this).apply { hint = "직접 추가할 버스 번호 (쉼표로 구분, 예: 9401, M4102)"; isSingleLine = true }
        lookupBox.addView(manual)
        lookupBox.addView(button("이 정류장 저장") {
            val chosen = boxes.filter { it.isChecked }.map { it.text.toString() } +
                manual.text.toString().split(",").map { it.trim() }.filter { it.isNotEmpty() }
            val stop = SavedStop(region, stopNo, stationId, name, chosen.distinct())
            prefs.stops = prefs.stops.filterNot { it.region == region && it.stationId == stationId } + stop
            renderStops()
            lookupBox.removeAllViews()
            lookupBox.addView(text("저장했어요 ✓", 16f, C.ACCENT).apply { gravity = Gravity.START })
        })
    }
}
