import {
  initialState, legalMoves, applyMove, winnerIfOver, pieceCount,
  moveNotation, other, rowOf, colOf, idx,
} from './engine.js';
import { fetchRooms, fetchInfo, createRoom, joinRoom, resolveCode, RoomChannel, scanNetwork, mesaCode, parseMesaInput, subnetPrefix } from './net.js';
import { sfx, isMuted, setMuted } from './audio.js';
import { captureBurst, kingSparkle, winConfetti } from './fx.js';

const $ = (s) => document.querySelector(s);
const AVATARS = [
  { id: 'cafe', e: '☕' }, { id: 'croissant', e: '🥐' }, { id: 'cupcake', e: '🧁' },
  { id: 'donut', e: '🍩' }, { id: 'bule', e: '🫖' }, { id: 'bolo', e: '🍰' },
  { id: 'gato', e: '🐈' }, { id: 'mocho', e: '🦉' }, { id: 'chapeu', e: '🎩' },
  { id: 'barista', e: '🧑‍🍳' }, { id: 'planta', e: '🌿' }, { id: 'fogo', e: '🔥' },
];
const COLORS = [
  { id: 'leite', name: 'Leite', hex: '#f2e8d5' },
  { id: 'cafe', name: 'Café', hex: '#4a2c1a' },
  { id: 'caramelo', name: 'Caramelo', hex: '#f0a63c' },
  { id: 'matcha', name: 'Matcha', hex: '#7fb069' },
  { id: 'framboesa', name: 'Framboesa', hex: '#e0526e' },
  { id: 'mirtilo', name: 'Mirtilo', hex: '#5b8bd9' },
  { id: 'uva', name: 'Uva', hex: '#9b6dd6' },
  { id: 'menta', name: 'Menta', hex: '#4ecdc4' },
];
const EMOTES = ['☕', '👏', '😂', '🔥'];
const avatarById = (id) => AVATARS.find((a) => a.id === id) || AVATARS[0];
const colorById = (id) => COLORS.find((c) => c.id === id) || COLORS[0];
const crownInk = (hex) => {
  const n = parseInt(hex.slice(1), 16);
  const lum = 0.299 * (n >> 16) + 0.587 * ((n >> 8) & 255) + 0.114 * (n & 255);
  return lum > 150 ? '#2a1710' : '#f6ead2';
};

const profile = {
  name: localStorage.getItem('dnc-name') || '',
  avatar: localStorage.getItem('dnc-avatar') || 'cafe',
};

const S = {
  screen: 'home', mode: null, role: null, mySide: 'w',
  roomName: '', players: { w: { name: '', avatar: 'cafe' }, b: { name: '', avatar: 'croissant' } },
  colors: { w: 'leite', b: 'cafe' },
  ready: { w: false, b: false },
  board: null, turn: 'w', legal: null, entities: [], sel: null,
  lastMove: null, history: [], capturedBy: { w: [], b: [] },
  quietKingPlies: 0, result: null, net: null, peerLost: false,
};

/* ─────────── utilidades de UI ─────────── */
function show(name) {
  S.screen = name;
  document.querySelectorAll('.screen').forEach((s) => s.classList.add('hidden'));
  $('#screen-' + name).classList.remove('hidden');
  window.scrollTo({ top: 0 });
}
function toast(msg, type = '') {
  const t = document.createElement('div');
  t.className = 'toast ' + type;
  t.textContent = msg;
  $('#toasts').appendChild(t);
  setTimeout(() => { t.classList.add('out'); setTimeout(() => t.remove(), 260); }, 2600);
}
let modalDismissable = true;
function openModal(html, dismissable = true) {
  modalDismissable = dismissable;
  $('#modalCard').innerHTML = html;
  $('#modal').classList.remove('hidden');
}
function closeModal() { $('#modal').classList.add('hidden'); }
$('#modal').addEventListener('click', (e) => {
  if (e.target === $('#modal') && modalDismissable) { sfx.click(); closeModal(); }
});
async function copyText(text, btn) {
  try {
    await navigator.clipboard.writeText(text);
  } catch {
    const ta = document.createElement('textarea');
    ta.value = text; document.body.appendChild(ta);
    ta.select(); document.execCommand('copy'); ta.remove();
  }
  const old = btn.textContent;
  btn.textContent = 'Copiado ✓';
  setTimeout(() => { btn.textContent = old; }, 1400);
  sfx.click();
}
function setChip(text, cls) {
  const chip = $('#connChip');
  if (!text) { chip.classList.add('hidden'); return; }
  chip.classList.remove('hidden', 'warn', 'bad');
  if (cls) chip.classList.add(cls);
  chip.textContent = text;
}
const flip = () => S.mode === 'online' && S.mySide === 'b';
const dispRC = (sq) => {
  const r = rowOf(sq), c = colOf(sq);
  return flip() ? [7 - r, 7 - c] : [r, c];
};

/* ─────────── som ─────────── */
function paintSound() {
  const m = isMuted();
  $('#soundBtn').setAttribute('aria-pressed', String(!m));
  $('#soundBtn').setAttribute('aria-label', m ? 'Som desligado' : 'Som ligado');
  $('#soundOnIcon').classList.toggle('hidden', m);
  $('#soundOffIcon').classList.toggle('hidden', !m);
}
$('#soundBtn').addEventListener('click', () => { setMuted(!isMuted()); paintSound(); if (!isMuted()) sfx.click(); });
paintSound();

