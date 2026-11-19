// Anunciador de mesas do "Damas no Café" para a rede Wi-Fi local.
// Uso: node server.mjs   (porta 8742; muda com PORT=...)
// Uma máquina na rede corre isto; os outros abrem http://<ip>:8742 e veem as mesas.
import http from 'node:http';
import { readFile } from 'node:fs/promises';
import { extname, join, normalize, sep } from 'node:path';
import { networkInterfaces } from 'node:os';
import { fileURLToPath } from 'node:url';

const ROOT = join(fileURLToPath(new URL('.', import.meta.url)), 'web');
const PORT = Number(process.env.PORT || 8742);
const ROOM_TTL = 15 * 60 * 1000;   // mesa sem atividade desaparece ao fim de 15 min
const SEEN_TTL = 12 * 1000;        // jogador que deixa de dar sinais é considerado ausente
const MAX_ROOMS = 64;

const rooms = new Map();
const CODE_CHARS = 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789';
function newId() {
  let id;
  do { id = Array.from({ length: 4 }, () => CODE_CHARS[Math.random() * CODE_CHARS.length | 0]).join(''); }
  while (rooms.has(id));
  return id;
}

function lanIp() {
  for (const list of Object.values(networkInterfaces()))
    for (const n of list || [])
      if (n.family === 'IPv4' && !n.internal) return n.address;
  return '127.0.0.1';
}

const MIME = {
  '.html': 'text/html; charset=utf-8', '.css': 'text/css; charset=utf-8',
  '.js': 'text/javascript; charset=utf-8', '.mjs': 'text/javascript; charset=utf-8',
  '.json': 'application/json', '.svg': 'image/svg+xml', '.png': 'image/png',
  '.webmanifest': 'application/manifest+json; charset=utf-8',
  '.woff2': 'font/woff2', '.ico': 'image/x-icon',
};
const CORS = {
  'access-control-allow-origin': '*',
  'access-control-allow-methods': 'GET, POST, OPTIONS',
  'access-control-allow-headers': 'content-type',
};
function sendJson(res, code, obj) {
  const body = JSON.stringify(obj);
  res.writeHead(code, { 'content-type': 'application/json; charset=utf-8', 'cache-control': 'no-store', ...CORS });
  res.end(body);
}
function readBody(req) {
  return new Promise((resolve, reject) => {
    let data = '';
    req.on('data', (c) => { data += c; if (data.length > 100000) { reject(new Error('body grande')); req.destroy(); } });
    req.on('end', () => { try { resolve(data ? JSON.parse(data) : {}); } catch { reject(new Error('json inválido')); } });
    req.on('error', reject);
  });
}
const cleanName = (s, max) => String(s ?? '').replace(/\s+/g, ' ').trim().slice(0, max);
const publicRoom = (r) => ({ id: r.id, name: r.name, status: r.status, host: r.players.h, guest: r.players.g, created: r.created });
function closeRoom(r) { if (r.status !== 'closed') { r.status = 'closed'; r.closedAt = Date.now(); } }
function pushMsg(r, from, msg) { r.msgs.push({ seq: ++r.seq, from, msg }); }

setInterval(() => {
  const now = Date.now();
  for (const [id, r] of rooms) {
    if (r.status === 'closed') { if (now - r.closedAt > 60000) rooms.delete(id); continue; }
    if (now - r.created > ROOM_TTL && r.msgs.length === 0) { closeRoom(r); continue; }
    if (now - r.lastSeen.h > SEEN_TTL) { pushMsg(r, 'h', { t: 'leave' }); closeRoom(r); continue; }
    if (r.status === 'playing' && now - r.lastSeen.g > SEEN_TTL) { pushMsg(r, 'g', { t: 'leave' }); closeRoom(r); }
  }
}, 2000).unref();

