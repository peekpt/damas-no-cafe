package com.damasnocafe.jogo

import android.content.Context
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import com.damasnocafe.ServicoMesa
import com.damasnocafe.rede.MesaClient
import com.damasnocafe.rede.emIo
import com.damasnocafe.rede.ipPrincipal
import com.damasnocafe.rede.prefixoRede
import com.damasnocafe.rede.urlBase
import com.damasnocafe.rede.varrerSubrede
import com.damasnocafe.som.Sons
import damas.core.Json
import damas.core.Lado
import damas.core.Lance
import damas.core.Lances
import damas.core.Tabuleiro
import damas.core.applyMove
import damas.core.codigoDaMesa
import damas.core.initialState
import damas.core.legalMoves
import damas.core.moveNotation
import damas.core.numeroDaMesa
import damas.core.winnerIfOver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

enum class Ecra { Inicio, Abrir, Mesas, Lobby, Jogo }

enum class Modo { Local, Online }

enum class Ligacao { Nada, A_ligar, Ligado, Perdida }

data class Aviso(val id: Int, val texto: String, val tipo: String = "")

data class Registo(val lado: Lado, val texto: String)

data class FimJogo(val vencedor: Lado? = null, val empate: String? = null, val motivo: String = "")

/** Um banco do lobby: nome, avatar, cor das peças e se o jogador já está pronto. */
@Stable
class Assento(nome: String, avatar: String, cor: String) {
  var nome by mutableStateOf(nome)
  var avatar by mutableStateOf(avatar)
  var cor by mutableStateOf(cor)
  var pronto by mutableStateOf(false)
}

/** A peça como o tabuleiro a vê: sobrevive entre jogadas para poder animar. */
@Stable
class PecaVisual(val id: Int, lado: Lado, dama: Boolean, casa: Int) {
  var lado by mutableStateOf(lado)
  var dama by mutableStateOf(dama)
  var casa by mutableStateOf(casa)
  var trilha by mutableStateOf<List<Int>>(emptyList())
  var morrendo by mutableStateOf(false)
}

enum class TipoEfeito { Lasca, Coroa }

data class Efeito(val id: Int, val tipo: TipoEfeito, val casa: Int, val cor: Color)

data class Emote(val id: Int, val emoji: String)

data class MesaDescoberta(
  val ip: String,
  val numero: Int,
  val palavra: String,
  val nome: String,
  val anfitriao: String,
  val avatar: String,
  val ocupada: Boolean,
)

sealed class Dialogo {
  object Regras : Dialogo()
  object Sair : Dialogo()
  object Desistir : Dialogo()
  data class Empate(val de: String) : Dialogo()
}

/**
 * Todo o estado de uma sessão — local ou pelo Wi-Fi.
 * Os dois lados falam o mesmo HTTP: o anfitrião aponta para 127.0.0.1.
 */
@Stable
class Sessao(private val contexto: Context, val sons: Sons, private val escopo: CoroutineScope) {
  var perfil by mutableStateOf(GuardaPerfil.ler(contexto))
    private set

  var ecra by mutableStateOf(Ecra.Inicio)
  var modo by mutableStateOf(Modo.Local)
    private set
  var anfitriao by mutableStateOf(true)
    private set

  /** Quem leva as brancas troca a cada partida; o anfitrião abre sempre de branco. */
  var brancosDoAnfitriao by mutableStateOf(true)
    private set

  val meuLado: Lado get() = if (modo == Modo.Local) Lado.BRANCAS else ladoDaWire(ladoWire)
  var ligacao by mutableStateOf(Ligacao.Nada)
  var convidadoAqui by mutableStateOf(false)

  var nomeMesa by mutableStateOf("")
  var numero by mutableStateOf(0)
  var palavra by mutableStateOf("")
  var ip by mutableStateOf("")

  val bancos = mapOf(
    Lado.BRANCAS to Assento("Jogador 1", "cafe", COR_BRANCAS),
    Lado.PRETAS to Assento("Jogador 2", "croissant", COR_PRETAS),
  )

