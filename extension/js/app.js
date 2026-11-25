// 메인: 상태 + 렌더 + 틱 + 설정 — Android MainActivity/SettingsActivity 이식

const DOW = ['일', '월', '화', '수', '목', '금', '토'];
const state = {
  weather: null, weatherError: null, stops: [],
  lastBusFetch: 0, lastWeatherFetch: 0, loadingBus: false, loadingWeather: false,
  updated: '', live: [],
};
let backdrop;

function el(tag, cls, text) {
  const e = document.createElement(tag);
  if (cls) e.className = cls;
  if (text != null) e.textContent = text;
  return e;
}

// ───────── 갱신 로직 ─────────
async function refreshWeather() {
  if (state.loadingWeather) return;
  state.loadingWeather = true;
  state.lastWeatherFetch = Date.now();
  const [w, err] = await Board.loadWeather();
  state.loadingWeather = false;
  if (w || !state.weather) { state.weather = w; state.weatherError = err; } else state.weatherError = err;
  if (err === Board.OFFLINE) {
    const interval = state.weather ? 15 * 60000 : 60000;
    state.lastWeatherFetch = Date.now() - interval + 5000;
  }
  updateMood();
  renderWeather();
}

async function refreshBuses() {
  if (state.loadingBus) return;
  state.loadingBus = true;
  state.lastBusFetch = Date.now();
  const result = await Board.loadBuses();
  state.loadingBus = false;
  const offline = result.length && result.every(r => r.error === Board.OFFLINE);
  if (offline) {
    state.lastBusFetch = Date.now() - 25000;
    if (!state.stops.length || state.stops.every(s => s.error)) state.stops = result;
    state.updated = '인터넷 연결 대기 중…';
  } else {
    state.stops = result;
    state.updated = `버스 ${Board.clock(Date.now())} 갱신`;
  }
  renderBuses();
}

function tick() {
  const now = Date.now();
  const d = new Date(now);
  document.getElementById('clock').textContent =
    `${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`;
  document.getElementById('date').textContent =
    `${d.getMonth() + 1}월 ${d.getDate()}일 ${DOW[d.getDay()]}요일`;
  document.getElementById('updated').textContent = state.updated;
  for (const l of state.live) l.el.textContent = Board.etaShort(l.eta, l.fetchedAt, l.withSec, now);
  renderShuttle();
  if (now - state.lastBusFetch > 30000) refreshBuses();
  const wInterval = state.weather ? 15 * 60000 : 60000;
  if (now - state.lastWeatherFetch > wInterval) refreshWeather();
}

function updateMood() {
  backdrop.setMood(state.weather ? moodOf(state.weather.code, state.weather.isDay) : null);
}

