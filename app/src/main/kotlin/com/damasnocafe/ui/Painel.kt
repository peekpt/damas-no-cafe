package com.damasnocafe.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.damasnocafe.ServicoMesa
import com.damasnocafe.jogo.AVATARES
import com.damasnocafe.jogo.CORES
import com.damasnocafe.jogo.Dialogo
import com.damasnocafe.jogo.Ecra
import com.damasnocafe.jogo.MesaDescoberta
import com.damasnocafe.jogo.Modo
import com.damasnocafe.jogo.Sessao
import com.damasnocafe.jogo.avatarPorId
import com.damasnocafe.jogo.corPorId
import com.damasnocafe.rede.PORTA_MESA
import com.damasnocafe.tema.Ambares
import com.damasnocafe.tema.Corpo
import com.damasnocafe.tema.Corpo_
import com.damasnocafe.tema.Creme
import com.damasnocafe.tema.Display
import com.damasnocafe.tema.MatchaFundo
import com.damasnocafe.tema.Morango
import com.damasnocafe.tema.Papel
import com.damasnocafe.tema.Tinta
import damas.core.Lado

/** Os ecrãs de serviço rolam; o do jogo usa a altura toda. */
@Composable
fun Raiz(sessao: Sessao) {
  if (sessao.ecra == Ecra.Jogo) {
    Box(Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
      EcranJogo(sessao)
    }
    return
  }
  Column(
    Modifier
      .fillMaxWidth()
      .verticalScroll(rememberScrollState())
      .padding(horizontal = 17.dp, vertical = 12.dp),
    verticalArrangement = Arrangement.spacedBy(13.dp),
  ) {
    when (sessao.ecra) {
      Ecra.Inicio -> EcranInicio(sessao)
      Ecra.Abrir -> EcranAbrir(sessao)
      Ecra.Mesas -> EcranMesas(sessao)
      Ecra.Lobby -> EcranLobby(sessao)
      Ecra.Jogo -> Unit
    }
  }
}

// ───────────────────────── início ─────────────────────────

@Composable
private fun EcranInicio(sessao: Sessao) {
  Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
    TituloDoCafe()
    Spacer(Modifier.height(14.dp))
    CartaoDePerfil(sessao)
    Spacer(Modifier.height(16.dp))
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(11.dp)) {
      BotaoCafe("☕  Abrir uma mesa", Modifier.fillMaxWidth()) { sessao.ecra = Ecra.Abrir }
      BotaoCafe("🔎  Entrar numa mesa", Modifier.fillMaxWidth(), estilo = EstiloBotao.Matcha) {
        sessao.ecra = Ecra.Mesas
        sessao.procurarMesas()
      }
      BotaoCafe("🏠  Jogar cá em casa", Modifier.fillMaxWidth(), estilo = EstiloBotao.Azul) { sessao.jogarLocal() }
      BotaoCafe("Regras de Portugal", Modifier.fillMaxWidth(), estilo = EstiloBotao.Tinta, pequeno = true) {
        sessao.dialogo = Dialogo.Regras
      }
    }
    Rodape("Duas pessoas, um Wi-Fi, um telemóvel a servir de mesa.")
  }
}

@Composable
private fun TituloDoCafe() {
  Column(horizontalAlignment = Alignment.CenterHorizontally) {
    Box(
      Modifier
        .size(84.dp)
        .clip(RoundedCornerShape(50))
        .background(Brush.verticalGradient(listOf(Color(0xFFF7B955), Ambares)))
        .border(3.dp, Tinta, RoundedCornerShape(50)),
      contentAlignment = Alignment.Center,
    ) {
      Text("☕", fontSize = 44.sp)
    }
    Spacer(Modifier.height(8.dp))
    Text("DAMAS NO CAFÉ", fontFamily = Display, fontSize = 34.sp, color = Tinta, letterSpacing = 1.sp)
    Text(
      "o jogo de damas português, à mesa do café",
      fontFamily = Corpo,
      fontSize = 13.sp,
      color = Tinta.copy(alpha = 0.62f),
      textAlign = TextAlign.Center,
    )
  }
}

