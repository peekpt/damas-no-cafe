package com.damasnocafe.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import com.damasnocafe.jogo.Efeito
import com.damasnocafe.jogo.PecaVisual
import com.damasnocafe.jogo.Sessao
import com.damasnocafe.jogo.TipoEfeito
import com.damasnocafe.jogo.brilhoDaPeca
import com.damasnocafe.jogo.corPorId
import com.damasnocafe.jogo.sombraDaPeca
import com.damasnocafe.jogo.tintaDaCorona
import com.damasnocafe.tema.Ambares
import com.damasnocafe.tema.CasaClara
import com.damasnocafe.tema.CasaEscuro
import com.damasnocafe.tema.Morango
import com.damasnocafe.tema.Papel
import com.damasnocafe.tema.Tinta
import damas.core.SIZE
import damas.core.colOf
import damas.core.idx
import damas.core.rowOf
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

/** Casa -> linha/coluna tal como aparecem no ecrã (o convidado joga de cabeça para baixo). */
fun casaNoEcran(casa: Int, virado: Boolean): Pair<Int, Int> {
  val r = rowOf(casa)
  val c = colOf(casa)
  return if (virado) (SIZE - 1 - r) to (SIZE - 1 - c) else r to c
}

@Composable
fun Tabuleiro(sessao: Sessao, modifier: Modifier = Modifier) {
  val virado = sessao.virado
  val haptica = LocalHapticFeedback.current
  BoxWithConstraints(modifier.aspectRatio(1f)) {
    val casaPx = with(LocalDensity.current) { (maxWidth / SIZE).toPx() }
    val casaDp = maxWidth / SIZE

    Casaxadrez(virado)
    SombrasDeCasas(sessao)
    for (peca in sessao.pecas) {
      key(peca.id) { PecaANimar(peca, sessao, virado, casaPx, casaDp) }
    }
    for (e in sessao.efeitos) {
      key(e.id) { EfeitoNaCasa(e, virado, casaPx, casaDp) { sessao.efeitoTerminado(e.id) } }
    }
    MarcasDeJogo(sessao)

    Box(
      Modifier
        .fillMaxSize()
        .pointerInput(virado) {
          detectTapGestures { pos ->
            val lado = size.width.toFloat() / SIZE
            val dc = (pos.x / lado).toInt().coerceIn(0, SIZE - 1)
            val dr = (pos.y / lado).toInt().coerceIn(0, SIZE - 1)
            val r = if (virado) SIZE - 1 - dr else dr
            val c = if (virado) SIZE - 1 - dc else dc
            haptica.performHapticFeedback(HapticFeedbackType.LongPress)
            sessao.tocarCasa(idx(r, c))
          }
        },
    )
  }
}

@Composable
private fun Casaxadrez(virado: Boolean) {
  Canvas(Modifier.fillMaxSize()) {
    val lado = size.width / SIZE
    for (dr in 0 until SIZE) {
      for (dc in 0 until SIZE) {
        val r = if (virado) SIZE - 1 - dr else dr
        val c = if (virado) SIZE - 1 - dc else dc
        val escura = (r + c) % 2 == 1
        drawRect(if (escura) CasaEscuro else CasaClara, Offset(dc * lado, dr * lado), Size(lado, lado))
        val fio = if (escura) Tinta.copy(alpha = 0.075f) else Tinta.copy(alpha = 0.035f)
        var k = 0.2f
        while (k < 0.9f) {
          drawLine(
            fio,
            Offset(dc * lado + lado * 0.05f, dr * lado + lado * k),
            Offset(dc * lado + lado * 0.95f, dr * lado + lado * (k + 0.055f)),
            lado * 0.03f,
          )
          k += 0.28f
        }
      }
    }
  }
}

/** O rectângulo torrado das duas últimas casas — por baixo das peças. */
@Composable
private fun SombrasDeCasas(sessao: Sessao) {
  val virado = sessao.virado
  val ultimo = sessao.ultimo ?: return
  val (de, para) = ultimo
  Canvas(Modifier.fillMaxSize()) {
    val lado = size.width / SIZE
    for (casa in listOf(de, para)) {
      val (r, c) = casaNoEcran(casa, virado)
      drawRoundRect(
        Ambares.copy(alpha = 0.40f),
        Offset(c * lado, r * lado),
        Size(lado, lado),
        CornerRadius(lado * 0.18f, lado * 0.18f),
      )
    }
  }
}

