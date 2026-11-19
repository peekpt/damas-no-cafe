package damas.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private fun vazio(): Tabuleiro = NullTabuleiro()

private fun tab(vararg pares: Pair<Int, Peca>): Tabuleiro {
  val t = vazio().toMutableList()
  for ((i, p) in pares) t[i] = p
  return t
}

private fun peca(lado: Lado, dama: Boolean = false) = Peca(lado, dama)

class DamasTest {

  @Test fun `estado inicial tem 12 pecas por lado e 7 lances para as brancas`() {
    val b = initialState()
    assertEquals(12, pieceCount(b, Lado.BRANCAS))
    assertEquals(12, pieceCount(b, Lado.PRETAS))
    val lm = legalMoves(b, Lado.BRANCAS)
    assertFalse(lm.mustCapture)
    assertEquals(7, lm.lista.size)
  }

  @Test fun `captura e obrigatoria`() {
    val b = tab(idx(5, 2) to peca(Lado.BRANCAS), idx(4, 3) to peca(Lado.PRETAS), idx(2, 1) to peca(Lado.BRANCAS))
    val lm = legalMoves(b, Lado.BRANCAS)
    assertTrue(lm.mustCapture)
    assertTrue(lm.lista.all { it.capturas.size == 1 })
    assertTrue(lm.lista.all { it.from == idx(5, 2) })
  }

  @Test fun `peao nao come para tras`() {
    val b = tab(idx(4, 3) to peca(Lado.BRANCAS), idx(5, 4) to peca(Lado.PRETAS))
    val lm = legalMoves(b, Lado.BRANCAS)
    assertFalse(lm.mustCapture)
    assertTrue(lm.lista.all { it.capturas.isEmpty() })
  }

  @Test fun `dama come para tras`() {
    val b = tab(idx(4, 3) to peca(Lado.BRANCAS, true), idx(5, 4) to peca(Lado.PRETAS))
    val lm = legalMoves(b, Lado.BRANCAS)
    assertTrue(lm.mustCapture)
    assertEquals(listOf(idx(5, 4)), lm.lista[0].capturas)
    assertEquals(idx(6, 5), lm.lista[0].to)
  }

  @Test fun `lei da maioria so vale o lance com mais capturas`() {
    val b = tab(
      idx(5, 0) to peca(Lado.BRANCAS), idx(4, 1) to peca(Lado.PRETAS),
      idx(5, 4) to peca(Lado.BRANCAS), idx(4, 5) to peca(Lado.PRETAS), idx(2, 5) to peca(Lado.PRETAS),
    )
    val lm = legalMoves(b, Lado.BRANCAS)
    assertEquals(2, lm.maxCapturas)
    assertTrue(lm.lista.all { it.capturas.size == 2 && it.from == idx(5, 4) })
  }

  @Test fun `dama voadora captura a distancia com varias aterragens`() {
    val b = tab(idx(7, 0) to peca(Lado.BRANCAS, true), idx(4, 3) to peca(Lado.PRETAS))
    val lm = legalMoves(b, Lado.BRANCAS)
    assertTrue(lm.mustCapture)
    assertEquals(listOf("e5", "f6", "g7", "h8"), lm.lista.map { sqName(it.to) }.sorted())
  }

  @Test fun `dama nao salta duas peças juntas`() {
    val b = tab(
      idx(7, 0) to peca(Lado.BRANCAS, true),
      idx(5, 2) to peca(Lado.PRETAS), idx(4, 3) to peca(Lado.PRETAS),
    )
    val lm = legalMoves(b, Lado.BRANCAS)
    assertFalse(lm.mustCapture)
  }

  @Test fun `capturada permanece como bloqueio ate ao fim do lance`() {
    val b = tab(idx(6, 1) to peca(Lado.BRANCAS, true), idx(4, 3) to peca(Lado.PRETAS))
    val lm = legalMoves(b, Lado.BRANCAS)
    assertTrue(lm.mustCapture)
    assertEquals(1, lm.maxCapturas)
    assertEquals(listOf("e5", "f6", "g7", "h8"), lm.lista.map { sqName(it.to) }.sorted())
    assertTrue(lm.lista.all { it.capturas.size == 1 })
  }

  @Test fun `promocao so no fim do lance`() {
    val b = tab(
      idx(6, 1) to peca(Lado.BRANCAS),
      idx(5, 2) to peca(Lado.PRETAS), idx(3, 4) to peca(Lado.PRETAS),
    )
    val lm = legalMoves(b, Lado.BRANCAS)
    assertEquals(2, lm.maxCapturas)
    val m = lm.lista[0]
    assertEquals(listOf("b2", "d4", "f6"), m.path.map { sqName(it) })
    val r = applyMove(b, m)
    assertFalse(r.promovida)
    assertEquals(false, r.board[idx(2, 5)]!!.dama)
  }

  @Test fun `promocao quando o lance termina na linha de coroacao`() {
    val b = tab(idx(2, 1) to peca(Lado.BRANCAS), idx(1, 2) to peca(Lado.PRETAS))
    val lm = legalMoves(b, Lado.BRANCAS)
    val r = applyMove(b, lm.lista[0])
    assertTrue(r.promovida)
    assertEquals(true, r.board[idx(0, 3)]!!.dama)
  }

  @Test fun `peao nao anda para tras sem captura`() {
    val b = tab(idx(4, 3) to peca(Lado.BRANCAS))
    val lm = legalMoves(b, Lado.BRANCAS)
    assertTrue(lm.lista.all { it.to < it.from })
  }

  @Test fun `vitoria por falta de pecas ou de jogadas`() {
    val b = tab(idx(7, 0) to peca(Lado.BRANCAS))
    assertEquals(Lado.BRANCAS, winnerIfOver(b, Lado.PRETAS))

    val b2 = tab(
      idx(5, 2) to peca(Lado.BRANCAS), idx(1, 2) to peca(Lado.PRETAS), idx(2, 3) to peca(Lado.PRETAS),
    )
    assertNull(winnerIfOver(b2, Lado.BRANCAS))

    val b3 = tab(
      idx(7, 0) to peca(Lado.BRANCAS), idx(6, 1) to peca(Lado.PRETAS), idx(5, 2) to peca(Lado.PRETAS),
    )
    assertEquals(Lado.PRETAS, winnerIfOver(b3, Lado.BRANCAS))
  }

  @Test fun `notacao algebrica`() {
    val b = tab(idx(5, 2) to peca(Lado.BRANCAS), idx(4, 3) to peca(Lado.PRETAS))
    val m = legalMoves(b, Lado.BRANCAS).lista[0]
    assertEquals("c3xe5", moveNotation(m))
  }

  @Test fun `indice e nome das casas batem certo com o tabuleiro`() {
    assertEquals(0, idx(0, 0))
    assertEquals("a8", sqName(0))
    assertEquals("h1", sqName(63))
    assertEquals("c3", sqName(idx(5, 2)))
    assertEquals(Lado.PRETAS, Lado.BRANCAS.outro)
    assertEquals(Lado.BRANCAS, Lado.fromWire("w"))
    assertNull(Lado.fromWire("x"))
  }
}
