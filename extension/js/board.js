// 설정 저장소 + 보드 공용 헬퍼 — Android Data.kt / Board.kt 이식

const Prefs = {
  _d: { apiKey: '', kakaoKey: '', locName: '', lat: null, lon: null, stops: [], shuttles: [] },
  _useChrome: typeof chrome !== 'undefined' && chrome.storage && chrome.storage.local,

  async load() {
    if (this._useChrome) {
      const o = await chrome.storage.local.get('board');
      if (o && o.board) this._d = Object.assign(this._d, o.board);
    } else {
      try { const s = localStorage.getItem('board'); if (s) this._d = Object.assign(this._d, JSON.parse(s)); } catch (e) {}
    }
    return this;
  },
  _save() {
    if (this._useChrome) chrome.storage.local.set({ board: this._d });
    else { try { localStorage.setItem('board', JSON.stringify(this._d)); } catch (e) {} }
  },
  get apiKey() { return this._d.apiKey || ''; },
  set apiKey(v) { this._d.apiKey = (v || '').trim(); this._save(); },
  get kakaoKey() { return this._d.kakaoKey || ''; },
  set kakaoKey(v) { this._d.kakaoKey = (v || '').trim(); this._save(); },
  get locName() { return this._d.locName || ''; },
  get lat() { return this._d.lat; },
  get lon() { return this._d.lon; },
  setLocation(name, lat, lon) { this._d.locName = name; this._d.lat = lat; this._d.lon = lon; this._save(); },
  get stops() { return this._d.stops || []; },
  set stops(v) { this._d.stops = v; this._save(); },
  get shuttles() { return this._d.shuttles || []; },
  set shuttles(v) { this._d.shuttles = v; this._save(); },
};

const Board = {
  OFFLINE: '인터넷 연결 대기 중… 곧 다시 시도해요',

  friendly(e, prefix = '') {
    if (e instanceof BusError) return e.message || '오류';
    const msg = (e && e.message) || '';
    if (/Failed to fetch|NetworkError|timeout|ENOTFOUND|network/i.test(msg)) return this.OFFLINE;
    return [prefix, msg || '오류'].filter(Boolean).join(': ');
  },

  async loadWeather() {
    if (Prefs.lat == null || Prefs.lon == null) return [null, '설정에서 위치를 정해주세요'];
    try { return [await WeatherApi.fetch(Prefs.lat, Prefs.lon), null]; }
    catch (e) { return [null, this.friendly(e, '날씨를 못 불러왔어요')]; }
  },

  async loadBuses() {
    const key = Prefs.apiKey, stops = Prefs.stops;
    if (!key || !stops.length) return [];
    return Promise.all(stops.map(s => BusApi.fetchStop(key, s)));
  },

  clock(ms) {
    const d = new Date(ms);
    return `${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`;
  },

  remaining(eta, fetchedAt, now = Date.now()) {
    if (eta.secs == null) return null;
    return eta.secs - Math.floor((now - fetchedAt) / 1000);
  },

  etaShort(eta, fetchedAt, withSec = false, now = Date.now()) {
    if (!eta) return '—';
    const r = this.remaining(eta, fetchedAt, now);
    if (r == null) return eta.text || '정보 없음';
    if (r <= 60) return '곧 도착';
    const m = Math.floor(r / 60), s = r % 60;
    return withSec ? `${m}분 ${String(s).padStart(2, '0')}초` : `${m}분`;
  },

  etaClock(eta, fetchedAt) {
    if (!eta || eta.secs == null) return null;
    return this.clock(fetchedAt + eta.secs * 1000);
  },

  stopsText(eta) {
    if (!eta || eta.stopsAway == null || eta.stopsAway <= 0) return '';
    return `${eta.stopsAway}정거장 전`;
  },
};