/** Anéis de escolha, peças obrigadas e pontos de destino — por cima das peças. */
@Composable
private fun MarcasDeJogo(sessao: Sessao) {
  val pulso = rememberInfiniteTransition(label = "pulso")
  val t by pulso.animateFloat(0f, 1f, infiniteRepeatable(tween(560), RepeatMode.Reverse), label = "t")
  val virado = sessao.virado
  val sel = sessao.sel
  val alvos = alvosVisiveis(sessao)
  val captura = sessao.lances.mustCapture
  val pode = sessao.podeJogar()
  val origem = if (captura && sel == null && pode) sessao.lances.lista.map { it.from }.distinct() else emptyList()
  val selecc = sel

  Canvas(Modifier.fillMaxSize()) {
    val lado = size.width / SIZE
    fun centro(casa: Int): Offset {
      val (r, c) = casaNoEcran(casa, virado)
      return Offset(c * lado + lado / 2f, r * lado + lado / 2f)
    }
    for (casa in origem) {
      drawCircle(Morango.copy(alpha = 0.28f + 0.5f * t), lado * 0.44f, centro(casa), style = Stroke(lado * 0.07f))
    }
    selecc?.lastOrNull()?.let { casa ->
      drawCircle(Ambares, lado * 0.46f, centro(casa), style = Stroke(lado * 0.08f))
    }
    for (casa in alvos) {
      val c = centro(casa)
      if (captura) {
        drawCircle(Morango.copy(alpha = 0.18f), lado * 0.44f, c)
        drawCircle(Morango, lado * 0.44f, c, style = Stroke(lado * 0.065f))
      }
      drawCircle(Color(0xFF3C6630).copy(alpha = 0.9f), lado * (0.13f + 0.045f * t), c)
      drawCircle(Papel.copy(alpha = 0.75f), lado * 0.05f, c)
    }
  }
}

private fun alvosVisiveis(sessao: Sessao): List<Int> {
  val prefix = sessao.sel ?: return emptyList()
  val saida = LinkedHashSet<Int>()
  for (m in sessao.seqsSel) {
    if (m.path.size > prefix.size && m.path.subList(0, prefix.size) == prefix) saida += m.path[prefix.size]
  }
  return saida.toList()
}

@Composable
private fun PecaANimar(peca: PecaVisual, sessao: Sessao, virado: Boolean, casaPx: Float, casaDp: Dp) {
  val (r, c) = posicaoDaPeca(peca, virado)
  val escolhida = sessao.sel?.lastOrNull() == peca.casa && !peca.morrendo
  val vida by animateFloatAsState(if (peca.morrendo) 0f else 1f, tween(330), label = "vida")
  val base = corPorId(sessao.bancos.getValue(peca.lado).cor).base
  Box(
    Modifier
      .offset { IntOffset((c * casaPx).roundToInt(), (r * casaPx).roundToInt()) }
      .size(casaDp),
    contentAlignment = Alignment.Center,
  ) {
    Canvas(Modifier.fillMaxSize()) {
      desenharPeca(
        lado = size.width,
        base = base,
        dama = peca.dama,
        vida = vida,
        crescer = escolhida,
      )
    }
  }
}

@Composable
private fun posicaoDaPeca(peca: PecaVisual, virado: Boolean): Pair<Float, Float> {
  val trilha = peca.trilha
  val passos = trilha.size - 1
  val progresso = remember(peca) { Animatable(0f) }
  LaunchedEffect(trilha) {
    if (passos > 0) {
      progresso.snapTo(0f)
      progresso.animateTo(passos.toFloat(), tween(passos * 145, easing = FastOutLinearInEasing))
      peca.trilha = emptyList()
    }
  }
  if (passos <= 0) {
    val (r0, c0) = casaNoEcran(peca.casa, virado)
    val r by animateFloatAsState(r0.toFloat(), spring(dampingRatio = 0.62f, stiffness = 900f), label = "linha")
    val c by animateFloatAsState(c0.toFloat(), spring(dampingRatio = 0.62f, stiffness = 900f), label = "coluna")
    return r to c
  }
  val p = progresso.value.coerceIn(0f, passos.toFloat())
  val i = p.toInt().coerceAtMost(passos - 1)
  val t = p - i
  val (r0, c0) = casaNoEcran(trilha[i], virado)
  val (r1, c1) = casaNoEcran(trilha[i + 1], virado)
  // o salto é maior numa captura (a peça salta mesmo por cima do adversário)
  val salto = sin(t * PI.toFloat()) * 0.34f
  return misturar(r0, r1, t) - salto to misturar(c0, c1, t)
}

private fun misturar(a: Int, b: Int, t: Float): Float = a + (b - a) * t

private fun DrawScope.desenharPeca(lado: Float, base: Color, dama: Boolean, vida: Float, crescer: Boolean) {
  if (vida <= 0.02f) return
  val cx = lado / 2f
  val cy = lado / 2f
  val r = lado * if (crescer) 0.425f else 0.395f
  val claro = brilhoDaPeca(base)
  val escuro = sombraDaPeca(base)
  drawCircle(Tinta.copy(alpha = 0.30f * vida), r * 0.96f, Offset(cx + r * 0.12f, cy + r * 0.26f))
  drawCircle(
    Brush.radialGradient(
      0f to claro,
      0.45f to base,
      1f to escuro,
      center = Offset(cx - r * 0.32f, cy - r * 0.4f),
      radius = r * 1.6f,
    ),
    r,
    Offset(cx, cy),
    alpha = vida,
  )
  drawCircle(escuro.copy(alpha = 0.95f), r * 0.985f, Offset(cx, cy), style = Stroke(r * 0.11f))
  drawCircle(claro.copy(alpha = 0.30f), r * 0.66f, Offset(cx, cy), style = Stroke(r * 0.05f))
  if (dama) {
    val largura = r * 1.05f
    val altura = r * 0.80f
    withTransform({
      translate(cx - largura / 2f, cy - altura / 2f + r * 0.05f)
      scale(largura, altura)
    }) {
      drawPath(Coroa, tintaDaCorona(base).copy(alpha = vida))
    }
  }
}