@Composable
private fun CartaoDePerfil(sessao: Sessao) {
  var nome by remember(sessao.perfil) { mutableStateOf(sessao.perfil.nome) }
  Cartao(Modifier.fillMaxWidth()) {
    Text("Quem joga?", fontFamily = Display, fontSize = 19.sp, color = Tinta)
    Spacer(Modifier.height(8.dp))
    CaixaDeTexto(nome, { nome = it.take(14) }, "o teu nome")
    Spacer(Modifier.height(10.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalAlignment = Alignment.CenterVertically) {
      Text("avatar", fontFamily = Corpo, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Tinta.copy(alpha = 0.6f))
      Text(avatarPorId(sessao.perfil.avatar).nome, fontFamily = Corpo, fontSize = 12.sp, color = Ambares)
    }
    Spacer(Modifier.height(6.dp))
    Row(
      Modifier
        .fillMaxWidth()
        .horizontalScroll(rememberScrollState()),
      horizontalArrangement = Arrangement.spacedBy(9.dp),
    ) {
      for (a in AVATARES) {
        RodadaAvatar(
          avatar = a,
          tamanho = 44,
          seleccionado = sessao.perfil.avatar == a.id,
          onClick = { sessao.guardarPerfil(nome, a.id) },
        )
      }
    }
    Spacer(Modifier.height(12.dp))
    BotaoCafe(
      if (sessao.perfil.nome == nome && sessao.perfil.completa) "Perfil guardado ✓" else "Guardar nome",
      Modifier.fillMaxWidth(),
      estilo = if (nome.isBlank()) EstiloBotao.Tinta else EstiloBotao.Ambar,
      pequeno = true,
    ) { sessao.guardarPerfil(nome, sessao.perfil.avatar) }
  }
}

@Composable
fun CaixaDeTexto(
  valor: String,
  aoEscrever: (String) -> Unit,
  dica: String,
  modifier: Modifier = Modifier,
  multiline: Boolean = false,
  teclado: KeyboardType = KeyboardType.Text,
) {
  val forma = RoundedCornerShape(16.dp)
  Box(
    modifier = modifier
      .fillMaxWidth()
      .clip(forma)
      .background(Creme)
      .border(2.5.dp, Tinta.copy(alpha = 0.85f), forma)
      .padding(horizontal = 14.dp, vertical = 12.dp),
  ) {
    BasicTextField(
      value = valor,
      onValueChange = aoEscrever,
      maxLines = if (multiline) 3 else 1,
      keyboardOptions = KeyboardOptions(keyboardType = teclado),
      textStyle = Corpo_.copy(fontFamily = Corpo, fontSize = 17.sp, color = Tinta, fontWeight = FontWeight.Bold),
      cursorBrush = SolidColor(Ambares),
      decorationBox = { campo ->
        if (valor.isEmpty()) {
          Text(dica, fontFamily = Corpo, fontSize = 16.sp, color = Tinta.copy(alpha = 0.38f))
        }
        campo()
      },
    )
  }
}

// ───────────────────────── abrir mesa ─────────────────────────

@Composable
private fun EcranAbrir(sessao: Sessao) {
  var nome by remember { mutableStateOf("Mesa de ${sessao.perfil.nome.ifBlank { "alguém" }}") }
  val estado = ServicoMesa.estado.collectAsState().value
  Column(Modifier.fillMaxWidth()) {
    Cabecalho("Abrir mesa", voltar = { sessao.ecra = Ecra.Inicio })
    Cartao(Modifier.fillMaxWidth()) {
      TextoSecundario("Quem abre a mesa empresta o telemóvel como juíz: é ele que manda as jogadas para o amigo.")
      Spacer(Modifier.height(12.dp))
      Text("Nome da mesa", fontFamily = Corpo, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Tinta.copy(alpha = 0.65f))
      Spacer(Modifier.height(6.dp))
      CaixaDeTexto(nome, { nome = it.take(24) }, "Mesa do João")
      Spacer(Modifier.height(12.dp))
      if (estado.ip.isNotBlank()) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          Chip("IP ${estado.ip}")
          Chip("porta $PORTA_MESA", cor = Papel)
        }
        Spacer(Modifier.height(10.dp))
      }
      BotaoCafe("Abrir e anunciar", Modifier.fillMaxWidth()) { sessao.abrirMesa(nome) }
      Spacer(Modifier.height(10.dp))
      TextoSecundario("Liga o Wi-Fi antes: é o endereço do telemóvel que dá o número à mesa.")
    }
  }
}

// ───────────────────────── procurar / entrar ─────────────────────────

@Composable
private fun EcranMesas(sessao: Sessao) {
  var texto by remember { mutableStateOf("") }
  Column(Modifier.fillMaxWidth()) {
    Cabecalho("Mesas neste Wi-Fi", voltar = { sessao.ecra = Ecra.Inicio })
    Cartao(Modifier.fillMaxWidth()) {
      TextoSecundario("Cada mesa tem um número e uma palavra. Quem abriu vê-los no ecrã da mesa.")
      Spacer(Modifier.height(10.dp))
      CaixaDeTexto(texto, { escrito -> texto = escrito.filter { !it.isWhitespace() }.take(14) }, "nº ou palavra: donut")
      Spacer(Modifier.height(10.dp))
      BotaoCafe(
        "Entrar",
        Modifier.fillMaxWidth(),
        estilo = EstiloBotao.Matcha,
        activado = !sessao.aVarrer && texto.isNotBlank(),
      ) { sessao.entrarPorNumero(texto) }
      Spacer(Modifier.height(10.dp))
      BotaoCafe(
        if (sessao.aVarrer) "A procurar…" else "Procurar mesas",
        Modifier.fillMaxWidth(),
        estilo = EstiloBotao.Tinta,
        pequeno = true,
        activado = !sessao.aVarrer,
      ) { sessao.procurarMesas() }
    }
    if (sessao.mesas.isEmpty()) {
      Cartao(Modifier.fillMaxWidth(), cor = Color(0xFFFFFBEE)) {
        TextoSecundario(
          if (sessao.aVarrer) "A percorrer a rede, casa a casa…"
          else "Nada encontrado. Pede ao amigo para abrir a mesa dele neste Wi-Fi.",
        )
      }
    } else {
      Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        for (mesa in sessao.mesas) {
          CartaoMesa(mesa) { sessao.entrarNaMesa(mesa.ip, mesa) }
        }
      }
    }
  }
}