// ───────── 날씨 렌더 ─────────
function renderWeather() {
  const card = document.getElementById('weatherCard');
  card.innerHTML = '';
  const w = state.weather;
  if (!w) {
    card.appendChild(el('div', 'w-empty', state.weatherError || '날씨 불러오는 중…'));
    return;
  }
  // 펼침(다 보기) 모드: 날씨를 한 줄 미니로 (공간 절약 → 버스 더 많이 노스크롤)
  if (Prefs.fullMode) {
    const outfit = WeatherApi.outfit(w);
    const mini = el('div', 'w-mini');
    mini.appendChild(makeSpriteCanvas(iconFor(w.code, w.isDay), 30, { tile: true }));
    const txt = el('div', 'w-mini-txt');
    txt.appendChild(el('span', 't', `${Math.round(w.temp)}°`));
    txt.appendChild(el('span', 'd', `${WeatherApi.desc(w.code)} · 체감 ${Math.round(w.feels)}° · ${outfit.items.map(i => i.label).join(' · ')}`));
    mini.appendChild(txt);
    card.appendChild(mini);
    const todayF = new Date().toISOString().slice(0, 10);
    const weekF = w.week.filter(d => d.date >= todayF).slice(0, 7);
    if (weekF.length) card.appendChild(renderWeek(weekF, todayF));
    return;
  }
  const top = el('div', 'w-top');
  top.appendChild(makeSpriteCanvas(iconFor(w.code, w.isDay), 52, { tile: true }));
  const col = el('div', 'col');
  col.appendChild(el('div', 'w-temp', `${Math.round(w.temp)}°`));
  col.appendChild(el('div', 'w-desc', `${WeatherApi.desc(w.code)} · 체감 ${Math.round(w.feels)}°`));
  col.appendChild(el('div', 'w-minmax', `최저 ${Math.round(w.min)}° / 최고 ${Math.round(w.max)}° · 강수 ${w.rainProb12h}%`));
  top.appendChild(col);
  card.appendChild(top);

  const outfit = WeatherApi.outfit(w);
  card.appendChild(el('div', 'section-label', '오늘의 옷차림'));
  const row = el('div', 'outfit-row');
  for (const item of outfit.items) {
    const cell = el('div', 'outfit-cell');
    cell.appendChild(makeSpriteCanvas(item.s, 44, { tile: true }));
    cell.appendChild(el('div', 'label', item.label));
    row.appendChild(cell);
  }
  const head = el('div', 'outfit-head');
  head.appendChild(el('div', 'headline', outfit.headline));
  for (const tip of outfit.tips) head.appendChild(el('div', 'tip', '• ' + tip));
  if (state.weatherError) { const e = el('div', 'tip', state.weatherError); e.style.color = 'var(--red)'; head.appendChild(e); }
  row.appendChild(head);
  card.appendChild(row);

  const today = new Date().toISOString().slice(0, 10);
  const week = w.week.filter(d => d.date >= today).slice(0, 7);
  if (week.length) card.appendChild(renderWeek(week, today));
}

function renderWeek(days, today) {
  const box = el('div', 'week');
  box.appendChild(el('div', 'section-label', '주간 날씨'));
  const row = el('div', 'week-row');
  const anyRain = days.some(d => d.rainProb >= 30);
  for (const d of days) {
    const date = new Date(d.date + 'T00:00:00');
    const dow = date.getDay();
    const isToday = d.date === today;
    const color = isToday ? 'var(--accent)' : dow === 0 ? 'var(--red)' : dow === 6 ? 'var(--blue)' : 'var(--sub)';
    const cell = el('div', 'week-cell');
    const lbl = el('div', 'week-dow', isToday ? '오늘' : DOW[dow]); lbl.style.color = color;
    cell.appendChild(lbl);
    cell.appendChild(makeSpriteCanvas(iconFor(d.code), 26, { tile: false }));
    const temps = el('div', 'week-temps');
    temps.appendChild(el('span', 'week-max', `${Math.round(d.max)}°`));
    temps.appendChild(el('span', 'week-min', `${Math.round(d.min)}°`));
    cell.appendChild(temps);
    if (anyRain) cell.appendChild(el('div', 'week-rain', d.rainProb >= 30 ? `☂ ${d.rainProb}%` : ' '));
    row.appendChild(cell);
  }
  box.appendChild(row);
  return box;
}

// ───────── 버스 렌더 ─────────
function renderBuses() {
  const list = document.getElementById('busList');
  list.innerHTML = '';
  state.live = [];
  if (!Prefs.apiKey) { list.appendChild(el('div', 'bus-empty', '설정에서 공공데이터포털 API 키를 넣어주세요')); return; }
  if (!Prefs.stops.length) { list.appendChild(el('div', 'bus-empty', '설정에서 정류장과 버스를 추가하세요')); return; }
  if (!state.stops.length) { list.appendChild(el('div', 'bus-empty', '버스 정보 불러오는 중…')); return; }
  const now = Date.now();
  for (const s of state.stops) {
    list.appendChild(el('div', 'bus-stopname', `${s.stopName}  ·  ${s.stop.stopNo}`));
    if (s.error) { list.appendChild(el('div', 'bus-err', s.error)); continue; }
    if (!s.arrivals.length) {
      list.appendChild(el('div', 'bus-empty', s.stop.routes.length ? `${s.stop.routes.join(', ')} 도착 정보 없음` : '도착 예정 버스 없음'));
      continue;
    }
    for (const a of s.arrivals) list.appendChild(busRow(a, s.fetchedAt, now));
  }
}

