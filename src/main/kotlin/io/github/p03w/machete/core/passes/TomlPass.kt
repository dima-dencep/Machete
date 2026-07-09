package io.github.p03w.machete.core.passes

import io.github.p03w.machete.config.MacheteConfig
import io.github.p03w.machete.core.libs.toml.TomlMinifier
import io.github.p03w.machete.util.entryExtension
import org.slf4j.Logger

object TomlPass : JarOptimizationPass {
    override fun shouldRunOnFile(name: String, config: MacheteConfig, log: Logger): Boolean {
        val ext = name.entryExtension
        return ext == "toml" || config.toml.extraFileExtensions.get().contains(ext)
    }

    override fun processFile(name: String, bytes: ByteArray, config: MacheteConfig, log: Logger): ByteArray {
        return try {
            val original = bytes.decodeToString()
            val minified = TomlMinifier(original).toString()
            if (minified.length < original.length) minified.encodeToByteArray() else bytes
        } catch (err: Throwable) {
            log.warn("Failed to optimize $name")
            err.printStackTrace()
            bytes
        }
    }
}