  var tabuleiro by mutableStateOf<Tabuleiro>(initialState())
    private set
  var vez by mutableStateOf(Lado.BRANCAS)
    private set
  var lances by mutableStateOf(Lances(emptyList(), false, 0))
    private set
  var sel by mutableStateOf<List<Int>?>(null)
    private set
  var seqsSel by mutableStateOf<List<Lance>>(emptyList())
    private set
  var ultimo by mutableStateOf<Pair<Int, Int>?>(null)
    private set
  var historico by mutableStateOf(listOf<Registo>())
    private set
  var capturas by mutableStateOf(mapOf(Lado.BRANCAS to 0, Lado.PRETAS to 0))
    private set
  var resultado by mutableStateOf<FimJogo?>(null)
  var silencioso by mutableStateOf(0)
    private set

  var pecas by mutableStateOf(listOf<PecaVisual>())
    private set
  var efeitos by mutableStateOf(listOf<Efeito>())
    private set
  var emote by mutableStateOf<Emote?>(null)
    private set
  /** O último emoji que se lançou: o toque no botão do canto dispara-o sem perguntar. */
  var ultimoEmoji by mutableStateOf(GuardaEmoji.ler(contexto))
    private set
  var celebracao by mutableStateOf(0)
    private set
  var treme by mutableStateOf(0)

  var aviso by mutableStateOf<Aviso?>(null)
    private set
  var dialogo by mutableStateOf<Dialogo?>(null)

  var mesas by mutableStateOf(listOf<MesaDescoberta>())
  var aVarrer by mutableStateOf(false)

  private var cliente: MesaClient? = null
  private var cursor = 0L
  private var proximoId = 0
  private var marcador = 0
  private var loop: Job? = null

  val outroLado: Lado get() = meuLado.outro
  val virado: Boolean get() = modo == Modo.Online && meuLado == Lado.PRETAS
  private val ladoWire: String get() = if (anfitriao) "h" else "g"

  /** O banco do tabuleiro correspondente a quem fala pela rede ("h" ou "g"). */
  private fun ladoDaWire(de: String): Lado =
    if ((de == "h") == brancosDoAnfitriao) Lado.BRANCAS else Lado.PRETAS

  fun podeJogar(): Boolean =
    resultado == null && (modo == Modo.Local || (vez == meuLado && ligacao == Ligacao.Ligado))

  /** O banco que se mostra por baixo no tabuleiro: no telemóvel é sempre o meu. */
  fun ladoDeBaixo(): Lado = if (modo == Modo.Online) meuLado else Lado.BRANCAS

  fun guardarPerfil(nome: String, avatar: String) {
    perfil = Perfil(nome.take(14), avatar)
    GuardaPerfil.gravar(contexto, perfil)
    bancos[meuLado]?.nome = perfil.nome.ifBlank { "Jogador" }
    bancos[meuLado]?.avatar = perfil.avatar
  }

  fun dizer(texto: String, tipo: String = "") {
    val id = ++marcador
    aviso = Aviso(id, texto, tipo)
    escopo.launch {
      delay(2600)
      if (aviso?.id == id) aviso = null
    }
  }

  // ───────────────────────── mesas ─────────────────────────

  /** Abrir mesa = ligar o servidor neste telemóvel e anunciá-la. */
  fun abrirMesa(nome: String) = escopo.launch {
    ServicoMesa.arrancar(contexto)
    delay(250)
    val c = MesaClient(urlBase("127.0.0.1"))
    val resposta = emIo { c.abrirMesa(nome.ifBlank { "Mesa de ${perfil.nome}" }, perfil.nome, perfil.avatar) }
    val sala = resposta?.get("room")
    if (sala == null || !resposta.bool("ok")) {
      dizer("Não consegui abrir a mesa. Liga o Wi-Fi e tenta outra vez.", "warn")
      sons.tocar("erro")
      return@launch
    }
    modo = Modo.Online
    anfitriao = true
    brancosDoAnfitriao = true
    configurarOnline(sala, c)
    resetBancos()
    nomeMesa = sala.str("roomName")
    ip = ipPrincipal()
    convidadoAqui = false
    bancos[meuLado]?.nome = perfil.nome.ifBlank { "Jogador" }
    bancos[meuLado]?.avatar = perfil.avatar
    entrarNoLobby()
    sons.tocar("porta")
  }

