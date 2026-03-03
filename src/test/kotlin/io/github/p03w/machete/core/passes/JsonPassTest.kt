package io.github.p03w.machete.core.passes

import io.github.p03w.machete.config.MachetePluginExtension
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class JsonPassTest {
    private val project = ProjectBuilder.builder().build()
    private val extension = project.extensions.create("machete", MachetePluginExtension::class.java)

    @Test
    fun `minifies json file`(@TempDir workDir: File) {
        val file = workDir.resolve("test.json")
        val original = """{ "key" : "value" , "num" : 42 }"""
        file.writeText(original)

        assertTrue(JsonPass.shouldRunOnFile(file, extension, project.logger))
        JsonPass.processFile(file, extension, project.logger, workDir, project)

        val result = file.readText()
        println("JSON ${file.name}: ${original.length} -> ${result.length} bytes (saved ${original.length - result.length})")
        assertEquals("""{"key":"value","num":42}""", result)
    }

    @Test
    fun `does not overwrite already minified file`(@TempDir workDir: File) {
        val file = workDir.resolve("test.json")
        val minified = """{"key":"value"}"""
        file.writeText(minified)

        JsonPass.processFile(file, extension, project.logger, workDir, project)

        val result = file.readText()
        println("JSON ${file.name}: ${minified.length} -> ${result.length} bytes (already optimal)")
        assertEquals(minified, result)
    }

    @Test
    fun `ignores non-json files`(@TempDir workDir: File) {
        val file = workDir.resolve("test.txt")
        assertFalse(JsonPass.shouldRunOnFile(file, extension, project.logger))
    }
}