/* ─────────── perfil / avatares ─────────── */
function renderAvatarRow(container, selected, onPick) {
  container.innerHTML = '';
  for (const a of AVATARS) {
    const b = document.createElement('button');
    b.className = 'avatar-opt' + (a.id === selected ? ' sel' : '');
    b.textContent = a.e;
    b.title = a.id;
    b.setAttribute('role', 'option');
    b.setAttribute('aria-selected', String(a.id === selected));
    b.addEventListener('click', () => { sfx.click(); onPick(a.id); });
    container.appendChild(b);
  }
}
function pickHomeAvatar(id) {
  profile.avatar = id;
  localStorage.setItem('dnc-avatar', id);
  renderAvatarRow($('#avatarRow'), id, pickHomeAvatar);
}
renderAvatarRow($('#avatarRow'), profile.avatar, pickHomeAvatar);
$('#nameInput').value = profile.name;
$('#nameInput').addEventListener('input', () => {
  profile.name = $('#nameInput').value.trim();
  localStorage.setItem('dnc-name', profile.name);
});
const myName = () => profile.name || 'Jogador';

/* ─────────── regras ─────────── */
const RULES_HTML = `
  <h2>📜 Regras de Portugal</h2>
  <h3>O essencial</h3>
  <ul>
    <li>Tabuleiro 8×8, 12 peças por jogador, jogadas nas casas escuras. <b>Brancas começam.</b></li>
    <li>O peão anda 1 casa na diagonal para a frente.</li>
    <li>O peão <b>captura para a frente e para trás</b>, saltando por cima da peça adversária.</li>
    <li>A dama é <b>voadora</b>: desloca-se e captura à distância ao longo da diagonal.</li>
  </ul>
  <h3>Capturas</h3>
  <ul>
    <li><b>Capturar é obrigatório.</b></li>
    <li><b>Lei da maioria:</b> tens de escolher o lance que captura mais peças.</li>
    <li>Podes saltar várias peças no mesmo lance; as capturadas só saem no fim e não se saltam duas vezes.</li>
    <li>Peão que chega à última linha vira dama — mas só se o lance <b>terminar</b> aí.</li>
  </ul>
  <h3>Fim de jogo</h3>
  <ul>
    <li>Perde quem fica sem peças ou sem jogadas legais.</li>
    <li>Empate por acordo, ou após 25 jogadas seguidas só de damas sem capturas.</li>
  </ul>
  <h3>As mesas</h3>
  <ul>
    <li>Todos no mesmo Wi-Fi do café. Uma máquina corre o anunciador (<code>node server.mjs</code>) e os outros abrem o endereço dele.</li>
    <li>Cria uma mesa: fica anunciada na rede com um nº (o último número do IP) e uma palavra, ex.: <b>mesa donut</b>.</li>
    <li>O teu amigo vê a mesa na lista e entra num toque — ou escreve só o nº/palavra na caixa.</li>
    <li>Em cada novo jogo, cada jogador escolhe a cor das suas peças.</li>
  </ul>
  <button class="btn btn-primary full" id="rulesClose">Fechar</button>`;
function openRules() {
  sfx.click();
  openModal(RULES_HTML);
  $('#rulesClose').addEventListener('click', () => { sfx.click(); closeModal(); });
}
$('#btnRulesHome').addEventListener('click', openRules);
$('#btnRulesGame').addEventListener('click', openRules);

/* ─────────── navegação base ─────────── */
function resetToHome() {
  stopSearch();
  S.net?.close();
  S.net = null; S.mode = null; S.role = null; S.peerLost = false;
  S.result = null; S.sel = null;
  setChip(null);
  closeModal();
  show('home');
}
document.querySelectorAll('.back-home').forEach((b) => b.addEventListener('click', () => { sfx.click(); resetToHome(); }));

$('#btnCreate').addEventListener('click', () => {
  sfx.click();
  $('#roomNameInput').value = `Mesa de ${myName()}`;
  $('#hostWaiting').classList.add('hidden');
  show('create');
});
$('#btnJoin').addEventListener('click', () => {
  sfx.click();
  $('#mesaInput').value = '';
  show('join');
  loadRooms();
});
$('#btnLocal').addEventListener('click', () => {
  sfx.click();
  S.mode = 'local'; S.role = null; S.mySide = 'w';
  S.roomName = 'Mesa deste dispositivo';
  S.players = {
    w: { name: myName(), avatar: profile.avatar },
    b: { name: 'Jogador 2', avatar: 'croissant' },
  };
  S.colors = { w: 'leite', b: 'cafe' };
  S.ready = { w: false, b: false };
  enterLobby();
});

/* ─────────── mesas: anunciador local ou relay na nuvem ─────────── */
const PORT = Number(location.port) || 8742;
let RELAY = false;                       // true = relay do Netlify (mesas pela internet)
const POLL = () => (RELAY ? 1200 : 350); // na nuvem cada poll custa uma invocação
async function detectAnnouncer() {
  try { RELAY = (await fetchInfo('')).relay === 'cloud'; } catch { RELAY = false; }
  document.querySelectorAll('[data-lan-only]').forEach((el) => el.classList.toggle('hidden', RELAY));
  document.querySelectorAll('[data-cloud-only]').forEach((el) => el.classList.toggle('hidden', !RELAY));
  const scan = $('#btnScanNet');
  if (scan) scan.textContent = RELAY ? '🔄 Atualizar lista de mesas' : '🔍 Procurar mesas noutras máquinas da rede';
}
detectAnnouncer();
function wireNet(net) {
  net.onOpen = () => {
    setChip('Ligado ✓');
    sfx.notify();
    net.send({ t: 'hello', name: myName(), avatar: profile.avatar, roomName: S.role === 'host' ? S.roomName : undefined });
    S.players[S.mySide] = { name: myName(), avatar: profile.avatar };
    enterLobby();
  };
  net.onState = (st) => {
    if (st === 'waiting') setChip('À espera…', 'warn');
    else if (st === 'connecting') setChip('A ligar…', 'warn');
    else if (st === 'connected') setChip('Ligado ✓');
  };
  net.onClose = () => {
    if (S.screen === 'home') return;
    S.peerLost = true;
    setChip('Ligação perdida', 'bad');
    if (S.screen === 'game' && !S.result) {
      openModal(`
        <div class="end-hero"><span class="end-emoji">📡</span>
        <h2 class="end-title">Ligação perdida</h2>
        <p class="end-sub">O teu amigo desligou-se ou a rede caiu.</p></div>
        <div class="modal-actions"><button class="btn btn-primary full" id="lostHome">Voltar ao início</button></div>`, false);
      $('#lostHome').addEventListener('click', resetToHome);
    } else {
      toast('O teu amigo saiu da mesa.', 'warn');
      resetToHome();
    }
  };
  net.onMessage = onNetMessage;
}

