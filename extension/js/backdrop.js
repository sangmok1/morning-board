// 날씨 분위기 배경 애니메이션 — Android WeatherBackdrop.kt 이식

function moodOf(code, isDay) {
  if (code === 0 || code === 1 || code === 2) return isDay ? 'CLEAR_DAY' : 'CLEAR_NIGHT';
  if (code === 3) return 'CLOUDY';
  if (code === 45 || code === 48) return 'FOG';
  if ((code >= 71 && code <= 77) || code === 85 || code === 86) return 'SNOW';
  if (code >= 95 && code <= 99) return 'THUNDER';
  if ((code >= 51 && code <= 67) || (code >= 80 && code <= 82)) return 'RAIN';
  return 'CLOUDY';
}

class WeatherBackdrop {
  constructor(canvas) {
    this.canvas = canvas;
    this.ctx = canvas.getContext('2d');
    this.mood = null;
    this.particles = [];
    this.last = 0;
    this.flashUntil = 0;
    this.nextFlash = 0;
    this._raf = null;
    this._seed = 7;
    window.addEventListener('resize', () => this._resize());
    this._resize();
  }

  // 결정적 난수(시드) — 안드로이드 Random(7) 느낌
  _rnd() {
    this._seed = (this._seed * 1103515245 + 12345) & 0x7fffffff;
    return this._seed / 0x7fffffff;
  }

  setMood(m) {
    if (this.mood === m) return;
    this.mood = m;
    this.particles = [];
    this._spawn();
  }

  _resize() {
    const dpr = window.devicePixelRatio || 1;
    this.w = this.canvas.clientWidth;
    this.h = this.canvas.clientHeight;
    this.canvas.width = Math.round(this.w * dpr);
    this.canvas.height = Math.round(this.h * dpr);
    this.ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
  }

  _spawn() {
    const m = this.mood;
    const counts = { RAIN: 90, THUNDER: 110, SNOW: 70, CLEAR_NIGHT: 80, CLOUDY: 5, FOG: 4, CLEAR_DAY: 24 };
    const n = counts[m] || 0;
    for (let i = 0; i < n; i++) {
      let speed, size;
      if (m === 'RAIN' || m === 'THUNDER') { speed = 1.1 + this._rnd() * 0.6; size = 14 + this._rnd() * 14; }
      else if (m === 'SNOW') { speed = 0.05 + this._rnd() * 0.07; size = 1.5 + this._rnd() * 2.5; }
      else if (m === 'CLOUDY' || m === 'FOG') { speed = 0.006 + this._rnd() * 0.01; size = 180 + this._rnd() * 160; }
      else if (m === 'CLEAR_DAY') { speed = 0.015 + this._rnd() * 0.02; size = 1 + this._rnd() * 1.8; }
      else { speed = 0; size = 0.8 + this._rnd() * 1.4; }
      this.particles.push({ x: this._rnd(), y: this._rnd(), speed, size, phase: this._rnd() * 2 * Math.PI });
    }
  }

  start() {
    if (this._raf) return;
    const loop = (ts) => {
      this._frame(ts);
      this._raf = requestAnimationFrame(loop);
    };
    this._raf = requestAnimationFrame(loop);
  }
  stop() { if (this._raf) { cancelAnimationFrame(this._raf); this._raf = null; } }