  fun entrarNaMesa(ipMesa: String) = entrarNaMesa(ipMesa, null)

  fun entrarNaMesa(ipMesa: String, mesa: MesaDescoberta?) = escopo.launch {
    ligacao = Ligacao.A_ligar
    val c = MesaClient(urlBase(ipMesa))
    val resposta = emIo { c.entrar(perfil.nome, perfil.avatar) }
    val sala = resposta?.get("room")
    if (resposta == null || sala == null || !resposta.bool("ok")) {
      ligacao = Ligacao.Nada
      dizer(resposta?.str("error")?.ifBlank { null } ?: "Não encontrei essa mesa. Confirma o número e o Wi-Fi.", "warn")
      sons.tocar("erro")
      return@launch
    }
    modo = Modo.Online
    anfitriao = false
    brancosDoAnfitriao = true
    configurarOnline(sala, c)
    resetBancos()
    nomeMesa = mesa?.nome?.ifBlank { null } ?: sala.str("roomName")
    ip = ipMesa
    convidadoAqui = true
    bancos[meuLado]?.nome = perfil.nome.ifBlank { "Convidado" }
    bancos[meuLado]?.avatar = perfil.avatar
    salasPara(sala)
    entrarNoLobby()
    sons.tocar("porta")
  }

  /** Quem entra pelo número/palavra: reconstrói o IP a partir do Wi-Fi actual. */
  fun entrarPorNumero(texto: String) = escopo.launch {
    val n = numeroDaMesa(texto)
    if (n == null) {
      dizer("Escreve o número da mesa (1 a 254) ou a palavra, por exemplo: donut.", "warn")
      sons.tocar("erro")
      return@launch
    }
    val base = prefixoRede(ipPrincipal()) ?: return@launch
    aVarrer = true
    val info = emIo { MesaClient(urlBase("$base$n")).info() }
    aVarrer = false
    when {
      info == null -> dizer("A mesa $n ($base$n) não responde. Estão no mesmo Wi-Fi?", "warn").also { sons.tocar("erro") }
      info.str("status") != "waiting" -> dizer("A mesa $n ${codigoDaMesa(n) ?: ""} já não está à espera de ninguém.", "warn").also { sons.tocar("erro") }
      else -> entrarNaMesa("$base$n", null)
    }
  }

  fun procurarMesas() = escopo.launch {
    aVarrer = true
    mesas = emptyList()
    val encontradas = ArrayList<MesaDescoberta>()
    for (ipVisitado in varrerSubrede(ipPrincipal())) {
      for (r in emIo { MesaClient(urlBase(ipVisitado)).mesas() }.orEmpty()) {
        encontradas += MesaDescoberta(
          ip = ipVisitado,
          numero = r.long("mesa").toInt(),
          palavra = r.str("word"),
          nome = r.str("roomName"),
          anfitriao = r.get("players").get("h").str("name"),
          avatar = r.get("players").get("h").str("avatar"),
          ocupada = r.str("status") != "waiting",
        )
      }
    }
    mesas = encontradas.sortedBy { it.numero }
    aVarrer = false
    if (encontradas.isEmpty()) dizer("Nenhuma mesa aberta neste Wi-Fi. Pede ao amigo para abrir a dele.", "warn")
  }

  private fun configurarOnline(sala: Json, c: MesaClient) {
    cliente = c
    numero = sala.long("mesa").toInt()
    palavra = sala.str("word")
    cursor = 0
    resultado = null
    ultimo = null
    historico = emptyList()
    pecas = emptyList()
    sel = null
    ligacao = Ligacao.Ligado
    arrancarLoop()
  }

