package io.github.p03w.machete.core.passes

import io.github.p03w.machete.config.MachetePluginExtension
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class TomlPassTest {
    private val project = ProjectBuilder.builder().build()
    private val extension = project.extensions.create("machete", MachetePluginExtension::class.java)

    @Test
    fun `minifies toml file`() {
        val original = "# comment\nkey = \"value\"\n\n# another\nnum = 42"

        assertTrue(TomlPass.shouldRunOnFile("test.toml", extension, project.logger))
        val result = TomlPass.processFile("test.toml", original.encodeToByteArray(), extension, project.logger).decodeToString()

        println("TOML test.toml: ${original.length} -> ${result.length} bytes (saved ${original.length - result.length})")
        assertEquals("key = \"value\"\nnum = 42", result)
    }

    @Test
    fun `does not overwrite file without comments`() {
        val content = "[section]\nkey = \"value\""

        val result = TomlPass.processFile("test.toml", content.encodeToByteArray(), extension, project.logger).decodeToString()

        println("TOML test.toml: ${content.length} -> ${result.length} bytes (already optimal)")
        assertEquals(content, result)
    }

    @Test
    fun `ignores non-toml files`() {
        assertFalse(TomlPass.shouldRunOnFile("test.txt", extension, project.logger))
    }
}
