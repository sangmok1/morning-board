package com.morningboard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URLEncoder

private data class Place(val label: String, val lat: Double, val lon: Double)
private data class RoutePicker(val region: String, val stopNo: String, val stationId: String, val name: String, val routes: List<String>)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(prefs: Prefs, onChanged: () -> Unit) {
    val scope = rememberCoroutineScope()
    val scroll = rememberScrollState()

    var apiKey by remember { mutableStateOf(prefs.apiKey) }
    var locLabel by remember { mutableStateOf(locText(prefs)) }
    var placeQuery by remember { mutableStateOf("") }
    val places = remember { mutableStateListOf<Place>() }
    var searchMsg by remember { mutableStateOf<String?>(null) }

    var region by remember { mutableStateOf("seoul") }
    var stopNoInput by remember { mutableStateOf("") }
    var lookupMsg by remember { mutableStateOf<String?>(null) }
    val stationChoices = remember { mutableStateListOf<StationHit>() }
    var picker by remember { mutableStateOf<RoutePicker?>(null) }
    val checked = remember { mutableStateListOf<String>() }
    var manual by remember { mutableStateOf("") }

    // 저장된 정류장 목록 (삭제/이동 시 갱신용)
    var stopsVersion by remember { mutableStateOf(0) }
    val savedStops = remember(stopsVersion) { prefs.stops }

    Column(Modifier.fillMaxSize().verticalScroll(scroll).padding(start = 28.dp, end = 28.dp, bottom = 40.dp)) {
        Text("설정", color = C.TEXT, fontSize = 30.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 8.dp))

        // ── ① API 키 ──
        SettingCard("① 버스 API 키 (공공데이터포털)") {
            Label(
                "data.go.kr 에서 아래 서비스를 '활용신청'하면 마이페이지에 인증키가 나와요. 일반 인증키(Decoding)를 붙여넣으세요. 한 키로 모든 서비스가 됩니다.\n" +
                    "• 서울: 서울특별시_정류소정보조회 서비스\n• 경기: 경기도_버스도착정보 조회 + 경기도_정류소 조회"
            )
            Field(apiKey, { apiKey = it }, "인증키 붙여넣기")
            Btn("키 저장") { prefs.apiKey = apiKey; onChanged() }
        }

