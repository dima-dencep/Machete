package io.github.p03w.machete.core.passes

import io.github.p03w.machete.tasks.OptimizeJarsTask
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class XmlPassTest {
    private val project = ProjectBuilder.builder().build()
    private val config = project.tasks.create("optimize", OptimizeJarsTask::class.java)

    @Test
    fun `minifies xml file`() {
        val original = "<root>  <!-- comment -->  <child/>  </root>"

        assertTrue(XmlPass.shouldRunOnFile("test.xml", config, project.logger))
        val result = XmlPass.processFile("test.xml", original.encodeToByteArray(), config, project.logger).decodeToString()

        println("XML test.xml: ${original.length} -> ${result.length} bytes (saved ${original.length - result.length})")
        assertEquals("<root><child/></root>", result)
    }

    @Test
    fun `does not overwrite already minified file`() {
        val minified = "<root><child/></root>"

        val result = XmlPass.processFile("test.xml", minified.encodeToByteArray(), config, project.logger).decodeToString()

        println("XML test.xml: ${minified.length} -> ${result.length} bytes (already optimal)")
        assertEquals(minified, result)
    }

    @Test
    fun `ignores non-xml files`() {
        assertFalse(XmlPass.shouldRunOnFile("test.txt", config, project.logger))
    }
}
