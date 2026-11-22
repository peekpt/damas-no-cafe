import assert from 'node:assert/strict';
import {
  initialState, legalMoves, applyMove, winnerIfOver, pieceCount,
  idx, sqName, moveNotation, N,
} from '../web/js/engine.js';

const empty = () => new Array(N).fill(null);
const W = (k = false) => ({ c: 'w', k });
const B = (k = false) => ({ c: 'b', k });
let passed = 0;
const t = (name, fn) => { fn(); passed++; console.log('ok -', name); };

t('estado inicial: 12 peças por lado, brancas com 7 lances', () => {
  const b = initialState();
  assert.equal(pieceCount(b, 'w'), 12);
  assert.equal(pieceCount(b, 'b'), 12);
  const lm = legalMoves(b, 'w');
  assert.equal(lm.mustCapture, false);
  assert.equal(lm.moves.length, 7);
});

t('captura é obrigatória', () => {
  const b = empty();
  b[idx(5, 2)] = W(); b[idx(4, 3)] = B(); b[idx(2, 1)] = W();
  const lm = legalMoves(b, 'w');
  assert.equal(lm.mustCapture, true);
  assert.ok(lm.moves.every((m) => m.captures.length === 1));
  assert.ok(lm.moves.every((m) => m.from === idx(5, 2)));
});

t('peão captura para trás (regra portuguesa)', () => {
  const b = empty();
  b[idx(4, 3)] = W(); b[idx(5, 4)] = B();
  const lm = legalMoves(b, 'w');
  assert.equal(lm.mustCapture, true);
  assert.deepEqual(lm.moves[0].captures, [idx(5, 4)]);
  assert.equal(lm.moves[0].to, idx(6, 5));
});

t('lei da maioria: só o lance com mais capturas é legal', () => {
  const b = empty();
  // peça A captura 1; peça B captura 2
  b[idx(5, 0)] = W(); b[idx(4, 1)] = B();
  b[idx(5, 4)] = W(); b[idx(4, 5)] = B(); b[idx(2, 5)] = B();
  const lm = legalMoves(b, 'w');
  assert.equal(lm.maxCaptures, 2);
  assert.ok(lm.moves.every((m) => m.captures.length === 2 && m.from === idx(5, 4)));
});

t('dama voadora: captura à distância com várias casas de aterragem', () => {
  const b = empty();
  b[idx(7, 0)] = W(true); b[idx(4, 3)] = B();
  const lm = legalMoves(b, 'w');
  assert.equal(lm.mustCapture, true);
  const lands = lm.moves.map((m) => sqName(m.to)).sort();
  assert.deepEqual(lands, ['e5', 'f6', 'g7', 'h8']);
});

t('dama não salta duas peças juntas nem a mesma peça duas vezes', () => {
  const b = empty();
  b[idx(7, 0)] = W(true); b[idx(5, 2)] = B(); b[idx(4, 3)] = B();
  const lm = legalMoves(b, 'w');
  // duas peças adjacentes na diagonal bloqueiam a captura
  assert.equal(lm.mustCapture, false);
});

t('capturada permanece como bloqueio: não se re-salta nem se atravessa', () => {
  const b = empty();
  b[idx(6, 1)] = W(true); b[idx(4, 3)] = B();
  const lm = legalMoves(b, 'w');
  assert.equal(lm.mustCapture, true);
  assert.equal(lm.maxCaptures, 1);
  const lands = lm.moves.map((m) => sqName(m.to)).sort();
  assert.deepEqual(lands, ['e5', 'f6', 'g7', 'h8']);
  assert.ok(lm.moves.every((m) => m.captures.length === 1));
});

t('promoção só no fim do lance: passar pela linha de coroação não promove', () => {
  const b = empty();
  b[idx(2, 1)] = W(); b[idx(1, 2)] = B(); b[idx(1, 4)] = B();
  const lm = legalMoves(b, 'w');
  assert.equal(lm.maxCaptures, 2);
  const m = lm.moves[0];
  assert.deepEqual(m.path.map(sqName), ['b6', 'd8', 'f6']);
  const { board, promoted } = applyMove(b, m);
  assert.equal(promoted, false);
  assert.equal(board[idx(2, 5)].k, false);
});

t('promoção quando o lance termina na linha de coroação', () => {
  const b = empty();
  b[idx(2, 1)] = W(); b[idx(1, 2)] = B();
  const lm = legalMoves(b, 'w');
  const { board, promoted } = applyMove(b, lm.moves[0]);
  assert.equal(promoted, true);
  assert.equal(board[idx(0, 3)].k, true);
});

t('peão não anda para trás sem captura', () => {
  const b = empty();
  b[idx(4, 3)] = W();
  const lm = legalMoves(b, 'w');
  assert.ok(lm.moves.every((m) => m.to < m.from));
});

t('vitória: sem peças ou sem jogadas', () => {
  const b = empty();
  b[idx(7, 0)] = W();
  assert.equal(winnerIfOver(b, 'b'), 'w');
  const b2 = empty();
  b2[idx(5, 2)] = W(); b2[idx(1, 2)] = B(); b2[idx(2, 3)] = B();
  assert.equal(winnerIfOver(b2, 'w'), null);
  const b3 = empty();
  b3[idx(7, 0)] = W(); b3[idx(6, 1)] = B(); b3[idx(5, 2)] = B();
  // branca a1 bloqueada por duas pretas -> sem capturas nem lances
  assert.equal(winnerIfOver(b3, 'w'), 'b');
});

t('notação algébrica', () => {
  const b = empty();
  b[idx(5, 2)] = W(); b[idx(4, 3)] = B();
  const m = legalMoves(b, 'w').moves[0];
  assert.equal(moveNotation(m), 'c3xe5');
});

console.log(`\n${passed} testes passaram`);