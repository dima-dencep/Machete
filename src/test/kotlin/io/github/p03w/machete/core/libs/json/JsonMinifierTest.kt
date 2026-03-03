package io.github.p03w.machete.core.libs.json

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class JsonMinifierTest {
    private fun minifyAndPrint(label: String, input: String): String {
        val result = JsonMinifier(input).toString()
        println("JSON $label: ${input.length} -> ${result.length} bytes (saved ${input.length - result.length})")
        return result
    }

    @Test
    fun `minifies object with whitespace`() {
        val input = """
        {
            "key" : "value" ,
            "number" : 42
        }
        """.trimIndent()
        assertEquals("""{"key":"value","number":42}""", minifyAndPrint("object", input))
    }

    @Test
    fun `minifies array with whitespace`() {
        val input = """[ 1 , 2 , 3 ]"""
        assertEquals("[1,2,3]", minifyAndPrint("array", input))
    }

    @Test
    fun `minifies nested structures`() {
        val input = """
        {
            "arr" : [ 1 , { "nested" : true } ],
            "obj" : { "a" : "b" }
        }
        """.trimIndent()
        assertEquals("""{"arr":[1,{"nested":true}],"obj":{"a":"b"}}""", minifyAndPrint("nested", input))
    }

    @Test
    fun `preserves escape sequences in strings`() {
        val input = """{ "key" : "hello \"world\"" }"""
        assertEquals("""{"key":"hello \"world\""}""", minifyAndPrint("escapes", input))
    }

    @Test
    fun `handles boolean and null values`() {
        val input = """{ "a" : true , "b" : false , "c" : null }"""
        assertEquals("""{"a":true,"b":false,"c":null}""", minifyAndPrint("booleans", input))
    }

    @Test
    fun `handles empty object`() {
        assertEquals("{}", minifyAndPrint("empty object", "{}"))
    }

    @Test
    fun `handles empty array`() {
        assertEquals("[]", minifyAndPrint("empty array", "[]"))
    }

    @Test
    fun `handles negative and float numbers`() {
        val input = """[ -1 , 3.14 , 1e10 , -2.5E+3 ]"""
        assertEquals("[-1,3.14,1e10,-2.5E+3]", minifyAndPrint("numbers", input))
    }

    @Test
    fun `already minified json unchanged`() {
        val input = """{"a":1,"b":"c"}"""
        assertEquals(input, minifyAndPrint("already minified", input))
    }

    @Test
    fun `throws on invalid json`() {
        assertThrows<JsonFormatError> {
            JsonMinifier("not json").toString()
        }
    }
}
