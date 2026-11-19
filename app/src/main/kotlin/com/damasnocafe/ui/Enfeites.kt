package com.damasnocafe.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.damasnocafe.jogo.Avatar
import com.damasnocafe.jogo.CorPeca
import com.damasnocafe.jogo.brilhoDaPeca
import com.damasnocafe.jogo.sombraDaPeca
import com.damasnocafe.tema.Ambares
import com.damasnocafe.tema.Cafe
import com.damasnocafe.tema.Corpo
import com.damasnocafe.tema.Corpo_
import com.damasnocafe.tema.Creme
import com.damasnocafe.tema.Display
import com.damasnocafe.tema.Papel
import com.damasnocafe.tema.Subtitulo
import com.damasnocafe.tema.Tinta

enum class EstiloBotao { Ambar, Matcha, Tinta, Perigo, Azul }

private fun paletaDoBotao(estilo: EstiloBotao): List<Color> = when (estilo) {
  EstiloBotao.Ambar -> listOf(Color(0xFFF7B955), Color(0xFFD98A1F), Color(0xFF9C5F10))
  EstiloBotao.Matcha -> listOf(Color(0xFF96C57C), Color(0xFF5D9147), Color(0xFF3C6630))
  EstiloBotao.Tinta -> listOf(Color(0xFF5A3B28), Color(0xFF3B2214), Tinta)
  EstiloBotao.Perigo -> listOf(Color(0xFFEF7059), Color(0xFFCE3E2B), Color(0xFF8E2317))
  EstiloBotao.Azul -> listOf(Color(0xFF7BA6E8), Color(0xFF3F6CB8), Color(0xFF27498A))
}

/** Botão de pastelaria: brilho por cima, rebordo torrado, afunda quando se carrega. */
@Composable
fun BotaoCafe(
  texto: String,
  modifier: Modifier = Modifier,
  estilo: EstiloBotao = EstiloBotao.Ambar,
  activado: Boolean = true,
  pequeno: Boolean = false,
  onClick: () -> Unit,
) {
  val (claro, medio, escuro) = paletaDoBotao(estilo)
  val fonte = remember { MutableInteractionSource() }
  val carregado by fonte.collectIsPressedAsState()
  val afunda by animateFloatAsState(if (carregado && activado) 1f else 0f, tween(90))
  val forma = RoundedCornerShape(if (pequeno) 14.dp else 20.dp)
  Box(
    modifier = modifier
      .graphicsLayer {
        translationY = afunda * 3f
        scaleX = 1f - afunda * 0.02f
        scaleY = 1f - afunda * 0.02f
      }
      .shadow(if (pequeno) 4.dp else 9.dp, forma, clip = false, spotColor = Tinta.copy(alpha = 0.45f))
      .clip(forma)
      .background(Brush.verticalGradient(listOf(claro, medio)))
      .border(width = if (pequeno) 2.dp else 3.dp, color = escuro, shape = forma)
      .clickable(enabled = activado, interactionSource = fonte, indication = null, onClick = onClick)
      .padding(horizontal = if (pequeno) 14.dp else 18.dp, vertical = if (pequeno) 9.dp else 15.dp),
    contentAlignment = Alignment.Center,
  ) {
    Text(
      texto,
      color = if (estilo == EstiloBotao.Tinta) Creme else Tinta,
      fontFamily = Display,
      fontSize = if (pequeno) 15.sp else 21.sp,
      letterSpacing = 0.7.sp,
      textAlign = TextAlign.Center,
      modifier = Modifier.graphicsLayer { alpha = if (activado) 1f else 0.45f },
    )
  }
}

@Composable
fun Cartao(
  modifier: Modifier = Modifier,
  cor: Color = Papel,
  conteudo: @Composable ColumnScope.() -> Unit,
) {
  val forma = RoundedCornerShape(22.dp)
  Column(
    modifier = modifier
      .shadow(6.dp, forma, clip = false, spotColor = Tinta.copy(alpha = 0.22f))
      .clip(forma)
      .background(cor)
      .border(2.5.dp, Tinta.copy(alpha = 0.9f), forma)
      .padding(16.dp),
    content = conteudo,
  )
}

@Composable
fun Chip(
  texto: String,
  modifier: Modifier = Modifier,
  cor: Color = Ambares,
  textoCor: Color = Tinta,
) {
  val forma = RoundedCornerShape(50)
  Box(
    modifier = modifier
      .clip(forma)
      .background(cor)
      .border(2.dp, Tinta.copy(alpha = 0.85f), forma)
      .padding(horizontal = 12.dp, vertical = 5.dp),
  ) {
    Text(texto, fontFamily = Corpo, fontWeight = FontWeight.Bold, fontSize = 12.5.sp, color = textoCor)
  }
}

@Composable
fun TituloCafe(texto: String, modifier: Modifier = Modifier, tamanho: Int = 30) {
  Text(
    texto,
    modifier = modifier,
    fontFamily = Display,
    fontSize = tamanho.sp,
    color = Tinta,
    letterSpacing = 0.6.sp,
  )
}

