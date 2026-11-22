package com.damasnocafe.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.damasnocafe.jogo.Dialogo
import com.damasnocafe.jogo.Modo
import com.damasnocafe.jogo.Sessao
import com.damasnocafe.tema.Ambares
import com.damasnocafe.tema.Corpo
import com.damasnocafe.tema.Creme
import com.damasnocafe.tema.Display
import com.damasnocafe.tema.Morango
import com.damasnocafe.tema.Papel
import com.damasnocafe.tema.Tinta

/** A barrinha de aviso que aparece em baixo e se vai embora sozinha. */
@Composable
fun AvisoFlutuante(sessao: Sessao) {
  var texto by remember { mutableStateOf("") }
  var tipo by remember { mutableStateOf("") }
  val actual = sessao.aviso
  LaunchedEffect(actual) {
    if (actual != null) {
      texto = actual.texto
      tipo = actual.tipo
    }
  }
  val fundo = when (tipo) {
    "warn" -> Color(0xFFB4761B)
    "bad" -> Morango
    else -> Tinta
  }
  AnimatedVisibility(
    visible = actual != null,
    enter = fadeIn(tween(140)) + slideInVertically(tween(180)) { it / 2 },
    exit = fadeOut(tween(220)) + slideOutVertically(tween(180)) { it / 2 },
    modifier = Modifier.padding(horizontal = 20.dp),
  ) {
    val forma = RoundedCornerShape(16.dp)
    Box(
      Modifier
        .clip(forma)
        .background(fundo)
        .border(2.5.dp, Tinta.copy(alpha = 0.7f), forma)
        .padding(horizontal = 15.dp, vertical = 11.dp),
    ) {
      Text(
        texto,
        fontFamily = Corpo,
        fontWeight = FontWeight.Bold,
        fontSize = 14.sp,
        color = if (fundo == Tinta) Creme else Papel,
        textAlign = TextAlign.Center,
      )
    }
  }
}

/** As janelas: regras, sair da mesa e o pedido de empate. */
@Composable
fun Janelas(sessao: Sessao) {
  val dialogo = sessao.dialogo ?: return
  Dialog(
    onDismissRequest = { sessao.dialogo = null },
    properties = DialogProperties(usePlatformDefaultWidth = false),
  ) {
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
      when (dialogo) {
        Dialogo.Regras -> JanelaRegras(sessao)
        Dialogo.Sair -> JanelaSair(sessao)
        Dialogo.Desistir -> JanelaDesistir(sessao)
        is Dialogo.Empate -> JanelaEmpate(sessao, dialogo.de)
      }
    }
  }
}

@Composable
private fun JanelaRegras(sessao: Sessao) {
  Cartao(Modifier.fillMaxWidth().padding(horizontal = 18.dp)) {
    Text("Regras de Portugal", fontFamily = Display, fontSize = 23.sp, color = Tinta)
    Spacer(Modifier.height(2.dp))
    TextoSecundario("Damas num tabuleiro de 8×8, com 12 peças para cada lado.")
    Spacer(Modifier.height(12.dp))
    BoxWithConstraints {
      Column(
        Modifier
          .heightIn(max = maxHeight * 0.6f)
          .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(9.dp),
      ) {
        Regra("1", "Só se joga nas casas escuras e o peão anda uma casa na diagonal, sempre para a frente — e também só come para a frente.")
        Regra("2", "Quem pode comer tem de comer: a captura é obrigatória.")
        Regra("3", "Lei da maioria — entre duas capturas escolhe-se a que come mais peças.")
        Regra("4", "As peças comidas só saem do tabuleiro no fim da jogada; não se salta por cima da mesma duas vezes.")
        Regra("5", "A dama voa e é a única que come para trás: percorre a diagonal livre, salta uma peça contrária e pára em qualquer casa depois dela.")
        Regra("6", "Só vira dama quem termina a jogada na última linha do adversário.")
        Regra("7", "Ficar sem jogadas é perder. Depois de 50 jogadas só de damas, sem capturas, é empate.")
      }
    }
    Spacer(Modifier.height(14.dp))
    BotaoCafe("Entendido", Modifier.fillMaxWidth(), pequeno = true) { sessao.dialogo = null }
  }
}

@Composable
private fun Regra(numero: String, texto: String) {
  Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
    Box(
      Modifier
        .size(23.dp)
        .clip(CircleShape)
        .background(Ambares)
        .border(2.dp, Tinta.copy(alpha = 0.8f), CircleShape),
      contentAlignment = Alignment.Center,
    ) {
      Text(numero, fontFamily = Corpo, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Tinta)
    }
    Text(texto, fontFamily = Corpo, fontSize = 13.5.sp, color = Tinta.copy(alpha = 0.84f), modifier = Modifier.weight(1f))
  }
}

@Composable
private fun JanelaSair(sessao: Sessao) {
  Cartao(Modifier.fillMaxWidth().padding(horizontal = 18.dp)) {
    Text("Sair da mesa?", fontFamily = Display, fontSize = 23.sp, color = Tinta)
    Spacer(Modifier.height(6.dp))
    TextoSecundario(
      if (sessao.modo == Modo.Online) {
        "Quem fica perde a partida, e o servidor desta mesa desliga-se."
      } else {
        "Voltas ao início e podes abrir outra mesa quando quiseres."
      },
    )
    Spacer(Modifier.height(16.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
      BotaoCafe("Ficar", Modifier.weight(1f), estilo = EstiloBotao.Tinta, pequeno = true) { sessao.dialogo = null }
      BotaoCafe("Sair", Modifier.weight(1f), estilo = EstiloBotao.Perigo, pequeno = true) { sessao.sairDaMesa() }
    }
  }
}

@Composable
private fun JanelaDesistir(sessao: Sessao) {
  Cartao(Modifier.fillMaxWidth().padding(horizontal = 18.dp)) {
    Text("Desistir da partida?", fontFamily = Display, fontSize = 23.sp, color = Tinta)
    Spacer(Modifier.height(6.dp))
    TextoSecundario(
      if (sessao.modo == Modo.Online) {
        "Dás a vitória ao teu amigo e podem abrir outra partida logo a seguir."
      } else {
        "Passas a vitória a quem joga do outro lado."
      },
    )
    Spacer(Modifier.height(16.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
      BotaoCafe("Continuar", Modifier.weight(1f), estilo = EstiloBotao.Matcha, pequeno = true) {
        sessao.dialogo = null
      }
      BotaoCafe("Desistir", Modifier.weight(1f), estilo = EstiloBotao.Perigo, pequeno = true) {
        sessao.desistir()
      }
    }
  }
}

@Composable
private fun JanelaEmpate(sessao: Sessao, de: String) {
  Cartao(Modifier.fillMaxWidth().padding(horizontal = 18.dp)) {
    Text("Empate?", fontFamily = Display, fontSize = 23.sp, color = Tinta)
    Spacer(Modifier.height(6.dp))
    TextoSecundario("$de propõe dividirem o ponto. Aceitas?")
    Spacer(Modifier.height(16.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
      BotaoCafe("Recusar", Modifier.weight(1f), estilo = EstiloBotao.Perigo, pequeno = true) {
        sessao.responderEmpate(false)
      }
      BotaoCafe("Aceitar", Modifier.weight(1f), estilo = EstiloBotao.Matcha, pequeno = true) {
        sessao.responderEmpate(true)
      }
    }
  }
}
