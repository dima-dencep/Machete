package io.github.p03w.machete.core.passes

import io.github.p03w.machete.config.MachetePluginExtension
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class JsonPassTest {
    private val project = ProjectBuilder.builder().build()
    private val extension = project.extensions.create("machete", MachetePluginExtension::class.java)

    @Test
    fun `minifies json file`() {
        val original = """{ "key" : "value" , "num" : 42 }"""

        assertTrue(JsonPass.shouldRunOnFile("test.json", extension, project.logger))
        val result = JsonPass.processFile("test.json", original.encodeToByteArray(), extension, project.logger).decodeToString()

        println("JSON test.json: ${original.length} -> ${result.length} bytes (saved ${original.length - result.length})")
        assertEquals("""{"key":"value","num":42}""", result)
    }

    @Test
    fun `does not overwrite already minified file`() {
        val minified = """{"key":"value"}"""

        val result = JsonPass.processFile("test.json", minified.encodeToByteArray(), extension, project.logger).decodeToString()

        println("JSON test.json: ${minified.length} -> ${result.length} bytes (already optimal)")
        assertEquals(minified, result)
    }

    @Test
    fun `ignores non-json files`() {
        assertFalse(JsonPass.shouldRunOnFile("test.txt", extension, project.logger))
    }
}
