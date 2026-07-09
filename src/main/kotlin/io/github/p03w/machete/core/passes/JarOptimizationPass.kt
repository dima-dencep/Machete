package io.github.p03w.machete.core.passes

import io.github.p03w.machete.config.MachetePluginExtension
import org.slf4j.Logger

/**
 * A single in-memory optimization step run against one jar entry.
 *
 * Passes never touch the filesystem: they receive the entry's raw bytes and return the optimized
 * bytes (or the input unchanged when no improvement was possible).
 */
interface JarOptimizationPass {
    fun shouldRunOnFile(name: String, config: MachetePluginExtension, log: Logger): Boolean
    fun processFile(name: String, bytes: ByteArray, config: MachetePluginExtension, log: Logger): ByteArray
}