async function hostOctet() {
  const h = location.hostname;
  if (/^\d+\.\d+\.\d+\.\d+$/.test(h) && !h.startsWith('127.')) return Number(h.split('.')[3]);
  try {
    const info = await fetchInfo('');
    return subnetPrefix(info.lanIp) ? Number(info.lanIp.split('.')[3]) : null;
  } catch { return null; }
}

$('#btnOpenRoom').addEventListener('click', async () => {
  const name = $('#roomNameInput').value.trim() || `Mesa de ${myName()}`;
  S.roomName = name;
  S.mode = 'online'; S.role = 'host'; S.mySide = 'w';
  S.colors = { w: 'leite', b: 'cafe' };
  S.ready = { w: false, b: false };
  const btn = $('#btnOpenRoom');
  btn.disabled = true; btn.textContent = 'A anunciar…';
  try {
    S.net?.close();
    const { id, code } = await createRoom('', name, { name: myName(), avatar: profile.avatar });
    S.net = new RoomChannel('', id, 'h', POLL());
    wireNet(S.net);
    S.net.start();
    const n = code || await hostOctet();
    $('#mesaBadge').innerHTML = n
      ? `Mesa nº ${n} · <b>${mesaCode(n).toUpperCase()}</b>`
      : `Mesa ${id}`;
    const url = location.origin;
    $('#shareUrl').textContent = url;
    $('#hostWaiting').classList.remove('hidden');
    $('#hostStatus').textContent = RELAY
      ? 'Mesa aberta — à espera que alguém entre…'
      : 'Mesa anunciada na rede — à espera que alguém entre…';
    $('#hostStatus').className = 'status';
    sfx.notify();
  } catch (e) {
    toast('Não foi possível abrir a mesa: ' + e.message, 'warn');
  }
  btn.disabled = false; btn.textContent = 'Anunciar mesa';
});
$('#btnCopyUrl').addEventListener('click', (e) => copyText($('#shareUrl').textContent, e.currentTarget));