@Composable
private fun CartaoMesa(mesa: MesaDescoberta, aoEntrar: () -> Unit) {
  val forma = RoundedCornerShape(20.dp)
  Row(
    Modifier
      .fillMaxWidth()
      .shadow(5.dp, forma, clip = false, spotColor = Tinta.copy(alpha = 0.25f))
      .clip(forma)
      .background(Papel)
      .border(2.5.dp, Tinta.copy(alpha = 0.9f), forma)
      .clickable(enabled = !mesa.ocupada, onClick = aoEntrar)
      .padding(13.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(11.dp),
  ) {
    RodadaAvatar(avatarPorId(mesa.avatar), tamanho = 42)
    Column(Modifier.weight(1f)) {
      Text(mesa.nome.ifBlank { "Mesa ${mesa.numero}" }, fontFamily = Display, fontSize = 17.sp, color = Tinta)
      Text(
        "${mesa.anfitriao} · ${mesa.ip}",
        fontFamily = Corpo,
        fontSize = 12.sp,
        color = Tinta.copy(alpha = 0.6f),
      )
    }
    Column(horizontalAlignment = Alignment.End) {
      Chip("Mesa ${mesa.numero}")
      Spacer(Modifier.height(4.dp))
      Text(
        if (mesa.ocupada) "a jogar" else mesa.palavra.uppercase(),
        fontFamily = Corpo,
        fontWeight = FontWeight.Bold,
        fontSize = 12.sp,
        color = if (mesa.ocupada) Morango else MatchaFundo,
      )
    }
  }
}

// ───────────────────────── lobby ─────────────────────────

@Composable
private fun EcranLobby(sessao: Sessao) {
  val meu = sessao.bancos.getValue(sessao.meuLado)
  val outro = sessao.bancos.getValue(sessao.outroLado)
  Column(Modifier.fillMaxWidth()) {
    if (sessao.modo == Modo.Online) {
      Cartao(Modifier.fillMaxWidth(), cor = Color(0xFF2A1710)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
          Text("🪪", fontSize = 26.sp)
          Column(Modifier.weight(1f)) {
            Text(
              "Mesa nº ${sessao.numero}",
              fontFamily = Display,
              fontSize = 21.sp,
              color = Creme,
            )
            Text(
              sessao.palavra.uppercase() + "  ·  " + sessao.ip,
              fontFamily = Corpo,
              fontSize = 13.sp,
              color = Creme.copy(alpha = 0.75f),
            )
          }
        }
        Spacer(Modifier.height(8.dp))
        Text(
          sessao.nomeMesa,
          fontFamily = Corpo,
          fontWeight = FontWeight.Bold,
          fontSize = 15.sp,
          color = Ambares,
        )
      }
    } else {
      Cabecalho(sessao.nomeMesa, voltar = { sessao.ecra = Ecra.Inicio })
    }

    if (sessao.modo == Modo.Local) {
      BancoEditavel(sessao, Lado.BRANCAS, "Jogador 1")
      BancoEditavel(sessao, Lado.PRETAS, "Jogador 2")
      Spacer(Modifier.height(14.dp))
      BotaoCafe("Começar o jogo ✓", Modifier.fillMaxWidth(), activado = meu.cor != outro.cor) { sessao.comecarLocal() }
      Spacer(Modifier.height(8.dp))
      BotaoCafe("Voltar", Modifier.fillMaxWidth(), estilo = EstiloBotao.Tinta, pequeno = true) { sessao.ecra = Ecra.Inicio }
      if (meu.cor == outro.cor) {
        TextoSecundario("Escolham cores diferentes.", Modifier.fillMaxWidth())
      }
    } else {
      Cartao(Modifier.fillMaxWidth()) {
        Text("As tuas peças", fontFamily = Display, fontSize = 18.sp, color = Tinta)
        Spacer(Modifier.height(4.dp))
        TextoSecundario("A cor troca a cada partida — é escolhida aqui.")
        Spacer(Modifier.height(10.dp))
        Row(
          Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
          horizontalArrangement = Arrangement.spacedBy(9.dp),
        ) {
          for (c in CORES) {
            PastilhaDeCor(
              cor = c,
              seleccionada = meu.cor == c.id,
              indisponivel = outro.cor == c.id,
            ) { sessao.escolherCor(sessao.meuLado, c.id) }
          }
        }
      }
      Spacer(Modifier.height(12.dp))
      Cartao(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(11.dp)) {
          RodadaAvatar(avatarPorId(outro.avatar), tamanho = 44, cor = corPorId(outro.cor).base)
          Column(Modifier.weight(1f)) {
            Text(
              if (sessao.convidadoAqui) outro.nome.ifBlank { "O teu amigo" } else "À espera do amigo…",
              fontFamily = Display,
              fontSize = 17.sp,
              color = Tinta,
            )
            Text(
              when {
                !sessao.convidadoAqui -> "diz-lhe o número da mesa"
                outro.pronto -> "pronto para jogar ✓"
                else -> "a escolher as peças…"
              },
              fontFamily = Corpo,
              fontSize = 13.sp,
              color = if (outro.pronto || !sessao.convidadoAqui) Tinta.copy(alpha = 0.6f) else MatchaFundo,
            )
          }
          if (sessao.convidadoAqui) {
            Box(
              Modifier
                .size(30.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(corPorId(outro.cor).base)
                .border(2.dp, Tinta, RoundedCornerShape(10.dp)),
            )
          }
        }
      }
      Spacer(Modifier.height(14.dp))
      BotaoCafe(
        when {
          meu.pronto && outro.pronto -> "A começar…"
          meu.pronto -> "Pronto ✓  à espera…"
          else -> "Estou pronto ✓"
        },
        Modifier.fillMaxWidth(),
        activado = !meu.pronto && sessao.convidadoAqui,
      ) { sessao.estouPronto() }
      Spacer(Modifier.height(8.dp))
      BotaoCafe("Sair da mesa", Modifier.fillMaxWidth(), estilo = EstiloBotao.Perigo, pequeno = true) {
        sessao.dialogo = Dialogo.Sair
      }
    }
  }
}

