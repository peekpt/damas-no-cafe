// Damas — regras portuguesas (tabuleiro 8x8, 12 peças por lado)
// - Peões andam 1 casa na diagonal para a frente; capturam nas 4 diagonais.
// - Damas são voadoras (deslizam e capturam à distância).
// - Captura é obrigatória e vale a lei da maioria (capturar o máximo de peças).
// - Peças capturadas só saem do tabuleiro no fim do lance; não se salta a mesma peça duas vezes.
// - Promoção só se o lance TERMINAR na linha de coroação.
// - Brancas jogam primeiro. Perde quem fica sem peças ou sem jogadas legais.

export const SIZE = 8;
export const N = 64;

export const idx = (r, c) => r * SIZE + c;
export const rowOf = (i) => (i / SIZE) | 0;
export const colOf = (i) => i % SIZE;
export const inBoard = (r, c) => r >= 0 && r < SIZE && c >= 0 && c < SIZE;
export const isDark = (r, c) => (r + c) % 2 === 1;

export function sqName(i) {
  return 'abcdefgh'[colOf(i)] + String(SIZE - rowOf(i));
}

export function initialState() {
  const board = new Array(N).fill(null);
  for (let r = 0; r < 3; r++)
    for (let c = 0; c < SIZE; c++)
      if (isDark(r, c)) board[idx(r, c)] = { c: 'b', k: false };
  for (let r = 5; r < SIZE; r++)
    for (let c = 0; c < SIZE; c++)
      if (isDark(r, c)) board[idx(r, c)] = { c: 'w', k: false };
  return board;
}

const DIRS = [[-1, -1], [-1, 1], [1, -1], [1, 1]];
const crownRow = (side) => (side === 'w' ? 0 : SIZE - 1);
const forwardDirs = (side) => (side === 'w' ? [[-1, -1], [-1, 1]] : [[1, -1], [1, 1]]);

function captureSequences(board, side) {
  const seqs = [];
  for (let start = 0; start < N; start++) {
    const p = board[start];
    if (!p || p.c !== side) continue;
    const work = board.slice();
    work[start] = null;
    const dfs = (cur, captured, path) => {
      const r = rowOf(cur), c = colOf(cur);
      let extended = false;
      for (const [dr, dc] of DIRS) {
        if (!p.k) {
          const mr = r + dr, mc = c + dc, lr = r + 2 * dr, lc = c + 2 * dc;
          if (!inBoard(lr, lc)) continue;
          const mid = idx(mr, mc), land = idx(lr, lc);
          const victim = work[mid];
          if (victim && victim.c !== side && !captured.includes(mid) && !work[land]) {
            extended = true;
            dfs(land, [...captured, mid], [...path, land]);
          }
        } else {
          let rr = r + dr, cc = c + dc;
          while (inBoard(rr, cc) && !work[idx(rr, cc)]) { rr += dr; cc += dc; }
          if (!inBoard(rr, cc)) continue;
          const mid = idx(rr, cc);
          const victim = work[mid];
          if (!victim || victim.c === side || captured.includes(mid)) continue;
          let lr = rr + dr, lc = cc + dc;
          while (inBoard(lr, lc) && !work[idx(lr, lc)]) {
            extended = true;
            dfs(idx(lr, lc), [...captured, mid], [...path, idx(lr, lc)]);
            lr += dr; lc += dc;
          }
        }
      }
      if (!extended && captured.length > 0) {
        seqs.push({ from: start, to: cur, path, captures: captured });
      }
    };
    dfs(start, [], [start]);
  }
  return seqs;
}

function quietMoves(board, side) {
  const moves = [];
  for (let i = 0; i < N; i++) {
    const p = board[i];
    if (!p || p.c !== side) continue;
    const r = rowOf(i), c = colOf(i);
    if (!p.k) {
      for (const [dr, dc] of forwardDirs(side)) {
        const rr = r + dr, cc = c + dc;
        if (inBoard(rr, cc) && !board[idx(rr, cc)]) {
          const to = idx(rr, cc);
          moves.push({ from: i, to, path: [i, to], captures: [] });
        }
      }
    } else {
      for (const [dr, dc] of DIRS) {
        let rr = r + dr, cc = c + dc;
        while (inBoard(rr, cc) && !board[idx(rr, cc)]) {
          const to = idx(rr, cc);
          moves.push({ from: i, to, path: [i, to], captures: [] });
          rr += dr; cc += dc;
        }
      }
    }
  }
  return moves;
}

// Devolve todos os lances legais já filtrados pela lei da maioria.
export function legalMoves(board, side) {
  const caps = captureSequences(board, side);
  if (caps.length > 0) {
    const max = Math.max(...caps.map((m) => m.captures.length));
    const best = caps.filter((m) => m.captures.length === max);
    return { moves: best, mustCapture: true, maxCaptures: max };
  }
  return { moves: quietMoves(board, side), mustCapture: false, maxCaptures: 0 };
}

export function applyMove(board, move) {
  const next = board.slice();
  const p = { ...next[move.from] };
  next[move.from] = null;
  for (const cap of move.captures) next[cap] = null;
  const promoted = !p.k && rowOf(move.to) === crownRow(p.c);
  if (promoted) p.k = true;
  next[move.to] = p;
  return { board: next, promoted, captured: move.captures.length };
}

export function pieceCount(board, side) {
  let n = 0;
  for (const p of board) if (p && p.c === side) n++;
  return n;
}

// 'w' | 'b' | null — null se o jogo continua. Quem vai jogar sem lances perde.
export function winnerIfOver(board, sideToMove) {
  if (pieceCount(board, sideToMove) === 0) return sideToMove === 'w' ? 'b' : 'w';
  if (legalMoves(board, sideToMove).moves.length === 0) return sideToMove === 'w' ? 'b' : 'w';
  return null;
}

export function moveNotation(move) {
  const sep = move.captures.length > 0 ? 'x' : '-';
  return move.path.map(sqName).join(sep);
}

export const other = (side) => (side === 'w' ? 'b' : 'w');