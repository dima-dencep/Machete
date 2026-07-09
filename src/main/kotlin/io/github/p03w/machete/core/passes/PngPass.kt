package io.github.p03w.machete.core.passes

import com.googlecode.pngtastic.core.PngImage
import com.googlecode.pngtastic.core.PngOptimizer
import io.github.p03w.machete.config.MacheteConfig
import io.github.p03w.machete.config.optimizations.PngConfig
import io.github.p03w.machete.util.entryExtension
import org.slf4j.Logger
import java.io.ByteArrayOutputStream
import java.util.concurrent.Semaphore

object PngPass : JarOptimizationPass {
    private val concurrencyLimit = Semaphore(2)

    override fun shouldRunOnFile(name: String, config: MacheteConfig, log: Logger): Boolean {
        val ext = name.entryExtension
        return ext == "png" || config.png.extraFileExtensions.get().contains(ext)
    }

    override fun processFile(name: String, bytes: ByteArray, config: MacheteConfig, log: Logger): ByteArray {
        concurrencyLimit.acquire()
        return try {
            val image = PngImage(bytes)

            val optimizer = PngOptimizer()
            val compressor = config.png.compressor.get()
            if (compressor != PngConfig.Compressor.NONE) {
                optimizer.setCompressor(compressor.value, config.png.compressorIterations.orNull)
            }

            val optimized = optimizer.optimize(image, config.png.removeGamma.get(), config.png.compressionLevel.get())
            ByteArrayOutputStream().use { baos ->
                optimized.writeDataOutputStream(baos)
                val optimizedBytes = baos.toByteArray()

                if (optimizedBytes.size < bytes.size) optimizedBytes else bytes
            }
        } catch (err: Throwable) {
            log.warn("Failed to optimize $name")
            err.printStackTrace()
            bytes
        } finally {
            concurrencyLimit.release()
        }
    }
}