private val Coroa = Path().apply {
  moveTo(0.03f, 0.97f)
  lineTo(0.97f, 0.97f)
  lineTo(0.88f, 0.28f)
  lineTo(0.63f, 0.55f)
  lineTo(0.50f, 0.03f)
  lineTo(0.37f, 0.55f)
  lineTo(0.12f, 0.28f)
  close()
}

@Composable
private fun EfeitoNaCasa(efeito: Efeito, virado: Boolean, casaPx: Float, casaDp: Dp, aoTerminar: () -> Unit) {
  val progresso = remember(efeito.id) { Animatable(0f) }
  val duracao = if (efeito.tipo == TipoEfeito.Lasca) 460 else 780
  LaunchedEffect(efeito.id) {
    progresso.animateTo(1f, tween(duracao, easing = FastOutLinearInEasing))
    aoTerminar()
  }
  val (r, c) = casaNoEcran(efeito.casa, virado)
  Box(
    Modifier
      .offset { IntOffset((c * casaPx).roundToInt(), (r * casaPx).roundToInt()) }
      .size(casaDp),
  ) {
    Canvas(Modifier.fillMaxSize()) {
      val lado = size.width
      val cx = lado / 2f
      val cy = lado / 2f
      val t = progresso.value
      if (efeito.tipo == TipoEfeito.Lasca) {
        repeat(11) { n ->
          val ang = (n.toFloat() / 11f) * 2f * PI.toFloat() + efeito.id * 0.37f
          val dist = lado * (0.10f + 0.48f * t)
          drawCircle(
            efeito.cor.copy(alpha = (1f - t) * 0.95f),
            lado * (0.085f - 0.06f * t),
            Offset(cx + cos(ang) * dist, cy + sin(ang) * dist - lado * 0.12f * t),
          )
        }
        drawCircle(Papel.copy(alpha = (1f - t) * 0.55f), lado * (0.16f + 0.34f * t), Offset(cx, cy), style = Stroke(lado * 0.035f))
      } else {
        drawCircle(Ambares.copy(alpha = (1f - t) * 0.95f), lado * (0.18f + 0.32f * t), Offset(cx, cy), style = Stroke(lado * 0.055f))
        repeat(8) { n ->
          val ang = (n.toFloat() / 8f) * 2f * PI.toFloat() + t
          drawCircle(
            Color(0xFFFFE9B0).copy(alpha = 1f - t),
            lado * 0.05f,
            Offset(cx + cos(ang) * lado * (0.22f + 0.28f * t), cy + sin(ang) * lado * (0.22f + 0.28f * t)),
          )
        }
      }
    }
  }
}

/** Chuva de confetti quando se ganha a partida. */
@Composable
fun Confetti(semear: Int, modifier: Modifier = Modifier) {
  if (semear == 0) return
  val progresso = remember(semear) { Animatable(0f) }
  LaunchedEffect(semear) {
    progresso.snapTo(0f)
    progresso.animateTo(1f, tween(2800))
  }
  val cores = listOf(Ambares, Morango, Color(0xFF7FB069), Color(0xFF5B8BD9), Color(0xFF9B6DD6), Papel)
  val particulas = remember(semear) {
    val rnd = Random(semear * 31L + 7L)
    List(80) {
      Flocos(
        x = rnd.nextFloat(),
        atraso = rnd.nextFloat() * 0.3f,
        vel = 0.7f + rnd.nextFloat() * 0.8f,
        larg = 0.013f + rnd.nextFloat() * 0.019f,
        gir = rnd.nextFloat() * 6.28f,
        cor = cores[rnd.nextInt(cores.size)],
      )
    }
  }
  Canvas(modifier.fillMaxSize()) {
    val t = progresso.value
    if (t <= 0f) return@Canvas
    for (f in particulas) {
      val local = ((t - f.atraso) / f.vel).coerceIn(0f, 1f)
      if (local <= 0f) continue
      val y = -0.12f + local * 1.25f
      val x = f.x + sin(local * 7f + f.gir) * 0.05f
      val tam = f.larg * size.width
      drawRoundRect(f.cor.copy(alpha = 1f - local * 0.65f), Offset(x * size.width, y * size.height), Size(tam, tam * 1.9f))
    }
  }
}

private data class Flocos(val x: Float, val atraso: Float, val vel: Float, val larg: Float, val gir: Float, val cor: Color)
