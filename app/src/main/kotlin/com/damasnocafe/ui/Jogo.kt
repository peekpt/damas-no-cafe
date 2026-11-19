package com.damasnocafe.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.damasnocafe.jogo.Dialogo
import com.damasnocafe.jogo.EMOTES
import com.damasnocafe.jogo.Emote
import com.damasnocafe.jogo.FimJogo
import com.damasnocafe.jogo.Ligacao
import com.damasnocafe.jogo.Modo
import com.damasnocafe.jogo.Sessao
import com.damasnocafe.jogo.avatarPorId
import com.damasnocafe.jogo.corPorId
import com.damasnocafe.tema.Ambares
import com.damasnocafe.tema.Creme
import com.damasnocafe.tema.Corpo
import com.damasnocafe.tema.Display
import com.damasnocafe.tema.MatchaFundo
import com.damasnocafe.tema.Morango
import com.damasnocafe.tema.Papel
import com.damasnocafe.tema.Tinta
import damas.core.Lado

/** O tabuleiro ao centro, com um banco em cada topos e os controlos por baixo. */
@Composable
fun EcranJogo(sessao: Sessao) {
  val tremura = remember { Animatable(0f) }
  LaunchedEffect(sessao.treme) {
    if (sessao.treme == 0) return@LaunchedEffect
    for (n in 0 until 5) tremura.animateTo(if (n % 2 == 0) 1f else -1f, tween(46))
    tremura.animateTo(0f, tween(46))
  }

  val deBaixo = sessao.ladoDeBaixo()
  val deCima = deBaixo.outro

  Box(Modifier.fillMaxSize()) {
    Column(Modifier.fillMaxWidth()) {
      BarraDeBanco(sessao, deCima, meu = false)
      Espaco(6)
      Box(
        Modifier
          .fillMaxWidth()
          .weight(1f),
        contentAlignment = Alignment.Center,
      ) {
        BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
          val lado = minOf(maxWidth, maxHeight)
          MolduraDeMadeira(
            Modifier
              .size(lado - 4.dp)
              .graphicsLayer { translationX = tremura.value * 11f },
          ) {
            Tabuleiro(sessao, Modifier.fillMaxSize())
            FlutuarEmote(sessao.emote)
          }
        }
      }
      Espaco(6)
      BarraDeBanco(sessao, deBaixo, meu = true)
      FaixaDeLances(sessao)
      Controlos(sessao)
      Espaco(4)
    }

    Confetti(sessao.celebracao, Modifier.matchParentSize())
    sessao.resultado?.let { OverlayResultado(sessao, it) }
  }
}

// ───────────────────────── bancos ─────────────────────────

