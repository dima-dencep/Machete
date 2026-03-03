package io.github.p03w.machete.config.optimizations

import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input

@Suppress("LeakingThis")
abstract class TomlConfig {
    /**
     * A list of file extensions to also process as TOML files
     */
    @get:Input
    abstract val extraFileExtensions: ListProperty<String>

    @get:Input
    abstract val enabled: Property<Boolean>

    init {
        enabled.convention(true)
    }
}
