// Uma mesa por telemóvel: quem abre a mesa corre este estado dentro do
// servidor HTTP local; o convidado fala com ele pela rede Wi-Fi.
// É o mesmo desenho do anunciador do projeto web (heartbeat + fila de
// mensagens), mas sem id de mesa — só existe a mesa deste telemóvel.
package damas.core

import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

const val APP_NAME = "damas-no-cafe"
const val SEEN_TTL = 12_000L
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
      val (vistoOutro, outroLado) = if (lado == "h") vistoG to "g" else vistoH to "h"
      val hostCalou = agora - vistoH > SEEN_TTL
      val outroPartiu = estadoAtual == Estado.A_JOGAR && (vistoOutro == 0L || agora - vistoOutro > SEEN_TTL)
      val expirou = criadoEm != 0L && agora - criadoEm > MESA_TTL
      if (outroPartiu) fechaComAviso(outroLado)
      else if (expirou || (lado == "g" && hostCalou)) fechaSemAviso()
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
