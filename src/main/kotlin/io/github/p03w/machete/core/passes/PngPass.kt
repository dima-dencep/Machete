package io.github.p03w.machete.core.passes

import com.googlecode.pngtastic.core.PngImage
import com.googlecode.pngtastic.core.PngOptimizer
import io.github.p03w.machete.config.MachetePluginExtension
import io.github.p03w.machete.config.optimizations.PngConfig
import org.gradle.api.Project
import org.slf4j.Logger
import java.io.ByteArrayOutputStream
import java.io.File

object PngPass : JarOptimizationPass {
    override fun shouldRunOnFile(file: File, config: MachetePluginExtension, log: Logger): Boolean {
        val ext = file.extension
        return ext == "png" || config.png.extraFileExtensions.get().contains(ext)
    }

    override fun processFile(file: File, config: MachetePluginExtension, log: Logger, workDir: File, project: Project) {
        try {
            val originalBytes = file.readBytes()
            val image = PngImage(originalBytes)

            val optimizer = PngOptimizer()
            val compressor = config.png.compressor.get()
            if (compressor != PngConfig.Compressor.NONE) {
                optimizer.setCompressor(compressor.value, config.png.compressorIterations.orNull)
            }

            val optimized = optimizer.optimize(image, config.png.removeGamma.get(), config.png.compressionLevel.get())
            ByteArrayOutputStream().use { baos ->
                optimized.writeDataOutputStream(baos)
                val optimizedBytes = baos.toByteArray()

                if (optimizedBytes.size < originalBytes.size) {
                    file.writeBytes(optimizedBytes)
                }
            }
        } catch (err: Throwable) {
            log.warn("Failed to optimize ${file.relativeTo(workDir).path}")
            err.printStackTrace()
        }
    }
}