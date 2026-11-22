package com.damasnocafe.rede

import damas.core.APP_NAME
import damas.core.Estado
import damas.core.Jogador
import damas.core.Json
import damas.core.MesaStore
import damas.core.codigoDaMesa
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder

/**
 * Servidor HTTP da mesa, a correr dentro do telemóvel que abriu o jogo.
 * Fala só a língua do jogo: /api/info, /api/open, /api/join, /api/poll, /api/msg, /api/beat.
 * Uma mesa por telemóvel — por isso o URL não leva id de mesa.
 */
class MesaServer(
  private val store: MesaStore,
  val porta: Int = PORTA_MESA,
) {
  @Volatile private var ativo = false
  private var escuta: ServerSocket? = null
  private var fio: Thread? = null
  private var numeroPalavra: Pair<Int, String>? = null

  fun arrancar(): Boolean = try {
    val s = ServerSocket().apply {
      reuseAddress = true
      bind(InetSocketAddress(porta))
    }
    escuta = s
    ativo = true
    fio = Thread(::aceitar, "mesa-http").apply { isDaemon = true; start() }
    true
  } catch (e: Exception) {
    false
  }

  fun parar() {
    ativo = false
    try {
      escuta?.close()
    } catch (e: Exception) {
      Unit
    }
    escuta = null
    fio = null
  }

  private fun aceitar() {
    val s = escuta ?: return
    while (ativo) {
      val ligacao = try {
        s.accept()
      } catch (e: Exception) {
        break
      }
      Thread({ tratar(ligacao) }, "mesa-http-req").apply { isDaemon = true; start() }
    }
  }

  private fun tratar(ligacao: Socket) {
    try {
      ligacao.soTimeout = 8000
      val entrada = BufferedInputStream(ligacao.getInputStream())
      val linha = lerLinha(entrada) ?: return
      val partes = linha.split(' ')
      if (partes.size < 2) return
      val metodo = partes[0]
      val alvo = partes[1]
      if (metodo != "GET" && metodo != "POST" && metodo != "OPTIONS") return
      val corpo = lerCorpo(entrada)
      responder(ligacao.getOutputStream(), metodo, alvo, corpo)
    } catch (e: Exception) {
      Unit
    } finally {
      try {
        ligacao.close()
      } catch (e: Exception) {
        Unit
      }
    }
  }

  private fun lerCorpo(entrada: InputStream): Json? {
    var comprimento = 0
    while (true) {
      val cabecalho = lerLinha(entrada) ?: break
      if (cabecalho.isEmpty()) break
      val i = cabecalho.indexOf(':')
      if (i > 0 && cabecalho.substring(0, i).equals("content-length", ignoreCase = true)) {
        comprimento = cabecalho.substring(i + 1).trim().toIntOrNull() ?: 0
      }
    }
    if (comprimento <= 0 || comprimento > 200_000) return null
    val bytes = ByteArray(comprimento)
    var lido = 0
    while (lido < comprimento) {
      val n = entrada.read(bytes, lido, comprimento - lido)
      if (n <= 0) break
      lido += n
    }
    val json = Json.parse(String(bytes, 0, lido, Charsets.UTF_8))
    return json as? Json.Obj
  }

  private fun responder(saida: OutputStream, metodo: String, alvo: String, corpo: Json?) {
    val caminho = alvo.substringBefore('?')
    val consulta = analiseConsulta(alvo.substringAfter('?', ""))
    if (caminho == "/" && metodo == "GET") {
      escrever(saida, 200, "OK", PAGINA, "text/html; charset=utf-8")
      return
    }
    val resposta = when {
      metodo == "OPTIONS" -> Triple(204, "", "text/plain")
      caminho == "/api/info" -> par(200, info())
      caminho == "/api/rooms" -> par(200, listas())
      caminho == "/api/open" && metodo == "POST" -> abrir(corpo)
      caminho == "/api/join" && metodo == "POST" -> entrar(corpo)
      caminho == "/api/poll" && metodo == "GET" ->
        par(200, store.poll(consulta["side"] ?: "h", (consulta["since"] ?: "0").toLongOrNull() ?: 0L))
      caminho == "/api/msg" && metodo == "POST" ->
        par(200, store.enviar(corpo?.str("side") ?: "", corpo?.get("msg") ?: Json.Null))
      caminho == "/api/beat" && metodo == "POST" -> {
        store.bater(corpo?.str("side") ?: "")
        par(200, Json.obj("ok" to Json.Bool(true)))
      }
      else -> par(404, erro("não encontrado"))
    }
    escrever(saida, resposta.first, nomeDoEstado(resposta.first), resposta.second, resposta.third)
  }

  private fun par(codigo: Int, json: Json) = Triple(codigo, json.render(), "application/json; charset=utf-8")

  private fun info(): Json =
    store.info()
      .field("app", APP_NAME)
      .field("port", PORTA_MESA)
      .field("ips", Json.array(ipsLocais().map { Json.Str(it) }))

  private fun listas(): Json {
    val i = store.info()
    val vivas = if (i.str("status") == Estado.FECHADA.wire) emptyList() else listOf(i.field("app", APP_NAME))
    return Json.EMPTY_OBJ.field("rooms", Json.array(vivas))
  }

  private fun abrir(corpo: Json?): Triple<Int, String, String> {
    val h = corpo?.get("host")
    if (h !is Json.Obj) return par(400, erro("anfitrião inválido"))
    if (store.estado == Estado.A_JOGAR) return par(409, erro("esta mesa já tem dois jogadores"))
    val (numero, palavra) = numeroDaMesaAtual()
    store.abrir(
      limpo(corpo.str("roomName"), 24) ?: "Mesa de café",
      numero,
      palavra,
      Jogador(limpo(h.str("name"), 14) ?: "Jogador", limpo(h.str("avatar"), 12) ?: "cafe"),
    )
    return par(200, Json.EMPTY_OBJ.field("ok", true).field("side", "h").field("room", store.info()))
  }

  private fun entrar(corpo: Json?): Triple<Int, String, String> {
    val nome = limpo(corpo?.str("name") ?: "", 14) ?: "Convidado"
    val avatar = limpo(corpo?.str("avatar") ?: "", 12) ?: "cafe"
    if (!store.entrar(Jogador(nome, avatar))) return par(409, erro("essa mesa já está completa"))
    return par(200, Json.EMPTY_OBJ.field("ok", true).field("side", "g").field("room", store.info()))
  }

  private fun limpo(bruto: String?, max: Int): String? {
    val semQuebras = bruto?.replace(Regex("\\s+"), " ")?.trim().orEmpty()
    return if (semQuebras.isEmpty()) null else semQuebras.take(max)
  }

  private fun numeroDaMesaAtual(): Pair<Int, String> {
    numeroPalavra?.let { return it }
    val octeto = ultimoOcteto(ipPrincipal()) ?: (1..254).random()
    return (octeto to (codigoDaMesa(octeto) ?: "mesa")).also { numeroPalavra = it }
  }

  private fun erro(msg: String) = Json.obj("ok" to Json.Bool(false), "error" to Json.Str(msg))
}

