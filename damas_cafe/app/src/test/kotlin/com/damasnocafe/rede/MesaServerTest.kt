package com.damasnocafe.rede

import damas.core.Json
import damas.core.MesaStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Protocolo completo por HTTP: abrir mesa, ver na lista, entrar, trocar lances,
 * bater o pé e fechar. Corre no JVM, sem Android.
 */
class MesaServerTest {
  private val porta = 18_700 + (System.nanoTime() % 400).toInt()

  private fun comServidor(teste: (MesaClient) -> Unit) {
    val servidor = MesaServer(MesaStore(), porta)
    assertTrue("a porta $porta deveria abrir", servidor.arrancar())
    try {
      teste(MesaClient("http://127.0.0.1:$porta"))
    } finally {
      servidor.parar()
    }
  }

  @Test
  fun `info responde com o nome da app e a mesa ainda fechada`() = comServidor { c ->
    val info = c.info()
    assertNotNull(info)
    assertEquals("damas-no-cafe", info!!.str("app"))
    assertEquals("closed", info.str("status"))
    assertTrue(c.mesas().isEmpty())
  }

  @Test
  fun `abrir mesa cria o numero e a palavra`() = comServidor { c ->
    val r = c.abrirMesa("Mesa do João", "João", "bule")
    assertNotNull(r)
    assertEquals("h", r!!.str("side"))
    val sala = r.get("room")
    assertTrue(sala.long("mesa") in 1..254)
    assertTrue(sala.str("word").isNotEmpty())
    assertEquals("waiting", sala.str("status"))
    assertEquals("João", sala.get("players").get("h").str("name"))
    assertEquals(1, c.mesas().size)
    assertEquals("Mesa do João", c.mesas().first().str("roomName"))
  }

  @Test
  fun `convidado entra e os dois trocam mensagens por ordem`() = comServidor { c ->
    c.abrirMesa("Café da manhã", "Ana", "cafe")
    val entrada = c.entrar("Bea", "croissant")
    assertNotNull(entrada)
    assertEquals("g", entrada!!.str("side"))
    assertEquals("playing", entrada.get("room").str("status"))

    // Terceiro jogador não cabe.
    val cheio = c.entrar("Carlos", "gato")
    assertEquals(false, cheio?.bool("ok"))

    assertEquals(0, c.poll("h", 0L).lista("msgs").size)
    c.enviar("g", Json.obj("t" to Json.Str("pick"), "color" to Json.Str("ambar")))
    val visto = c.poll("h", 0L)
    val mensagens = visto.lista("msgs")
    assertEquals(1, mensagens.size)
    assertEquals("g", mensagens.first().str("from"))
    assertEquals("pick", mensagens.first().get("msg").str("t"))
    // Já lidas: um poll novo com o cursor certo não traz nada.
    assertEquals(0, c.poll("h", mensagens.first().long("seq")).lista("msgs").size)
    // O convidado não vê a própria mensagem.
    assertEquals(0, c.poll("g", 0L).lista("msgs").size)
  }

  @Test
  fun `sair fecha a mesa e diz ao outro lado`() = comServidor { c ->
    c.abrirMesa("X", "Ana", "cafe")
    c.entrar("Bea", "bolo")
    c.enviar("g", Json.obj("t" to Json.Str("leave")))
    val depois = c.poll("h", 0L)
    assertEquals("closed", depois?.str("status"))
    assertEquals("leave", depois.lista("msgs").first().get("msg").str("t"))
    assertTrue(c.mesas().isEmpty())
  }

  @Test
  fun `paginas nao api devolvem html`() = comServidor { c ->
    val texto = pedidoHttp("GET", "http://127.0.0.1:$porta/", null)
    assertNull("html não é json", texto)
  }
}

private fun Json?.lista(chave: String): List<Json> = this?.get(chave)?.items ?: emptyList()