function busRow(a, fetchedAt, now) {
  const row = el('div', 'bus-row');
  const route = el('div', 'bus-route');
  route.appendChild(el('div', 'no', a.route));
  if (a.dest) route.appendChild(el('div', 'dest', a.dest));
  row.appendChild(route);

  const eta = el('div', 'bus-eta');
  if (!a.first) eta.appendChild(el('div', 'main none', '도착 정보 없음'));
  else {
    const main = el('div', 'main', Board.etaShort(a.first, fetchedAt, true, now));
    eta.appendChild(main);
    state.live.push({ el: main, eta: a.first, fetchedAt, withSec: true });
  }
  const sub = [Board.etaClock(a.first, fetchedAt) && Board.etaClock(a.first, fetchedAt) + ' 도착', Board.stopsText(a.first) || null]
    .filter(Boolean).join(' · ');
  if (sub) eta.appendChild(el('div', 'sub', sub));
  row.appendChild(eta);

  const next = el('div', 'bus-next');
  next.appendChild(el('div', 'lbl', '다음'));
  const val = el('div', 'val', Board.etaShort(a.second, fetchedAt, false, now));
  next.appendChild(val);
  state.live.push({ el: val, eta: a.second, fetchedAt, withSec: false });
  const st = Board.stopsText(a.second);
  if (st) next.appendChild(el('div', 'stops', st));
  row.appendChild(next);
  return row;
}

// ───────── 셔틀 렌더 ─────────
function dayLabel(d) { return d === 'weekday' ? '평일' : d === 'weekend' ? '주말' : '매일'; }

function isServiceDay(s, now) {
  const day = new Date(now).getDay();
  const wd = day >= 1 && day <= 5;
  if (!s.days || s.days === 'daily') return true;
  if (s.days === 'weekday') return wd;
  if (s.days === 'weekend') return !wd;
  return true;
}

function upcomingTimes(s, now, count) {
  if (!isServiceDay(s, now)) return [];
  const d = new Date(now);
  const nowSec = d.getHours() * 3600 + d.getMinutes() * 60 + d.getSeconds();
  return (s.times || [])
    .map(t => { const [h, m] = t.split(':').map(Number); return { hm: t, abs: h * 3600 + m * 60 }; })
    .filter(x => !isNaN(x.abs))
    .sort((a, b) => a.abs - b.abs)
    .map(x => ({ hm: x.hm, secs: x.abs - nowSec }))
    .filter(x => x.secs > 0)
    .slice(0, count);
}

function fmtLeft(secs) {
  if (secs <= 60) return '곧 출발';
  const m = Math.floor(secs / 60);
  if (m < 60) return `${m}분 후`;
  return `${Math.floor(m / 60)}시간 ${m % 60}분 후`;
}

function renderShuttle() {
  const card = document.getElementById('shuttleCard');
  const shuttles = Prefs.shuttles;
  if (!shuttles.length) { card.hidden = true; return; }
  card.hidden = false;
  const list = document.getElementById('shuttleList');
  list.innerHTML = '';
  const now = Date.now();
  for (const s of shuttles) {
    const item = el('div', 'shuttle-item');
    item.appendChild(el('div', 'shuttle-name', s.name + (s.days && s.days !== 'daily' ? ` · ${dayLabel(s.days)}` : '')));
    const nexts = upcomingTimes(s, now, 2);
    const noneMsg = isServiceDay(s, now) ? '오늘 운행 종료' : '오늘 운행 안 함';
    if (Prefs.fullMode) {
      // 펼침 모드: 한 줄 compact (18:00 (7분) · 18:06 (13분))
      const line = el('div', 'shuttle-mini');
      const shortLeft = (secs) => {
        if (secs <= 60) return '곧';
        const m = Math.floor(secs / 60);
        return m < 60 ? `${m}분` : `${Math.floor(m / 60)}시간${m % 60 ? m % 60 + '분' : ''}`;
      };
      line.textContent = nexts.length
        ? nexts.map(n => `${n.hm} (${shortLeft(n.secs)})`).join('    ·    ')
        : noneMsg;
      item.appendChild(line);
    } else {
      const times = el('div', 'shuttle-times');
      if (!nexts.length) {
        times.appendChild(el('div', 'shuttle-empty', noneMsg));
      } else {
        for (const n of nexts) {
          const cell = el('div', 'shuttle-time' + (n.secs <= 300 ? ' soon' : ''));
          cell.appendChild(el('div', 'hm', n.hm));
          cell.appendChild(el('div', 'left', fmtLeft(n.secs)));
          times.appendChild(cell);
        }
      }
      item.appendChild(times);
    }
    list.appendChild(item);
  }
}

