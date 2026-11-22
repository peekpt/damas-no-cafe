package com.damasnocafe.rede

import damas.core.APP_NAME
import damas.core.Json
import java.net.HttpURLConnection
import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.Collections
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext

const val PORTA_MESA = 8742
private const val TEMPO_LIGACAO = 900
private const val TEMPO_LEITURA = 2500
private const val TEMPO_VARREDURA = 260

/** Endereços IPv4 reais do telemóvel (Wi-Fi), sem o loopback. */
fun ipsLocais(): List<String> {
  val saida = ArrayList<String>()
  val interfaces = try {
    Collections.list(NetworkInterface.getNetworkInterfaces())
  } catch (e: Exception) {
    return saida
  }
  for (nif in interfaces) {
    if (!nif.isUp || nif.isLoopback) continue
    for (addr in Collections.list(nif.inetAddresses)) {
      if (addr is Inet4Address && !addr.isLoopbackAddress && !addr.isLinkLocalAddress) {
        addr.hostAddress?.let { saida += it }
      }
    }
  }
  return saida
}

fun ipPrincipal(): String = ipsLocais().firstOrNull { it.startsWith("192.168.") || it.startsWith("10.") }
  ?: ipsLocais().firstOrNull()
  ?: "127.0.0.1"

fun ultimoOcteto(ip: String): Int? = ip.substringAfterLast('.').toIntOrNull()?.takeIf { it in 1..254 }

/** "192.168.1.45" -> "192.168.1." */
fun prefixoRede(ip: String): String? {
  val i = ip.lastIndexOf('.')
  if (i < 0) return null
  return ip.substring(0, i + 1)
}

fun urlBase(ip: String, porta: Int = PORTA_MESA) = "http://$ip:$porta"

/**
 * Varre a última casa do `/24` à procura de mesas.
 * Devolve os IPs que responderam ao `/api/info` do jogo.
 */
suspend fun varrerSubrede(ipLocal: String): List<String> {
  val prefixo = prefixoRede(ipLocal) ?: return emptyList()
  val hospedeiro = ultimoOcteto(ipLocal)
  return coroutineScope {
    (1..254)
      .filter { it != hospedeiro }
      .map { octeto ->
        async(Dispatchers.IO) {
          val ip = "$prefixo$octeto"
          try {
            val json = pedidoHttp("GET", "${urlBase(ip)}/api/info", null, TEMPO_VARREDURA, TEMPO_VARREDURA)
            if (json?.str("app") == APP_NAME) ip else null
          } catch (e: Exception) {
            null
          }
        }
      }
      .awaitAll()
      .filterNotNull()
  }
}

internal fun pedidoHttp(metodo: String, url: String, corpo: String?, liga: Int = TEMPO_LIGACAO, le: Int = TEMPO_LEITURA): Json? {
  val ligacao = (java.net.URL(url).openConnection() as HttpURLConnection).apply {
    requestMethod = metodo
    connectTimeout = liga
    readTimeout = le
    useCaches = false
    if (corpo != null) {
      doOutput = true
      setRequestProperty("Content-Type", "application/json; charset=utf-8")
      setRequestProperty("Accept", "application/json")
    }
  }
  try {
    if (corpo != null) ligacao.outputStream.use { it.write(corpo.toByteArray(Charsets.UTF_8)) }
    val codigo = ligacao.responseCode
    val fluxo = if (codigo in 200..399) ligacao.inputStream else ligacao.errorStream
    val texto = fluxo?.use { it.readBytes().toString(Charsets.UTF_8) } ?: return null
    val json = Json.parse(texto)
    return if (json is Json.Obj) json else null
  } catch (e: Exception) {
    return null
  } finally {
    ligacao.disconnect()
  }
}

suspend fun <T> emIo(bloco: () -> T): T = withContext(Dispatchers.IO) { bloco() }
