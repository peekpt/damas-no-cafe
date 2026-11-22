package com.damasnocafe.som

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random

private const val TAXA = 22050

internal enum class Forma { Seno, Dente, Quadrada, Ruido, Raspa }

/**
 * Uma pastilha do sintetizador: frequência (com deslizamento), tempo em ms, volume.
 * O `ataque` são os ms a subir e o `sustem` a fracção do som que se aguenta em cheio
 * antes da cauda — um toque de porcelana quer 1 ms, um arrasto de madeira quer 20.
 */
internal data class Nota(
  val freq: Float,
  val inicio: Int,
  val dur: Int,
  val vol: Float = 0.5f,
  val forma: Forma = Forma.Seno,
  val desliza: Float = 1f,
  val ataque: Int = 4,
  val sustem: Float = 0f,
)

private const val MS = TAXA / 1000

/** Todos os sons do jogo, gerados em código — nenhum ficheiro no APK. */
internal val DESENHOS: Map<String, List<Nota>> = mapOf(
  "clique" to listOf(Nota(880f, 0, 40, 0.22f, Forma.Seno, 0.7f)),
  // O arrasto da peça pelo tampo: a cerâmica agarra, range e solta-se.
  // A raspa é o ruído da madeira; o seno grave por baixo é o peso em cima dela.
  "mover" to listOf(
    Nota(150f, 0, 250, 0.16f, Forma.Raspa, 1.7f, 20, 0.5f),
    Nota(74f, 12, 230, 0.10f, Forma.Seno, 1.35f, 24, 0.45f),
  ),
  // A peça comida: três toques secos no tampo, um por salto, a descer um pouco.
  "captura" to listOf(
    Nota(246f, 0, 62, 0.28f, Forma.Dente, 0.62f, 2),
    Nota(210f, 0, 26, 0.15f, Forma.Raspa, 1.3f, 1),
    Nota(233f, 84, 62, 0.25f, Forma.Dente, 0.62f, 2),
    Nota(200f, 84, 26, 0.14f, Forma.Raspa, 1.3f, 1),
    Nota(220f, 168, 78, 0.24f, Forma.Dente, 0.60f, 2),
    Nota(224f, 168, 30, 0.15f, Forma.Raspa, 1.4f, 1),
  ),
  "coroa" to listOf(
    Nota(660f, 0, 90, 0.30f),
    Nota(880f, 80, 90, 0.30f),
    Nota(1320f, 160, 150, 0.32f, Forma.Seno, 1.02f),
  ),
  // A fanfarra de quem ganha: sobe a escala, descansa no si e remata em acorde.
  "vitoria" to listOf(
    Nota(523f, 0, 160, 0.26f, Forma.Dente),
    Nota(659f, 160, 160, 0.26f, Forma.Dente),
    Nota(784f, 320, 200, 0.28f, Forma.Dente),
    Nota(1046f, 520, 320, 0.30f, Forma.Seno),
    Nota(988f, 840, 160, 0.24f, Forma.Dente),
    Nota(1046f, 1000, 300, 0.28f, Forma.Seno),
    Nota(1318f, 1300, 340, 0.28f, Forma.Dente, 1.004f),
    Nota(1568f, 1640, 720, 0.32f, Forma.Seno, 1.003f),
    Nota(1046f, 1640, 720, 0.20f, Forma.Seno),
    Nota(1318f, 1640, 720, 0.16f, Forma.Seno),
    Nota(131f, 0, 520, 0.18f, Forma.Seno),
    Nota(196f, 520, 480, 0.18f, Forma.Seno),
    Nota(262f, 1000, 640, 0.17f, Forma.Seno),
    Nota(392f, 1640, 720, 0.18f, Forma.Seno),
  ),
  "derrota" to listOf(
    Nota(392f, 0, 160, 0.28f),
    Nota(311f, 140, 160, 0.26f),
    Nota(233f, 280, 320, 0.28f, Forma.Seno, 0.94f),
  ),
  "empate" to listOf(Nota(440f, 0, 150, 0.26f), Nota(440f, 170, 220, 0.26f, Forma.Seno, 0.98f)),
  "erro" to listOf(Nota(180f, 0, 70, 0.30f, Forma.Quadrada), Nota(150f, 85, 90, 0.28f, Forma.Quadrada)),
  "aviso" to listOf(Nota(988f, 0, 70, 0.26f), Nota(1319f, 90, 110, 0.26f)),
  "porta" to listOf(Nota(523f, 0, 90, 0.24f), Nota(784f, 80, 150, 0.26f)),
)