@Composable
private fun BancoEditavel(sessao: Sessao, lado: Lado, titulo: String) {
  val banco = sessao.bancos.getValue(lado)
  var nome by remember(lado) { mutableStateOf(banco.nome) }
  Cartao(Modifier.fillMaxWidth()) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
      RodadaAvatar(avatarPorId(banco.avatar), tamanho = 44, cor = corPorId(banco.cor).base)
      Column(Modifier.weight(1f)) {
        Text(titulo, fontFamily = Display, fontSize = 16.sp, color = Tinta)
        Spacer(Modifier.height(6.dp))
        CaixaDeTexto(
          valor = nome,
          aoEscrever = {
            nome = it.take(14)
            banco.nome = nome
          },
          dica = "nome",
        )
      }
    }
    Spacer(Modifier.height(8.dp))
    Row(
      Modifier
        .fillMaxWidth()
        .horizontalScroll(rememberScrollState()),
      horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
      for (a in AVATARES) {
        RodadaAvatar(
          avatar = a,
          tamanho = 34,
          seleccionado = banco.avatar == a.id,
          cor = corPorId(banco.cor).base,
          onClick = { banco.avatar = a.id },
        )
      }
    }
    Spacer(Modifier.height(8.dp))
    Row(
      Modifier
        .fillMaxWidth()
        .horizontalScroll(rememberScrollState()),
      horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      for (c in CORES) {
        PastilhaDeCor(
          cor = c,
          seleccionada = banco.cor == c.id,
          indisponivel = sessao.bancos.getValue(lado.outro).cor == c.id,
        ) { sessao.escolherCor(lado, c.id) }
      }
    }
  }
}

@Composable
fun Cabecalho(titulo: String, voltar: (() -> Unit)? = null, modifier: Modifier = Modifier) {
  Row(
    modifier.fillMaxWidth(),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(10.dp),
  ) {
    voltar?.let { BotaoVoltar(it) }
    Text(titulo, fontFamily = Display, fontSize = 24.sp, color = Tinta, modifier = Modifier.weight(1f, fill = false))
    Spacer(Modifier.width(1.dp))
  }
}

@Composable
private fun BotaoVoltar(voltar: () -> Unit) {
  val forma = RoundedCornerShape(14.dp)
  Box(
    Modifier
      .size(40.dp)
      .clip(forma)
      .background(Papel)
      .border(2.5.dp, Tinta, forma)
      .clickable(onClick = voltar),
    contentAlignment = Alignment.Center,
  ) {
    Text("‹", fontFamily = Display, fontSize = 26.sp, color = Tinta)
  }
}