@Composable
fun TextoSecundario(texto: String, modifier: Modifier = Modifier) {
  Text(texto, modifier, fontFamily = Corpo, fontSize = 14.sp, color = Tinta.copy(alpha = 0.72f))
}

/** Moeda do avatar: emoji dentro de um disco com rebordo torrado. */
@Composable
fun RodadaAvatar(
  avatar: Avatar,
  modifier: Modifier = Modifier,
  tamanho: Int = 46,
  seleccionado: Boolean = false,
  cor: Color = Ambares,
  onClick: (() -> Unit)? = null,
) {
  val forma = RoundedCornerShape(50)
  Box(
    modifier = modifier
      .size(tamanho.dp)
      .graphicsLayer {
        scaleX = if (seleccionado) 1.1f else 1f
        scaleY = if (seleccionado) 1.1f else 1f
      }
      .shadow(if (seleccionado) 6.dp else 2.dp, forma, clip = false, spotColor = Tinta.copy(alpha = 0.35f))
      .clip(forma)
      .background(Brush.linearGradient(listOf(brilhoDaPeca(cor), cor, sombraDaPeca(cor))))
      .border(if (seleccionado) 3.dp else 2.dp, Tinta, forma)
      .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
    contentAlignment = Alignment.Center,
  ) {
    Text(avatar.emoji, fontSize = (tamanho * 0.48f).sp)
  }
}

/** Moldura de madeira onde assenta o tabuleiro. */
@Composable
fun MolduraDeMadeira(modifier: Modifier = Modifier, conteudo: @Composable () -> Unit) {
  val forma = RoundedCornerShape(26.dp)
  Box(
    modifier = modifier
      .shadow(14.dp, forma, clip = false, spotColor = Tinta.copy(alpha = 0.5f))
      .clip(forma)
      .background(Brush.linearGradient(listOf(Color(0xFF6B4326), Cafe, Color(0xFF3A2113))))
      .border(3.dp, Tinta, forma)
      .padding(9.dp),
  ) {
    Box(modifier = Modifier.clip(RoundedCornerShape(17.dp))) {
      conteudo()
    }
  }
}

/** Fundo de toalha de café, com manchas de copo. */
@Composable
fun FundoCafe(modifier: Modifier = Modifier) {
  Box(
    modifier = modifier
      .fillMaxSize()
      .background(Brush.verticalGradient(0f to Color(0xFFFFF6E3), 0.45f to Creme, 1f to Color(0xFFEED9B4))),
  ) {
    Canvas(Modifier.fillMaxSize()) {
      val manchas = listOf(
        Triple(Offset(size.width * 0.12f, size.height * 0.07f), size.width * 0.30f, 0.10f),
        Triple(Offset(size.width * 0.95f, size.height * 0.29f), size.width * 0.38f, 0.08f),
        Triple(Offset(size.width * 0.16f, size.height * 0.74f), size.width * 0.44f, 0.07f),
        Triple(Offset(size.width * 0.9f, size.height * 0.95f), size.width * 0.26f, 0.09f),
      )
      for ((centro, raio, alpha) in manchas) {
        drawCircle(
          Color(0xFF9C5F10).copy(alpha = alpha),
          raio,
          centro,
          style = androidx.compose.ui.graphics.drawscope.Stroke(width = raio * 0.09f),
        )
        drawCircle(Color(0xFF9C5F10).copy(alpha = alpha * 0.3f), raio * 0.6f, centro)
      }
    }
  }
}

@Composable
fun PastilhaDeCor(cor: CorPeca, seleccionada: Boolean, indisponivel: Boolean, onClick: () -> Unit) {
  val forma = RoundedCornerShape(15.dp)
  Box(
    modifier = Modifier
      .size(if (seleccionada) 50.dp else 46.dp)
      .graphicsLayer { alpha = if (indisponivel) 0.32f else 1f }
      .shadow(if (seleccionada) 7.dp else 3.dp, forma, clip = false, spotColor = Tinta.copy(alpha = 0.4f))
      .clip(forma)
      .background(Brush.linearGradient(listOf(brilhoDaPeca(cor.base), cor.base, sombraDaPeca(cor.base))))
      .border(if (seleccionada) 3.dp else 2.5.dp, Tinta, forma)
      .clickable(enabled = !indisponivel, onClick = onClick),
    contentAlignment = Alignment.Center,
  ) {
    if (seleccionada) {
      Text("✓", color = if (cor.base.luminance() > 0.55f) Tinta else Papel, fontSize = 21.sp, fontFamily = Corpo)
    }
  }
}

@Composable
fun Rodape(texto: String, modifier: Modifier = Modifier) {
  Text(
    texto,
    modifier = modifier.fillMaxWidth(),
    fontFamily = Corpo,
    fontSize = 11.sp,
    fontWeight = FontWeight.Bold,
    color = Tinta.copy(alpha = 0.42f),
    textAlign = TextAlign.Center,
    style = Corpo_,
  )
}