/* ─────────── entrar: lista de mesas + nº/palavra ─────────── */
const roomCache = new Map();
let searchToken = 0;
function stopSearch() { searchToken++; }
function roomCode(r, base) {
  if (r?.code) return r.code;                    // relay na nuvem atribui nº próprio
  const h = base ? new URL(base).hostname : location.hostname;
  return /^\d+\.\d+\.\d+\.\d+$/.test(h) && !h.startsWith('127.') ? Number(h.split('.')[3]) : null;
}
function renderRoomList() {
  const list = $('#roomList');
  list.innerHTML = '';
  const items = [...roomCache.values()].sort((a, b) =>
    (a.r.status === 'waiting' ? 0 : 1) - (b.r.status === 'waiting' ? 0 : 1) || a.r.created - b.r.created);
  if (!items.length) {
    list.innerHTML = '<p class="empty-list">Ainda não há mesas anunciadas…</p>';
    return;
  }
  for (const { base, r } of items) {
    const joinable = r.status === 'waiting';
    const n = roomCode(r, base);
    const row = document.createElement('div');
    row.className = 'room-row';
    row.innerHTML = `
      <span class="room-avatar">${avatarById(r.host?.avatar).e}</span>
      <div class="room-info">
        <b>${escapeHtml(r.name)}</b>
        <span class="room-meta">${escapeHtml(r.host?.name || 'Jogador')}${n ? ` · mesa <b>${mesaCode(n)}</b> (nº ${n})` : ''} · ${joinable ? 'à espera de jogador' : 'a jogar'}</span>
      </div>
      <button class="btn btn-small ${joinable ? 'btn-primary' : 'btn-ghost'}" ${joinable ? '' : 'disabled'}>${joinable ? 'Entrar' : 'Cheia'}</button>`;
    row.querySelector('button').addEventListener('click', () => enterRoom(base, r));
    list.appendChild(row);
  }
}
async function loadRooms() {
  const my = ++searchToken;
  roomCache.clear();
  renderRoomList();
  $('#scanStatus').textContent = 'A procurar mesas…';
  try {
    for (const r of await fetchRooms('')) roomCache.set(r.id + ':' + r.created, { base: '', r });
  } catch { /* sem anunciador nesta página */ }
  if (my !== searchToken) return;
  renderRoomList();
  $('#scanStatus').textContent = roomCache.size
    ? (RELAY ? `${roomCache.size} mesa(s) aberta(s).` : `${roomCache.size} mesa(s) nesta máquina.`)
    : (RELAY ? 'Ainda não há mesas abertas — cria uma tu.'
             : 'Sem mesas nesta máquina — procura na rede ou escreve o nº da mesa.');
  if (RELAY) setTimeout(() => { if (my === searchToken && S.screen === 'join') loadRooms(); }, 5000);
}
$('#btnScanNet').addEventListener('click', async () => {
  sfx.click();
  if (RELAY) { loadRooms(); return; }   // na nuvem a lista já é global
  const my = ++searchToken;
  const btn = $('#btnScanNet');
  btn.disabled = true;
  const bases = await scanNetwork(PORT, (done, total) => {
    if (my === searchToken) $('#scanStatus').textContent = `A sondar a rede Wi-Fi… ${done}/${total}`;
  });
  if (my === searchToken) {
    for (const b of bases) {
      if (b === location.origin) continue;
      try {
        for (const r of await fetchRooms(b)) roomCache.set(r.id + ':' + r.created, { base: b, r });
      } catch { /* máquina sem mesas */ }
    }
    renderRoomList();
    $('#scanStatus').textContent = roomCache.size
      ? `${roomCache.size} mesa(s) encontradas na rede.`
      : 'Nenhuma mesa anunciada nesta rede.';
  }
  btn.disabled = false;
});
async function enterRoom(base, r) {
  sfx.click();
  S.mode = 'online'; S.role = 'guest'; S.mySide = 'b';
  S.roomName = r.name;
  S.colors = { w: 'leite', b: 'cafe' };
  S.ready = { w: false, b: false };
  try {
    await joinRoom(base, r.id, { name: myName(), avatar: profile.avatar });
    S.net?.close();
    S.net = new RoomChannel(base, r.id, 'g', POLL());
    wireNet(S.net);
    S.net.start();
    setChip('A ligar…', 'warn');
  } catch (e) {
    toast('Não foi possível entrar: ' + e.message, 'warn');
    loadRooms();
  }
}
$('#btnDirect').addEventListener('click', async () => {
  const n = parseMesaInput($('#mesaInput').value);
  if (!n) { toast('Escreve o nº da mesa (1–254) ou a palavra, ex.: donut.', 'warn'); sfx.error(); return; }
  if (RELAY) {
    sfx.click();
    try {
      const { id } = await resolveCode('', n);
      const r = (await fetchRooms('')).find((x) => x.id === id);
      if (!r) { toast('Essa mesa já fechou.', 'warn'); sfx.error(); return; }
      if (r.status !== 'waiting') { toast('Essa mesa já está a jogar.', 'warn'); sfx.error(); return; }
      enterRoom('', r);
    } catch (e) {
      toast(`Não encontrei a mesa ${mesaCode(n)} (nº ${n}): ${e.message}`, 'warn');
      sfx.error();
    }
    return;
  }
  let sub = subnetPrefix(location.hostname);
  if (!sub) {
    try { sub = subnetPrefix((await fetchInfo('')).lanIp); } catch { /* sem info */ }
  }
  if (!sub) { toast('Não consigo descobrir a rede Wi-Fi a partir desta página.', 'warn'); return; }
  const base = `http://${sub}.${n}:${PORT}`;
  sfx.click();
  try {
    const rooms = await fetchRooms(base);
    const open = rooms.filter((r) => r.status === 'waiting');
    if (!open.length) {
      toast(rooms.length ? 'Essa mesa já está a jogar.' : 'Essa máquina não tem mesas abertas.', 'warn');
      return;
    }
    enterRoom(base, open[0]);
  } catch {
    toast(`Não encontrei a mesa ${n} (${mesaCode(n)}). Confirma o nº e se estão no mesmo Wi-Fi.`, 'warn');
    sfx.error();
  }
});
$('#mesaInput').addEventListener('keydown', (e) => { if (e.key === 'Enter') $('#btnDirect').click(); });

