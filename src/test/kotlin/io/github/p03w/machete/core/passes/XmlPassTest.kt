package io.github.p03w.machete.core.passes

import io.github.p03w.machete.config.MachetePluginExtension
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class XmlPassTest {
    private val project = ProjectBuilder.builder().build()
    private val extension = project.extensions.create("machete", MachetePluginExtension::class.java)

    @Test
    fun `minifies xml file`(@TempDir workDir: File) {
        val file = workDir.resolve("test.xml")
        val original = "<root>  <!-- comment -->  <child/>  </root>"
        file.writeText(original)

        assertTrue(XmlPass.shouldRunOnFile(file, extension, project.logger))
        XmlPass.processFile(file, extension, project.logger, workDir, project)

        val result = file.readText()
        println("XML ${file.name}: ${original.length} -> ${result.length} bytes (saved ${original.length - result.length})")
        assertEquals("<root><child/></root>", result)
    }

    @Test
    fun `does not overwrite already minified file`(@TempDir workDir: File) {
        val file = workDir.resolve("test.xml")
        val minified = "<root><child/></root>"
        file.writeText(minified)

        XmlPass.processFile(file, extension, project.logger, workDir, project)

        val result = file.readText()
        println("XML ${file.name}: ${minified.length} -> ${result.length} bytes (already optimal)")
        assertEquals(minified, result)
    }

    @Test
    fun `ignores non-xml files`(@TempDir workDir: File) {
        val file = workDir.resolve("test.txt")
        assertFalse(XmlPass.shouldRunOnFile(file, extension, project.logger))
    }
}
