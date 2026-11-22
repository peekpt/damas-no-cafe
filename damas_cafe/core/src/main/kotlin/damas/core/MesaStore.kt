// Uma mesa por telemóvel: quem abre a mesa corre este estado dentro do
// servidor HTTP local; o convidado fala com ele pela rede Wi-Fi.
// É o mesmo desenho do anunciador do projeto web (heartbeat + fila de
// mensagens), mas sem id de mesa — só existe a mesa deste telemóvel.
package damas.core

import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

const val APP_NAME = "damas-no-cafe"

/** Quanto tempo um telemóvel pode ficar mudo no lobby antes de a mesa se fechar. */
const val SEEN_TTL = 12_000L

/** A meio de uma partida há pausas: ecrã apagado, um café, uma chamada. Dá-se minutos. */
const val JOGO_TTL = 4 * 60_000L

/** Mesa em espera ou adormecida: 15 minutos sem um sinal de ninguém libertam a mesa. */
const val MESA_TTL = 15 * 60_000L
private const val MAX_MENSAGENS = 512

enum class Estado(val wire: String) {
  ESPERA("waiting"),
  A_JOGAR("playing"),
  FECHADA("closed");

  companion object {
    fun fromWire(s: String) = entries.firstOrNull { it.wire == s } ?: FECHADA
  }
}

data class Jogador(val nome: String, val avatar: String) {
  fun json(): Json = Json.obj("name" to Json.Str(nome), "avatar" to Json.Str(avatar))
}

class MesaStore(private val agora: () -> Long = System::currentTimeMillis) {
  private val lock = ReentrantLock()

  private var nomeMesa = ""
  private var numero = 0
  private var palavraMesa = ""
  private var estadoAtual = Estado.FECHADA
  private var criadoEm = 0L
  private var jogadorH: Jogador? = null
  private var jogadorG: Jogador? = null
  private val fila = ArrayList<Triple<Long, String, Json>>()
  private var seq = 0L
  private var vistoH = 0L
  private var vistoG = 0L

  val estado: Estado get() = lock.withLock { estadoAtual }

  fun abrir(nome: String, numero: Int, palavra: String, anfitriao: Jogador) = lock.withLock {
    nomeMesa = nome
    this.numero = numero
    palavraMesa = palavra
    estadoAtual = Estado.ESPERA
    criadoEm = agora()
    jogadorH = anfitriao
    jogadorG = null
    fila.clear()
    seq = 0
    vistoH = agora()
    vistoG = 0
  }

  fun entrar(convidado: Jogador): Boolean = lock.withLock {
    if (estadoAtual != Estado.ESPERA) return@withLock false
    jogadorG = convidado
    estadoAtual = Estado.A_JOGAR
    val agora = agora()
    vistoG = agora
    vistoH = agora
    true
  }

  fun poll(lado: String, desde: Long): Json = lock.withLock {
    if (lado != "h" && lado != "g") return@withLock erro("lado inválido")
    val agora = agora()
    marcar(lado, agora)
    if (estadoAtual != Estado.FECHADA) {
      val aJogar = estadoAtual == Estado.A_JOGAR
      val graca = if (aJogar) JOGO_TTL else SEEN_TTL
      val quieto = if (aJogar) outroPartiu(agora, graca)
      else if (seCaiu(agora, vistoH, SEEN_TTL)) "h" else null
      val quieta = maxOf(vistoH, vistoG)
      val expirou = agora - maxOf(criadoEm, quieta) > MESA_TTL
      if (quieto != null) fechaComAviso(quieto)
      else if (expirou) fechaSemAviso()
    }
    val novas = fila.filter { it.first > desde && it.second != lado }
      .map { (n, de, msg) -> Json.obj("seq" to Json.Num(n.toDouble()), "from" to Json.Str(de), "msg" to msg) }
    Json.EMPTY_OBJ
      .field("status", estadoAtual.wire)
      .field("msgs", Json.array(novas))
      .field("players", jogadores())
      .field("mesa", numero)
      .field("word", palavraMesa)
      .field("roomName", nomeMesa)
  }

  fun enviar(lado: String, msg: Json): Json = lock.withLock {
    if (lado != "h" && lado != "g") return@withLock erro("lado inválido")
    if (lado == "g" && jogadorG == null) return@withLock erro("ainda ninguém entrou nesta mesa")
    if (msg !is Json.Obj) return@withLock erro("mensagem inválida")
    if (estadoAtual == Estado.FECHADA) return@withLock erro("a mesa já fechou").field("ok", false)
    marcar(lado, agora())
    empurra(lado, msg)
    if (msg.str("t") == "leave") fechaSemAviso()
    Json.EMPTY_OBJ.field("ok", true).field("seq", seq)
  }

  /** Bate o pé do lado que pergunta, sem ler mensagens. */
  fun bater(lado: String) = lock.withLock { if (lado == "h" || lado == "g") marcar(lado, agora()) }

  fun info(): Json = lock.withLock {
    Json.EMPTY_OBJ
      .field("app", APP_NAME)
      .field("status", estadoAtual.wire)
      .field("mesa", numero)
      .field("word", palavraMesa)
      .field("roomName", nomeMesa)
      .field("players", jogadores())
  }

  private fun marcar(lado: String, quando: Long) {
    if (lado == "h") vistoH = quando else vistoG = quando
  }

  /** Qual dos dois deixou de dar sinais durante a partida (null se estão os dois vivos). */
  private fun outroPartiu(agora: Long, graca: Long): String? = when {
    seCaiu(agora, vistoG, graca) -> "g"
    jogadorG != null && seCaiu(agora, vistoH, graca) -> "h"
    else -> null
  }

  private fun seCaiu(agora: Long, visto: Long, graca: Long): Boolean =
    visto == 0L || agora - visto > graca

  private fun empurra(lado: String, msg: Json) {
    seq += 1
    fila.add(Triple(seq, lado, msg))
    while (fila.size > MAX_MENSAGENS) fila.removeAt(0)
  }

  private fun fechaComAviso(ladoQueDesapareceu: String) {
    if (estadoAtual == Estado.FECHADA) return
    empurra(ladoQueDesapareceu, Json.obj("t" to Json.Str("leave")))
    estadoAtual = Estado.FECHADA
  }

  private fun fechaSemAviso() {
    estadoAtual = Estado.FECHADA
  }

  private fun jogadores(): Json = Json.obj("h" to (jogadorH?.json() ?: Json.Null), "g" to (jogadorG?.json() ?: Json.Null))

  private fun erro(msg: String) = Json.obj("ok" to Json.Bool(false), "error" to Json.Str(msg))
}