internal fun geraWav(notas: List<Nota>): ByteArray {
  val fim = notas.maxOf { it.inicio + it.dur }
  val total = fim * MS
  val amostras = FloatArray(total)
  val ruido = Random(7)
  for (n in notas) {
    val inicio = n.inicio * MS
    val comprimento = n.dur * MS
    var fase = 0f
    var grao = 0f
    var fundo = 0f
    var aresta = 0f
    for (i in 0 until comprimento) {
      val t = i / comprimento.toFloat()
      val f = n.freq * (1f + (n.desliza - 1f) * t)
      fase += 2f * PI.toFloat() * f / TAXA
      val cru = when (n.forma) {
        Forma.Seno -> sin(fase)
        Forma.Dente -> 2f * (fase / (2f * PI.toFloat()) % 1f) - 1f
        Forma.Quadrada -> if (sin(fase) >= 0f) 1f else -1f
        Forma.Ruido -> ruido.nextFloat() * 2f - 1f
        // O ranger de algo arrastado: ruído segurado entre dois filtros, a bandpass
        // que se vai abrindo, partido em grãos pelo dente da madeira.
        Forma.Raspa -> {
          val semente = ruido.nextFloat() * 2f - 1f
          fundo += (0.045f + 0.03f * t) * (semente - fundo)
          aresta += (0.34f + 0.16f * t) * (semente - aresta)
          grao += 2f * PI.toFloat() * (44f + 34f * t) / TAXA
          (aresta - fundo) * (0.4f + 0.6f * sin(grao)) * 2.2f
        }
      }
      // Ataque e cauda: os toques de porcelana começam em 1 ms e calam logo;
      // o arrasto segura o volume até meio do caminho e alivia no fim.
      val subida = (i.toFloat() / (n.ataque * MS).coerceAtLeast(1)).coerceAtMost(1f)
      val cauda = when {
        n.sustem > 0f && t < n.sustem -> 1f
        n.sustem > 0f -> exp(-5.5f * (t - n.sustem) / (1f - n.sustem))
        else -> exp(-3.6f * t)
      }
      amostras[inicio + i] += cru * n.vol * subida * cauda
    }
  }
  val pcm = ByteBuffer.allocate(total * 2).order(ByteOrder.LITTLE_ENDIAN)
  for (a in amostras) pcm.putShort((a.coerceIn(-1f, 1f) * 29000).toInt().toShort())
  val saida = ByteArrayOutputStream(44 + total * 2)
  fun u32(v: Int) = saida.write(
    byteArrayOf((v and 255).toByte(), ((v shr 8) and 255).toByte(), ((v shr 16) and 255).toByte(), ((v shr 24) and 255).toByte())
  )
  fun u16(v: Int) = saida.write(byteArrayOf((v and 255).toByte(), ((v shr 8) and 255).toByte()))
  saida.write("RIFF".toByteArray()); u32(36 + total * 2); saida.write("WAVE".toByteArray())
  saida.write("fmt ".toByteArray()); u32(16); u16(1); u16(1); u32(TAXA); u32(TAXA * 2); u16(2); u16(16)
  saida.write("data".toByteArray()); u32(total * 2); saida.write(pcm.array())
  return saida.toByteArray()
}

/** Efeito sonoro do jogo, com o interruptor do café guardado nas preferências. */
class Sons(private val contexto: Context) {
  private val pool = SoundPool.Builder()
    .setMaxStreams(6)
    .setAudioAttributes(
      AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build(),
    )
    .build()
  private val ids = HashMap<String, Int>()

  var mudo by mutableStateOf(
    contexto.getSharedPreferences("som", Context.MODE_PRIVATE).getBoolean("mudo", false),
  )
    private set

  init {
    val pasta = File(contexto.cacheDir, "sons").apply { mkdirs() }
    for ((nome, desenho) in DESENHOS) {
      val ficheiro = File(pasta, "$nome.wav")
      runCatching { ficheiro.writeBytes(geraWav(desenho)) }
      ids[nome] = pool.load(ficheiro.absolutePath, 1)
    }
  }

  fun tocar(nome: String) {
    if (mudo) return
    val id = ids[nome] ?: return
    pool.play(id, 1f, 1f, 1, 0, 1f)
  }

  fun alternarMudo() {
    val novo = !mudo
    contexto.getSharedPreferences("som", Context.MODE_PRIVATE).edit().putBoolean("mudo", novo).apply()
    mudo = novo
    if (!novo) tocar("clique")
  }

  fun libertar() = pool.release()
}