  private fun salasPara(sala: Json) {
    val players = sala.get("players")
    bancos.getValue(ladoDaWire("h")).nome = players.get("h").str("name")
    bancos.getValue(ladoDaWire("h")).avatar = players.get("h").str("avatar")
    bancos.getValue(ladoDaWire("g")).nome = players.get("g").str("name")
    bancos.getValue(ladoDaWire("g")).avatar = players.get("g").str("avatar")
  }

  private fun arrancarLoop() {
    loop?.cancel()
    loop = escopo.launch {
      var falhas = 0
      while (isActive) {
        val c = cliente
        if (c == null) {
          delay(600)
          continue
        }
        val resposta = emIo { c.poll(ladoWire, cursor) }
        if (resposta == null || !resposta.has("status")) {
          falhas++
          if (falhas >= 3 && ligacao == Ligacao.Ligado) {
            ligacao = Ligacao.Perdida
            dizer("Perdi a ligação ao teu amigo.", "bad")
            sons.tocar("erro")
          }
        } else {
          falhas = 0
          if (ligacao == Ligacao.Perdida) {
            ligacao = Ligacao.Ligado
            dizer("Ligação recuperada.")
          }
          numero = resposta.long("mesa").toInt()
          palavra = resposta.str("word")
          val estado = resposta.str("status")
          convidadoAqui = estado != "waiting"
          for (entrada in resposta.get("msgs").items) {
            cursor = maxOf(cursor, entrada.long("seq"))
            aplicar((entrada.get("from") as? Json.Str)?.v ?: "h", entrada.get("msg"))
          }
          resposta.get("players").let { players ->
            for (de in listOf("h", "g")) {
              val banco = bancos.getValue(ladoDaWire(de))
              val p = players.get(de)
              banco.nome = p.str("name").ifBlank { banco.nome }
              banco.avatar = p.str("avatar").ifBlank { banco.avatar }
            }
          }
          if (estado == "closed" && ecra != Ecra.Inicio) {
            dizer("A mesa fechou.", "warn")
            voltarParaInicio()
          }
        }
        delay(if (ecra == Ecra.Lobby) 500 else 700)
      }
    }
  }

  private fun enviar(vararg pares: Pair<String, Json>) {
    if (modo != Modo.Online) return
    val c = cliente ?: return
    val msg = Json.obj(*pares)
    escopo.launch { emIo { c.enviar(ladoWire, msg) } }
  }

  // ───────────────────────── lobby ─────────────────────────

  fun jogarLocal() {
    loop?.cancel()
    loop = null
    cliente = null
    modo = Modo.Local
    anfitriao = true
    brancosDoAnfitriao = true
    ligacao = Ligacao.Nada
    nomeMesa = "Mesa cá de casa"
    numero = 0
    palavra = ""
    resetBancos()
    bancos.getValue(Lado.BRANCAS).nome = "Jogador 1"
    bancos.getValue(Lado.BRANCAS).avatar = "cafe"
    bancos.getValue(Lado.PRETAS).nome = "Jogador 2"
    bancos.getValue(Lado.PRETAS).avatar = "croissant"
    entrarNoLobby()
  }

  /** O jogo de damas só se joga a preto e branco: a cor pertence ao lado, não ao jogador. */
  private fun resetBancos() {
    bancos.getValue(Lado.BRANCAS).cor = COR_BRANCAS
    bancos.getValue(Lado.PRETAS).cor = COR_PRETAS
    bancos.values.forEach { it.pronto = false }
  }

  /** Modo local: as duas pessoas trocam de lado, para que cada uma faça de brancas à vez. */
  private fun trocarDeLado() {
    val w = bancos.getValue(Lado.BRANCAS)
    val b = bancos.getValue(Lado.PRETAS)
    val nome = w.nome
    val avatar = w.avatar
    w.nome = b.nome
    w.avatar = b.avatar
    b.nome = nome
    b.avatar = avatar
  }

  private fun entrarNoLobby() {
    resultado = null
    ecra = Ecra.Lobby
  }

  fun estouPronto() {
    if (modo == Modo.Local) return
    val meu = bancos.getValue(meuLado)
    meu.pronto = true
    enviar("t" to Json.Str("ready"))
    maybeStart()
  }

