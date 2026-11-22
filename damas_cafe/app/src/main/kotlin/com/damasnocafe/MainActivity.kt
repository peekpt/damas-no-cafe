package com.damasnocafe

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.damasnocafe.jogo.Dialogo
import com.damasnocafe.jogo.Ecra
import com.damasnocafe.jogo.Ligacao
import com.damasnocafe.jogo.Modo
import com.damasnocafe.jogo.Sessao
import com.damasnocafe.som.Sons
import com.damasnocafe.tema.DamasTema
import com.damasnocafe.ui.AvisoFlutuante
import com.damasnocafe.ui.FundoCafe
import com.damasnocafe.ui.Janelas
import com.damasnocafe.ui.Raiz
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

class MainActivity : ComponentActivity() {
  private val escopo = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
  private lateinit var sessao: Sessao
  private lateinit var sons: Sons

  override fun onCreate(savedInstanceState: Bundle?) {
    enableEdgeToEdge()
    super.onCreate(savedInstanceState)
    pedirPermissaoNotificacoes()
    sons = Sons(this)
    sessao = Sessao(this, sons, escopo)
    setContent {
      DamasTema {
        Box(
          Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding(),
        ) {
          FundoCafe()
          Raiz(sessao)
          Box(
            Modifier
              .align(Alignment.BottomCenter)
              .padding(bottom = 10.dp),
          ) {
            AvisoFlutuante(sessao)
          }
        }
        Janelas(sessao)
        Voltar(sessao)
        NaoDeixarDormir(sessao.ecra == Ecra.Jogo)
      }
    }
  }

  override fun onDestroy() {
    sessao.fechar()
    escopo.cancel()
    sons.libertar()
    super.onDestroy()
  }

  /** O botão de trás fecha janelas, devolve ao início e, no jogo, pergunta primeiro. */
  @Composable
  private fun Voltar(sessao: Sessao) {
    BackHandler(enabled = sessao.ecra != Ecra.Inicio || sessao.dialogo != null) {
      val actual = sessao.dialogo
      when {
        actual != null -> sessao.dialogo = null
        sessao.ecra == Ecra.Jogo -> sessao.dialogo = Dialogo.Sair
        sessao.modo == Modo.Online && sessao.ligacao != Ligacao.Nada -> sessao.dialogo = Dialogo.Sair
        else -> sessao.ecra = Ecra.Inicio
      }
    }
  }

  @Composable
  private fun NaoDeixarDormir(aJogar: Boolean) {
    SideEffect {
      if (aJogar) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
      else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }
  }

  private fun pedirPermissaoNotificacoes() {
    if (Build.VERSION.SDK_INT < 33) return
    if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) return
    requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), CODIGO_PERMISSAO)
  }

  private companion object {
    const val CODIGO_PERMISSAO = 7
  }
}
