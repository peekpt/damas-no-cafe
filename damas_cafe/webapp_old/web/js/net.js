// Ligação às mesas anunciadas na rede Wi-Fi local.
// O anunciador (server.mjs) corre numa máquina da rede; os jogadores falam
// com ele por HTTP. O nº da mesa é o último octeto do IP do anfitrião,
// codificado numa palavra de café (ex.: 18 -> donut).

const WORDS = [
  'bica', 'galao', 'mocha', 'meialeite', 'bule', 'cafe', 'cha', 'matcha', 'chai', 'cacau', 'leite', 'mel', 'agua', 'sumo', 'limao', 'gelo',
  'bolo', 'donut', 'croissant', 'bagel', 'muffin', 'cookie', 'brownie', 'waffle', 'panqueca', 'torrada', 'sandes', 'queijo', 'fiambre', 'manteiga', 'compota', 'avela',
  'pires', 'chavena', 'caneca', 'copo', 'jarro', 'prato', 'garfo', 'faca', 'colher', 'guardanapo', 'tabuleiro', 'balcao', 'montra', 'ementa', 'conta', 'gorjeta',
  'barista', 'garcom', 'cliente', 'amigo', 'vizinho', 'chefe', 'cozinheiro', 'padeiro', 'pasteleiro', 'empregado', 'gerente', 'caixa', 'musico', 'poeta', 'pintor', 'leitor',
  'mesa', 'cadeira', 'sofa', 'banco', 'janela', 'porta', 'parede', 'chao', 'teto', 'lampada', 'vela', 'quadro', 'espelho', 'tapete', 'cortina', 'planta',
  'gato', 'cao', 'mocho', 'passaro', 'peixe', 'coelho', 'tartaruga', 'hamster', 'papagaio', 'canario', 'abelha', 'borboleta', 'formiga', 'joaninha', 'grilo', 'caracol',
  'sol', 'lua', 'estrela', 'nuvem', 'chuva', 'vento', 'neve', 'arcoiris', 'trovoada', 'neblina', 'calor', 'frio', 'sombra', 'luz', 'aurora', 'crepusculo',
  'doce', 'amargo', 'quente', 'gelado', 'forte', 'suave', 'claro', 'escuro', 'novo', 'velho', 'alto', 'baixo', 'cheio', 'vazio', 'rapido', 'lento',
  'rosa', 'tulipa', 'margarida', 'girassol', 'violeta', 'jasmim', 'lavanda', 'alecrim', 'hortela', 'manjericao', 'canela', 'baunilha', 'nozmoscada', 'gengibre', 'anis', 'cravo',
  'xadrez', 'damas', 'cartas', 'dados', 'domino', 'puzzle', 'trivia', 'bingo', 'sudoku', 'golfe', 'tenis', 'futebol', 'basquetebol', 'voleibol', 'remo', 'gamao',
  'viola', 'guitarra', 'piano', 'flauta', 'tambor', 'harpa', 'sino', 'gaita', 'acordeon', 'banjo', 'saxofone', 'trompete', 'violino', 'contrabaixo', 'pandeiro', 'castanholas',
  'livro', 'revista', 'jornal', 'carta', 'postal', 'bilhete', 'nota', 'caneta', 'lapis', 'borracha', 'regua', 'tesoura', 'cola', 'fita', 'clipe', 'agrafador',
  'chave', 'moeda', 'troco', 'carteira', 'bolso', 'botao', 'fecho', 'agulha', 'linha', 'dedal', 'pano', 'saco', 'cesta', 'caixote', 'frasco', 'boiao',
  'tampa', 'rolha', 'comboio', 'autocarro', 'metro', 'barco', 'aviao', 'bicicleta', 'mota', 'carro', 'taxi', 'carrinha', 'camiao', 'trator', 'balao', 'helicoptero',
  'patins', 'skate', 'praia', 'monte', 'rio', 'lago', 'fonte', 'ponte', 'torre', 'farol', 'moinho', 'castelo', 'igreja', 'mercado', 'praca', 'jardim',
  'bosque', 'gruta', 'coroa', 'trono', 'espada', 'escudo', 'elmo', 'dragao', 'fada', 'mago', 'duende', 'gigante', 'sereia', 'fantasma', 'pirata', 'tesouro',
];

export function mesaCode(n) { return WORDS[n - 1]; }
export function parseMesaInput(text) {
  const t = String(text || '').trim();
  if (/^\d{1,3}$/.test(t)) {
    const n = Number(t);
    return n >= 1 && n <= 254 ? n : null;
  }
  const norm = t.toLowerCase().normalize('NFD').replace(/[̀-ͯ]/g, '').replace(/[^a-z]/g, '');
  const i = WORDS.indexOf(norm);
  return i >= 0 ? i + 1 : null;
}

