package com.damasnocafe.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.damasnocafe.jogo.COR_BRANCAS
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
          Box(Modifier.size(lado - 4.dp)) {
            MolduraDeMadeira(
              Modifier
                .fillMaxSize()
                .graphicsLayer { translationX = tremura.value * 11f },
            ) {
              Tabuleiro(sessao, Modifier.fillMaxSize())
              FlutuarEmote(sessao.emote)
            }
            // O canto de baixo à direita é sempre casa clara: nunca se joga ali.
            if (sessao.modo == Modo.Online) {
              BotaoDeEmojis(sessao, Modifier.align(Alignment.BottomEnd).padding(4.dp))
            }
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
      .padding(horizontal = 10.dp, vertical = 6.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(9.dp),
  ) {
    RodadaAvatar(avatarPorId(banco.avatar), tamanho = 38, cor = base)
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
    // Os comandos de casa vivem no topo, longe dos emojis de reacção.
    if (!meu) {
      BotaoIcone(if (sessao.sons.mudo) "🔇" else "🔊", 34) { sessao.sons.alternarMudo() }
      BotaoIcone("📖", 34) { sessao.dialogo = Dialogo.Regras }
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

/** O emoji que o amigo (ou eu) mandou: mesmo no centro do tabuleiro, um instante. */
@Composable
private fun FlutuarEmote(emote: Emote?) {
  if (emote == null) return
  val t = remember(emote.id) { Animatable(0f) }
  LaunchedEffect(emote.id) { t.animateTo(1f, tween(1500)) }
  val p = t.value
  val entra = (p / 0.16f).coerceAtMost(1f)
  Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
    Text(
      emote.emoji,
      fontSize = 92.sp,
      modifier = Modifier.graphicsLayer {
        alpha = if (p < 0.16f) entra else (1f - (p - 0.16f) / 0.84f).coerceIn(0f, 1f)
        // cai em cima do centro e fica lá, porque o centro é que é o tabuleiro.
        translationY = (1f - entra) * 26f
        scaleX = 1.5f - 0.5f * entra
        scaleY = 1.5f - 0.5f * entra
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
  val terminado = sessao.resultado != null
  if (!online) {
    // As reacções vivem no canto direito do tabuleiro; aqui só fica a nota de quem passa o telemóvel.
    TextoSecundario("Passa o telemóvel a quem joga", Modifier.fillMaxWidth())
  }
  // no fim da partida os botões de repetir e sair vivem no cartão do resultado
  if (!terminado) {
    Espaco(8)
    Row(
      Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(9.dp),
    ) {
      if (online) {
        BotaoCafe("Empate", Modifier.weight(1f), estilo = EstiloBotao.Azul, pequeno = true) {
          sessao.oferecerEmpate()
        }
      }
      BotaoCafe("Desistir", Modifier.weight(1f), estilo = EstiloBotao.Perigo, pequeno = true) {
        sessao.dialogo = Dialogo.Desistir
      }
      BotaoCafe("Sair", Modifier.weight(1f), estilo = EstiloBotao.Tinta, pequeno = true) {
        sessao.dialogo = Dialogo.Sair
      }
    }
  }
  if (online && !terminado && sessao.silencioso >= 24) {
    Espaco(6)
    TextoSecundario(
      "${sessao.silencioso} sem capturas — o empate chega aos 50.",
      Modifier.fillMaxWidth(),
    )
  }
}

/**
 * O botão do canto direito do tabuleiro. Um toque manda o último emoji ao amigo;
 * manter premido abre a coluna de emojis para cima, rente à moldura.
 */
@Composable
private fun BotaoDeEmojis(sessao: Sessao, modifier: Modifier = Modifier) {
  var aberto by remember { mutableStateOf(false) }
  val haptica = LocalHapticFeedback.current
  val abre by animateFloatAsState(if (aberto) 1f else 0f, tween(180), label = "abre")
  Box(modifier, contentAlignment = Alignment.BottomEnd) {
    if (abre > 0.01f) {
      Column(
        Modifier
          .padding(bottom = 47.dp)
          .graphicsLayer {
            alpha = abre
            scaleX = 0.75f + 0.25f * abre
            scaleY = 0.75f + 0.25f * abre
            translationY = (1f - abre) * 12f
          },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(5.dp),
      ) {
        for (emoji in EMOTES) {
          PastilhaDeEmoji(emoji, ultimo = emoji == sessao.ultimoEmoji) {
            aberto = false
            sessao.lancarEmote(emoji)
          }
        }
      }
    }
    BotaoCanto(
      mostrado = sessao.ultimoEmoji,
      aberto = aberto,
      aoTocar = { sessao.emojiRapido() },
      aoPremirLongo = {
        haptica.performHapticFeedback(HapticFeedbackType.LongPress)
        aberto = !aberto
      },
    )
  }
}

/** Uma pastilha da coluna: a última que se usou leva o aro âmbar. */
@Composable
private fun PastilhaDeEmoji(emoji: String, ultimo: Boolean, onClick: () -> Unit) {
  val forma = RoundedCornerShape(14.dp)
  Box(
    Modifier
      .size(40.dp)
      .shadow(if (ultimo) 5.dp else 2.dp, forma, clip = false, spotColor = Tinta.copy(alpha = 0.40f))
      .clip(forma)
      .background(Papel)
      .border(2.5.dp, if (ultimo) Ambares else Tinta.copy(alpha = 0.8f), forma)
      .clickable(onClick = onClick),
    contentAlignment = Alignment.Center,
  ) {
    Text(emoji, fontSize = 21.sp)
  }
}

@Composable
private fun BotaoCanto(
  mostrado: String,
  aberto: Boolean,
  aoTocar: () -> Unit,
  aoPremirLongo: () -> Unit,
) {
  val fonte = remember { MutableInteractionSource() }
  val carregado by fonte.collectIsPressedAsState()
  val afunda by animateFloatAsState(if (carregado) 1f else 0f, tween(90), label = "afunda")
  val forma = RoundedCornerShape(50)
  Box(
    Modifier
      .graphicsLayer {
        translationY = afunda * 2f
        scaleX = 1f - afunda * 0.05f
        scaleY = 1f - afunda * 0.05f
      }
      .shadow(6.dp, forma, clip = false, spotColor = Tinta.copy(alpha = 0.5f))
      .size(42.dp)
      .clip(forma)
      .background(if (aberto) Creme else Tinta.copy(alpha = 0.85f))
      .border(2.5.dp, if (aberto) Ambares else Tinta, forma)
      .combinedClickable(
        interactionSource = fonte,
        indication = null,
        onLongClick = aoPremirLongo,
        onClick = aoTocar,
      ),
    contentAlignment = Alignment.Center,
  ) {
    if (aberto) {
      Text("✕", fontFamily = Display, fontSize = 19.sp, color = Tinta.copy(alpha = 0.85f))
    } else {
      Text(mostrado, fontSize = 21.sp)
    }
  }
}

@Composable
private fun BotaoIcone(emoji: String, tamanho: Int = 38, onClick: () -> Unit) {
  val forma = RoundedCornerShape(12.dp)
  Box(
    Modifier
      .size(tamanho.dp)
      .clip(forma)
      .background(Tinta.copy(alpha = 0.85f))
      .border(2.dp, Tinta, forma)
      .clickable(onClick = onClick),
    contentAlignment = Alignment.Center,
  ) {
    Text(emoji, fontSize = (tamanho * 0.47f).sp)
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
    fim.motivo.isNotBlank() -> fim.motivo
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
          textoCor = if (meu.cor == COR_BRANCAS) Tinta else Papel,
        )
        Chip(
          "${if (online) "Amigo" else outro.nome.ifBlank { "Adversário" }} · ${sessao.capturas.getValue(sessao.outroLado)} comidas",
          cor = corPorId(outro.cor).base,
          textoCor = if (outro.cor == COR_BRANCAS) Tinta else Papel,
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