  fun comecarLocal() {
    comecarJogo()
  }

  private fun maybeStart() {
    if (!anfitriao) return
    if (!bancos.getValue(Lado.BRANCAS).pronto || !bancos.getValue(Lado.PRETAS).pronto) return
    enviar("t" to Json.Str("start"), "swap" to Json.Bool(brancosDoAnfitriao))
    comecarJogo()
  }

  // ───────────────────────── jogo ─────────────────────────

  private fun comecarJogo() {
    resetBancos()
    tabuleiro = initialState()
    vez = Lado.BRANCAS
    lances = legalMoves(tabuleiro, Lado.BRANCAS)
    sel = null
    seqsSel = emptyList()
    ultimo = null
    historico = emptyList()
    capturas = mapOf(Lado.BRANCAS to 0, Lado.PRETAS to 0)
    silencioso = 0
    resultado = null
    efeitos = emptyList()
    var id = proximoId
    pecas = tabuleiro.mapIndexedNotNull { casa, peca ->
      if (peca == null) null else PecaVisual(++id, peca.lado, peca.dama, casa)
    }
    proximoId = id
    ecra = Ecra.Jogo
    dialogo = null
    sons.tocar("aviso")
  }

  /** Toque numa casa: selecciona, recua um passo ou joga. */
  fun tocarCasa(casa: Int) {
    if (resultado != null) return
    if (!podeJogar()) {
      if (modo == Modo.Online && ligacao != Ligacao.Perdida) {
        dizer("Agora joga o teu amigo.")
        sons.tocar("erro")
      }
      return
    }
    val prefix = sel
    if (prefix != null && prefix.size > 1 && casa == prefix[prefix.size - 2]) {
      sel = prefix.dropLast(1)
      sons.tocar("clique")
      return
    }
    if (prefix != null) {
      val sequencias = seqsSel.filter { it.path.subList(0, prefix.size) == prefix }
      if (sequencias.any { it.path.size > prefix.size && it.path[prefix.size] == casa }) {
        val novo = prefix + casa
        val completo = sequencias.firstOrNull { it.path == novo }
        if (completo != null) {
          sel = null
          seqsSel = emptyList()
          executarLance(completo, meu = true)
        } else {
          sel = novo
          sons.tocar("clique")
        }
        return
      }
      if (casa == prefix.first() && prefix.size == 1) {
        sel = null
        seqsSel = emptyList()
        sons.tocar("clique")
        return
      }
      if (prefix.size > 1) {
        abanar()
        return
      }
    }
    val peca = tabuleiro.getOrNull(casa)
    if (peca != null && peca.lado == vez) {
      val minhas = lances.lista.filter { it.from == casa }
      if (minhas.isEmpty()) {
        abanar()
        dizer(if (lances.mustCapture) "Captura obrigatória! Toca numa peça a pulsar." else "Essa peça não tem jogadas.", "warn")
        return
      }
      seqsSel = minhas
      sel = listOf(casa)
      sons.tocar("clique")
    } else if (sel != null) {
      sel = null
      seqsSel = emptyList()
    }
  }

  private fun abanar() {
    sons.tocar("erro")
    treme++
  }

