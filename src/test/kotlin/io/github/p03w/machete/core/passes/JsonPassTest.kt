package io.github.p03w.machete.core.passes

import io.github.p03w.machete.tasks.OptimizeJarsTask
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class JsonPassTest {
    private val project = ProjectBuilder.builder().build()
    private val config = project.tasks.create("optimize", OptimizeJarsTask::class.java)

    @Test
    fun `minifies json file`() {
        val original = """{ "key" : "value" , "num" : 42 }"""

        assertTrue(JsonPass.shouldRunOnFile("test.json", config, project.logger))
        val result = JsonPass.processFile("test.json", original.encodeToByteArray(), config, project.logger).decodeToString()

        println("JSON test.json: ${original.length} -> ${result.length} bytes (saved ${original.length - result.length})")
        assertEquals("""{"key":"value","num":42}""", result)
    }

    @Test
    fun `does not overwrite already minified file`() {
        val minified = """{"key":"value"}"""

        val result = JsonPass.processFile("test.json", minified.encodeToByteArray(), config, project.logger).decodeToString()

        println("JSON test.json: ${minified.length} -> ${result.length} bytes (already optimal)")
        assertEquals(minified, result)
    }

    @Test
    fun `ignores non-json files`() {
        assertFalse(JsonPass.shouldRunOnFile("test.txt", config, project.logger))
    }
}