@Composable
private fun BarraDeBanco(sessao: Sessao, lado: Lado, meu: Boolean) {
  val banco = sessao.bancos.getValue(lado)
  val online = sessao.modo == Modo.Online
  val aJogar = sessao.resultado == null && sessao.vez == lado
  val comeu = sessao.capturas.getValue(lado)
  val base = corPorId(banco.cor).base
  val forma = RoundedCornerShape(18.dp)
  Row(
    Modifier
      .fillMaxWidth()
      .clip(forma)
      .background(if (meu) Papel else Creme)
      .border(2.5.dp, if (aJogar) Ambares else Tinta.copy(alpha = 0.85f), forma)
      .padding(horizontal = 10.dp, vertical = 7.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(9.dp),
  ) {
    RodadaAvatar(avatarPorId(banco.avatar), tamanho = 40, cor = base)
    Column(Modifier.weight(1f)) {
      Text(
        if (online && meu) "Tu · ${banco.nome.ifBlank { "Jogador" }}" else banco.nome.ifBlank { "Jogador" },
        fontFamily = Display,
        fontSize = 16.sp,
        color = Tinta,
      )
      Text(
        when {
          sessao.resultado != null -> "partida terminada"
          online && meu -> if (aJogar) "é a tua vez" else "à espera do amigo…"
          online -> if (aJogar) "a pensar…" else "descansa"
          aJogar -> "a jogar"
          else -> "em espera"
        },
        fontFamily = Corpo,
        fontWeight = FontWeight.Bold,
        fontSize = 12.sp,
        color = when {
          aJogar && sessao.resultado == null -> MatchaFundo
          else -> Tinta.copy(alpha = 0.6f)
        },
      )
    }
    CaixinhaDeComeidas(comeu, lado.outro, sessao)
    if (online && meu && sessao.ligacao == Ligacao.Perdida) {
      Text("⚠️", fontSize = 17.sp)
    }
  }
}

@Composable
private fun CaixinhaDeComeidas(quantas: Int, deQuem: Lado, sessao: Sessao) {
  val base = corPorId(sessao.bancos.getValue(deQuem).cor).base
  Row(
    Modifier
      .clip(RoundedCornerShape(11.dp))
      .background(Tinta.copy(alpha = 0.06f))
      .padding(horizontal = 8.dp, vertical = 4.dp),
    horizontalArrangement = Arrangement.spacedBy(3.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    repeat(quantas.coerceAtMost(6)) {
      Box(Modifier.size(8.dp).clip(RoundedCornerShape(50)).background(base))
    }
    if (quantas > 6) Text("+${quantas - 6}", fontFamily = Corpo, fontSize = 11.sp, color = Tinta.copy(alpha = 0.7f))
    if (quantas == 0) Text("0", fontFamily = Corpo, fontSize = 11.sp, color = Tinta.copy(alpha = 0.45f))
  }
}

/** O emoji que o amigo (ou eu) mandou, a subir por cima do tabuleiro. */
@Composable
private fun FlutuarEmote(emote: Emote?) {
  if (emote == null) return
  val t = remember(emote.id) { Animatable(0f) }
  LaunchedEffect(emote.id) { t.animateTo(1f, tween(1600)) }
  val p = t.value
  Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
    Text(
      emote.emoji,
      fontSize = 68.sp,
      modifier = Modifier.graphicsLayer {
        alpha = if (p < 0.12f) p / 0.12f else (1f - (p - 0.12f) / 0.88f).coerceIn(0f, 1f)
        translationY = -p * 150f
        scaleX = 0.8f + p * 0.6f
        scaleY = 0.8f + p * 0.6f
        rotationZ = if (emote.id % 2 == 0) 9f * p else -9f * p
      },
    )
  }
}

// ───────────────────────── histórico ─────────────────────────

@Composable
private fun FaixaDeLances(sessao: Sessao) {
  val historico = sessao.historico
  if (historico.isEmpty()) return
  val estado = rememberScrollState()
  LaunchedEffect(historico.size) { estado.scrollTo(estado.maxValue) }
  val forma = RoundedCornerShape(12.dp)
  Row(
    Modifier
      .fillMaxWidth()
      .clip(forma)
      .background(Tinta.copy(alpha = 0.05f))
      .border(1.5.dp, Tinta.copy(alpha = 0.16f), forma)
      .horizontalScroll(estado)
      .padding(horizontal = 9.dp, vertical = 5.dp),
    horizontalArrangement = Arrangement.spacedBy(9.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Text("♟", fontSize = 12.sp, color = Tinta.copy(alpha = 0.5f))
    historico.forEachIndexed { n, registo ->
      val base = corPorId(sessao.bancos.getValue(registo.lado).cor).base
      Row(horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(7.dp).clip(RoundedCornerShape(50)).background(base))
        Text(
          "${n + 1}. ${registo.texto}",
          fontFamily = Corpo,
          fontSize = 12.sp,
          color = Tinta.copy(alpha = 0.78f),
        )
      }
    }
  }
}

// ───────────────────────── controlos ─────────────────────────

@Composable
private fun Controlos(sessao: Sessao) {
  val online = sessao.modo == Modo.Online
  Row(
    Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.spacedBy(7.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    if (online) {
      for (emoji in EMOTES) {
        CaixaDeEmote(emoji) { sessao.lancarEmote(emoji) }
      }
    } else {
      TextoSecundario("Passa o telemóvel a quem joga", Modifier.weight(1f))
    }
    BotaoIcone(if (sessao.sons.mudo) "🔇" else "🔊") { sessao.sons.alternarMudo() }
    BotaoIcone("📖") { sessao.dialogo = Dialogo.Regras }
    BotaoIcone("🚪") { sessao.dialogo = Dialogo.Sair }
  }
  Espaco(8)
  Row(
    Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.spacedBy(9.dp),
  ) {
    if (online) {
      BotaoCafe("Empate", Modifier.weight(1f), estilo = EstiloBotao.Azul, pequeno = true, activado = sessao.resultado == null) {
        sessao.oferecerEmpate()
      }
    }
    BotaoCafe(
      if (sessao.resultado == null) "Nova partida" else "Jogar outra vez",
      Modifier.weight(1f),
      estilo = EstiloBotao.Matcha,
      pequeno = true,
    ) { sessao.novaPartida() }
    BotaoCafe("Sair", Modifier.weight(1f), estilo = EstiloBotao.Perigo, pequeno = true) {
      sessao.dialogo = Dialogo.Sair
    }
  }
  if (online && sessao.silencioso >= 24 && sessao.resultado == null) {
    Espaco(6)
    TextoSecundario(
      "${sessao.silencioso} sem capturas — o empate chega aos 50.",
      Modifier.fillMaxWidth(),
    )
  }
}

@Composable
private fun CaixaDeEmote(emoji: String, onClick: () -> Unit) {
  val forma = RoundedCornerShape(13.dp)
  Box(
    Modifier
      .size(38.dp)
      .clip(forma)
      .background(Papel)
      .border(2.dp, Tinta.copy(alpha = 0.8f), forma)
      .clickable(onClick = onClick),
    contentAlignment = Alignment.Center,
  ) {
    Text(emoji, fontSize = 19.sp)
  }
}

@Composable
private fun BotaoIcone(emoji: String, onClick: () -> Unit) {
  val forma = RoundedCornerShape(13.dp)
  Box(
    Modifier
      .size(38.dp)
      .clip(forma)
      .background(Tinta.copy(alpha = 0.85f))
      .border(2.dp, Tinta, forma)
      .clickable(onClick = onClick),
    contentAlignment = Alignment.Center,
  ) {
    Text(emoji, fontSize = 17.sp)
  }
}

// ───────────────────────── fim de jogo ─────────────────────────

@Composable
private fun OverlayResultado(sessao: Sessao, fim: FimJogo) {
  val online = sessao.modo == Modo.Online
  val vencedor = fim.vencedor
  val titulo = when {
    vencedor == null -> "Empate ☕"
    !online -> "Ganhou ${sessao.bancos.getValue(vencedor).nome.ifBlank { "Jogador" }}!"
    vencedor == sessao.meuLado -> "Ganhaste! ☕"
    else -> "Perdeste. 😌"
  }
  val explicacao = when {
    fim.empate != null -> fim.empate
    vencedor != null -> {
      val nome = sessao.bancos.getValue(vencedor).nome.ifBlank { "Jogador" }
      if (online && vencedor != sessao.meuLado) "${sessao.bancos.getValue(sessao.outroLado).nome.ifBlank { "O teu amigo" }} deixou-te sem jogadas."
      else "$nome comeu tudo ou deixou o adversário sem jogadas."
    }
    else -> ""
  }
  val cor = if (vencedor == null) Ambares else if (!online || vencedor == sessao.meuLado) Creme else Papel
  Box(
    Modifier
      .fillMaxSize()
      .background(Tinta.copy(alpha = 0.55f)),
    contentAlignment = Alignment.Center,
  ) {
    Cartao(Modifier.fillMaxWidth().padding(horizontal = 22.dp), cor = cor) {
      Text(
        titulo,
        fontFamily = Display,
        fontSize = 27.sp,
        color = Tinta,
        modifier = Modifier.fillMaxWidth(),
      )
      Espaco(4)
      TextoSecundario(explicacao)
      Espaco(6)
      val meu = sessao.bancos.getValue(sessao.meuLado)
      val outro = sessao.bancos.getValue(sessao.outroLado)
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Chip(
          "${if (online) "Tu" else meu.nome.ifBlank { "Jogador" }} · ${sessao.capturas.getValue(sessao.meuLado)} comidas",
          cor = corPorId(meu.cor).base,
        )
        Chip(
          "${if (online) "Amigo" else outro.nome.ifBlank { "Adversário" }} · ${sessao.capturas.getValue(sessao.outroLado)} comidas",
          cor = corPorId(outro.cor).base,
        )
      }
      Espaco(12)
      BotaoCafe("Jogar outra vez", Modifier.fillMaxWidth()) { sessao.novaPartida() }
      Espaco(8)
      BotaoCafe("Sair da mesa", Modifier.fillMaxWidth(), estilo = EstiloBotao.Tinta, pequeno = true) {
        sessao.dialogo = Dialogo.Sair
      }
    }
  }
}

// ───────────────────────── utilidades ─────────────────────────

@Composable
private fun Espaco(dp: Int) = Spacer(Modifier.height(dp.dp))