  private fun executarLance(lance: Lance, meu: Boolean) {
    val antes = tabuleiro
    val lado = vez
    val eraPeao = antes[lance.from]?.dama != true
    val resultadoLance = applyMove(antes, lance)
    tabuleiro = resultadoLance.board

    // As peças comidas caem uma a uma, sincronizadas com o salto por cima delas.
    val vitimadas = lance.capturas.mapNotNull { cap -> pecas.firstOrNull { it.casa == cap && !it.morrendo } }
    if (vitimadas.isNotEmpty()) {
      capturas = capturas + (lado to (capturas.getValue(lado) + vitimadas.size))
      escopo.launch {
        vitimadas.forEachIndexed { n, vitima ->
          delay(140L * n + 90L)
          vitima.morrendo = true
          efeitos += Efeito(++proximoId, TipoEfeito.Lasca, vitima.casa, corPorId(bancos.getValue(vitima.lado).cor).base)
        }
      }
      escopo.launch {
        delay(140L * vitimadas.size + 460L)
        pecas = pecas.filterNot { it.morrendo }
      }
    }

    val movedora = pecas.firstOrNull { it.casa == lance.from && !it.morrendo }
    if (movedora != null) {
      if (lance.path.size > 1) movedora.trilha = lance.path
      movedora.casa = lance.to
      if (resultadoLance.promovida) {
        movedora.dama = true
        escopo.launch {
          delay(140L * (lance.path.size - 1) + 120L)
          efeitos += Efeito(++proximoId, TipoEfeito.Coroa, lance.to, CorDaCoroa)
          sons.tocar("coroa")
          dizer("Nova dama!")
        }
      }
    }

    ultimo = lance.from to lance.to
    historico = historico + Registo(lado, moveNotation(lance))
    silencioso = if (resultadoLance.capturadas > 0 || eraPeao) 0 else silencioso + 1
    sons.tocar(if (resultadoLance.capturadas > 0) "captura" else "mover")
    vez = lado.outro
    lances = legalMoves(tabuleiro, vez)
    if (meu && modo == Modo.Online) {
      enviar(
        "t" to Json.Str("move"),
        "from" to Json.Num(lance.from.toDouble()),
        "to" to Json.Num(lance.to.toDouble()),
        "captures" to Json.array(lance.capturas.map { Json.Num(it.toDouble()) }),
      )
    }
    winnerIfOver(tabuleiro, vez)?.let { terminar(FimJogo(vencedor = it)); return }
    if (silencioso >= 50) terminar(FimJogo(empate = "25 jogadas seguidas só de damas, sem capturas."))
    else if (lances.mustCapture && podeJogar()) dizer("Captura obrigatória!", "warn")
  }

  fun terminarResultado(fim: FimJogo) {
    if (modo == Modo.Online && resultado == null && ecra == Ecra.Jogo) terminar(fim)
  }

  private fun terminar(fim: FimJogo) {
    resultado = fim
    sel = null
    seqsSel = emptyList()
    dialogo = null
    val vencedor = fim.vencedor
    if (vencedor != null) {
      val ganhei = modo == Modo.Online && vencedor == meuLado
      if (modo == Modo.Local || ganhei) {
        sons.tocar("vitoria")
        celebracao++
      } else {
        sons.tocar("derrota")
      }
    } else {
      sons.tocar("empate")
    }
  }

  fun oferecerEmpate() {
    if (resultado != null) return
    if (modo == Modo.Local) {
      terminar(FimJogo(empate = "Empate por acordo."))
      return
    }
    if (ligacao != Ligacao.Ligado) {
      dizer("Sem ligação ao teu amigo.", "warn")
      return
    }
    enviar("t" to Json.Str("drawOffer"))
    dizer("Proposta de empate enviada.")
  }

  fun responderEmpate(aceite: Boolean) {
    dialogo = null
    enviar("t" to Json.Str("drawResponse"), "accept" to Json.Bool(aceite))
    if (aceite) terminar(FimJogo(empate = "Empate por acordo."))
  }

  /** Dar a partida como perdida, para o amigo poder vencer e abrirem outra. */
  fun desistir() {
    dialogo = null
    if (modo == Modo.Local) {
      val quemDesiste = bancos.getValue(vez).nome.ifBlank { "Jogador" }
      terminar(FimJogo(vencedor = vez.outro, motivo = "$quemDesiste desistiu da partida."))
      return
    }
    if (ligacao != Ligacao.Ligado) {
      dizer("Sem ligação ao teu amigo.", "warn")
      return
    }
    enviar("t" to Json.Str("resign"))
    terminar(FimJogo(vencedor = outroLado, motivo = "Desististe da partida."))
  }