// ───────── 설정 패널 ─────────
function openSettings() {
  renderSettings();
  document.getElementById('settingsOverlay').hidden = false;
}
function closeSettings() {
  document.getElementById('settingsOverlay').hidden = true;
  state.lastBusFetch = 0; state.lastWeatherFetch = 0;
  refreshWeather(); refreshBuses();
}

function renderSettings() {
  const root = document.getElementById('settings');
  root.innerHTML = '';
  const h1 = el('h1', null, '설정');
  const close = el('button', 'close', '완료'); close.onclick = closeSettings;
  h1.appendChild(close); root.appendChild(h1);

  // ① API 키
  const c1 = scard('① 버스 API 키 (공공데이터포털)');
  c1.appendChild(desc("data.go.kr 에서 아래 서비스를 '활용신청'하면 마이페이지에 인증키가 나와요. 일반 인증키(Decoding)를 붙여넣으세요.\n• 서울: 서울특별시_정류소정보조회 서비스\n• 경기: 경기도_버스도착정보 조회 + 경기도_정류소 조회"));
  const keyIn = input('인증키 붙여넣기', Prefs.apiKey);
  c1.appendChild(keyIn);
  c1.appendChild(btn('키 저장', () => { Prefs.apiKey = keyIn.value; toast(c1, '저장했어요'); }));
  root.appendChild(c1);

  // ② 날씨 위치
  const c2 = scard('② 날씨 위치');
  const loc = el('div', 'muted', locText()); c2.appendChild(loc);
  const results = el('div');
  const searchIn = input('동네 이름 검색 (예: 상암동, 분당구 정자동)');
  const doSearch = async () => {
    const qv = searchIn.value.trim(); if (!qv) return;
    results.innerHTML = '<div class="muted">검색 중…</div>';
    let hits = [];
    try { hits = await geocode(qv, Prefs.kakaoKey); }
    catch (e) { results.innerHTML = ''; results.appendChild(el('div', 'bus-err', '검색 오류: ' + (e.message || e))); return; }
    results.innerHTML = '';
    if (!hits.length) { results.appendChild(el('div', 'muted', '결과가 없어요. 다른 이름으로 검색해보세요')); return; }
    for (const p of hits) {
      const h = el('div', 'hit', '→ ' + p.label);
      h.onclick = () => { Prefs.setLocation(p.label.split(',')[0].trim(), p.lat, p.lon); loc.textContent = locText(); results.innerHTML = '<div class="ok">위치 저장됨 ✓</div>'; };
      results.appendChild(h);
    }
  };
  searchIn.addEventListener('keydown', e => { if (e.key === 'Enter') { e.preventDefault(); doSearch(); } });
  const searchRow = el('div', 'row');
  searchRow.appendChild(searchIn);
  searchRow.appendChild(btnInline('검색', doSearch));
  c2.appendChild(searchRow);
  c2.appendChild(results);
  root.appendChild(c2);

  // ③ 정류장
  const c3 = scard('③ 버스 정류장');
  c3.appendChild(desc('네이버 지도에서 정류장을 누르면 5자리 번호가 있어요. 서울/경기를 고르고 번호(경기는 이름도 가능)로 조회 후 볼 버스를 체크해 저장하세요.'));
  const stopList = el('div'); c3.appendChild(stopList);
  renderStopList(stopList);

  c3.appendChild(el('h2', null, '정류장 추가')); c3.lastChild.style.marginTop = '16px';
  let region = 'seoul';
  const chips = el('div');
  const seoulChip = chip('서울', true); const ggChip = chip('경기', false);
  seoulChip.onclick = () => { region = 'seoul'; seoulChip.classList.add('on'); ggChip.classList.remove('on'); };
  ggChip.onclick = () => { region = 'gyeonggi'; ggChip.classList.add('on'); seoulChip.classList.remove('on'); };
  chips.appendChild(seoulChip); chips.appendChild(ggChip); c3.appendChild(chips);

  const lookRow = el('div', 'row'); lookRow.style.marginTop = '10px';
  const noIn = input('정류소 번호 5자리 (경기는 이름도 가능)');
  lookRow.appendChild(noIn);
  const lookBox = el('div');
  lookRow.appendChild(btnInline('조회', () => lookup(region, noIn.value.replace(/-/g, '').trim(), lookBox, stopList)));
  c3.appendChild(lookRow);
  c3.appendChild(lookBox);
  root.appendChild(c3);

  // ④ 셔틀 (직접 추가)
  const c4 = scard('④ 셔틀 (시간표 직접 추가)');
  c4.appendChild(desc('셔틀 노선 자동목록은 로그인 토큰이 필요해 미지원이에요. 여기에 이름·출발시각을 직접 넣으면, 보드 맨 위에 지금 시간 기준 다음 2대가 떠요.'));
  const shList = el('div'); c4.appendChild(shList);
  renderShuttleList(shList);

  c4.appendChild(el('h2', null, '셔틀 추가')); c4.lastChild.style.marginTop = '14px';
  const shName = input('노선 이름 (예: 퇴근 순환셔틀)');
  c4.appendChild(shName);
  const shTimes = input('출발 시각 (쉼표, 예: 17:30, 18:00, 18:30, 19:00)');
  c4.appendChild(shTimes);
  let shDays = 'weekday';
  const dayRow = el('div'); dayRow.style.marginTop = '4px';
  const dc = {};
  for (const [val, label] of [['weekday', '평일'], ['daily', '매일'], ['weekend', '주말']]) {
    const b = chip(label, val === shDays);
    b.onclick = () => { shDays = val; for (const k in dc) dc[k].classList.toggle('on', k === val); };
    dc[val] = b; dayRow.appendChild(b);
  }
  c4.appendChild(dayRow);
  c4.appendChild(btn('셔틀 저장', () => {
    const name = shName.value.trim();
    const times = normalizeTimes(shTimes.value);
    if (!name) { toast(c4, '이름을 넣어주세요'); return; }
    if (!times.length) { toast(c4, '시각을 넣어주세요 (예: 17:30, 18:00)'); return; }
    Prefs.shuttles = [...Prefs.shuttles, { name, times, days: shDays }];
    shName.value = ''; shTimes.value = '';
    renderShuttleList(shList); renderShuttle();
    toast(c4, '추가했어요 ✓');
  }));
  root.appendChild(c4);
}