export function subnetPrefix(hostname) {
  const m = String(hostname).match(/^(\d+\.\d+\.\d+)\.\d+$/);
  if (!m || m[1].startsWith('127.')) return null;
  return m[1];
}

async function api(base, path, opts = {}) {
  const ctrl = new AbortController();
  const timer = setTimeout(() => ctrl.abort(), opts.timeout ?? 5000);
  try {
    const res = await fetch(base + path, {
      method: opts.method || 'GET',
      body: opts.body,
      headers: { 'content-type': 'application/json' },
      signal: ctrl.signal,
    });
    const data = await res.json().catch(() => null);
    if (!res.ok) throw new Error(data?.error || 'Erro ' + res.status);
    return data;
  } finally { clearTimeout(timer); }
}

export const fetchInfo = (base = '') => api(base, '/api/info');
export const fetchRooms = (base = '') => api(base, '/api/rooms').then((d) => d.rooms);
export const createRoom = (base, name, host) =>
  api(base, '/api/rooms', { method: 'POST', body: JSON.stringify({ name, host }) });
export const joinRoom = (base, id, player) =>
  api(base, `/api/rooms/${id}/join`, { method: 'POST', body: JSON.stringify(player) });
// no relay da nuvem o nº da mesa não vem do IP: perguntamos ao relay.
export const resolveCode = (base, n) => api(base, `/api/code/${n}`);

// Canal de jogo: polling curto ao anunciador. Mesma interface que o antigo Net.
export class RoomChannel {
  constructor(base, id, side, interval = 350) {
    this.base = base; this.id = id; this.side = side;
    this.interval = interval;
    this.since = 0; this.open = false; this.stopped = false;
    this.timer = 0; this.fails = 0;
    this.onOpen = null; this.onMessage = null; this.onClose = null; this.onState = null;
  }
  start() { this.onState?.('connecting'); this._poll(); }
  async _poll() {
    if (this.stopped) return;
    try {
      const d = await api(this.base, `/api/rooms/${this.id}/poll?side=${this.side}&since=${this.since}`, { timeout: 6000 });
      this.fails = 0;
      for (const m of d.msgs) {
        this.since = Math.max(this.since, m.seq);
        if (m.from !== this.side) this.onMessage?.(m.msg);
      }
      if (d.status === 'playing' && !this.open) {
        this.open = true;
        this.onState?.('connected');
        this.onOpen?.();
      }
      if (d.status === 'closed') { this.open = false; this.stopped = true; this.onClose?.(); return; }
      if (d.status === 'waiting') this.onState?.('waiting');
    } catch {
      if (++this.fails >= 4) { this.stopped = true; this.open = false; this.onClose?.(); return; }
    }
    // na nuvem cada poll é uma invocação de função: polling mais calmo
    const wait = this.fails > 1 ? this.interval * 2 : this.interval;
    this.timer = setTimeout(() => this._poll(), wait);
  }
  send(obj) {
    if (this.stopped) return;
    api(this.base, `/api/rooms/${this.id}/msg`, {
      method: 'POST', body: JSON.stringify({ side: this.side, msg: obj }),
    }).catch(() => { /* o heartbeat do servidor deteta a queda */ });
  }
  beaconLeave() {
    try {
      navigator.sendBeacon(this.base + `/api/rooms/${this.id}/msg`,
        new Blob([JSON.stringify({ side: this.side, msg: { t: 'leave' } })], { type: 'application/json' }));
    } catch { /* sem beacon — o servidor fecha por heartbeat */ }
  }
  close() { this.stopped = true; clearTimeout(this.timer); this.open = false; }
}

// Procura anunciadores noutros IPs da mesma sub-rede (o browser não pode
// ouvir anúncios da rede, por isso sondamos o porto do jogo em cada IP).
export async function scanNetwork(port, onProgress) {
  const sub = subnetPrefix(location.hostname);
  const found = [];
  if (!sub) return found;
  const ips = Array.from({ length: 254 }, (_, i) => `${sub}.${i + 1}`);
  let done = 0;
  for (let i = 0; i < ips.length; i += 24) {
    await Promise.all(ips.slice(i, i + 24).map(async (ip) => {
      const base = `http://${ip}:${port}`;
      try {
        const ctrl = new AbortController();
        const t = setTimeout(() => ctrl.abort(), 600);
        const res = await fetch(base + '/api/info', { signal: ctrl.signal });
        clearTimeout(t);
        if (res.ok) {
          const d = await res.json().catch(() => null);
          if (d?.app === 'damas-no-cafe') found.push(base);
        }
      } catch { /* IP sem anunciador */ }
      done++; onProgress?.(done, ips.length);
    }));
  }
  return found;
}