        // ── ② 날씨 위치 ──
        SettingCard("② 날씨 위치") {
            Text(locLabel, color = C.TEXT, fontSize = 17.sp)
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) { Field(placeQuery, { placeQuery = it }, "동네 이름 검색 (예: 분당구 정자동)") }
                Spacer(Modifier.width(8.dp))
                Btn("검색") {
                    val q = placeQuery.trim()
                    if (q.isBlank()) return@Btn
                    places.clear(); searchMsg = "검색 중…"
                    scope.launch {
                        val hits = withContext(Dispatchers.IO) { geocode(q) }
                        places.clear(); places.addAll(hits)
                        searchMsg = if (hits.isEmpty()) "결과가 없어요. 다른 이름으로 검색해보세요" else null
                    }
                }
            }
            searchMsg?.let { Label(it) }
            for (p in places) {
                Text("→ ${p.label}", color = C.BLUE, fontSize = 17.sp, modifier = Modifier.fillMaxWidth()
                    .clickable {
                        prefs.locName = placeQuery.trim(); prefs.lat = p.lat; prefs.lon = p.lon
                        locLabel = locText(prefs); places.clear(); onChanged()
                    }.padding(vertical = 8.dp))
            }
        }

        // ── ③ 버스 정류장 ──
        SettingCard("③ 버스 정류장") {
            Label(
                "네이버 지도에서 정류장을 누르면 5자리 번호가 있어요. 서울을 고르면 서울 번호, 경기를 고르면 경기 번호를 넣고 볼 버스를 체크해 저장하세요. 경기는 이름으로도 검색돼요."
            )
            // 저장된 정류장
            if (savedStops.isEmpty()) Label("저장된 정류장이 없어요")
            else savedStops.forEachIndexed { i, s ->
                Row(
                    Modifier.fillMaxWidth().padding(top = 8.dp).clip(RoundedCornerShape(14.dp)).background(C.CARD2)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("${s.stopName} (${s.stopNo}) · ${if (s.region == "seoul") "서울" else "경기"}", color = C.TEXT, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                        Text(if (s.routes.isEmpty()) "모든 버스" else s.routes.joinToString(", "), color = C.ACCENT, fontSize = 15.sp)
                    }
                    if (i > 0) SmallBtn("▲") {
                        val l = prefs.stops.toMutableList(); val t = l[i]; l[i] = l[i - 1]; l[i - 1] = t
                        prefs.stops = l; stopsVersion++; onChanged()
                    }
                    SmallBtn("삭제") {
                        prefs.stops = prefs.stops.filterIndexed { j, _ -> j != i }; stopsVersion++; onChanged()
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            Text("정류장 추가", color = C.TEXT, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Row {
                RegionChip("서울", region == "seoul") { region = "seoul" }
                Spacer(Modifier.width(10.dp))
                RegionChip("경기", region == "gyeonggi") { region = "gyeonggi" }
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) { Field(stopNoInput, { stopNoInput = it }, "정류소 번호 5자리 (경기는 이름도 가능)") }
                Spacer(Modifier.width(8.dp))
                Btn("조회") {
                    val input = stopNoInput.replace("-", "").trim()
                    if (prefs.apiKey.isBlank()) { lookupMsg = "먼저 ① API 키를 저장해 주세요"; return@Btn }
                    if (input.isBlank()) return@Btn
                    lookupMsg = "조회 중…"; stationChoices.clear(); picker = null; checked.clear()
                    val r = region; val key = prefs.apiKey
                    scope.launch {
                        try {
                            if (r == "seoul") {
                                val (name, list) = withContext(Dispatchers.IO) { BusApi.seoul(key, input) }
                                if (list.isEmpty()) lookupMsg = "정류장을 찾지 못했어요. 번호를 확인하거나 '경기'로 바꿔보세요"
                                else { lookupMsg = null; picker = RoutePicker(r, input, input, name, list.map { it.route }.filter { it.isNotBlank() }.distinct()) }
                            } else {
                                val hits = withContext(Dispatchers.IO) { BusApi.ggFindStation(key, input) }
                                val exact = hits.filter { it.stopNo == input }
                                val pick = if (exact.size == 1) exact else hits
                                if (pick.size == 1) {
                                    val h = pick[0]
                                    val routes = withContext(Dispatchers.IO) { BusApi.gyeonggi(key, h.stationId).map { it.route } }
                                    lookupMsg = null; picker = RoutePicker(r, h.stopNo.ifBlank { input }, h.stationId, h.name, routes.filter { it.isNotBlank() }.distinct())
                                } else { lookupMsg = if (pick.isEmpty()) "정류장을 찾지 못했어요" else "정류장을 골라주세요"; stationChoices.addAll(pick.take(15)) }
                            }
                        } catch (e: Exception) {
                            lookupMsg = Board.friendly(e, "조회 실패")
                        }
                    }
                }
            }
            lookupMsg?.let { Label(it) }

            // 경기 다중 정류장 선택
            for (h in stationChoices) {
                Text("→ ${if (h.area.isNotBlank()) "[${h.area}] " else ""}${h.name} (${h.stopNo.ifBlank { "번호 없음" }})",
                    color = C.BLUE, fontSize = 17.sp, modifier = Modifier.fillMaxWidth().clickable {
                        lookupMsg = "노선 불러오는 중…"; stationChoices.clear()
                        scope.launch {
                            runCatching { withContext(Dispatchers.IO) { BusApi.gyeonggi(prefs.apiKey, h.stationId).map { it.route } } }
                                .onSuccess { lookupMsg = null; picker = RoutePicker("gyeonggi", h.stopNo, h.stationId, h.name, it.filter { r -> r.isNotBlank() }.distinct()) }
                                .onFailure { lookupMsg = it.message ?: "조회 실패" }
                        }
                    }.padding(vertical = 8.dp))
            }

            // 노선 선택 + 저장
            picker?.let { pk ->
                Spacer(Modifier.height(10.dp))
                Text("✔ ${pk.name} (${pk.stopNo})", color = C.TEXT, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                if (pk.routes.isEmpty()) Label("지금 도착 정보가 있는 버스가 없어요. 아래에 버스 번호를 직접 적어주세요")
                else {
                    Label("볼 버스를 체크하세요 (아무것도 안 고르면 전부 표시)")
                    FlowRow(Modifier.fillMaxWidth()) {
                        for (rt in pk.routes) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.width(130.dp)) {
                                Checkbox(
                                    checked = rt in checked,
                                    onCheckedChange = { if (it) checked.add(rt) else checked.remove(rt) },
                                    colors = CheckboxDefaults.colors(checkedColor = C.BLUE, uncheckedColor = C.DIM),
                                )
                                Text(rt, color = C.TEXT, fontSize = 16.sp)
                            }
                        }
                    }
                }
                Field(manual, { manual = it }, "직접 추가 (쉼표로 구분, 예: 9401, M4102)")
                Btn("이 정류장 저장") {
                    val chosen = (checked.toList() + manual.split(",").map { it.trim() }.filter { it.isNotEmpty() }).distinct()
                    val stop = SavedStop(pk.region, pk.stopNo, pk.stationId, pk.name, chosen)
                    prefs.stops = prefs.stops.filterNot { it.region == pk.region && it.stationId == pk.stationId } + stop
                    stopsVersion++; picker = null; checked.clear(); manual = ""; stopNoInput = ""; lookupMsg = "저장했어요 ✓"
                    onChanged()
                }
            }
        }
    }
}

