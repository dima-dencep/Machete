package io.github.p03w.machete.core.libs.xml

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class XMLMinifierTest {
    private fun minifyAndPrint(label: String, input: String): String {
        val result = XMLMinifier(input).toString()
        println("XML $label: ${input.length} -> ${result.length} bytes (saved ${input.length - result.length})")
        return result
    }

    @Test
    fun `removes whitespace between tags`() {
        val input = "<root>   <child>   text   </child>   </root>"
        assertEquals("<root><child>text   </child></root>", minifyAndPrint("whitespace", input))
    }

    @Test
    fun `removes xml comments`() {
        val input = "<root><!-- comment --><child/></root>"
        assertEquals("<root><child/></root>", minifyAndPrint("comments", input))
    }

    @Test
    fun `removes multiline comments`() {
        val input = """
            <root>
            <!-- multi
                 line
                 comment -->
            <child/>
            </root>
        """.trimIndent()
        val result = minifyAndPrint("multiline comments", input)
        assertFalse(result.contains("<!--"))
        assertTrue(result.contains("<root>"))
        assertTrue(result.contains("<child/>"))
    }

    @Test
    fun `removes whitespace and comments together`() {
        val input = "<root>  <!-- comment -->  <child/>  </root>"
        assertEquals("<root><child/></root>", minifyAndPrint("combined", input))
    }

    @Test
    fun `already minified xml unchanged`() {
        val input = "<root><child/></root>"
        assertEquals(input, minifyAndPrint("already minified", input))
    }
}
