package io.github.p03w.machete.core.libs.toml

class TomlMinifier(private val original: String) {
    override fun toString(): String {
        return original.lineSequence()
            .map { line ->
                val trimmed = line.trimEnd()
                val commentIndex = findCommentIndex(trimmed)
                if (commentIndex >= 0) trimmed.substring(0, commentIndex).trimEnd() else trimmed
            }
            .filter { it.isNotEmpty() }
            .joinToString("\n")
    }

    companion object {
        private fun findCommentIndex(line: String): Int {
            var inString = false
            var inLiteralString = false
            var i = 0
            while (i < line.length) {
                val c = line[i]
                when {
                    inLiteralString -> {
                        if (c == '\'') inLiteralString = false
                    }
                    inString -> {
                        if (c == '\\') { i++; i++; continue }
                        if (c == '"') inString = false
                    }
                    c == '"' -> inString = true
                    c == '\'' -> inLiteralString = true
                    c == '#' -> return i
                }
                i++
            }
            return -1
        }
    }
}
