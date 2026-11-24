// 날씨 (Open-Meteo, 키 불필요) — Android Weather.kt 이식
const WeatherApi = {
  async fetch(lat, lon) {
    const url = `https://api.open-meteo.com/v1/forecast?latitude=${lat}&longitude=${lon}` +
      `&current=temperature_2m,apparent_temperature,weather_code,is_day` +
      `&hourly=precipitation_probability,weather_code` +
      `&daily=weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max` +
      `&forecast_hours=12&forecast_days=7&timezone=Asia%2FSeoul`;
    const res = await fetch(url);
    if (!res.ok) throw new Error('HTTP ' + res.status);
    const o = await res.json();
    const cur = o.current, daily = o.daily, hourly = o.hourly;
    const probs = hourly.precipitation_probability || [];
    const codes = hourly.weather_code || [];
    let maxProb = 0, wetSoon = false;
    for (let i = 0; i < probs.length; i++) {
      const p = probs[i] || 0;
      maxProb = Math.max(maxProb, p);
      if (this.isWet(codes[i] || 0) && p >= 40) wetSoon = true;
    }
    const code = cur.weather_code;
    const dTime = daily.time, dCode = daily.weather_code, dMin = daily.temperature_2m_min,
      dMax = daily.temperature_2m_max, dRain = daily.precipitation_probability_max || [];
    const week = [];
    for (let i = 0; i < dTime.length; i++) {
      if (dMin[i] == null || dMax[i] == null) continue;
      week.push({ date: dTime[i], code: dCode[i] ?? 3, min: dMin[i], max: dMax[i], rainProb: dRain[i] ?? 0 });
    }
    return {
      temp: cur.temperature_2m,
      feels: cur.apparent_temperature,
      code,
      isDay: (cur.is_day ?? 1) === 1,
      min: dMin[0],
      max: dMax[0],
      rainProb12h: maxProb,
      rainSoon: this.isWet(code) || wetSoon || maxProb >= 50,
      fetchedAt: Date.now(),
      week,
    };
  },

  isWet: (c) => (c >= 51 && c <= 67) || (c >= 71 && c <= 77) || (c >= 80 && c <= 86) || (c >= 95 && c <= 99),
  isSnow: (c) => (c >= 71 && c <= 77) || c === 85 || c === 86,

  desc(code) {
    if (code === 0) return '맑음';
    if (code === 1) return '대체로 맑음';
    if (code === 2) return '구름 조금';
    if (code === 3) return '흐림';
    if (code === 45 || code === 48) return '안개';
    if (code >= 51 && code <= 57) return '이슬비';
    if (code >= 61 && code <= 67) return '비';
    if (code >= 71 && code <= 77) return '눈';
    if (code >= 80 && code <= 82) return '소나기';
    if (code === 85 || code === 86) return '소낙눈';
    if (code >= 95 && code <= 99) return '뇌우';
    return '—';
  },

  // 체감온도 기준 옷차림
  outfit(w) {
    const t = w.feels;
    const items = [];
    let headline;
    if (t <= 0) { items.push({ s: SPRITES.LONG_PADDING, label: '롱패딩' }); headline = '롱패딩 입으세요'; }
    else if (t <= 6) { items.push({ s: SPRITES.SHORT_PADDING, label: '숏패딩' }); headline = '숏패딩이면 OK'; }
    else if (t <= 12) { items.push({ s: SPRITES.HOODIE, label: '후디·경량패딩' }); headline = '후디나 경량패딩'; }
    else if (t <= 19) {
      items.push({ s: SPRITES.SWEATSHIRT, label: '맨투맨·가디건' });
      items.push({ s: SPRITES.PANTS, label: '긴바지' });
      headline = '맨투맨·가디건';
    } else if (t < 25) {
      items.push({ s: SPRITES.TSHIRT, label: '반팔' });
      items.push({ s: SPRITES.PANTS, label: '긴바지' });
      headline = '반팔에 긴바지';
    } else {
      items.push({ s: SPRITES.TSHIRT, label: '반팔' });
      items.push({ s: SPRITES.SHORTS, label: '반바지' });
      headline = '반팔·반바지';
    }
    const tips = [];
    if (w.rainSoon) {
      items.push({ s: SPRITES.UMBRELLA, label: '우산' });
      tips.push(this.isSnow(w.code) ? '눈 와요, 우산 챙기세요' : `우산 챙기세요 (최대 강수확률 ${w.rainProb12h}%)`);
    }
    if (w.max - w.min >= 10) tips.push(`일교차 ${Math.round(w.max - w.min)}° — 겉옷 하나 더`);
    return { items, headline, tips };
  },
};
