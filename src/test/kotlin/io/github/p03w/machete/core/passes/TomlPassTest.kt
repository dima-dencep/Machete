package io.github.p03w.machete.core.passes

import io.github.p03w.machete.config.MachetePluginExtension
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class TomlPassTest {
    private val project = ProjectBuilder.builder().build()
    private val extension = project.extensions.create("machete", MachetePluginExtension::class.java)

    @Test
    fun `minifies toml file`(@TempDir workDir: File) {
        val file = workDir.resolve("test.toml")
        val original = "# comment\nkey = \"value\"\n\n# another\nnum = 42"
        file.writeText(original)

        assertTrue(TomlPass.shouldRunOnFile(file, extension, project.logger))
        TomlPass.processFile(file, extension, project.logger, workDir, project)

        val result = file.readText()
        println("TOML ${file.name}: ${original.length} -> ${result.length} bytes (saved ${original.length - result.length})")
        assertEquals("key = \"value\"\nnum = 42", result)
    }

    @Test
    fun `does not overwrite file without comments`(@TempDir workDir: File) {
        val file = workDir.resolve("test.toml")
        val content = "[section]\nkey = \"value\""
        file.writeText(content)

        TomlPass.processFile(file, extension, project.logger, workDir, project)

        val result = file.readText()
        println("TOML ${file.name}: ${content.length} -> ${result.length} bytes (already optimal)")
        assertEquals(content, result)
    }

    @Test
    fun `ignores non-toml files`(@TempDir workDir: File) {
        val file = workDir.resolve("test.txt")
        assertFalse(TomlPass.shouldRunOnFile(file, extension, project.logger))
    }
}
