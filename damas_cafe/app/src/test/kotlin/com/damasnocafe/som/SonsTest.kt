package com.damasnocafe.som

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Prova-se o som pela forma de onda, porque aqui não há ouvidos na sala: o arrasto
 * tem de se aguentar durante a viagem toda e ranger em grãos, e a captura têm de se
 * contar em três toques. É o mesmo WAV que o `Sons` escreve na cache — 22 050 Hz,
 * mono, 16 bits, 44 bytes de cabeça.
 */
class SonsTest {
  private val taxa = 22_050

  private fun pcm(nome: String): ShortArray {
    val wav = geraWav(DESENHOS.getValue(nome))
    val n = (wav.size - 44) / 2
    val saida = ShortArray(n)
    for (i in 0 until n) {
      val baixo = wav[44 + 2 * i].toInt() and 0xFF
      val alto = wav[45 + 2 * i].toInt() and 0xFF
      saida[i] = ((alto shl 8) or baixo).toShort()
    }
    return saida
  }

  /** A silhueta do som: energia média ao longo de janelas de `ms` milissegundos. */
  private fun envolvente(pcm: ShortArray, ms: Int): FloatArray {
    val janela = taxa * ms / 1000
    val n = pcm.size / janela
    return FloatArray(n) { i ->
      var soma = 0.0
      for (k in 0 until janela) {
        val a = pcm[i * janela + k] / 32768.0
        soma += a * a
      }
      Math.sqrt(soma / janela).toFloat()
    }
  }

  /** Quantas vezes a onda corta o zero por milissegundo: um tom dá poucas, o ruído dá muitas. */
  private fun cruzamentosPorMs(pcm: ShortArray): Double {
    var n = 0
    for (i in 1 until pcm.size) if ((pcm[i] >= 0) != (pcm[i - 1] >= 0)) n++
    return n * 1000.0 / (pcm.size / taxa.toDouble() * 1000.0)
  }

  /** Os blocos de som acima do limiar, separados por silêncio: um por peça comida. */
  private fun toques(pcm: ShortArray, limiar: Float = 0.35f): Int {
    val env = envolvente(pcm, 10)
    val topo = env.maxOrNull() ?: return 0
    var n = 0
    var dentro = false
    for (v in env) {
      val cheio = v > limiar * topo
      if (cheio && !dentro) n++
      dentro = cheio
    }
    return n
  }

  @Test
  fun `o arraso aguenta-se enquanto a peça viaja`() {
    val env = envolvente(pcm("mover"), 10)
    val topo = env.maxOrNull() ?: 0f
    val cheias = env.count { it > 0.35f * topo }
    assertTrue("o arrasto só dura $cheias janelas de 10 ms", cheias >= 12)
  }

  @Test
  fun `o arraso é ruído de madeira e não um beep`() {
    val porMs = cruzamentosPorMs(pcm("mover"))
    assertTrue("apenas $porMs cortes por milissegundo — isso é um tom", porMs > 1.0)
  }

  @Test
  fun `o arraso range em grãos`() {
    val env = envolvente(pcm("mover"), 5)
    val topo = env.maxOrNull() ?: 0f
    var graos = 0
    for (i in 1 until env.size - 1) {
      if (env[i] > 0.4f * topo && env[i] >= env[i - 1] && env[i] > env[i + 1]) graos++
    }
    assertTrue("o arrasto tem $graos grãos; a madeira range mais", graos >= 5)
  }

  @Test
  fun `a captura são três toques`() {
    assertEquals(3, toques(pcm("captura")))
  }

  @Test
  fun `um lance sem comida é um arrasto e não uma pancada`() {
    assertEquals(1, toques(pcm("mover")))
  }

  @Test
  fun `nenhum som encosta no topo da escala`() {
    for (nome in DESENHOS.keys) {
      val pico = pcm(nome).maxOf { Math.abs(it.toInt()) }
      assertTrue("$nome racha: pico $pico de 29 000", pico < 29_000)
    }
  }
}
