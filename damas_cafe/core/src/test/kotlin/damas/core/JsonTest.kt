package damas.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class JsonTest {
  @Test fun `escreve e le objetos`() {
    val j = Json.EMPTY_OBJ.field("a", 1).field("b", "x").field("c", true)
      .field("d", Json.array(listOf(Json.Num(1.0), Json.Str("y"))))
    val lido = Json.parse(j.render())
    assertEquals(1, lido.long("a").toInt())
    assertEquals("x", lido.str("b"))
    assertEquals(true, lido.bool("c"))
    assertEquals("y", (lido.get("d").items[1] as Json.Str).v)
  }

  @Test fun `escapa aspas e acentos`() {
    val texto = "dama \"real\" à 100% ☕\nfim"
    val lido = Json.parse(Json.obj("n" to Json.Str(texto)).render())
    assertEquals(texto, lido.str("n"))
  }

  @Test fun `tolera lixo sem rebentar`() {
    assertNull(Json.parse(null) as? Json.Obj)
    assertEquals(true, Json.parse("{ invalid") is Json.Null)
    assertEquals("y", Json.parse("""{"a":"y"}""").str("a"))
  }

  @Test fun `numeros inteiros saem sem virgula`() {
    assertEquals("""{"n":18}""", Json.EMPTY_OBJ.field("n", 18).render())
  }
}

class PalavrasMesaTest {
  @Test fun `numero vira palavra e volta`() {
    assertEquals("donut", codigoDaMesa(18))
    assertEquals(18, numeroDaMesa("donut"))
    assertEquals(18, numeroDaMesa("  DONUT  "))
    assertEquals(1, numeroDaMesa("bica"))
    assertEquals(254, numeroDaMesa(codigoDaMesa(254)))
  }

  @Test fun `aceita numeros escritos`() {
    assertEquals(18, numeroDaMesa("18"))
    assertEquals(7, numeroDaMesa("07"))
    assertNull(numeroDaMesa("255"))
    assertNull(numeroDaMesa("0"))
  }

  @Test fun `ignora acentos e espacos`() {
    assertEquals(numeroDaMesa("cafe"), numeroDaMesa("café"))
    assertEquals(numeroDaMesa("limao"), numeroDaMesa("Limão"))
  }

  @Test fun `texto sem interesse devolve null`() {
    assertNull(numeroDaMesa(""))
    assertNull(numeroDaMesa(null))
    assertNull(numeroDaMesa("mesa desconhecida"))
  }
}
