package com.damasnocafe.jogo

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance

data class Avatar(val id: String, val emoji: String, val nome: String)

val AVATARES = listOf(
  Avatar("cafe", "☕", "Café"),
  Avatar("croissant", "🥐", "Croissant"),
  Avatar("cupcake", "🧁", "Cupcake"),
  Avatar("donut", "🍩", "Donut"),
  Avatar("bule", "🫖", "Bule"),
  Avatar("bolo", "🍰", "Bolo"),
  Avatar("gato", "🐈", "Gato"),
  Avatar("mocho", "🦉", "Mocho"),
  Avatar("chapeu", "🎩", "Chapéu"),
  Avatar("barista", "🧑‍🍳", "Barista"),
  Avatar("planta", "🌿", "Planta"),
  Avatar("fogo", "🔥", "Fogo"),
)

data class CorPeca(val id: String, val nome: String, val base: Color)

// Em Portugal jogam-se damas a preto e branco: são só estas duas cerâmicas.
val CORES = listOf(
  CorPeca("brancas", "Brancas", Color(0xFFF6EFDD)),
  CorPeca("pretas", "Pretas", Color(0xFF211812)),
)

const val COR_BRANCAS = "brancas"
const val COR_PRETAS = "pretas"

val EMOTES = listOf("☕", "👏", "😂", "🔥")

fun corPorId(id: String?): CorPeca = CORES.firstOrNull { it.id == id } ?: CORES[0]
fun avatarPorId(id: String?): Avatar = AVATARES.firstOrNull { it.id == id } ?: AVATARES[0]

/** A cor da coroa e das riscas do disco consoante o brilho da peça. */
fun tintaDaCorona(base: Color): Color = if (base.luminance() > 0.55f) Color(0xFF2A1710) else Color(0xFFFFF8EA)

fun brilhoDaPeca(base: Color): Color = lerp(base, Color.White, 0.55f)
fun sombraDaPeca(base: Color): Color = lerp(base, Color(0xFF2A1710), 0.42f)

data class Perfil(val nome: String, val avatar: String) {
  val completa: Boolean get() = nome.isNotBlank()
}

object GuardaPerfil {
  private const val FICHEIRO = "perfil"

  fun ler(contexto: Context): Perfil {
    val p = contexto.getSharedPreferences(FICHEIRO, Context.MODE_PRIVATE)
    return Perfil(p.getString("nome", "").orEmpty().take(14), p.getString("avatar", "cafe").orEmpty())
  }

  fun gravar(contexto: Context, perfil: Perfil) {
    contexto.getSharedPreferences(FICHEIRO, Context.MODE_PRIVATE).edit()
      .putString("nome", perfil.nome.take(14))
      .putString("avatar", perfil.avatar)
      .apply()
  }
}

/** O último emoji enviado, na mesma gaveta do nome: é esse que o toque rápido dispara. */
object GuardaEmoji {
  private const val FICHEIRO = "perfil"

  fun ler(contexto: Context): String =
    contexto.getSharedPreferences(FICHEIRO, Context.MODE_PRIVATE).getString("emoji", null)
      ?.takeIf { it in EMOTES }
      ?: EMOTES.first()

  fun gravar(contexto: Context, emoji: String) {
    contexto.getSharedPreferences(FICHEIRO, Context.MODE_PRIVATE).edit().putString("emoji", emoji).apply()
  }
}
