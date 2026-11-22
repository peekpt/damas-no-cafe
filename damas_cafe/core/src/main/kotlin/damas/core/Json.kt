// JSON mínimo — só o que o protocolo das mesas precisa, sem dependências.
package damas.core

sealed class Json {
  object Null : Json()
  data class Bool(val v: Boolean) : Json()
  data class Num(val v: Double) : Json()
  data class Str(val v: String) : Json()
  data class Arr(val v: List<Json>) : Json()
  data class Obj(val v: Map<String, Json>) : Json()

  fun get(key: String): Json = (this as? Obj)?.v?.get(key) ?: Null
  val items: List<Json> get() = (this as? Arr)?.v ?: emptyList()

  fun str(key: String, default: String = ""): String = (get(key) as? Str)?.v ?: default
  fun long(key: String, default: Long = 0): Long = (get(key) as? Num)?.v?.toLong() ?: default
  fun bool(key: String, default: Boolean = false): Boolean = (get(key) as? Bool)?.v ?: default
  fun has(key: String): Boolean = (this as? Obj)?.v?.keys?.contains(key) == true

  fun field(key: String, value: Json): Obj = Obj(campos() + (key to value))
  fun field(key: String, value: String?): Obj = field(key, if (value == null) Null else Str(value))
  fun field(key: String, value: Int): Obj = field(key, Num(value.toDouble()))
  fun field(key: String, value: Long): Obj = field(key, Num(value.toDouble()))
  fun field(key: String, value: Boolean): Obj = field(key, Bool(value))

  private fun campos(): Map<String, Json> = (this as? Obj)?.v ?: emptyMap()

  fun render(): String = StringBuilder().also { write(this, it) }.toString()

  companion object {
    val EMPTY_OBJ = Obj(emptyMap())

    fun obj(vararg pares: Pair<String, Json>) = Obj(pares.toMap())

    fun array(itens: List<Json>) = Arr(itens)

    private fun write(j: Json, out: StringBuilder) {
      when (j) {
        Null -> out.append("null")
        is Bool -> out.append(if (j.v) "true" else "false")
        is Num -> out.append(if (j.v == j.v.toLong().toDouble()) j.v.toLong().toString() else j.v.toString())
        is Str -> quote(j.v, out)
        is Arr -> {
          out.append('[')
          j.v.forEachIndexed { i, x -> if (i > 0) out.append(','); write(x, out) }
          out.append(']')
        }
        is Obj -> {
          out.append('{')
          j.v.entries.forEachIndexed { i, (k, x) ->
            if (i > 0) out.append(',')
            quote(k, out); out.append(':'); write(x, out)
          }
          out.append('}')
        }
      }
    }

    private fun quote(s: String, out: StringBuilder) {
      out.append('"')
      for (ch in s) when (ch) {
        '"' -> out.append("\\\"")
        '\\' -> out.append("\\\\")
        '\n' -> out.append("\\n")
        '\r' -> out.append("\\r")
        '\t' -> out.append("\\t")
        else -> if (ch < ' ') out.append("\\u").append(ch.code.toString(16).padStart(4, '0')) else out.append(ch)
      }
      out.append('"')
    }

    /** Devolve Null quando o texto não é JSON utilizável — os pedidos são todos tolerados. */
    fun parse(text: String?): Json = try {
      if (text.isNullOrBlank()) Null else JsonParser(text).parseDocument()
    } catch (e: Exception) {
      Null
    }
  }
}