function renderShuttleList(container) {
  container.innerHTML = '';
  const sh = Prefs.shuttles;
  if (!sh.length) { container.appendChild(el('div', 'muted', '추가된 셔틀이 없어요')); return; }
  sh.forEach((s, i) => {
    const item = el('div', 'stop-item');
    const col = el('div', 'col');
    col.appendChild(el('div', 'nm', `${s.name} · ${dayLabel(s.days)}`));
    col.appendChild(el('div', 'rt', (s.times || []).join(', ')));
    item.appendChild(col);
    item.appendChild(smallBtn('삭제', () => { Prefs.shuttles = Prefs.shuttles.filter((_, j) => j !== i); renderShuttleList(container); renderShuttle(); }));
    container.appendChild(item);
  });
}

function normalizeTimes(str) {
  return str.split(',').map(t => t.trim()).filter(Boolean).map(t => {
    const m = t.match(/^(\d{1,2}):(\d{1,2})$/);
    if (!m) return null;
    const h = Math.min(23, parseInt(m[1])), mi = Math.min(59, parseInt(m[2]));
    return String(h).padStart(2, '0') + ':' + String(mi).padStart(2, '0');
  }).filter(Boolean);
}

function renderStopList(container) {
  container.innerHTML = '';
  const stops = Prefs.stops;
  if (!stops.length) { container.appendChild(el('div', 'muted', '저장된 정류장이 없어요')); return; }
  stops.forEach((s, i) => {
    const item = el('div', 'stop-item');
    const col = el('div', 'col');
    col.appendChild(el('div', 'nm', `${s.stopName} (${s.stopNo}) · ${s.region === 'seoul' ? '서울' : '경기'}`));
    col.appendChild(el('div', 'rt', s.routes.length ? s.routes.join(', ') : '모든 버스'));
    item.appendChild(col);
    if (i > 0) item.appendChild(smallBtn('▲', () => {
      const l = Prefs.stops.slice(); [l[i], l[i - 1]] = [l[i - 1], l[i]]; Prefs.stops = l; renderStopList(container);
    }));
    item.appendChild(smallBtn('삭제', () => { Prefs.stops = Prefs.stops.filter((_, j) => j !== i); renderStopList(container); }));
    container.appendChild(item);
  });
}

