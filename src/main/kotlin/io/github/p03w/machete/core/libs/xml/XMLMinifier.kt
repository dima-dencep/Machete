package io.github.p03w.machete.core.libs.xml

class XMLMinifier(private val original: String) {
    override fun toString(): String {
        return emptyTagRegex.replace(commentRegex.replace(original, ""), "$1")
    }

    companion object {
        val commentRegex = Regex("<!--[\\s\\S]*?-->")
        val emptyTagRegex = Regex("(<.*?>)\\s*")
    }
}