  _frame(nowMs) {
    const { ctx } = this;
    const w = this.w, h = this.h, m = this.mood;
    if (w <= 0 || h <= 0) return;
    const dt = this.last === 0 ? 0 : Math.min(nowMs - this.last, 200) / 1000;
    this.last = nowMs;
    const t = nowMs / 1000;

    // 배경 그라데이션
    this._bg(ctx, w, h, m);

    const P = this.particles;
    if (m === 'RAIN' || m === 'THUNDER') {
      const slant = 0.18;
      ctx.strokeStyle = 'rgba(169,200,255,0.33)';
      ctx.lineWidth = 1.3;
      ctx.beginPath();
      for (const p of P) {
        p.y += p.speed * dt; p.x += p.speed * dt * slant * h / w;
        if (p.y > 1.05) { p.y = -0.05; p.x = this._rnd() * 1.1 - 0.1; }
        const len = p.size, x = p.x * w, y = p.y * h;
        ctx.moveTo(x, y); ctx.lineTo(x - len * slant, y - len);
      }
      ctx.stroke();
      if (m === 'THUNDER') {
        if (this.nextFlash === 0) this.nextFlash = nowMs + 4000 + this._rnd() * 8000;
        if (nowMs >= this.nextFlash) { this.flashUntil = nowMs + 160; this.nextFlash = nowMs + 6000 + this._rnd() * 12000; }
        if (nowMs < this.flashUntil) { ctx.fillStyle = 'rgba(221,230,255,0.18)'; ctx.fillRect(0, 0, w, h); }
      }
    } else if (m === 'SNOW') {
      ctx.fillStyle = 'rgba(235,242,255,0.59)';
      for (const p of P) {
        p.y += p.speed * dt;
        if (p.y > 1.03) { p.y = -0.03; p.x = this._rnd(); }
        const x = (p.x + 0.015 * Math.sin(t * 0.8 + p.phase)) * w;
        ctx.beginPath(); ctx.arc(x, p.y * h, p.size, 0, 2 * Math.PI); ctx.fill();
      }
    } else if (m === 'CLEAR_NIGHT') {
      for (const p of P) {
        const a = Math.min(220, Math.max(20, Math.round(110 + 100 * Math.sin(t * 1.3 + p.phase * 3))));
        ctx.fillStyle = `rgba(220,228,255,${a / 255})`;
        ctx.beginPath(); ctx.arc(p.x * w, p.y * h * 0.75, p.size, 0, 2 * Math.PI); ctx.fill();
      }
    } else if (m === 'CLEAR_DAY') {
      for (const p of P) {
        p.y -= p.speed * dt;
        if (p.y < -0.02) { p.y = 1.02; p.x = this._rnd(); }
        const a = Math.min(120, Math.max(10, Math.round(60 + 50 * Math.sin(t * 0.9 + p.phase))));
        ctx.fillStyle = `rgba(255,214,140,${a / 255})`;
        const x = (p.x + 0.01 * Math.sin(t * 0.5 + p.phase)) * w;
        ctx.beginPath(); ctx.arc(x, p.y * h, p.size, 0, 2 * Math.PI); ctx.fill();
      }
    } else if (m === 'CLOUDY' || m === 'FOG') {
      const base = m === 'FOG' ? 0.11 : 0.078;
      for (const p of P) {
        p.x += p.speed * dt;
        const r = p.size;
        if (p.x * w - r > w) { p.x = -r / w; p.y = this._rnd(); }
        const cx = p.x * w, cy = p.y * h;
        const g = ctx.createRadialGradient(cx, cy, 0, cx, cy, r);
        g.addColorStop(0, `rgba(255,255,255,${base})`);
        g.addColorStop(1, 'rgba(255,255,255,0)');
        ctx.fillStyle = g;
        ctx.beginPath(); ctx.arc(cx, cy, r, 0, 2 * Math.PI); ctx.fill();
      }
    }
  }

  _bg(ctx, w, h, m) {
    const lin = (top, bot) => { const g = ctx.createLinearGradient(0, 0, 0, h); g.addColorStop(0, top); g.addColorStop(1, bot); return g; };
    const glow = (cx, cy, r, color) => { const g = ctx.createRadialGradient(cx, cy, 0, cx, cy, r); g.addColorStop(0, color); g.addColorStop(1, 'rgba(0,0,0,0)'); return g; };
    const fill = (style) => { ctx.fillStyle = style; ctx.fillRect(0, 0, w, h); };
    switch (m) {
      case 'CLEAR_DAY': fill(lin('#15325A', '#0E1622')); fill(glow(w * 0.92, h * 0.02, w * 0.55, 'rgba(255,184,77,0.4)')); break;
      case 'CLEAR_NIGHT': fill(lin('#0B1030', '#07080F')); fill(glow(w * 0.85, h * 0.08, w * 0.3, 'rgba(175,198,255,0.2)')); break;
      case 'CLOUDY': fill(lin('#2A303B', '#12151B')); break;
      case 'RAIN': fill(lin('#1B2838', '#0B0F15')); break;
      case 'SNOW': fill(lin('#2C3A4E', '#141A24')); fill(glow(w * 0.5, h * 1.1, w * 0.7, 'rgba(255,255,255,0.13)')); break;
      case 'FOG': fill(lin('#33373E', '#181A1E')); break;
      case 'THUNDER': fill(lin('#1E1830', '#0A0910')); break;
      default: fill('#0F1115');
    }
  }
}
