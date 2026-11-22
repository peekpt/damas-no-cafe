package com.damasnocafe

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import com.damasnocafe.rede.MesaServer
import com.damasnocafe.rede.PORTA_MESA
import com.damasnocafe.rede.ipPrincipal
import damas.core.MesaStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class EstadoServidor(
  val aEscutar: Boolean = false,
  val ip: String = "",
  val mesa: Int = 0,
  val palavra: String = "",
)

/**
 * O telemóvel que abre a mesa liga este serviço: é ele que serve o jogo.
 * Continua ativo com o ecrã desligado, para o convidado não ficar à porta.
 */
class ServicoMesa : Service() {
  private var servidor: MesaServer? = null
  private var escopo = CoroutineScope(SupervisorJob() + Dispatchers.Default)

  override fun onBind(intent: Intent?): IBinder? = null

  override fun onCreate() {
    super.onCreate()
    criarCanal()
    val servidorNovo = MesaServer(MesaStore())
    if (servidorNovo.arrancar()) servidor = servidorNovo else servidorNovo.parar()
    _estado.value = EstadoServidor(
      aEscutar = servidor != null,
      ip = ipPrincipal(),
      mesa = ultimoOctetoDaMesa(),
      palavra = palavraDaMesa(),
    )
  }

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    val ativo = servidor != null
    _estado.value = _estado.value.copy(aEscutar = ativo, ip = ipPrincipal())
    notificar()
    // O Wi-Fi pode ainda estar a estabilizar: reescrever o endereço passado um instante.
    escopo.launch {
      repeat(4) {
        delay(2500)
        _estado.value = _estado.value.copy(ip = ipPrincipal())
      }
    }
    return START_STICKY
  }

  override fun onDestroy() {
    servidor?.parar()
    servidor = null
    _estado.value = EstadoServidor()
    escopo.cancel()
    super.onDestroy()
  }

  private fun notificar() {
    val e = _estado.value
    val titulo = if (e.aEscutar) "Mesa nº ${e.mesa} · ${e.palavra.uppercase()}" else "Servidor da mesa não abriu"
    val texto = if (e.aEscutar) "${e.ip}:$PORTA_MESA — os amigos entram pela app" else "Fecha e abre a mesa outra vez"
    val notificacao = Notification.Builder(this, CANAL)
      .setSmallIcon(R.drawable.ic_mesa_branca)
      .setContentTitle(titulo)
      .setContentText(texto)
      .setOngoing(true)
      .build()
    if (Build.VERSION.SDK_INT >= 34) {
      startForeground(ID_NOTIFICACAO, notificacao, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
    } else {
      startForeground(ID_NOTIFICACAO, notificacao)
    }
  }

  private fun ultimoOctetoDaMesa(): Int = ipPrincipal().substringAfterLast('.').toIntOrNull() ?: 0

  private fun palavraDaMesa(): String = damas.core.codigoDaMesa(ultimoOctetoDaMesa()) ?: "café"

  private fun criarCanal() {
    val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    if (manager.getNotificationChannel(CANAL) != null) return
    manager.createNotificationChannel(
      NotificationChannel(CANAL, "Mesa aberta", NotificationManager.IMPORTANCE_LOW),
    )
  }

  companion object {
    private const val CANAL = "mesa"
    private const val ID_NOTIFICACAO = 42

    private val _estado = MutableStateFlow(EstadoServidor())
    val estado: StateFlow<EstadoServidor> = _estado

    fun arrancar(contexto: Context) {
      val intencao = Intent(contexto, ServicoMesa::class.java)
      try {
        contexto.startForegroundService(intencao)
      } catch (e: Exception) {
        _estado.value = EstadoServidor(aEscutar = false)
      }
    }

    fun desligar(contexto: Context) {
      contexto.stopService(Intent(contexto, ServicoMesa::class.java))
      _estado.value = EstadoServidor()
    }
  }
}
