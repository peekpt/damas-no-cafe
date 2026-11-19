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

internal enum class Forma { Seno, Dente, Quadrada, Ruido }

/** Uma pastilha do sintetizador: frequência (com deslizamento), tempo em ms, volume. */
internal data class Nota(
  val freq: Float,
  val inicio: Int,
  val dur: Int,
  val vol: Float = 0.5f,
  val forma: Forma = Forma.Seno,
  val desliza: Float = 1f,
)

private const val MS = TAXA / 1000

/** Todos os sons do jogo, gerados em código — nenhum ficheiro no APK. */
internal val DESENHOS: Map<String, List<Nota>> = mapOf(
  "clique" to listOf(Nota(880f, 0, 40, 0.22f, Forma.Seno, 0.7f)),
  "mover" to listOf(
    Nota(330f, 0, 55, 0.34f, Forma.Dente, 0.72f),
    Nota(190f, 6, 70, 0.20f, Forma.Seno, 0.8f),
  ),
  "captura" to listOf(
    Nota(150f, 0, 120, 0.42f, Forma.Dente, 0.55f),
    Nota(1400f, 0, 45, 0.16f, Forma.Ruido, 0.5f),
    Nota(520f, 55, 90, 0.20f, Forma.Seno, 0.6f),
  ),
  "coroa" to listOf(
    Nota(660f, 0, 90, 0.30f),
    Nota(880f, 80, 90, 0.30f),
    Nota(1320f, 160, 150, 0.32f, Forma.Seno, 1.02f),
  ),
  "vitoria" to listOf(
    Nota(523f, 0, 130, 0.30f),
    Nota(659f, 110, 130, 0.30f),
    Nota(784f, 220, 130, 0.30f),
    Nota(1046f, 330, 320, 0.36f, Forma.Seno, 1.01f),
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
    for (i in 0 until comprimento) {
      val t = i / comprimento.toFloat()
      val f = n.freq * (1f + (n.desliza - 1f) * t)
      fase += 2f * PI.toFloat() * f / TAXA
      val cru = when (n.forma) {
        Forma.Seno -> sin(fase)
        Forma.Dente -> 2f * (fase / (2f * PI.toFloat()) % 1f) - 1f
        Forma.Quadrada -> if (sin(fase) >= 0f) 1f else -1f
        Forma.Ruido -> ruido.nextFloat() * 2f - 1f
      }
      // Ataque rápido e cauda exponencial: soa a porcelana, não a beep.
      val ataque = (i.toFloat() / (MS * 4).coerceAtLeast(1)).coerceAtMost(1f)
      val cauda = exp(-3.6f * t)
      amostras[inicio + i] += cru * n.vol * ataque * cauda
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
