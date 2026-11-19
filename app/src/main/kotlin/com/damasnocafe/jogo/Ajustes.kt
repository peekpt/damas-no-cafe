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

// As peças são de cerâmica de café: o tabuleiro é madeira clara e escura.
val CORES = listOf(
  CorPeca("leite", "Leite", Color(0xFFF2E8D5)),
  CorPeca("cafe", "Café", Color(0xFF4A2C1A)),
  CorPeca("caramelo", "Caramelo", Color(0xFFF0A63C)),
  CorPeca("matcha", "Matcha", Color(0xFF7FB069)),
  CorPeca("framboesa", "Framboesa", Color(0xFFE0526E)),
  CorPeca("mirtilo", "Mirtilo", Color(0xFF5B8BD9)),
  CorPeca("uva", "Uva", Color(0xFF9B6DD6)),
  CorPeca("menta", "Menta", Color(0xFF4ECDC4)),
)

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
