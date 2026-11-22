package com.damasnocafe.rede

import damas.core.Json

/**
 * Cliente da mesa. Todos os métodos bloqueiam — correr em Dispatchers.IO.
 * O anfitrião usa esta mesma classe apontando para 127.0.0.1: assim há um só
 * caminho de código para os dois lados do jogo.
 */
class MesaClient(private val endereco: String) {
  private val raiz = when {
    endereco.startsWith("http://") -> endereco
    endereco.startsWith("https://") -> endereco
    else -> urlBase(endereco)
  }.trimEnd('/')

  fun info(): Json? = em("GET", "/api/info", null)

  fun mesas(): List<Json> = em("GET", "/api/rooms", null)?.get("rooms")?.items ?: emptyList()

  fun abrirMesa(nomeMesa: String, nome: String, avatar: String): Json? =
    em(
      "POST", "/api/open",
      Json.EMPTY_OBJ
        .field("roomName", nomeMesa)
        .field("host", Json.EMPTY_OBJ.field("name", nome).field("avatar", avatar))
        .render(),
    )

  fun entrar(nome: String, avatar: String): Json? =
    em("POST", "/api/join", Json.EMPTY_OBJ.field("name", nome).field("avatar", avatar).render())

  fun poll(lado: String, desde: Long): Json? = em("GET", "/api/poll?side=$lado&since=$desde", null)

  fun enviar(lado: String, msg: Json): Json? =
    em("POST", "/api/msg", Json.EMPTY_OBJ.field("side", lado).field("msg", msg).render())

  fun baterPé(lado: String): Json? =
    em("POST", "/api/beat", Json.EMPTY_OBJ.field("side", lado).render())

  private fun em(metodo: String, caminho: String, corpo: String?): Json? = try {
    pedidoHttp(metodo, raiz + caminho, corpo)
  } catch (e: Exception) {
    null
  }
}
