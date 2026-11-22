package com.damasnocafe.tema

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.damasnocafe.R

// Paleta do café: tinta forte, creme, âmbar. As peças são cerâmica (café e leite).
val Tinta = Color(0xFF2A1710)
val Cafe = Color(0xFF4A2C1A)
val Creme = Color(0xFFF6EAD2)
val Papel = Color(0xFFFFF8EA)
val Ambares = Color(0xFFF0A63C)
val AmbarFundo = Color(0xFFD98A1F)
val Morango = Color(0xFFE2503C)
val Matcha = Color(0xFF7FB069)
val MatchaFundo = Color(0xFF5D9147)
val Azul = Color(0xFF5B8BD9)
val AzulFundo = Color(0xFF3F6CB8)
val Uva = Color(0xFF9B6DD6)
val CasaClara = Color(0xFFF9EFD9)
val CasaEscuro = Color(0xFFE0A458)

val Display = FontFamily(Font(R.font.luckiest_guy))
val Corpo = FontFamily(Font(R.font.baloo2))

private val Esquema = lightColorScheme(
  primary = AmbarFundo,
  onPrimary = Tinta,
  secondary = MatchaFundo,
  onSecondary = Papel,
  background = Creme,
  onBackground = Tinta,
  surface = Papel,
  onSurface = Tinta,
  error = Morango,
  onError = Papel,
)

val Titulo = TextStyle(fontFamily = Display, fontSize = 30.sp, color = Tinta, letterSpacing = 0.5.sp)
val Subtitulo = TextStyle(fontFamily = Corpo, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Tinta.copy(alpha = 0.72f))
val Corpo_ = TextStyle(fontFamily = Corpo, fontSize = 15.sp, color = Tinta)

@Composable
fun DamasTema(content: @Composable () -> Unit) {
  MaterialTheme(
    colorScheme = Esquema,
    typography = Typography(
      titleLarge = Titulo,
      titleMedium = Titulo.copy(fontSize = 22.sp),
      bodyLarge = Corpo_,
      bodyMedium = Corpo_.copy(fontSize = 14.sp),
      labelLarge = TextStyle(fontFamily = Corpo, fontWeight = FontWeight.Bold, fontSize = 16.sp),
    ),
    content = content,
  )
}
