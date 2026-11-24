// 버스 (서울/경기) + 지명검색 — Android Bus.kt 이식, XML 은 DOMParser 사용

function enc(key) { return key.includes('%') ? key : encodeURIComponent(key); }
function q(s) { return encodeURIComponent(s); }

// rowTag 안의 하위 leaf 태그는 각 row 로, 그 밖의 leaf 태그는 header 로
function parseXmlItems(xmlText, rowTag) {
  const doc = new DOMParser().parseFromString(xmlText, 'text/xml');
  const header = {};
  const rows = [];
  for (const el of doc.getElementsByTagName(rowTag)) {
    const r = {};
    for (const child of el.children) {
      if (child.children.length === 0) r[child.tagName] = (child.textContent || '').trim();
    }
    rows.push(r);
  }
  for (const el of doc.getElementsByTagName('*')) {
    if (el.children.length !== 0) continue;
    let p = el.parentElement, inRow = false;
    while (p) { if (p.tagName === rowTag) { inRow = true; break; } p = p.parentElement; }
    if (!inRow) {
      const t = (el.textContent || '').trim();
      if (t) header[el.tagName] = t;
    }
  }
  return { header, rows };
}

const SEOUL_RE = /(?:(\d+)분)?\s*(?:(\d+)초)?\s*후\s*\[(\d+)번째\s*전\]/;
function parseSeoulMsg(msg) {
  if (!msg) return null;
  const m = SEOUL_RE.exec(msg);
  if (m) {
    const min = parseInt(m[1]) || 0, sec = parseInt(m[2]) || 0;
    return { secs: min * 60 + sec, stopsAway: m[3] ? parseInt(m[3]) : null, text: msg };
  }
  if (msg.includes('곧 도착')) return { secs: 0, stopsAway: null, text: '곧 도착' };
  return { secs: null, stopsAway: null, text: msg };
}

const BusApi = {
  async _get(url) {
    const res = await fetch(url);
    if (res.status === 401 || res.status === 403)
      throw new BusError(`API 키 인증 실패(HTTP ${res.status}). data.go.kr에서 이 버스 서비스 활용신청 승인 여부·키를 확인하세요(승인 직후면 1~2시간 뒤 적용).`);
    const body = await res.text();
    if (!res.ok && !body) throw new Error('HTTP ' + res.status);
    return body;
  },

  async fetchStop(key, stop) {
    const now = Date.now();
    try {
      let name, list;
      if (stop.region === 'seoul') { [name, list] = await this.seoul(key, stop.stationId); }
      else { name = stop.stopName; list = await this.gyeonggi(key, stop.stationId); }
      let filtered = list;
      if (stop.routes.length) {
        const order = new Map(stop.routes.map((r, i) => [String(r), i]));
        filtered = list.filter(a => order.has(String(a.route))).sort((a, b) => order.get(String(a.route)) - order.get(String(b.route)));
      }
      return { stop, stopName: name || stop.stopName, arrivals: filtered, error: null, fetchedAt: now };
    } catch (e) {
      return { stop, stopName: stop.stopName, arrivals: [], error: Board.friendly(e), fetchedAt: now };
    }
  },

  // 서울: getStationByUid (arsId = 5자리 정류소 번호). ws.bus.go.kr 은 http 만 지원
  async seoul(key, arsId) {
    const url = `http://ws.bus.go.kr/api/rest/stationinfo/getStationByUid?serviceKey=${enc(key)}&arsId=${q(arsId)}`;
    const { header, rows } = parseXmlItems(await this._get(url), 'itemList');
    const cd = header.headerCd;
    if (cd != null && cd !== '0' && cd !== '4') throw new BusError(header.headerMsg || `서울 API 오류(${cd})`);
    if (header.returnAuthMsg) throw new BusError('API 키 오류: ' + header.returnAuthMsg);
    if (header.errMsg && cd == null) throw new BusError('API 오류: ' + header.errMsg);
    let stopName = '';
    const list = rows.map(r => {
      if (!stopName) stopName = r.stNm || '';
      const dir = r.adirection || '';
      return {
        route: String(r.rtNm || ''),
        dest: dir ? dir + ' 방면' : '',
        first: parseSeoulMsg(r.arrmsg1),
        second: parseSeoulMsg(r.arrmsg2),
      };
    });
    return [stopName, list];
  },

  // 경기: 정류소 조회 v2
  async ggFindStation(key, keyword) {
    const url = `https://apis.data.go.kr/6410000/busstationservice/v2/getBusStationListv2` +
      `?serviceKey=${enc(key)}&keyword=${q(keyword)}&format=json`;
    const body = this._ggBody(await this._get(url));
    if (!body) return [];
    return this._arr(body, 'busStationList').map(it => ({
      stationId: it.stationId || '',
      name: it.stationName || '',
      stopNo: (it.mobileNo || '').trim(),
      area: it.regionName || '',
    }));
  },

  // 경기: 버스도착정보 v2
  async gyeonggi(key, stationId) {
    const url = `https://apis.data.go.kr/6410000/busarrivalservice/v2/getBusArrivalListv2` +
      `?serviceKey=${enc(key)}&stationId=${q(stationId)}&format=json`;
    const body = this._ggBody(await this._get(url));
    if (!body) return [];
    return this._arr(body, 'busArrivalList').map(r => ({
      route: String(r.routeName || r.routeId || ''),
      dest: r.routeDestName ? r.routeDestName + ' 방면' : '',
      first: this._ggEta(r, '1'),
      second: this._ggEta(r, '2'),
    }));
  },

  _ggEta(r, n) {
    const sec = parseInt(r['predictTimeSec' + n]);
    const min = parseInt(r['predictTime' + n]);
    const stops = parseInt(r['locationNo' + n]);
    if (isNaN(sec) && isNaN(min)) {
      return (n === '1' && r.flag === 'WAIT') ? { secs: null, stopsAway: null, text: '회차지 대기' } : null;
    }
    return { secs: isNaN(sec) ? min * 60 : sec, stopsAway: isNaN(stops) ? null : stops, text: '' };
  },

  _ggBody(text) {
    const t = text.trim();
    if (!t.startsWith('{')) {
      const h = parseXmlItems(t, '__none__').header;
      throw new BusError('API 키 오류: ' + (h.returnAuthMsg || h.errMsg || t.slice(0, 80)));
    }
    const o = JSON.parse(t);
    if (o.OpenAPI_ServiceResponse) {
      const h = o.OpenAPI_ServiceResponse.cmmMsgHeader;
      throw new BusError('API 키 오류: ' + ((h && h.returnAuthMsg) || '알 수 없음'));
    }
    const resp = o.response;
    if (!resp) throw new BusError('알 수 없는 응답');
    const code = (resp.msgHeader && resp.msgHeader.resultCode) ?? '0';
    if (String(code) === '4') return null;
    if (String(code) !== '0') throw new BusError((resp.msgHeader && resp.msgHeader.resultMessage) || `경기 API 오류(${code})`);
    return resp.msgBody;
  },

  _arr(body, name) {
    const v = body[name];
    if (Array.isArray(v)) return v;
    if (v && typeof v === 'object') return [v];
    return [];
  },
};

