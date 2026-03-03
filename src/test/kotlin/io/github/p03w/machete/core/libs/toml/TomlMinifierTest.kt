package io.github.p03w.machete.core.libs.toml

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class TomlMinifierTest {
    private fun minifyAndPrint(label: String, input: String): String {
        val result = TomlMinifier(input).toString()
        println("TOML $label: ${input.length} -> ${result.length} bytes (saved ${input.length - result.length})")
        return result
    }

    @Test
    fun `removes comment lines`() {
        val input = """
            # This is a comment
            key = "value"
        """.trimIndent()
        assertEquals("key = \"value\"", minifyAndPrint("comment lines", input))
    }

    @Test
    fun `removes inline comments`() {
        val input = """key = "value" # inline comment"""
        assertEquals("key = \"value\"", minifyAndPrint("inline comment", input))
    }

    @Test
    fun `preserves hash inside double-quoted strings`() {
        val input = """key = "value # not a comment" """
        assertEquals("key = \"value # not a comment\"", minifyAndPrint("hash in double quotes", input))
    }

    @Test
    fun `preserves hash inside single-quoted strings`() {
        val input = """key = 'value # not a comment' """
        assertEquals("key = 'value # not a comment'", minifyAndPrint("hash in single quotes", input))
    }

    @Test
    fun `removes blank lines`() {
        val input = "a = 1\n\n\nb = 2"
        assertEquals("a = 1\nb = 2", minifyAndPrint("blank lines", input))
    }

    @Test
    fun `removes trailing whitespace`() {
        val input = "key = \"value\"   "
        assertEquals("key = \"value\"", minifyAndPrint("trailing ws", input))
    }

    @Test
    fun `file without comments unchanged`() {
        val input = "[section]\nkey = \"value\""
        assertEquals(input, minifyAndPrint("no comments", input))
    }

    @Test
    fun `handles section headers with comments`() {
        val input = """
            [section] # section comment
            key = "value"
        """.trimIndent()
        assertEquals("[section]\nkey = \"value\"", minifyAndPrint("section with comment", input))
    }
}