const server = http.createServer(async (req, res) => {
  const url = new URL(req.url, 'http://localhost');
  const path = url.pathname;
  try {
    if (req.method === 'OPTIONS') { res.writeHead(204, CORS); res.end(); return; }

    if (path === '/api/info') return sendJson(res, 200, { app: 'damas-no-cafe', port: PORT, lanIp: lanIp() });

    if (path === '/api/rooms' && req.method === 'GET')
      return sendJson(res, 200, { rooms: [...rooms.values()].filter((r) => r.status !== 'closed').map(publicRoom) });

    if (path === '/api/rooms' && req.method === 'POST') {
      if (rooms.size >= MAX_ROOMS) return sendJson(res, 503, { error: 'Demasiadas mesas abertas.' });
      const b = await readBody(req);
      const now = Date.now();
      const r = {
        id: newId(), name: cleanName(b.name, 24) || 'Mesa de café',
        status: 'waiting', players: { h: { name: cleanName(b.host?.name, 14) || 'Jogador', avatar: cleanName(b.host?.avatar, 12) || 'cafe' }, g: null },
        msgs: [], seq: 0, created: now, lastSeen: { h: now, g: 0 },
      };
      rooms.set(r.id, r);
      return sendJson(res, 200, { id: r.id });
    }

    const m = path.match(/^\/api\/rooms\/([A-Z0-9]{4})\/(join|poll|msg)$/);
    if (m) {
      const r = rooms.get(m[1]);
      const action = m[2];
      if (!r) return sendJson(res, 404, { error: 'Essa mesa já não existe.' });
      if (r.status === 'closed' && action !== 'poll') return sendJson(res, 404, { error: 'Essa mesa já fechou.' });

      if (action === 'join' && req.method === 'POST') {
        if (r.status !== 'waiting') return sendJson(res, 409, { error: 'Essa mesa já está completa.' });
        const b = await readBody(req);
        r.players.g = { name: cleanName(b.name, 14) || 'Jogador', avatar: cleanName(b.avatar, 12) || 'cafe' };
        r.status = 'playing';
        r.lastSeen.g = Date.now();
        return sendJson(res, 200, { ok: true, room: publicRoom(r) });
      }

      if (action === 'poll' && req.method === 'GET') {
        const side = url.searchParams.get('side');
        if (side !== 'h' && side !== 'g') return sendJson(res, 400, { error: 'lado inválido' });
        if (side === 'h' || r.players.g) r.lastSeen[side] = Date.now();
        const since = Number(url.searchParams.get('since') || 0);
        return sendJson(res, 200, {
          status: r.status,
          msgs: r.msgs.filter((x) => x.seq > since && x.from !== side),
          players: { h: r.players.h, g: r.players.g },
        });
      }

      if (action === 'msg' && req.method === 'POST') {
        const b = await readBody(req);
        const side = b.side;
        if ((side !== 'h' && side !== 'g') || (side === 'g' && !r.players.g))
          return sendJson(res, 400, { error: 'lado inválido' });
        if (!b.msg || typeof b.msg !== 'object') return sendJson(res, 400, { error: 'mensagem inválida' });
        r.lastSeen[side] = Date.now();
        pushMsg(r, side, b.msg);
        if (b.msg.t === 'leave') closeRoom(r);
        return sendJson(res, 200, { ok: true, seq: r.seq });
      }
    }

    if (path.startsWith('/api/')) return sendJson(res, 404, { error: 'não encontrado' });

    // ficheiros estáticos de web/
    let file = normalize(join(ROOT, decodeURIComponent(path === '/' ? '/index.html' : path)));
    if (!file.startsWith(ROOT + sep) && file !== ROOT) { res.writeHead(403); res.end(); return; }
    try {
      const data = await readFile(file);
      res.writeHead(200, { 'content-type': MIME[extname(file)] || 'application/octet-stream', 'cache-control': 'no-cache' });
      res.end(data);
    } catch {
      res.writeHead(404, { 'content-type': 'text/plain; charset=utf-8' });
      res.end('404');
    }
  } catch (e) {
    sendJson(res, 400, { error: e.message || 'pedido inválido' });
  }
});

server.listen(PORT, '0.0.0.0', () => {
  console.log('☕ Damas no Café — anunciador de mesas');
  console.log(`   Nesta máquina:  http://localhost:${PORT}`);
  console.log(`   Na rede Wi-Fi:  http://${lanIp()}:${PORT}`);
  console.log('   Os jogadores abrem o endereço da rede e escolhem a mesa.');
});