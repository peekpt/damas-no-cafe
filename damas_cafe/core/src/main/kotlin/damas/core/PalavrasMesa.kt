package damas.core

// 254 palavras de café: o nº da mesa é o último número do IP de quem a abriu.
private val PALAVRAS_MESA = listOf(
    "bica", "galao", "mocha", "meialeite", "bule", "cafe",
    "cha", "matcha", "chai", "cacau", "leite", "mel",
    "agua", "sumo", "limao", "gelo", "bolo", "donut",
    "croissant", "bagel", "muffin", "cookie", "brownie", "waffle",
    "panqueca", "torrada", "sandes", "queijo", "fiambre", "manteiga",
    "compota", "avela", "pires", "chavena", "caneca", "copo",
    "jarro", "prato", "garfo", "faca", "colher", "guardanapo",
    "tabuleiro", "balcao", "montra", "ementa", "conta", "gorjeta",
    "barista", "garcom", "cliente", "amigo", "vizinho", "chefe",
    "cozinheiro", "padeiro", "pasteleiro", "empregado", "gerente", "caixa",
    "musico", "poeta", "pintor", "leitor", "mesa", "cadeira",
    "sofa", "banco", "janela", "porta", "parede", "chao",
    "teto", "lampada", "vela", "quadro", "espelho", "tapete",
    "cortina", "planta", "gato", "cao", "mocho", "passaro",
    "peixe", "coelho", "tartaruga", "hamster", "papagaio", "canario",
    "abelha", "borboleta", "formiga", "joaninha", "grilo", "caracol",
    "sol", "lua", "estrela", "nuvem", "chuva", "vento",
    "neve", "arcoiris", "trovoada", "neblina", "calor", "frio",
    "sombra", "luz", "aurora", "crepusculo", "doce", "amargo",
    "quente", "gelado", "forte", "suave", "claro", "escuro",
    "novo", "velho", "alto", "baixo", "cheio", "vazio",
    "rapido", "lento", "rosa", "tulipa", "margarida", "girassol",
    "violeta", "jasmim", "lavanda", "alecrim", "hortela", "manjericao",
    "canela", "baunilha", "nozmoscada", "gengibre", "anis", "cravo",
    "xadrez", "damas", "cartas", "dados", "domino", "puzzle",
    "trivia", "bingo", "sudoku", "golfe", "tenis", "futebol",
    "basquetebol", "voleibol", "remo", "gamao", "viola", "guitarra",
    "piano", "flauta", "tambor", "harpa", "sino", "gaita",
    "acordeon", "banjo", "saxofone", "trompete", "violino", "contrabaixo",
    "pandeiro", "castanholas", "livro", "revista", "jornal", "carta",
    "postal", "bilhete", "nota", "caneta", "lapis", "borracha",
    "regua", "tesoura", "cola", "fita", "clipe", "agrafador",
    "chave", "moeda", "troco", "carteira", "bolso", "botao",
    "fecho", "agulha", "linha", "dedal", "pano", "saco",
    "cesta", "caixote", "frasco", "boiao", "tampa", "rolha",
    "comboio", "autocarro", "metro", "barco", "aviao", "bicicleta",
    "mota", "carro", "taxi", "carrinha", "camiao", "trator",
    "balao", "helicoptero", "patins", "skate", "praia", "monte",
    "rio", "lago", "fonte", "ponte", "torre", "farol",
    "moinho", "castelo", "igreja", "mercado", "praca", "jardim",
    "bosque", "gruta", "coroa", "trono", "espada", "escudo",
    "elmo", "dragao", "fada", "mago", "duende", "gigante",
    "sereia", "fantasma",
)

fun codigoDaMesa(n: Int): String? = PALAVRAS_MESA.getOrNull(n - 1)

fun numeroDaMesa(texto: String?): Int? {
  val t = texto?.trim().orEmpty()
  if (t.isEmpty()) return null
  if (t.length <= 3 && t.all { it.isDigit() }) {
    val n = t.toIntOrNull() ?: return null
    return if (n in 1..PALAVRAS_MESA.size) n else null
  }
  val limpo = java.text.Normalizer.normalize(t.lowercase(), java.text.Normalizer.Form.NFD)
    .filter { it.code < 0x300 || it.code > 0x36f }
    .filter { it in 'a'..'z' }
  val i = PALAVRAS_MESA.indexOf(limpo)
  return if (i >= 0) i + 1 else null
}
