package com.morningboard

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import kotlin.math.roundToInt

/** 홈 화면 위젯: 30분마다 + '새로고침' 탭 시 갱신. 보드 앱이 켜져 있으면 앱이 갱신할 때마다 같이 바뀜 */
class BoardWidget : AppWidgetProvider() {

    override fun onUpdate(ctx: Context, mgr: AppWidgetManager, ids: IntArray) = refreshAsync(ctx)

    override fun onReceive(ctx: Context, intent: Intent) {
        super.onReceive(ctx, intent)
        if (intent.action == ACTION_REFRESH) refreshAsync(ctx)
    }

    private fun refreshAsync(ctx: Context) {
        val pending = goAsync()
        val app = ctx.applicationContext
        Thread {
            try {
                render(app, null, loading = true)
                render(app, Board.loadAll(app))
            } catch (_: Exception) {
            } finally {
                pending.finish()
            }
        }.start()
    }

    companion object {
        const val ACTION_REFRESH = "com.morningboard.REFRESH"
        private const val MAX_ROWS = 6
        @Volatile private var last: Snapshot? = null

        fun render(ctx: Context, snap: Snapshot?, loading: Boolean = false) {
            val mgr = AppWidgetManager.getInstance(ctx)
            val ids = mgr.getAppWidgetIds(ComponentName(ctx, BoardWidget::class.java))
            if (ids.isEmpty()) return
            if (snap != null) last = snap
            val s = snap ?: last
            val v = RemoteViews(ctx.packageName, R.layout.widget_board)
            val density = ctx.resources.displayMetrics.density

            // 클릭: 전체 → 보드 앱, 새로고침 → 위젯 갱신
            val open = PendingIntent.getActivity(ctx, 0, Intent(ctx, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            v.setOnClickPendingIntent(R.id.widget_root, open)
            val refresh = PendingIntent.getBroadcast(ctx, 1,
                Intent(ctx, BoardWidget::class.java).setAction(ACTION_REFRESH),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            v.setOnClickPendingIntent(R.id.w_refresh, refresh)
            v.setTextViewText(R.id.w_refresh, if (loading) "불러오는 중…" else "↻ 새로고침")

            // 날씨
            val w = s?.weather
            if (w != null) {
                v.setImageViewBitmap(R.id.w_icon, Pixel.tile(WeatherApi.icon(w), (64 * density).toInt()))
                v.setTextViewText(R.id.w_temp, "${w.temp.roundToInt()}°")
                v.setTextViewText(R.id.w_desc, "${WeatherApi.desc(w.code)} · 체감 ${w.feels.roundToInt()}° · " +
                    "${w.min.roundToInt()}°/${w.max.roundToInt()}°")
                val o = WeatherApi.outfit(w)
                v.setImageViewBitmap(R.id.w_outfit, Pixel.row(o.items.map { it.sprite }, (72 * density).toInt(), (8 * density).toInt()))
                v.setTextViewText(R.id.w_outfit_text, (listOf(o.headline) + o.tips.take(1)).joinToString("\n"))
            } else {
                v.setTextViewText(R.id.w_temp, "--°")
                v.setTextViewText(R.id.w_desc, s?.weatherError ?: "날씨 불러오는 중")
                v.setTextViewText(R.id.w_outfit_text, "")
            }

            // 버스
            val prefs = Prefs(ctx)
            val rows = mutableListOf<Pair<String, String>>()
            var emptyMsg: String? = null
            when {
                prefs.apiKey.isBlank() -> emptyMsg = "설정에서 API 키를 넣어주세요"
                prefs.stops.isEmpty() -> emptyMsg = "설정에서 정류장을 추가하세요"
                s == null || s.stops.isEmpty() -> emptyMsg = "버스 정보 불러오는 중"
                else -> for (st in s.stops) {
                    if (st.error != null) { rows += "⚠" to "${st.stopName}: ${st.error}"; continue }
                    if (st.arrivals.isEmpty()) { rows += "—" to "${st.stopName}: 도착 정보 없음"; continue }
                    for (a in st.arrivals) {
                        val clock = Board.etaClock(a.first, st.fetchedAt)
                        val first = if (clock != null) "$clock 도착" else Board.etaShort(a.first, st.fetchedAt)
                        val next = a.second?.let { e -> Board.etaClock(e, st.fetchedAt) ?: Board.etaShort(e, st.fetchedAt) }
                        rows += a.route to (first + (next?.let { "  · 다음 $it" } ?: ""))
                    }
                }
            }
            v.setViewVisibility(R.id.bus_empty, if (emptyMsg != null) View.VISIBLE else View.GONE)
            v.setTextViewText(R.id.bus_empty, emptyMsg ?: "")
            val stopNames = prefs.stops.joinToString(" · ") { it.stopName }
            v.setTextViewText(R.id.bus_title, if (stopNames.isBlank()) "버스 도착" else "🚌 $stopNames")
            val rowIds = listOf(R.id.bus_row1, R.id.bus_row2, R.id.bus_row3, R.id.bus_row4, R.id.bus_row5, R.id.bus_row6)
            val routeIds = listOf(R.id.bus_route1, R.id.bus_route2, R.id.bus_route3, R.id.bus_route4, R.id.bus_route5, R.id.bus_route6)
            val etaIds = listOf(R.id.bus_eta1, R.id.bus_eta2, R.id.bus_eta3, R.id.bus_eta4, R.id.bus_eta5, R.id.bus_eta6)
            for (i in 0 until MAX_ROWS) {
                val r = rows.getOrNull(i)
                v.setViewVisibility(rowIds[i], if (r != null) View.VISIBLE else View.GONE)
                if (r != null) { v.setTextViewText(routeIds[i], r.first); v.setTextViewText(etaIds[i], r.second) }
            }
            val busTime = s?.stops?.firstOrNull()?.fetchedAt
            v.setTextViewText(R.id.w_updated, busTime?.let { "${Board.clock(it)} 기준" } ?: "")

            mgr.updateAppWidget(ids, v)
        }
    }
}
