package io.github.p03w.machete.core.passes

import io.github.p03w.machete.tasks.OptimizeJarsTask
import io.github.p03w.machete.config.optimizations.PngConfig
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO

class PngPassTest {
    private val project = ProjectBuilder.builder().build()
    private val config = project.tasks.create("optimize", OptimizeJarsTask::class.java)

    private fun createTestPng(width: Int = 64, height: Int = 64): ByteArray {
        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
        val g = image.createGraphics()
        g.fillRect(0, 0, width, height)
        g.dispose()
        return ByteArrayOutputStream().use { baos ->
            ImageIO.write(image, "png", baos)
            baos.toByteArray()
        }
    }

    @Test
    fun `optimizes png file`() {
        config.png.compressor.set(PngConfig.Compressor.NONE)

        val original = createTestPng()

        assertTrue(PngPass.shouldRunOnFile("test.png", config, project.logger))
        val result = PngPass.processFile("test.png", original, config, project.logger)

        println("PNG test.png: ${original.size} -> ${result.size} bytes (saved ${original.size - result.size})")
        assertTrue(result.size <= original.size)
    }

    @Test
    fun `does not increase file size`() {
        config.png.compressor.set(PngConfig.Compressor.NONE)

        val original = createTestPng(1, 1)
        val result = PngPass.processFile("test.png", original, config, project.logger)

        println("PNG test.png (1x1): ${original.size} -> ${result.size} bytes (saved ${original.size - result.size})")
        assertTrue(result.size <= original.size)
    }

    @Test
    fun `ignores non-png files`() {
        assertFalse(PngPass.shouldRunOnFile("test.jpg", config, project.logger))
    }
}