class BusError extends Error {}

// ───────── 지명검색: Photon(OSM·브라우저친화·한글OK) → 카카오(키 있으면) → Open-Meteo ─────────
async function geocode(query, kakaoKey) {
  // 1) 카카오 (키 있으면 최우선 — 한국 POI/주소 최강)
  if (kakaoKey) {
    try {
      const res = await fetch('https://dapi.kakao.com/v2/local/search/keyword.json?query=' + q(query),
        { headers: { Authorization: 'KakaoAK ' + kakaoKey } });
      if (res.ok) {
        const o = await res.json();
        const hits = (o.documents || []).map(d => ({
          label: d.place_name + (d.road_address_name ? ` · ${d.road_address_name}` : d.address_name ? ` · ${d.address_name}` : ''),
          lat: parseFloat(d.y), lon: parseFloat(d.x),
        }));
        if (hits.length) return hits;
      }
    } catch (e) { /* 폴백 */ }
  }
  // 2) Photon (komoot, 키 불필요, 브라우저 UA 차단 안 함, 한글 동네 잘 잡음)
  try {
    const res = await fetch('https://photon.komoot.io/api/?lang=default&limit=10&q=' + q(query));
    if (res.ok) {
      const o = await res.json();
      const seen = new Set();
      const hits = [];
      for (const f of (o.features || [])) {
        const p = f.properties || {};
        if (p.countrycode !== 'KR') continue;
        const c = f.geometry && f.geometry.coordinates;
        if (!c) continue;
        const label = [...new Set([p.name, p.district || p.city || p.county, p.state].filter(Boolean))].join(', ');
        const keyk = label + c[0].toFixed(3);
        if (seen.has(keyk)) continue;
        seen.add(keyk);
        hits.push({ label, lat: c[1], lon: c[0] });
      }
      if (hits.length) return hits;
    }
  } catch (e) { /* 폴백 */ }
  // 3) Open-Meteo (최후)
  try {
    const res = await fetch('https://geocoding-api.open-meteo.com/v1/search?count=6&language=ko&countryCode=KR&name=' + q(query));
    if (res.ok) {
      const o = await res.json();
      return (o.results || []).map(r => ({
        label: [r.name, r.admin2, r.admin1].filter(Boolean).join(', '),
        lat: r.latitude, lon: r.longitude,
      }));
    }
  } catch (e) { /* */ }
  return [];
}