/* ─────────── lobby / cores ─────────── */
function enterLobby() {
  S.ready = { w: false, b: false };
  renderLobby();
  show('lobby');
}
function renderColorRow(container, selected, takenId, onPick) {
  container.innerHTML = '';
  for (const c of COLORS) {
    const b = document.createElement('button');
    b.className = 'color-opt' + (c.id === selected ? ' sel' : '') + (c.id === takenId ? ' taken' : '');
    b.style.background = c.hex;
    b.title = c.name;
    b.setAttribute('aria-label', c.name);
    b.addEventListener('click', () => {
      if (c.id === takenId) { sfx.error(); toast('Essa cor já é do teu amigo.'); return; }
      sfx.click(); onPick(c.id);
    });
    container.appendChild(b);
  }
}
function playerHead(side) {
  const p = S.players[side];
  return `<div class="lp-head">
    <span class="lp-avatar">${avatarById(p.avatar).e}</span>
    <span class="lp-name">${escapeHtml(p.name || 'Jogador')}</span>
    <span class="lp-side">${side === 'w' ? 'brancas' : 'pretas'}</span>
    <span class="ready-badge ${S.ready[side] ? 'on' : ''}">${S.ready[side] ? 'pronto ✓' : 'a escolher'}</span>
  </div>`;
}
function escapeHtml(s) {
  return String(s).replace(/[&<>"']/g, (m) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[m]));
}
function renderLobby() {
  $('#lobbyRoomName').textContent = S.roomName;
  const wrap = $('#lobbyPlayers');
  wrap.innerHTML = '';
  const pickWrap = $('#colorPickWrap');
  const readyBtn = $('#btnReady');

  if (S.mode === 'local') {
    pickWrap.classList.add('hidden');
    for (const side of ['w', 'b']) {
      const card = document.createElement('div');
      card.className = 'lobby-player';
      card.innerHTML = playerHead(side) + `
        <div class="lp-edit">
          <input class="input lp-name-input" maxlength="14" value="${escapeHtml(S.players[side].name)}" aria-label="Nome do jogador ${side === 'w' ? 1 : 2}">
          <div class="avatar-row lp-avatars"></div>
          <div class="color-row lp-colors"></div>
        </div>`;
      wrap.appendChild(card);
      card.querySelector('.lp-name-input').addEventListener('input', (e) => {
        S.players[side].name = e.target.value.trim() || (side === 'w' ? 'Jogador 1' : 'Jogador 2');
        card.querySelector('.lp-name').textContent = S.players[side].name;
      });
      const pickAvatar = (id) => {
        S.players[side].avatar = id;
        card.querySelector('.lp-avatar').textContent = avatarById(id).e;
        renderAvatarRow(card.querySelector('.lp-avatars'), id, pickAvatar);
      };
      renderAvatarRow(card.querySelector('.lp-avatars'), S.players[side].avatar, pickAvatar);
      renderColorRow(card.querySelector('.lp-colors'), S.colors[side], S.colors[other(side)], (id) => {
        S.colors[side] = id;
        renderLobby();
      });
    }
    readyBtn.textContent = 'Começar o jogo ✓';
    readyBtn.disabled = S.colors.w === S.colors.b;
    $('#lobbyStatus').textContent = S.colors.w === S.colors.b ? 'Escolham cores diferentes.' : 'Cada jogador escolhe o nome, o avatar e a cor das peças.';
  } else {
    pickWrap.classList.remove('hidden');
    const me = S.mySide, opp = other(me);
    const oppCard = document.createElement('div');
    oppCard.className = 'lobby-player';
    oppCard.innerHTML = playerHead(opp) + `
      <div class="lp-chosen"><span class="swatch" style="background:${colorById(S.colors[opp]).hex}"></span>
      ${S.ready[opp] ? 'Pronto para jogar' : 'A escolher as peças…'}</div>`;
    wrap.appendChild(oppCard);
    const meCard = document.createElement('div');
    meCard.className = 'lobby-player me';
    meCard.innerHTML = playerHead(me);
    wrap.appendChild(meCard);
    renderColorRow($('#colorRow'), S.colors[me], S.colors[opp], (id) => {
      S.colors[me] = id;
      S.ready[me] = false;
      S.net.send({ t: 'pick', color: id });
      renderLobby();
    });
    readyBtn.textContent = S.ready[me] ? 'Pronto ✓ à espera do amigo…' : 'Estou pronto ✓';
    readyBtn.disabled = S.ready[me];
    $('#lobbyStatus').textContent = S.ready[me] ? 'O jogo começa quando os dois estiverem prontos.' : 'Escolhe a cor das tuas peças para este jogo.';
  }
}
$('#btnReady').addEventListener('click', () => {
  sfx.click();
  if (S.mode === 'local') {
    beginGame({ ...S.colors });
    return;
  }
  const me = S.mySide;
  S.ready[me] = true;
  S.net.send({ t: 'ready', color: S.colors[me] });
  renderLobby();
  maybeHostStart();
});
function maybeHostStart() {
  if (S.role !== 'host' || !S.ready.w || !S.ready.b) return;
  const colors = { ...S.colors };
  if (colors.w === colors.b) {
    colors.b = COLORS.find((c) => c.id !== colors.w).id;
  }
  S.net.send({ t: 'start', colors });
  beginGame(colors);
}
document.querySelectorAll('.leave-room').forEach((b) => b.addEventListener('click', () => {
  sfx.click();
  confirmLeave();
}));

/* ─────────── mensagens do amigo ─────────── */
function onNetMessage(m) {
  switch (m.t) {
    case 'hello': {
      const opp = other(S.mySide);
      S.players[opp] = { name: m.name || 'Jogador', avatar: m.avatar || 'cafe' };
      if (m.roomName) S.roomName = m.roomName;
      if (S.screen === 'lobby') renderLobby();
      break;
    }
    case 'pick':
      S.colors[other(S.mySide)] = m.color;
      S.ready[other(S.mySide)] = false;
      if (S.screen === 'lobby') renderLobby();
      break;
    case 'ready':
      S.colors[other(S.mySide)] = m.color;
      S.ready[other(S.mySide)] = true;
      if (S.screen === 'lobby') renderLobby();
      maybeHostStart();
      break;
    case 'start':
      S.colors = { ...m.colors };
      beginGame(m.colors);
      break;
    case 'move': {
      if (S.screen !== 'game' || S.result) break;
      const mv = S.legal.moves.find((x) =>
        x.from === m.from && x.to === m.to && x.captures.join(',') === (m.captures || []).join(','));
      if (mv) doMove(mv, false);
      break;
    }
    case 'rematch':
      toast('O teu amigo quer outro jogo!');
      enterLobby();
      break;
    case 'emote':
      floatEmote(m.e);
      break;
    case 'drawOffer':
      sfx.notify();
      openModal(`
        <div class="end-hero"><span class="end-emoji">🤝</span>
        <h2 class="end-title">Proposta de empate</h2>
        <p class="end-sub">${escapeHtml(S.players[other(S.mySide)].name)} propõe empate. Aceitas?</p></div>
        <div class="modal-actions">
          <button class="btn btn-matcha full" id="drawYes">Aceitar empate</button>
          <button class="btn btn-ghost full" id="drawNo">Continuar a jogar</button>
        </div>`, false);
      $('#drawYes').addEventListener('click', () => {
        S.net.send({ t: 'drawResponse', accept: true });
        closeModal(); endGame({ type: 'draw', reason: 'Empate por acordo.' });
      });
      $('#drawNo').addEventListener('click', () => {
        S.net.send({ t: 'drawResponse', accept: false });
        closeModal();
      });
      break;
    case 'drawResponse':
      if (m.accept) { closeModal(); endGame({ type: 'draw', reason: 'Empate por acordo.' }); }
      else toast('O teu amigo recusou o empate.', 'warn');
      break;
    case 'leave':
      toast('O teu amigo saiu da mesa.', 'warn');
      resetToHome();
      break;
  }
}

/* ─────────── tabuleiro ─────────── */
const boardEl = $('#board');
let pieceUid = 0;
function buildBoard() {
  boardEl.innerHTML = '';
  for (let dr = 0; dr < 8; dr++) {
    for (let dc = 0; dc < 8; dc++) {
      const r = flip() ? 7 - dr : dr, c = flip() ? 7 - dc : dc;
      const sq = document.createElement('div');
      sq.className = 'square ' + ((r + c) % 2 === 1 ? 'dark' : 'light');
      sq.dataset.sq = idx(r, c);
      if (dr === 7) {
        const f = document.createElement('span');
        f.className = 'coord file'; f.textContent = 'abcdefgh'[c];
        sq.appendChild(f);
      }
      if (dc === 0) {
        const k = document.createElement('span');
        k.className = 'coord rank'; k.textContent = String(8 - r);
        sq.appendChild(k);
      }
      boardEl.appendChild(sq);
    }
  }
  const layer = document.createElement('div');
  layer.className = 'pieces';
  boardEl.appendChild(layer);
  S.entities = [];
  for (let i = 0; i < 64; i++) {
    const p = S.board[i];
    if (!p) continue;
    const el = document.createElement('div');
    el.className = 'piece';
    const hex = colorById(S.colors[p.c]).hex;
    el.style.setProperty('--pc', hex);
    el.innerHTML = `<div class="disc"></div>
      <svg class="crown" viewBox="0 0 24 24"><path d="M3 18h18l-1.6-9-4.4 3.4L12 5l-3 7.4L4.6 9 3 18z" fill="${crownInk(hex)}" stroke="#2a1710" stroke-width="1.6" stroke-linejoin="round"/></svg>`;
    layer.appendChild(el);
    S.entities.push({ id: pieceUid++, c: p.c, k: p.k, sq: i, el });
    placeEntity(S.entities[S.entities.length - 1], false);
  }
}
function placeEntity(en, animate = true) {
  const [r, c] = dispRC(en.sq);
  if (!animate) en.el.style.transition = 'none';
  en.el.style.left = c * 12.5 + '%';
  en.el.style.top = r * 12.5 + '%';
  en.el.classList.toggle('king', en.k);
  if (!animate) requestAnimationFrame(() => { en.el.style.transition = ''; });
}
const canPlay = () => !S.result && !S.peerLost && (S.mode === 'local' || (S.turn === S.mySide && S.net?.open));

function paintBoard() {
  boardEl.querySelectorAll('.square').forEach((sq) => {
    const i = Number(sq.dataset.sq);
    sq.classList.toggle('last', !!S.lastMove && (i === S.lastMove.from || i === S.lastMove.to));
    sq.classList.remove('hint', 'capture');
  });
  if (S.sel) {
    const next = new Set();
    for (const m of S.sel.seqs) {
      if (m.path.length > S.sel.prefix.length &&
          S.sel.prefix.every((v, j) => m.path[j] === v)) {
        next.add(m.path[S.sel.prefix.length]);
      }
    }
    for (const i of next) {
      const sq = boardEl.querySelector(`.square[data-sq="${i}"]`);
      sq?.classList.add('hint');
      if (S.legal.mustCapture) sq?.classList.add('capture');
    }
  }
  const urgeFroms = new Set();
  if (S.legal?.mustCapture && canPlay() && !S.sel) {
    for (const m of S.legal.moves) urgeFroms.add(m.from);
  }
  for (const en of S.entities) {
    en.el.classList.toggle('sel', !!S.sel && en.sq === S.sel.from && !en.el.classList.contains('dying'));
    en.el.classList.toggle('urge', urgeFroms.has(en.sq));
  }
}
boardEl.addEventListener('click', (e) => {
  if (S.screen !== 'game' || S.result) return;
  const rect = boardEl.getBoundingClientRect();
  const dc = Math.floor((e.clientX - rect.left) / (rect.width / 8));
  const dr = Math.floor((e.clientY - rect.top) / (rect.height / 8));
  if (dc < 0 || dc > 7 || dr < 0 || dr > 7) return;
  const r = flip() ? 7 - dr : dr, c = flip() ? 7 - dc : dc;
  handleSquare(idx(r, c));
});
function handleSquare(i) {
  if (!canPlay()) {
    if (S.mode === 'online' && !S.peerLost) { toast('Não é a tua vez.'); sfx.error(); }
    return;
  }
  const p = S.board[i];
  if (S.sel) {
    const prefix = S.sel.prefix;
    if (prefix.length > 1 && i === prefix[prefix.length - 2]) {
      prefix.pop();
      sfx.click(); paintBoard(); return;
    }
    const matching = S.sel.seqs.filter((m) => prefix.every((v, j) => m.path[j] === v));
    const isLanding = matching.some((m) => m.path.length > prefix.length && m.path[prefix.length] === i);
    if (isLanding) {
      prefix.push(i);
      const done = matching.find((m) => m.path.length === prefix.length &&
        prefix.every((v, j) => m.path[j] === v));
      if (done) { const mv = done; S.sel = null; doMove(mv, true); }
      else { sfx.click(); paintBoard(); }
      return;
    }
    if (i === S.sel.from && prefix.length === 1) { S.sel = null; sfx.click(); paintBoard(); return; }
    if (prefix.length > 1) { shakeBoard(); return; }
  }
  if (p && p.c === S.turn) {
    const seqs = S.legal.moves.filter((m) => m.from === i);
    if (seqs.length === 0) {
      shakeBoard();
      toast(S.legal.mustCapture ? 'Captura obrigatória! Escolhe uma peça a pulsar.' : 'Essa peça não tem jogadas.', 'warn');
      return;
    }
    S.sel = { from: i, prefix: [i], seqs };
    sfx.click(); paintBoard();
  } else if (S.sel) {
    S.sel = null; paintBoard();
  }
}
function shakeBoard() {
  sfx.error();
  boardEl.classList.remove('shake');
  void boardEl.offsetWidth;
  boardEl.classList.add('shake');
}

/* ─────────── jogadas ─────────── */
function doMove(move, mine) {
  const mover = S.entities.find((en) => en.sq === move.from && !en.el.classList.contains('dying'));
  const wasMan = !mover.k;
  const res = applyMove(S.board, move);
  S.board = res.board;
  for (const cap of move.captures) {
    const victim = S.entities.find((en) => en.sq === cap && !en.el.classList.contains('dying'));
    if (victim) {
      victim.el.classList.add('dying');
      setTimeout(() => victim.el.remove(), 340);
      S.capturedBy[mover.c].push(victim.c);
      captureBurst(boardEl, cap, flip(), colorById(S.colors[victim.c]).hex);
    }
  }
  mover.sq = move.to;
  mover.k = res.promoted ? true : mover.k;
  placeEntity(mover);
  mover.el.classList.remove('land');
  void mover.el.offsetWidth;
  mover.el.classList.add('land');
  S.lastMove = { from: move.from, to: move.to };
  S.history.push({ side: mover.c, text: moveNotation(move) });
  if (res.captured > 0 || wasMan) S.quietKingPlies = 0;
  else S.quietKingPlies++;
  if (res.captured > 0) sfx.capture(); else sfx.move();
  if (res.promoted) { sfx.king(); kingSparkle(boardEl, move.to, flip()); toast('👑 Nova dama!'); }
  S.turn = other(S.turn);
  S.legal = legalMoves(S.board, S.turn);
  if (mine && S.mode === 'online') {
    S.net.send({ t: 'move', from: move.from, to: move.to, captures: move.captures });
  }
  renderGameUI();
  const winner = winnerIfOver(S.board, S.turn);
  if (winner) { endGame({ type: 'win', winner }); return; }
  if (S.quietKingPlies >= 50) { endGame({ type: 'draw', reason: '25 jogadas seguidas só de damas, sem capturas.' }); return; }
  if (S.legal.mustCapture && canPlay()) toast('Captura obrigatória!', 'warn');
}

/* ─────────── UI do jogo ─────────── */
function renderBars() {
  const bottomSide = S.mode === 'online' ? S.mySide : 'w';
  const topSide = other(bottomSide);
  for (const [barId, side] of [['#topBar', topSide], ['#bottomBar', bottomSide]]) {
    const p = S.players[side];
    const bar = $(barId);
    const caps = S.capturedBy[side];
    const oppHex = colorById(S.colors[other(side)]).hex;
    bar.classList.toggle('active', S.turn === side && !S.result);
    bar.innerHTML = `
      <span class="lp-avatar">${avatarById(p.avatar).e}</span>
      <div class="pb-info">
        <div class="pb-name">${escapeHtml(p.name || 'Jogador')}${S.mode === 'online' && side === S.mySide ? ' (tu)' : ''}</div>
        <div class="pb-captures">${caps.map(() => `<span class="cap-dot" style="background:${oppHex}"></span>`).join('')}</div>
      </div>
      <span class="swatch" title="${colorById(S.colors[side]).name}" style="background:${colorById(S.colors[side]).hex}"></span>
      <span class="pb-count">${pieceCount(S.board, side)}</span>
      <span class="turn-dot"></span>`;
  }
}
function renderBanner() {
  const b = $('#turnBanner');
  b.classList.remove('mine', 'theirs');
  if (S.result) { b.textContent = 'Jogo terminado'; return; }
  if (S.mode === 'local') {
    b.textContent = `Vez de ${S.players[S.turn].name || (S.turn === 'w' ? 'Jogador 1' : 'Jogador 2')}`;
    b.classList.add('mine');
  } else if (S.turn === S.mySide) {
    b.textContent = 'A tua vez — joga!';
    b.classList.add('mine');
  } else {
    b.textContent = `Vez de ${S.players[S.turn].name || 'amigo'}…`;
    b.classList.add('theirs');
  }
}
function renderLog() {
  const log = $('#moveLog');
  if (!S.history.length) {
    log.innerHTML = '<span class="empty">As jogadas aparecem aqui…</span>';
    return;
  }
  log.innerHTML = S.history.map((h, i) =>
    `<span class="move-chip ${h.side} ${i === S.history.length - 1 ? 'latest' : ''}">${h.text}</span>`).join('');
  log.scrollLeft = log.scrollWidth;
}
function renderGameUI() {
  paintBoard();
  renderBars();
  renderBanner();
  renderLog();
  $('#emoteRow').classList.toggle('hidden', S.mode !== 'online');
}

function beginGame(colors) {
  S.colors = { ...colors };
  S.board = initialState();
  S.turn = 'w';
  S.legal = legalMoves(S.board, 'w');
  S.sel = null; S.lastMove = null; S.history = [];
  S.capturedBy = { w: [], b: [] };
  S.quietKingPlies = 0; S.result = null;
  closeModal();
  buildBoard();
  renderGameUI();
  show('game');
  setChip(S.mode === 'online' ? 'Ligado ✓' : null);
  sfx.notify();
}

/* ─────────── fim de jogo / novo jogo / sair ─────────── */
function endGame(result) {
  S.result = result;
  S.sel = null;
  renderGameUI();
  let emoji, title, sub;
  if (result.type === 'win') {
    const w = result.winner;
    const winnerName = S.players[w].name || (w === 'w' ? 'Brancas' : 'Pretas');
    const iWon = S.mode === 'online' && w === S.mySide;
    emoji = '🏆';
    title = S.mode === 'online' ? (iWon ? 'Ganhaste!' : 'Perdeste!') : `${winnerName} ganha!`;
    sub = `${avatarById(S.players[w].avatar).e} ${escapeHtml(winnerName)} leva a mesa. Café por conta de quem perdeu?`;
    if (S.mode === 'online') { if (iWon) { sfx.win(); winConfetti(); } else sfx.lose(); }
    else { sfx.win(); winConfetti(); }
  } else {
    emoji = '🤝'; title = 'Empate!'; sub = result.reason || 'Empate por acordo.';
    sfx.draw();
  }
  const capsW = S.capturedBy.w.length, capsB = S.capturedBy.b.length;
  openModal(`
    <div class="end-hero">
      <span class="end-emoji">${emoji}</span>
      <h2 class="end-title">${title}</h2>
      <p class="end-sub">${sub}</p>
      <div class="end-stats">
        <div class="stat"><b>${S.history.length}</b><span>jogadas</span></div>
        <div class="stat"><b>${capsW}</b><span>capturas brancas</span></div>
        <div class="stat"><b>${capsB}</b><span>capturas pretas</span></div>
      </div>
    </div>
    <div class="modal-actions">
      <button class="btn btn-primary btn-big full" id="endRematch">☕ Novo jogo (mudar cores)</button>
      <button class="btn btn-ghost full" id="endLeave">Sair da mesa</button>
    </div>`, false);
  $('#endRematch').addEventListener('click', () => { sfx.click(); requestRematch(); });
  $('#endLeave').addEventListener('click', () => { sfx.click(); confirmLeave(); });
}
function requestRematch() {
  closeModal();
  if (S.mode === 'online') {
    if (S.peerLost || !S.net?.open) { toast('Sem ligação ao teu amigo.', 'warn'); return; }
    S.net.send({ t: 'rematch' });
  }
  enterLobby();
}
$('#btnRematch').addEventListener('click', () => {
  sfx.click();
  if (!S.result) {
    openModal(`
      <h2>Recomeçar?</h2>
      <p class="end-sub">O jogo atual termina e vão os dois escolher novas cores.</p>
      <div class="modal-actions">
        <button class="btn btn-primary full" id="rmYes">Sim, novo jogo</button>
        <button class="btn btn-ghost full" id="rmNo">Continuar este</button>
      </div>`);
    $('#rmYes').addEventListener('click', () => { sfx.click(); requestRematch(); });
    $('#rmNo').addEventListener('click', () => { sfx.click(); closeModal(); });
  } else requestRematch();
});
function confirmLeave() {
  const inGame = S.screen === 'game' && !S.result;
  openModal(`
    <h2>Sair da mesa?</h2>
    <p class="end-sub">${inGame ? 'Se saíres a meio do jogo, o teu amigo é avisado e a mesa fecha.' : 'A mesa fecha para os dois jogadores.'}</p>
    <div class="modal-actions">
      <button class="btn btn-danger full" id="lvYes">Sair da mesa</button>
      <button class="btn btn-ghost full" id="lvNo">Ficar</button>
    </div>`);
  $('#lvYes').addEventListener('click', () => {
    sfx.click();
    S.net?.send({ t: 'leave' });
    resetToHome();
  });
  $('#lvNo').addEventListener('click', () => { sfx.click(); closeModal(); });
}
$('#btnLeave').addEventListener('click', () => { sfx.click(); confirmLeave(); });
$('#btnDraw').addEventListener('click', () => {
  sfx.click();
  if (S.result) return;
  if (S.mode === 'online') {
    if (!S.net?.open) { toast('Sem ligação ao teu amigo.', 'warn'); return; }
    S.net.send({ t: 'drawOffer' });
    toast('Proposta de empate enviada.');
  } else {
    openModal(`
      <h2>Acordam empate?</h2>
      <p class="end-sub">Os dois jogadores têm de concordar.</p>
      <div class="modal-actions">
        <button class="btn btn-matcha full" id="dwYes">Sim, empate</button>
        <button class="btn btn-ghost full" id="dwNo">Continuar a jogar</button>
      </div>`);
    $('#dwYes').addEventListener('click', () => { sfx.click(); closeModal(); endGame({ type: 'draw', reason: 'Empate por acordo.' }); });
    $('#dwNo').addEventListener('click', () => { sfx.click(); closeModal(); });
  }
});

/* ─────────── emotes ─────────── */
function floatEmote(e) {
  if (!EMOTES.includes(e)) return;
  const el = document.createElement('span');
  el.className = 'float-emote';
  el.textContent = e;
  $('#emoteLayer').appendChild(el);
  sfx.emote();
  setTimeout(() => el.remove(), 1600);
}
document.querySelectorAll('.emote-btn').forEach((b) => {
  b.addEventListener('click', () => {
    const e = b.dataset.emote;
    floatEmote(e);
    S.net?.send({ t: 'emote', e });
  });
});

addEventListener('beforeunload', () => { S.net?.beaconLeave(); });
show('home');

// Service worker só em contexto seguro (https ou localhost); em http puro o browser recusa.
if ('serviceWorker' in navigator && (isSecureContext || location.hostname === 'localhost')) {
  addEventListener('load', () => {
    navigator.serviceWorker.register('sw.js').catch(() => { /* sem cache, o jogo continua online */ });
  });
}