// Partículas cartoon: grãos de café, vapor e confetes. Um único rAF, ativo só quando há partículas.
const canvas = document.getElementById('fx');
const g = canvas.getContext('2d');
let parts = [], raf = 0, dpr = 1;

function resize() {
  dpr = Math.min(window.devicePixelRatio || 1, 2);
  canvas.width = Math.floor(innerWidth * dpr);
  canvas.height = Math.floor(innerHeight * dpr);
}
addEventListener('resize', resize);
resize();

const BEAN_COLORS = ['#4a2c1a', '#6b4226', '#f0a63c', '#e2503c', '#7fb069', '#5b8bd9'];

function spawn(n, make) {
  for (let i = 0; i < n; i++) parts.push(make(i));
  if (!raf) raf = requestAnimationFrame(tick);
}

function tick() {
  g.setTransform(dpr, 0, 0, dpr, 0, 0);
  g.clearRect(0, 0, innerWidth, innerHeight);
  parts = parts.filter((p) => p.life > 0);
  for (const p of parts) {
    p.life -= p.decay;
    p.x += p.vx; p.y += p.vy;
    p.vy += p.grav; p.vx *= 0.985;
    p.rot += p.vr;
    const a = Math.max(0, Math.min(1, p.life));
    g.save();
    g.globalAlpha = a;
    g.translate(p.x, p.y);
    g.rotate(p.rot);
    if (p.kind === 'bean') {
      g.fillStyle = p.color;
      g.strokeStyle = '#2a1710';
      g.lineWidth = 1.6;
      g.beginPath();
      g.ellipse(0, 0, p.size, p.size * 0.68, 0, 0, Math.PI * 2);
      g.fill(); g.stroke();
      g.beginPath();
      g.moveTo(-p.size * 0.7, 0);
      g.quadraticCurveTo(0, p.size * 0.35, p.size * 0.7, 0);
      g.stroke();
    } else if (p.kind === 'steam') {
      g.fillStyle = 'rgba(246,234,210,.85)';
      g.beginPath();
      g.arc(0, 0, p.size * (1.6 - a * 0.6), 0, Math.PI * 2);
      g.fill();
    } else {
      g.fillStyle = p.color;
      g.strokeStyle = '#2a1710';
      g.lineWidth = 1.4;
      g.fillRect(-p.size / 2, -p.size / 2, p.size, p.size * 0.7);
      g.strokeRect(-p.size / 2, -p.size / 2, p.size, p.size * 0.7);
    }
    g.restore();
  }
  if (parts.length) raf = requestAnimationFrame(tick);
  else { raf = 0; g.clearRect(0, 0, innerWidth, innerHeight); }
}

function boardPoint(el, sq, flip) {
  const r = el.getBoundingClientRect();
  let row = (sq / 8) | 0, col = sq % 8;
  if (flip) { row = 7 - row; col = 7 - col; }
  const cell = r.width / 8;
  return { x: r.left + (col + 0.5) * cell, y: r.top + (row + 0.5) * cell, cell };
}

export function captureBurst(boardEl, sq, flip, color) {
  const { x, y, cell } = boardPoint(boardEl, sq, flip);
  spawn(10, () => ({
    kind: 'bean', x, y,
    vx: (Math.random() - 0.5) * cell * 0.16,
    vy: -Math.random() * cell * 0.14 - 1,
    grav: cell * 0.006, rot: Math.random() * 6, vr: (Math.random() - 0.5) * 0.3,
    size: cell * (0.09 + Math.random() * 0.07),
    color: Math.random() < 0.6 ? color : BEAN_COLORS[(Math.random() * BEAN_COLORS.length) | 0],
    life: 1, decay: 0.022,
  }));
  spawn(4, () => ({
    kind: 'steam', x: x + (Math.random() - 0.5) * cell * 0.3, y,
    vx: (Math.random() - 0.5) * 0.6, vy: -1.2 - Math.random(), grav: -0.01,
    rot: 0, vr: 0, size: cell * 0.1, color: '#fff', life: 1, decay: 0.03,
  }));
}

export function kingSparkle(boardEl, sq, flip) {
  const { x, y, cell } = boardPoint(boardEl, sq, flip);
  spawn(14, () => {
    const ang = Math.random() * Math.PI * 2;
    const sp = cell * (0.05 + Math.random() * 0.1);
    return {
      kind: 'confetti', x, y, vx: Math.cos(ang) * sp, vy: Math.sin(ang) * sp - 1.5,
      grav: cell * 0.005, rot: Math.random() * 6, vr: (Math.random() - 0.5) * 0.4,
      size: cell * 0.12, color: Math.random() < 0.5 ? '#f0a63c' : '#fff8ea',
      life: 1, decay: 0.02,
    };
  });
}

export function winConfetti() {
  spawn(90, () => ({
    kind: Math.random() < 0.55 ? 'bean' : 'confetti',
    x: Math.random() * innerWidth, y: -20 - Math.random() * innerHeight * 0.4,
    vx: (Math.random() - 0.5) * 2.4, vy: 1.5 + Math.random() * 2.6,
    grav: 0.045, rot: Math.random() * 6, vr: (Math.random() - 0.5) * 0.25,
    size: 7 + Math.random() * 8,
    color: BEAN_COLORS[(Math.random() * BEAN_COLORS.length) | 0],
    life: 1, decay: 0.006,
  }));
}