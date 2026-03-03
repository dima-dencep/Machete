package io.github.p03w.machete.config.optimizations

import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional

@Suppress("LeakingThis")
abstract class PngConfig {
    /**
     * A list of file extensions to also process as PNG files
     */
    @get:Input
    abstract val extraFileExtensions: ListProperty<String>

    @get:Input
    abstract val enabled: Property<Boolean>

    /**
     * Compression level for pngtastic (null to try all levels and pick the best)
     */
    @get:Input
    abstract val compressionLevel: Property<Int>

    /**
     * Whether to remove gamma correction information from the PNG
     */
    @get:Input
    abstract val removeGamma: Property<Boolean>

    @get:Input
    abstract val compressor: Property<Compressor>

    @get:Input
    @get:Optional
    abstract val compressorIterations: Property<Int>

    init {
        enabled.convention(true)
        compressionLevel.convention(9)
        removeGamma.convention(false)
        compressor.convention(Compressor.ZOPFLI)
        compressorIterations.convention(32)
    }

    @Suppress("unused")
    enum class Compressor(val value: String) {
        NONE(""),
        ZOPFLI("zopfli")
    }
}