async function lookup(region, input_, box, stopList) {
  box.innerHTML = '';
  if (!Prefs.apiKey) { box.appendChild(el('div', 'bus-err', '먼저 ① API 키를 저장해 주세요')); return; }
  if (!input_) return;
  box.innerHTML = '<div class="muted">조회 중…</div>';
  try {
    if (region === 'seoul') {
      const [name, list] = await BusApi.seoul(Prefs.apiKey, input_);
      box.innerHTML = '';
      if (!list.length) { box.appendChild(el('div', 'bus-err', "정류장을 못 찾았어요. 번호 확인하거나 '경기'로 바꿔보세요")); return; }
      showRoutePicker(box, stopList, region, input_, input_, name, uniq(list.map(a => a.route)));
    } else {
      const hits = await BusApi.ggFindStation(Prefs.apiKey, input_);
      const exact = hits.filter(h => h.stopNo === input_);
      const pick = exact.length === 1 ? exact : hits;
      box.innerHTML = '';
      if (pick.length === 1) {
        const h = pick[0];
        const routes = await BusApi.gyeonggi(Prefs.apiKey, h.stationId);
        showRoutePicker(box, stopList, 'gyeonggi', h.stopNo || input_, h.stationId, h.name, uniq(routes.map(r => r.route)));
      } else if (!pick.length) { box.appendChild(el('div', 'bus-err', '정류장을 못 찾았어요')); }
      else {
        box.appendChild(el('div', 'muted', '정류장을 골라주세요'));
        for (const h of pick.slice(0, 15)) {
          const hit = el('div', 'hit', `→ ${h.area ? '[' + h.area + '] ' : ''}${h.name} (${h.stopNo || '번호 없음'})`);
          hit.onclick = async () => {
            box.innerHTML = '<div class="muted">노선 불러오는 중…</div>';
            try { const routes = await BusApi.gyeonggi(Prefs.apiKey, h.stationId); box.innerHTML = ''; showRoutePicker(box, stopList, 'gyeonggi', h.stopNo, h.stationId, h.name, uniq(routes.map(r => r.route))); }
            catch (e) { box.innerHTML = ''; box.appendChild(el('div', 'bus-err', Board.friendly(e, '조회 실패'))); }
          };
          box.appendChild(hit);
        }
      }
    }
  } catch (e) { box.innerHTML = ''; box.appendChild(el('div', 'bus-err', Board.friendly(e, '조회 실패'))); }
}

