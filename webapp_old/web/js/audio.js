// Som sintetizado — apenas SFX ligados a eventos do jogo (sem música de fundo).
let ctx = null, master = null;
let muted = localStorage.getItem('dnc-muted') === '1';

function ensure() {
  if (!ctx) {
    ctx = new (window.AudioContext || window.webkitAudioContext)();
    master = ctx.createGain();
    master.gain.value = muted ? 0 : 1;
    master.connect(ctx.destination);
  }
  if (ctx.state === 'suspended') ctx.resume();
}
document.addEventListener('pointerdown', ensure, { once: false });

function env(t, dur, peak = 0.2) {
  const g = ctx.createGain();
  g.gain.setValueAtTime(0.0001, t);
  g.gain.exponentialRampToValueAtTime(peak, t + 0.008);
  g.gain.exponentialRampToValueAtTime(0.0001, t + dur);
  g.connect(master);
  return g;
}

function tone(freq, dur, type = 'sine', peak = 0.2, delay = 0, slideTo = null) {
  if (!ctx || muted) return;
  const t = ctx.currentTime + delay;
  const o = ctx.createOscillator();
  o.type = type; o.frequency.setValueAtTime(freq, t);
  if (slideTo) o.frequency.exponentialRampToValueAtTime(slideTo, t + dur);
  o.connect(env(t, dur, peak));
  o.start(t); o.stop(t + dur + 0.05);
}

function noise(dur, filterFreq, type = 'lowpass', peak = 0.25, delay = 0) {
  if (!ctx || muted) return;
  const t = ctx.currentTime + delay;
  const len = Math.ceil(ctx.sampleRate * dur);
  const buf = ctx.createBuffer(1, len, ctx.sampleRate);
  const d = buf.getChannelData(0);
  for (let i = 0; i < len; i++) d[i] = (Math.random() * 2 - 1) * (1 - i / len);
  const src = ctx.createBufferSource(); src.buffer = buf;
  const f = ctx.createBiquadFilter(); f.type = type; f.frequency.value = filterFreq;
  src.connect(f).connect(env(t, dur, peak));
  src.start(t);
}

export const sfx = {
  click() { ensure(); tone(660, 0.05, 'square', 0.06); },
  move() { ensure(); tone(170, 0.1, 'sine', 0.3, 0, 120); noise(0.06, 900, 'lowpass', 0.18); },
  capture() {
    ensure();
    tone(1250, 0.14, 'sine', 0.22); tone(1870, 0.12, 'sine', 0.14, 0.015);
    noise(0.08, 3200, 'highpass', 0.12); tone(150, 0.12, 'sine', 0.26, 0.02, 100);
  },
  king() {
    ensure();
    [523, 659, 784, 1047].forEach((f, i) => tone(f, 0.3, 'triangle', 0.18, i * 0.09));
  },
  win() {
    ensure();
    [523, 659, 784, 1047, 784, 1047, 1319].forEach((f, i) => tone(f, 0.26, 'triangle', 0.2, i * 0.12));
    noise(0.5, 5000, 'highpass', 0.05, 0.8);
  },
  lose() { ensure(); [392, 330, 262].forEach((f, i) => tone(f, 0.3, 'sine', 0.16, i * 0.16)); },
  draw() { ensure(); tone(440, 0.22, 'triangle', 0.16); tone(440, 0.22, 'triangle', 0.16, 0.22); },
  notify() { ensure(); tone(880, 0.09, 'sine', 0.14); tone(1175, 0.12, 'sine', 0.12, 0.08); },
  emote() { ensure(); tone(500, 0.14, 'sine', 0.16, 0, 1100); },
  error() { ensure(); tone(180, 0.14, 'square', 0.1); tone(140, 0.16, 'square', 0.1, 0.1); },
};

export function isMuted() { return muted; }
export function setMuted(m) {
  muted = m;
  localStorage.setItem('dnc-muted', m ? '1' : '0');
  ensure();
  if (master) master.gain.setTargetAtTime(m ? 0 : 1, ctx.currentTime, 0.03);
}