private fun nomeDoEstado(codigo: Int): String = when (codigo) {
  200 -> "OK"
  204 -> "No Content"
  400 -> "Bad Request"
  404 -> "Not Found"
  409 -> "Conflict"
  else -> "Erro"
}

private fun analiseConsulta(consulta: String): Map<String, String> =
  consulta.split('&').filter { it.isNotEmpty() }.associate { parte ->
    val i = parte.indexOf('=')
    if (i < 0) decodifica(parte) to "" else decodifica(parte.substring(0, i)) to decodifica(parte.substring(i + 1))
  }

private fun decodifica(texto: String): String =
  try {
    URLDecoder.decode(texto, "UTF-8")
  } catch (e: Exception) {
    texto
  }

private fun lerLinha(entrada: InputStream): String? {
  val buffer = ByteArrayOutputStream(64)
  while (true) {
    val c = entrada.read()
    if (c == -1) return if (buffer.size() == 0) null else buffer.toString("UTF-8")
    if (c == '\r'.code) continue
    if (c == '\n'.code) return buffer.toString("UTF-8")
    if (buffer.size() > 8192) return null
    buffer.write(c)
  }
}

private fun escrever(saida: OutputStream, codigo: Int, estado: String, corpo: String, tipo: String) {
  val bytes = corpo.toByteArray(Charsets.UTF_8)
  val cabecalho = "HTTP/1.1 $codigo $estado\r\n" +
    "content-type: $tipo\r\n" +
    "content-length: ${bytes.size}\r\n" +
    "cache-control: no-store\r\n" +
    "connection: close\r\n\r\n"
  saida.write(cabecalho.toByteArray(Charsets.US_ASCII))
  if (bytes.isNotEmpty()) saida.write(bytes)
  saida.flush()
}

private val PAGINA = """
<!doctype html><html lang="pt"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>Damas no Café</title>
<style>body{margin:0;background:#2a1710;color:#f6ead2;font-family:system-ui;display:grid;place-items:center;height:100vh;text-align:center}
h1{font-size:30px;margin:0 0 10px}p{opacity:.8;margin:6px 24px;line-height:1.5}</style></head>
<body><div><h1>&#9749; Damas no Caf&#233;</h1>
<p>Esta &#233; a mesa de um telem&#243;vel. Instala a app, liga-te &#224; mesma rede Wi-Fi e entra na mesa.</p>
</div></body></html>
""".trimIndent()
