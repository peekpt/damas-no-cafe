package damas.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MesaStoreTest {
  private var relogio = 1_000_000L
  private val mesa = MesaStore({ relogio })
  private val ana = Jogador("Ana", "cafe")
  private val bruno = Jogador("Bruno", "bolo")

  private fun avanca(ms: Long) { relogio += ms }

  private fun msg(t: String) = Json.obj("t" to Json.Str(t))

  @Test fun `abrir mesa fica a espera e mostra a palavra`() {
    mesa.abrir("Mesa da Ana", 18, "donut", ana)
    val info = mesa.info()
    assertEquals("waiting", info.str("status"))
    assertEquals(18, info.long("mesa").toInt())
    assertEquals("donut", info.str("word"))
    assertEquals("Ana", info.get("players").get("h").str("name"))
    assertTrue(info.get("players").get("g") is Json.Null)
    assertEquals(Estado.ESPERA, mesa.estado)
  }

  @Test fun `entrar passa a playing e duplicado e recusado`() {
    mesa.abrir("M", 1, "bica", ana)
    assertTrue(mesa.entrar(bruno))
    assertEquals(Estado.A_JOGAR, mesa.estado)
    assertFalse(mesa.entrar(Jogador("Terceiro", "gato")))
    assertEquals("Bruno", mesa.info().get("players").get("g").str("name"))
  }

  @Test fun `entrar numa mesa fechada falha`() {
    assertFalse(mesa.entrar(bruno))
  }

  @Test fun `mensagens so chegam ao outro lado`() {
    mesa.abrir("M", 2, "galao", ana)
    mesa.entrar(bruno)
    val enviado = mesa.enviar("h", Json.obj("t" to Json.Str("move"), "to" to Json.Num(35.0)))
    assertTrue(enviado.bool("ok"))
    val seq = enviado.long("seq")

    val doBruno = mesa.poll("g", 0)
    assertEquals(1, doBruno.get("msgs").items.size)
    val m = doBruno.get("msgs").items[0]
    assertEquals("h", m.str("from"))
    assertEquals("move", m.get("msg").str("t"))
    assertEquals(35, m.get("msg").long("to").toInt())

    assertEquals(0, mesa.poll("h", seq).get("msgs").items.size)

    mesa.enviar("g", Json.obj("t" to Json.Str("ready")))
    val novas = mesa.poll("h", seq).get("msgs").items
    assertEquals(1, novas.size)
    assertEquals("g", novas[0].str("from"))
  }

  @Test fun `lado invalido da erro`() {
    mesa.abrir("M", 3, "mocha", ana)
    assertNotNull(mesa.poll("x", 0).get("error"))
    assertNotNull(mesa.enviar("x", Json.EMPTY_OBJ).get("error"))
  }

  @Test fun `convidado sem entrar nao pode falar`() {
    mesa.abrir("M", 4, "bule", ana)
    assertNotNull(mesa.enviar("g", msg("hello")).get("error"))
  }

  @Test fun `leave fecha a mesa e bloqueia o resto`() {
    mesa.abrir("M", 5, "cha", ana)
    mesa.entrar(bruno)
    mesa.enviar("g", msg("leave"))
    assertEquals(Estado.FECHADA, mesa.estado)
    val depois = mesa.poll("h", 0)
    assertEquals("closed", depois.str("status"))
    val msgs = depois.get("msgs").items
    assertEquals(1, msgs.size)
    assertEquals("g", msgs[0].str("from"))
    assertEquals("leave", msgs[0].get("msg").str("t"))
    assertNotNull(mesa.enviar("h", msg("move")).get("error"))
  }

  @Test fun `convidado que desaparece fecha a mesa com aviso`() {
    mesa.abrir("M", 6, "matcha", ana)
    mesa.entrar(bruno)
    avanca(SEEN_TTL + 1000)
    val depois = mesa.poll("h", 0)
    assertEquals("closed", depois.str("status"))
    val ultimo = depois.get("msgs").items.last()
    assertEquals("g", ultimo.str("from"))
    assertEquals("leave", ultimo.get("msg").str("t"))
  }

  @Test fun `anfitriao que desaparece fecha a mesa para o convidado`() {
    mesa.abrir("M", 7, "chai", ana)
    mesa.entrar(bruno)
    avanca(SEEN_TTL + 1000)
    val depois = mesa.poll("g", 0)
    assertEquals("closed", depois.str("status"))
  }

  @Test fun `mesa expira ao fim de 15 minutos`() {
    mesa.abrir("M", 8, "cacau", ana)
    avanca(MESA_TTL + 1000)
    assertEquals("closed", mesa.poll("h", 0).str("status"))
  }

  @Test fun `abrir de novo limpa o historico`() {
    mesa.abrir("M", 9, "leite", ana)
    mesa.entrar(bruno)
    mesa.enviar("h", msg("emote"))
    mesa.abrir("Outra", 10, "mel", ana)
    assertEquals(0, mesa.poll("h", 0).get("msgs").items.size)
    assertEquals(Estado.ESPERA, mesa.estado)
    assertEquals("mel", mesa.info().str("word"))
  }

  @Test fun `fila tem teto e mantem as mensagens mais recentes`() {
    mesa.abrir("M", 11, "agua", ana)
    mesa.entrar(bruno)
    repeat(600) { mesa.enviar("h", msg("ping")) }
    val visiveis = mesa.poll("g", 0).get("msgs").items
    assertEquals(512, visiveis.size)
    assertEquals(89L, visiveis.first().long("seq"))
    assertEquals(600L, visiveis.last().long("seq"))
  }
}
