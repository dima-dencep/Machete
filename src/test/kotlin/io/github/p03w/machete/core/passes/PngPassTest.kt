package io.github.p03w.machete.core.passes

import io.github.p03w.machete.config.MachetePluginExtension
import io.github.p03w.machete.config.optimizations.PngConfig
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

class PngPassTest {
    private val project = ProjectBuilder.builder().build()
    private val extension = project.extensions.create("machete", MachetePluginExtension::class.java)

    private fun createTestPng(file: File, width: Int = 64, height: Int = 64) {
        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
        val g = image.createGraphics()
        g.fillRect(0, 0, width, height)
        g.dispose()
        ImageIO.write(image, "png", file)
    }

    @Test
    fun `optimizes png file`(@TempDir workDir: File) {
        extension.png.compressor.set(PngConfig.Compressor.NONE)

        val file = workDir.resolve("test.png")
        createTestPng(file)
        val originalSize = file.length()

        assertTrue(PngPass.shouldRunOnFile(file, extension, project.logger))
        PngPass.processFile(file, extension, project.logger, workDir, project)

        val newSize = file.length()
        println("PNG ${file.name}: $originalSize -> $newSize bytes (saved ${originalSize - newSize})")
        assertTrue(file.exists())
        assertTrue(newSize <= originalSize)
    }

    @Test
    fun `does not increase file size`(@TempDir workDir: File) {
        extension.png.compressor.set(PngConfig.Compressor.NONE)

        val file = workDir.resolve("test.png")
        createTestPng(file, 1, 1)
        val originalSize = file.length()

        PngPass.processFile(file, extension, project.logger, workDir, project)

        val newSize = file.length()
        println("PNG ${file.name} (1x1): $originalSize -> $newSize bytes (saved ${originalSize - newSize})")
        assertTrue(newSize <= originalSize)
    }

    @Test
    fun `ignores non-png files`(@TempDir workDir: File) {
        val file = workDir.resolve("test.jpg")
        assertFalse(PngPass.shouldRunOnFile(file, extension, project.logger))
    }
}
