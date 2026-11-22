// Damas — regras portuguesas (tabuleiro 8x8, 12 peças por lado).
// Peões andam 1 casa na diagonal para a frente e capturam nas 4 diagonais.
// Damas são voadoras. Captura é obrigatória e aplica-se a lei da maioria.
// As peças capturadas só saem no fim do lance; a promoção só acontece se o
// lance terminar na linha de coroação.
package damas.core

const val SIZE = 8
const val N = 64

fun idx(r: Int, c: Int) = r * SIZE + c
fun rowOf(i: Int) = i / SIZE
fun colOf(i: Int) = i % SIZE
fun inBoard(r: Int, c: Int) = r in 0 until SIZE && c in 0 until SIZE
fun isDark(r: Int, c: Int) = (r + c) % 2 == 1

fun sqName(i: Int): String = "abcdefgh"[colOf(i)] + (SIZE - rowOf(i)).toString()

enum class Lado(val wire: String) {
  BRANCAS("w"),
  PRETAS("b");

  val outro: Lado get() = if (this == BRANCAS) PRETAS else BRANCAS

  companion object {
    fun fromWire(s: String?): Lado? = entries.firstOrNull { it.wire == s }
  }
}

data class Peca(val lado: Lado, val dama: Boolean = false)

typealias Tabuleiro = List<Peca?>

class Lance(
  val from: Int,
  val to: Int,
  val path: List<Int>,
  val capturas: List<Int>,
) {
  val captura: Boolean get() = capturas.isNotEmpty()
}

class Lances(val lista: List<Lance>, val mustCapture: Boolean, val maxCapturas: Int)

fun initialState(): Tabuleiro {
  val board = MutableList<Peca?>(N) { null }
  for (r in 0 until SIZE) for (c in 0 until SIZE) if (isDark(r, c)) {
    when {
      r < 3 -> board[idx(r, c)] = Peca(Lado.PRETAS)
      r >= 5 -> board[idx(r, c)] = Peca(Lado.BRANCAS)
    }
  }
  return board
}

fun NullTabuleiro(): Tabuleiro = List(N) { null as Peca? }

private val DIRS = arrayOf(intArrayOf(-1, -1), intArrayOf(-1, 1), intArrayOf(1, -1), intArrayOf(1, 1))

private fun crownRow(lado: Lado) = if (lado == Lado.BRANCAS) 0 else SIZE - 1

private fun forwardDirs(lado: Lado) =
  if (lado == Lado.BRANCAS) arrayOf(intArrayOf(-1, -1), intArrayOf(-1, 1))
  else arrayOf(intArrayOf(1, -1), intArrayOf(1, 1))

private fun captureSequences(board: Tabuleiro, lado: Lado): List<Lance> {
  val seqs = ArrayList<Lance>()
  for (start in 0 until N) {
    val p = board[start] ?: continue
    if (p.lado != lado) continue
    val work = board.toMutableList()
    work[start] = null

    fun dfs(cur: Int, captured: List<Int>, path: List<Int>) {
      val r = rowOf(cur)
      val c = colOf(cur)
      var extended = false
      // Em Portugal o peão só come para a frente; apenas a dama come para trás.
      val direcoes = if (p.dama) DIRS else forwardDirs(lado)
      for (d in direcoes) {
        val dr = d[0]
        val dc = d[1]
        if (!p.dama) {
          val mr = r + dr
          val mc = c + dc
          val lr = r + 2 * dr
          val lc = c + 2 * dc
          if (!inBoard(lr, lc)) continue
          val mid = idx(mr, mc)
          val land = idx(lr, lc)
          val victim = work[mid]
          if (victim != null && victim.lado != lado && mid !in captured && work[land] == null) {
            extended = true
            dfs(land, captured + mid, path + land)
          }
        } else {
          var rr = r + dr
          var cc = c + dc
          while (inBoard(rr, cc) && work[idx(rr, cc)] == null) { rr += dr; cc += dc }
          if (!inBoard(rr, cc)) continue
          val mid = idx(rr, cc)
          val victim = work[mid]
          if (victim == null || victim.lado == lado || mid in captured) continue
          var lr = rr + dr
          var lc = cc + dc
          while (inBoard(lr, lc) && work[idx(lr, lc)] == null) {
            extended = true
            dfs(idx(lr, lc), captured + mid, path + idx(lr, lc))
            lr += dr
            lc += dc
          }
        }
      }
      if (!extended && captured.isNotEmpty()) seqs.add(Lance(start, cur, path, captured))
    }

    dfs(start, emptyList(), listOf(start))
  }
  return seqs
}

private fun quietMoves(board: Tabuleiro, lado: Lado): List<Lance> {
  val moves = ArrayList<Lance>()
  for (i in 0 until N) {
    val p = board[i] ?: continue
    if (p.lado != lado) continue
    val r = rowOf(i)
    val c = colOf(i)
    if (!p.dama) {
      for (d in forwardDirs(lado)) {
        val rr = r + d[0]
        val cc = c + d[1]
        if (inBoard(rr, cc) && board[idx(rr, cc)] == null) {
          val to = idx(rr, cc)
          moves.add(Lance(i, to, listOf(i, to), emptyList()))
        }
      }
    } else {
      for (d in DIRS) {
        var rr = r + d[0]
        var cc = c + d[1]
        while (inBoard(rr, cc) && board[idx(rr, cc)] == null) {
          val to = idx(rr, cc)
          moves.add(Lance(i, to, listOf(i, to), emptyList()))
          rr += d[0]
          cc += d[1]
        }
      }
    }
  }
  return moves
}

// Todos os lances legais, já filtrados pela lei da maioria.
fun legalMoves(board: Tabuleiro, lado: Lado): Lances {
  val caps = captureSequences(board, lado)
  if (caps.isNotEmpty()) {
    val max = caps.maxOf { it.capturas.size }
    return Lances(caps.filter { it.capturas.size == max }, true, max)
  }
  return Lances(quietMoves(board, lado), false, 0)
}

class ResultadoLance(val board: Tabuleiro, val promovida: Boolean, val capturadas: Int)

fun applyMove(board: Tabuleiro, move: Lance): ResultadoLance {
  val next = board.toMutableList()
  val original = next[move.from] ?: return ResultadoLance(board, false, 0)
  var p = original
  next[move.from] = null
  for (cap in move.capturas) next[cap] = null
  val promoted = !p.dama && rowOf(move.to) == crownRow(p.lado)
  if (promoted) p = p.copy(dama = true)
  next[move.to] = p
  return ResultadoLance(next, promoted, move.capturas.size)
}

fun pieceCount(board: Tabuleiro, lado: Lado): Int = board.count { it != null && it.lado == lado }

// null se o jogo continua; senão, quem ganhou. Quem joga sem lances legais perde.
fun winnerIfOver(board: Tabuleiro, sideToMove: Lado): Lado? {
  if (pieceCount(board, sideToMove) == 0) return sideToMove.outro
  if (legalMoves(board, sideToMove).lista.isEmpty()) return sideToMove.outro
  return null
}

fun moveNotation(move: Lance): String {
  val sep = if (move.captura) "x" else "-"
  return move.path.joinToString(sep) { sqName(it) }
}