function showRoutePicker(box, stopList, region, stopNo, stationId, name, routes) {
  box.innerHTML = '';
  box.appendChild(el('div', 'nm', `✔ ${name} (${stopNo})`)); box.lastChild.style.cssText = 'font-size:19px;font-weight:700;margin:12px 0 4px';
  const checks = [];
  if (!routes.length) box.appendChild(el('div', 'muted', '지금 도착 정보가 있는 버스가 없어요. 아래에 직접 적어주세요'));
  else {
    box.appendChild(el('div', 'muted', '볼 버스를 체크하세요 (아무것도 안 고르면 전부 표시)'));
    const grid = el('div', 'route-grid');
    for (const rt of routes) {
      const lab = document.createElement('label');
      const cb = document.createElement('input'); cb.type = 'checkbox'; cb.value = rt;
      lab.appendChild(cb); lab.appendChild(document.createTextNode(rt));
      grid.appendChild(lab); checks.push(cb);
    }
    box.appendChild(grid);
  }
  const manual = input('직접 추가 (쉼표로 구분, 예: 9401, M4102)');
  box.appendChild(manual);
  box.appendChild(btn('이 정류장 저장', () => {
    const chosen = uniq([...checks.filter(c => c.checked).map(c => c.value), ...manual.value.split(',').map(s => s.trim()).filter(Boolean)]);
    const stop = { region, stopNo, stationId, stopName: name, routes: chosen };
    Prefs.stops = [...Prefs.stops.filter(s => !(s.region === region && s.stationId === stationId)), stop];
    renderStopList(stopList);
    box.innerHTML = '<div class="ok">저장했어요 ✓</div>';
  }));
}

// ───────── 작은 UI 헬퍼 ─────────
function scard(title) { const c = el('div', 'scard'); c.appendChild(el('h2', null, title)); return c; }
function desc(t) { return el('div', 'desc', t); }
function input(ph, val) { const i = document.createElement('input'); i.type = 'text'; i.placeholder = ph; if (val) i.value = val; return i; }
function btn(label, fn) { const b = el('button', 'btn', label); b.onclick = fn; return b; }
function btnInline(label, fn) { const b = el('button', 'btn', label); b.onclick = fn; return b; }
function smallBtn(label, fn) { const b = el('button', 'btn small', label); b.onclick = fn; return b; }
function chip(label, on) { const b = el('button', 'chip' + (on ? ' on' : ''), label); return b; }
function uniq(a) { return [...new Set(a.filter(Boolean))]; }
function toast(container, msg) { const t = el('div', 'ok', msg); container.appendChild(t); setTimeout(() => t.remove(), 2000); }
function locText() { return Prefs.lat == null ? '아직 위치가 없어요' : `현재: ${Prefs.locName || '지정 위치'}  (${Prefs.lat.toFixed(3)}, ${Prefs.lon.toFixed(3)})`; }

// ───────── 시작 ─────────
// 최초 1회 회사 셔틀 기본값 시드 (CJ ENM센터 기점). 삭제하면 다시 안 생김.
function seedShuttles() {
  if (Prefs.shuttles.length || Prefs._d.shuttleSeeded) return;
  Prefs._d.shuttleSeeded = true;
  Prefs.shuttles = [
    { name: '순환셔틀(퇴근)', days: 'weekday', times: ['18:00', '18:06', '18:12', '18:18', '18:25', '18:30', '18:35', '18:40', '18:45', '18:50', '18:55', '18:59', '19:03', '19:07', '19:11', '19:15', '19:19', '19:23', '19:28', '19:33', '19:38', '19:43', '19:48', '19:54', '20:00'] },
    { name: '분당 셔틀(퇴근)', days: 'daily', times: ['19:30'] },
  ];
}

(async function init() {
  await Prefs.load();
  seedShuttles();
  backdrop = new WeatherBackdrop(document.getElementById('backdrop'));
  backdrop.start();
  document.getElementById('gearBtn').onclick = openSettings;
  document.getElementById('busCard').onclick = () => { state.lastBusFetch = 0; refreshBuses(); };
  const expandBtn = document.getElementById('expandBtn');
  const applyFull = () => {
    document.body.classList.toggle('full', Prefs.fullMode);
    document.documentElement.classList.toggle('full', Prefs.fullMode);
    expandBtn.textContent = Prefs.fullMode ? '⤡' : '⤢';
    expandBtn.title = Prefs.fullMode ? '접기' : '펼치기';
  };
  expandBtn.onclick = () => { Prefs.fullMode = !Prefs.fullMode; applyFull(); renderWeather(); };
  applyFull();
  tick();
  renderWeather(); renderBuses(); renderShuttle();
  refreshWeather(); refreshBuses();
  setInterval(tick, 1000);
})();