private fun locText(prefs: Prefs) = if (prefs.lat == null) "아직 위치가 없어요"
else "현재: ${prefs.locName.ifBlank { "지정 위치" }}  (${"%.3f".format(prefs.lat)}, ${"%.3f".format(prefs.lon)})"

/** Open-Meteo 지명 검색 (키 불필요) */
private fun geocode(query: String): List<Place> = runCatching {
    val o = JSONObject(Http.get("https://geocoding-api.open-meteo.com/v1/search?name=" +
        URLEncoder.encode(query, "UTF-8") + "&count=8&language=ko&countryCode=KR"))
    val a = o.optJSONArray("results") ?: return emptyList()
    (0 until a.length()).map { i ->
        val r = a.getJSONObject(i)
        val label = listOf(r.optString("name"), r.optString("admin2"), r.optString("admin1"))
            .filter { it.isNotBlank() }.joinToString(", ")
        Place(label, r.getDouble("latitude"), r.getDouble("longitude"))
    }
}.getOrDefault(emptyList())

// ───────── 작은 UI 조각 ─────────
@Composable
private fun SettingCard(title: String, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = 20.dp).clip(RoundedCornerShape(20.dp)).background(C.CARD).padding(22.dp)) {
        Text(title, color = C.TEXT, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 8.dp))
        content()
    }
}

@Composable
private fun Label(text: String) = Text(text, color = C.SUB, fontSize = 15.sp, modifier = Modifier.padding(vertical = 4.dp))

@Composable
private fun Field(value: String, onValue: (String) -> Unit, hint: String) {
    OutlinedTextField(
        value = value,
        onValueChange = onValue,
        placeholder = { Text(hint, color = C.DIM, fontSize = 15.sp) },
        singleLine = true,
        textStyle = androidx.compose.ui.text.TextStyle(color = C.TEXT, fontSize = 16.sp),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = C.CARD2, unfocusedContainerColor = C.CARD2,
            focusedTextColor = C.TEXT, unfocusedTextColor = C.TEXT,
            cursorColor = C.ACCENT,
            focusedIndicatorColor = C.BLUE, unfocusedIndicatorColor = C.DIM,
        ),
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
    )
}

@Composable
private fun Btn(label: String, onClick: () -> Unit) {
    Box(
        Modifier.padding(top = 8.dp).clip(RoundedCornerShape(12.dp)).background(C.BLUE).clickable { onClick() }
            .padding(horizontal = 22.dp, vertical = 11.dp),
        contentAlignment = Alignment.Center,
    ) { Text(label, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold) }
}

@Composable
private fun SmallBtn(label: String, onClick: () -> Unit) {
    Box(
        Modifier.padding(start = 8.dp).clip(RoundedCornerShape(10.dp)).background(C.CARD).clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) { Text(label, color = C.TEXT, fontSize = 15.sp) }
}

@Composable
private fun RegionChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.clip(RoundedCornerShape(12.dp)).background(if (selected) C.BLUE else C.CARD2).clickable { onClick() }
            .padding(horizontal = 24.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) { Text(label, color = C.TEXT, fontSize = 17.sp) }
}
