package damas.core

internal class JsonParser(private val s: String) {
  private var i = 0

  fun parseDocument(): Json {
    val j = value()
    ws()
    if (i != s.length) throw IllegalArgumentException("json com lixo no fim")
    return j
  }

  private fun ws() { while (i < s.length && s[i].isWhitespace()) i++ }

  private fun value(): Json {
    ws()
    if (i >= s.length) throw IllegalArgumentException("json incompleto")
    return when (val c = s[i]) {
      '{' -> obj()
      '[' -> arr()
      '"' -> Json.Str(str())
      't' -> word("true", Json.Bool(true))
      'f' -> word("false", Json.Bool(false))
      'n' -> word("null", Json.Null)
      else -> if (c == '-' || c.isDigit()) num() else throw IllegalArgumentException("json inválido em $i")
    }
  }

  private fun word(lit: String, resultado: Json): Json {
    if (!s.startsWith(lit, i)) throw IllegalArgumentException("literal inválido em $i")
    i += lit.length
    return resultado
  }

  private fun num(): Json {
    val start = i
    if (s[i] == '-') i++
    while (i < s.length && (s[i].isDigit() || "._eE+-".contains(s[i]))) i++
    return s.substring(start, i).toDoubleOrNull()?.let { Json.Num(it) }
      ?: throw IllegalArgumentException("número inválido")
  }

  private fun str(): String {
    val sb = StringBuilder()
    i++
    while (i < s.length) {
      val c = s[i++]
      if (c == '"') return sb.toString()
      if (c != '\\') { sb.append(c); continue }
      when (val e = s[i++]) {
        'n' -> sb.append('\n')
        't' -> sb.append('\t')
        'r' -> sb.append('\r')
        'b' -> sb.append('\b')
        'u' -> { sb.append(s.substring(i, i + 4).toInt(16).toChar()); i += 4 }
        else -> sb.append(e)
      }
    }
    throw IllegalArgumentException("string por fechar")
  }

  private fun obj(): Json {
    val map = LinkedHashMap<String, Json>()
    i++
    ws()
    if (s[i] == '}') { i++; return Json.Obj(map) }
    while (true) {
      ws()
      val k = str()
      ws()
      if (s[i] != ':') throw IllegalArgumentException("esperava : em $i")
      i++
      map[k] = value()
      ws()
      when (s[i]) {
        ',' -> i++
        '}' -> { i++; return Json.Obj(map) }
        else -> throw IllegalArgumentException("esperava , ou } em $i")
      }
    }
  }

  private fun arr(): Json {
    val list = ArrayList<Json>()
    i++
    ws()
    if (s[i] == ']') { i++; return Json.Arr(list) }
    while (true) {
      list.add(value())
      ws()
      when (s[i]) {
        ',' -> i++
        ']' -> { i++; return Json.Arr(list) }
        else -> throw IllegalArgumentException("esperava , ou ] em $i")
      }
    }
  }
}