  fun novaPartida() {
    if (modo == Modo.Online) {
      if (ligacao == Ligacao.Perdida) {
        dizer("Sem ligação ao teu amigo.", "warn")
        return
      }
      brancosDoAnfitriao = !brancosDoAnfitriao
      enviar("t" to Json.Str("rematch"))
    } else {
      trocarDeLado()
    }
    resultado = null
    resetBancos()
    entrarNoLobby()
  }

  fun sairDaMesa() {
    dialogo = null
    if (modo == Modo.Online && ligacao != Ligacao.Perdida) enviar("t" to Json.Str("leave"))
    voltarParaInicio()
    ServicoMesa.desligar(contexto)
  }

  private fun voltarParaInicio() {
    loop?.cancel()
    loop = null
    cliente = null
    modo = Modo.Local
    anfitriao = true
    brancosDoAnfitriao = true
    ligacao = Ligacao.Nada
    convidadoAqui = false
    resultado = null
    sel = null
    pecas = emptyList()
    efeitos = emptyList()
    numero = 0
    palavra = ""
    ecra = Ecra.Inicio
  }

  fun lancarEmote(emoji: String) {
    ultimoEmoji = emoji
    GuardaEmoji.gravar(contexto, emoji)
    sons.tocar("clique")
    mostrarEmote(emoji)
    if (modo == Modo.Online) enviar("t" to Json.Str("emote"), "e" to Json.Str(emoji))
  }

  /** O toque curto do botão do canto: dispara o último emoji para o ecrã do amigo. */
  fun emojiRapido() = lancarEmote(ultimoEmoji)

  private fun mostrarEmote(emoji: String) {
    val id = ++proximoId
    emote = Emote(id, emoji)
    escopo.launch {
      delay(1500)
      if (emote?.id == id) emote = null
    }
  }

  // ───────────────────────── o que chega do outro lado ─────────────────────────

  private fun aplicar(de: String, msg: Json) {
    if (msg !is Json.Obj) return
    val banco = bancos.getValue(ladoDaWire(de))
    val nome = banco.nome.ifBlank { "O teu amigo" }
    when (msg.str("t")) {
      "ready" -> {
        banco.pronto = true
        maybeStart()
      }

      "start" -> {
        brancosDoAnfitriao = msg.bool("swap", brancosDoAnfitriao)
        comecarJogo()
      }

      "move" -> {
        val casaDe = msg.long("from").toInt()
        val casaPara = msg.long("to").toInt()
        val comidas = msg.get("captures").items.mapNotNull { (it as? Json.Num)?.v?.toInt() }
        // a jogada legal que bate certo com o que o amigo mandou: dá-nos o trajeto para animar
        val lance = lances.lista.firstOrNull {
          it.from == casaDe && it.to == casaPara && it.capturas == comidas
        } ?: Lance(casaDe, casaPara, listOf(casaDe, casaPara), comidas)
        sel = null
        seqsSel = emptyList()
        executarLance(lance, meu = false)
      }

      "rematch" -> {
        resultado = null
        brancosDoAnfitriao = !brancosDoAnfitriao
        resetBancos()
        entrarNoLobby()
        dizer("$nome quer jogar outra vez.")
        sons.tocar("aviso")
      }

      "resign" -> terminar(FimJogo(vencedor = meuLado, motivo = "$nome desistiu da partida."))

      "emote" -> mostrarEmote(msg.str("e", "☕"))

      "drawOffer" -> {
        dialogo = Dialogo.Empate(nome)
        sons.tocar("aviso")
      }

      "drawResponse" -> {
        if (msg.bool("accept")) terminar(FimJogo(empate = "Empate por acordo."))
        else dizer("$nome recusou o empate.")
      }

      "leave" -> {
        dizer("$nome saiu da mesa.", "warn")
        voltarParaInicio()
      }
    }
  }

  fun efeitoTerminado(id: Int) {
    efeitos = efeitos.filterNot { it.id == id }
  }

  fun pedirSaida() {
    dialogo = Dialogo.Sair
  }

  fun fechar() {
    loop?.cancel()
  }
}

private val CorDaCoroa = Color(0xFFF0A63C)
