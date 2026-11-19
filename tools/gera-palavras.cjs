const fs = require("fs");
const src = fs.readFileSync("webapp_old/web/js/net.js", "utf8");
const bloco = src.match(/const WORDS = \[([\s\S]*?)\];/)[1];
const palavras = [...bloco.matchAll(/'([^']+)'/g)].map((x) => x[1]).slice(0, 254);
if (palavras.length !== 254) {
  console.error("esperava 254 palavras, obtive", palavras.length);
  process.exit(1);
}
const linhas = [];
for (let i = 0; i < palavras.length; i += 6) {
  linhas.push("    " + palavras.slice(i, i + 6).map((p) => `"${p}"`).join(", ") + ",");
}
const kotlin = [
  "package damas.core",
  "",
  "// 254 palavras de café: o nº da mesa é o último número do IP de quem a abriu.",
  "private val PALAVRAS_MESA = listOf(",
  ...linhas,
  ")",
  "",
  "fun codigoDaMesa(n: Int): String? = PALAVRAS_MESA.getOrNull(n - 1)",
  "",
  "fun numeroDaMesa(texto: String?): Int? {",
  "  val t = texto?.trim().orEmpty()",
  "  if (t.isEmpty()) return null",
  "  if (t.length <= 3 && t.all { it.isDigit() }) {",
  "    val n = t.toIntOrNull() ?: return null",
  "    return if (n in 1..PALAVRAS_MESA.size) n else null",
  "  }",
  "  val limpo = java.text.Normalizer.normalize(t.lowercase(), java.text.Normalizer.Form.NFD)",
  '    .filter { it.code < 0x300 || it.code > 0x36f }',
  '    .filter { it in \'a\'..\'z\' }',
  "  val i = PALAVRAS_MESA.indexOf(limpo)",
  "  return if (i >= 0) i + 1 else null",
  "}",
  "",
].join("\n");
fs.writeFileSync("core/src/main/kotlin/damas/core/PalavrasMesa.kt", kotlin);
console.log("geradas", palavras.length, "palavras");
