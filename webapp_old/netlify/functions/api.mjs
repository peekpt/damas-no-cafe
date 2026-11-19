// Relay de mesas na nuvem — mesma API do anunciador local (server.mjs),
// para a app instalada do Netlify jogar sem depender do Wi-Fi do café.
// O estado vive em Netlify Blobs, com chaves escritas por um único dono:
//   r/<id>  mesa   s/<id>/<seq>-<lado>  mensagem (append-only)
//   hb/<id>/<lado> batimento   c/<n>  código da mesa -> id (onlyIfNew)
import { getStore } from '@netlify/blobs';

const SEEN_TTL = 12_000;      // sem sinais há 12 s = jogador ausente
const ROOM_TTL = 15 * 60_000; // mesa vive no máximo 15 min
const MAX_ROOMS = 64;
const MAX_CODES = 254;        // 1..254 -> palavra no frontend
const ID_CHARS = 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789';

const json = (obj, status = 200) =>
  new Response(JSON.stringify(obj), {
    status,
    headers: { 'content-type': 'application/json; charset=utf-8', 'cache-control': 'no-store' },
  });
const err = (msg, status = 400) => json({ error: msg }, status);
const clean = (s, max) => String(s ?? '').replace(/\s+/g, ' ').trim().slice(0, max);
const pad = (n) => String(n).padStart(15, '0');
const store = () => getStore({ name: 'mesas', consistency: 'strong' });

async function readRoom(id) {
  const entry = await store().getWithMetadata(`r/${id}`, { type: 'json' });
  return entry ? { room: entry.data, etag: entry.etag } : { room: null, etag: null };
}
async function hbTime(id, side) {
  const v = await store().get(`hb/${id}/${side}`, { type: 'json' }).catch(() => null);
  return v?.t || 0;
}
async function closeRoom(id, room, etag, fromSide) {
  const s = store();
  if (room.status !== 'closed') {
    const seq = Date.now();
    await s.setJSON(`s/${id}/${pad(seq)}-x`, { seq, from: fromSide || 'x', msg: { t: 'leave' } });
    await s.setJSON(`r/${id}`, { ...room, status: 'closed', closedAt: Date.now() }, { onlyIfMatch: etag });
  }
  return { ...room, status: 'closed', closedAt: Date.now() };
}
function purge(s, id, code) {
  return (async () => {
    const { blobs } = await s.list({ prefix: `s/${id}/` });
    for (const b of blobs.slice(0, 400)) await s.delete(b.key);
    await s.delete(`hb/${id}/h`); await s.delete(`hb/${id}/g`);
    if (code) {
      const owner = await s.get(`c/${code}`).catch(() => null);
      if (owner === id) await s.delete(`c/${code}`);
    }
    await s.delete(`r/${id}`);
  })().catch(() => { /* a próxima mesa limpa o resto */ });
}
const publicRoom = (r) => ({
  id: r.id, name: r.name, status: r.status, code: r.code,
  host: r.players.h, guest: r.players.g, created: r.created,
});

