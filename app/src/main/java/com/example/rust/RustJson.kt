package com.example.rust

/**
 * Dependency-free RFC 8259 JSON serializer and parser for the Kotlin <-> Rust JNI boundary.
 * Works identically on Android ART runtime, Robolectric, and plain JVM unit tests.
 */
object RustJson {
  fun encode(value: Any?): String = buildString { appendValue(value) }

  private fun StringBuilder.appendValue(value: Any?) {
    when (value) {
      null -> append("null")
      is Boolean -> append(if (value) "true" else "false")
      is Number -> append(value.toString())
      is String -> appendString(value)
      is Map<*, *> -> {
        append('{')
        var first = true
        for ((k, v) in value) {
          if (k == null) continue
          if (!first) append(',')
          first = false
          appendString(k.toString())
          append(':')
          appendValue(v)
        }
        append('}')
      }
      is Iterable<*> -> {
        append('[')
        var first = true
        for (item in value) {
          if (!first) append(',')
          first = false
          appendValue(item)
        }
        append(']')
      }
      is Array<*> -> {
        append('[')
        var first = true
        for (item in value) {
          if (!first) append(',')
          first = false
          appendValue(item)
        }
        append(']')
      }
      else -> appendString(value.toString())
    }
  }

  private fun StringBuilder.appendString(text: String) {
    append('"')
    for (ch in text) {
      when (ch) {
        '"' -> append("\\\"")
        '\\' -> append("\\\\")
        '\b' -> append("\\b")
        '\u000C' -> append("\\f")
        '\n' -> append("\\n")
        '\r' -> append("\\r")
        '\t' -> append("\\t")
        else -> {
          if (ch.code < 0x20) {
            append("\\u")
            append(ch.code.toString(16).padStart(4, '0'))
          } else {
            append(ch)
          }
        }
      }
    }
    append('"')
  }

  fun parseObject(json: String): Map<String, Any?> {
    val parser = Parser(json)
    val result = parser.parseValue()
    @Suppress("UNCHECKED_CAST")
    return result as? Map<String, Any?>
      ?: throw IllegalArgumentException("Expected JSON object, got: ${result?.javaClass?.simpleName}")
  }

  fun parseValue(json: String): Any? = Parser(json).parseValue()

  private class Parser(private val src: String) {
    private var idx = 0

    fun parseValue(): Any? {
      skipWhitespace()
      if (idx >= src.length) throw IllegalArgumentException("Unexpected end of JSON")
      return when (val ch = src[idx]) {
        '{' -> parseObj()
        '[' -> parseArr()
        '"' -> parseStr()
        't', 'f' -> parseBool()
        'n' -> parseNull()
        '-', in '0'..'9' -> parseNum()
        else -> throw IllegalArgumentException("Unexpected char '$ch' at index $idx")
      }
    }

    private fun parseObj(): Map<String, Any?> {
      idx++ // '{'
      val map = LinkedHashMap<String, Any?>()
      skipWhitespace()
      if (peek() == '}') {
        idx++
        return map
      }
      while (true) {
        skipWhitespace()
        val key = parseStr()
        skipWhitespace()
        expect(':')
        val value = parseValue()
        map[key] = value
        skipWhitespace()
        when (peek()) {
          ',' -> idx++
          '}' -> {
            idx++
            return map
          }
          else -> throw IllegalArgumentException("Expected ',' or '}' at index $idx")
        }
      }
    }

    private fun parseArr(): List<Any?> {
      idx++ // '['
      val list = ArrayList<Any?>()
      skipWhitespace()
      if (peek() == ']') {
        idx++
        return list
      }
      while (true) {
        list.add(parseValue())
        skipWhitespace()
        when (peek()) {
          ',' -> idx++
          ']' -> {
            idx++
            return list
          }
          else -> throw IllegalArgumentException("Expected ',' or ']' at index $idx")
        }
      }
    }

    private fun parseStr(): String {
      expect('"')
      val sb = StringBuilder()
      while (idx < src.length) {
        val ch = src[idx++]
        if (ch == '"') return sb.toString()
        if (ch == '\\') {
          if (idx >= src.length) throw IllegalArgumentException("Unterminated escape")
          when (val esc = src[idx++]) {
            '"' -> sb.append('"')
            '\\' -> sb.append('\\')
            '/' -> sb.append('/')
            'b' -> sb.append('\b')
            'f' -> sb.append('\u000C')
            'n' -> sb.append('\n')
            'r' -> sb.append('\r')
            't' -> sb.append('\t')
            'u' -> {
              if (idx + 4 > src.length) throw IllegalArgumentException("Invalid unicode escape")
              val code = src.substring(idx, idx + 4).toInt(16)
              sb.append(code.toChar())
              idx += 4
            }
            else -> throw IllegalArgumentException("Unknown escape '\\$esc'")
          }
        } else {
          sb.append(ch)
        }
      }
      throw IllegalArgumentException("Unterminated string")
    }

    private fun parseBool(): Boolean {
      return if (src.startsWith("true", idx)) {
        idx += 4
        true
      } else if (src.startsWith("false", idx)) {
        idx += 5
        false
      } else {
        throw IllegalArgumentException("Invalid boolean at $idx")
      }
    }

    private fun parseNull(): Any? {
      if (src.startsWith("null", idx)) {
        idx += 4
        return null
      }
      throw IllegalArgumentException("Invalid null at $idx")
    }

    private fun parseNum(): Number {
      val start = idx
      if (src[idx] == '-') idx++
      while (idx < src.length && src[idx].isDigit()) idx++
      var isFloat = false
      if (idx < src.length && src[idx] == '.') {
        isFloat = true
        idx++
        while (idx < src.length && src[idx].isDigit()) idx++
      }
      if (idx < src.length && (src[idx] == 'e' || src[idx] == 'E')) {
        isFloat = true
        idx++
        if (idx < src.length && (src[idx] == '+' || src[idx] == '-')) idx++
        while (idx < src.length && src[idx].isDigit()) idx++
      }
      val token = src.substring(start, idx)
      return if (isFloat) token.toDouble() else token.toLong()
    }

    private fun skipWhitespace() {
      while (idx < src.length && src[idx].isWhitespace()) idx++
    }

    private fun peek(): Char =
      if (idx < src.length) src[idx] else throw IllegalArgumentException("Unexpected EOF")

    private fun expect(expected: Char) {
      if (idx >= src.length || src[idx] != expected) {
        throw IllegalArgumentException("Expected '$expected' at index $idx")
      }
      idx++
    }
  }
}