async function handle(req, context) {
  const url = new URL(req.url);
  const path = url.pathname.replace(/^\/api/, '') || '/';
  const s = store();

  if (path === '/info') {
    return json({ app: 'damas-no-cafe', relay: 'cloud', port: null, lanIp: null });
  }

  if (path === '/rooms' && req.method === 'GET') {
    const { blobs } = await s.list({ prefix: 'r/' });
    const bodies = (await Promise.all(
      blobs.slice(0, MAX_ROOMS).map((b) => s.get(b.key, { type: 'json' }).catch(() => null)),
    )).filter((r) => r && r.status !== 'closed');
    const now = Date.now();
    const judged = await Promise.all(bodies.map(async (r) => {
      const host = await hbTime(r.id, 'h');
      const guest = r.status === 'playing' ? await hbTime(r.id, 'g') : now;
      return { r, dead: now - r.created > ROOM_TTL || now - host > SEEN_TTL || now - guest > SEEN_TTL };
    }));
    // Quem fecha o browser sem dizer "sair" é apanhado aqui: a lista é o limpa-espaços.
    for (const { r } of judged.filter((x) => x.dead)) {
      const { room, etag } = await readRoom(r.id);
      if (room && room.status !== 'closed') await closeRoom(r.id, room, etag, 'x');
      context.waitUntil(purge(s, r.id, r.code));
    }
    return json({ rooms: judged.filter((x) => !x.dead).map(({ r }) => publicRoom(r)) });
  }

  if (path === '/rooms' && req.method === 'POST') {
    const body = await req.json().catch(() => ({}));
    const now = Date.now();
    const existing = await s.list({ prefix: 'r/' });
    if (existing.blobs.length >= MAX_ROOMS) return err('Demasiadas mesas abertas.', 503);
    let id = null;
    for (let tries = 0; tries < 8 && !id; tries++) {
      const cand = Array.from({ length: 4 }, () => ID_CHARS[Math.random() * ID_CHARS.length | 0]).join('');
      if (!(await s.get(`r/${cand}`))) id = cand;
    }
    if (!id) return err('Demasiadas mesas abertas.', 503);
    let code = 0;
    const start = 1 + Math.floor(Math.random() * MAX_CODES);
    for (let i = 0; i < MAX_CODES && !code; i++) {
      const n = ((start + i - 1) % MAX_CODES) + 1;
      const { modified } = await s.set(`c/${n}`, id, { onlyIfNew: true });
      if (modified) code = n;
    }
    if (!code) return err('Demasiadas mesas abertas.', 503);
    const room = {
      id, code, name: clean(body.name, 24) || 'Mesa de café', status: 'waiting',
      players: { h: { name: clean(body.host?.name, 14) || 'Jogador', avatar: clean(body.host?.avatar, 12) || 'cafe' }, g: null },
      created: now, closedAt: 0,
    };
    await s.setJSON(`r/${id}`, room, { onlyIfNew: true });
    await s.setJSON(`hb/${id}/h`, { t: now });
    return json({ id, code });
  }

  const m = path.match(/^\/rooms\/([A-Z0-9]{4})\/(join|poll|msg)$/);
  if (m) {
    const [, id, action] = m;
    const { room, etag } = await readRoom(id);
    if (!room) {
      // Já foi limpa: quem ainda pergunta é porque não sabe que acabou.
      if (action === 'poll') return json({ status: 'closed', msgs: [], players: { h: null, g: null } });
      if (action === 'msg') return json({ ok: true, closed: true });
      return err('Essa mesa já não existe.', 404);
    }

    if (action === 'join' && req.method === 'POST') {
      if (room.status !== 'waiting') return err('Essa mesa já está completa.', 409);
      const body = await req.json().catch(() => ({}));
      const now = Date.now();
      const updated = {
        ...room, status: 'playing',
        players: { ...room.players, g: { name: clean(body.name, 14) || 'Jogador', avatar: clean(body.avatar, 12) || 'cafe' } },
      };
      const { modified } = await s.setJSON(`r/${id}`, updated, { onlyIfMatch: etag });
      if (!modified) return err('Alguém entrou nessa mesa agora — tenta de novo.', 409);
      await s.setJSON(`hb/${id}/g`, { t: now });
      return json({ ok: true, room: publicRoom(updated) });
    }

    if (action === 'poll' && req.method === 'GET') {
      const side = url.searchParams.get('side');
      if (side !== 'h' && side !== 'g') return err('lado inválido');
      const since = Number(url.searchParams.get('since') || 0);
      const now = Date.now();
      let live = room;
      if (live.status !== 'closed') {
        const mine = await hbTime(id, side);
        const peer = await hbTime(id, side === 'h' ? 'g' : 'h');
        const idle = now - Math.max(mine, live.created);
        const gonePeer = live.status === 'playing' && now - peer > SEEN_TTL;
        if (now - live.created > ROOM_TTL || idle > ROOM_TTL || gonePeer) {
          live = await closeRoom(id, live, etag, gonePeer ? (side === 'h' ? 'g' : 'h') : 'x');
        } else if (now - mine > 3000) {
          await s.setJSON(`hb/${id}/${side}`, { t: now });
        }
      }
      const msgs = [];
      {
        const { blobs } = await s.list({ prefix: `s/${id}/` });
        const fresh = blobs.map((b) => ({ key: b.key, seq: Number(b.key.slice(-17, -2)) }))
          .filter((x) => x.seq > since).sort((a, b) => a.seq - b.seq).slice(0, 64);
        for (const f of fresh) {
          const msg = await s.get(f.key, { type: 'json' }).catch(() => null);
          if (msg) msgs.push(msg);
        }
      }
      if (live.status === 'closed') context.waitUntil(purge(s, id, live.code));
      return json({ status: live.status, msgs, players: { h: live.players.h, g: live.players.g } });
    }

    if (action === 'msg' && req.method === 'POST') {
      const body = await req.json().catch(() => ({}));
      const side = body.side;
      if (side !== 'h' && side !== 'g') return err('lado inválido');
      if (side === 'g' && !room.players.g) return err('lado inválido');
      if (!body.msg || typeof body.msg !== 'object') return err('mensagem inválida');
      if (room.status === 'closed') return json({ ok: true, closed: true });
      const seq = Date.now();
      await s.setJSON(`s/${id}/${pad(seq)}-${side}`, { seq, from: side, msg: body.msg });
      await s.setJSON(`hb/${id}/${side}`, { t: seq });
      if (body.msg.t === 'leave') {
        await closeRoom(id, room, etag, side);
        context.waitUntil(purge(s, id, room.code));
      }
      return json({ ok: true, seq });
    }

    return err('método inválido', 405);
  }

  const c = path.match(/^\/code\/(\d{1,3})$/);
  if (c && req.method === 'GET') {
    const id = await s.get(`c/${Number(c[1])}`);
    if (!id) return err('Não há mesa com esse número.', 404);
    const { room } = await readRoom(id);
    if (!room || room.status === 'closed') { await s.delete(`c/${Number(c[1])}`); return err('Essa mesa já fechou.', 404); }
    return json({ id: room.id, code: room.code });
  }

  return err('não encontrado', 404);
}

export default async (req, context) => {
  try {
    return await handle(req, context);
  } catch (e) {
    console.error('relay de mesas:', e);
    return err(e.message || 'o relay falhou', 500);
  }
};

export const config = { path: ['/api/*'] };
