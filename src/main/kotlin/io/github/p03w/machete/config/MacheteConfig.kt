package io.github.p03w.machete.config

import io.github.p03w.machete.config.optimizations.JIJConfig
import io.github.p03w.machete.config.optimizations.JsonConfig
import io.github.p03w.machete.config.optimizations.PngConfig
import io.github.p03w.machete.config.optimizations.TomlConfig
import io.github.p03w.machete.config.optimizations.XmlConfig
import io.github.p03w.machete.config.optimizations.lossy.LVTStripConfig
import io.github.p03w.machete.config.optimizations.lossy.SourceFileStripConfig
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Nested

/**
 * The optimization settings consumed by [io.github.p03w.machete.core.JarOptimizer].
 *
 * This is implemented directly by [io.github.p03w.machete.tasks.OptimizeJarsTask], so every option
 * is a property of the task itself — there is no separate extension. Project-wide defaults are set
 * the idiomatic way, via `tasks.withType(OptimizeJarsTask::class).configureEach { }`.
 *
 * The annotations here are honored by Gradle on the implementing task (annotations are inherited
 * through the type hierarchy), so the task does not need to redeclare them.
 */
interface MacheteConfig {
    /**
     * Whether to preserve original file timestamps in the output jar.
     * When false, all entries get a constant timestamp for reproducible builds.
     */
    @get:Input
    val preserveFileTimestamps: Property<Boolean>

    /**
     * Whether to sort JAR entries so that META-INF/MANIFEST.MF comes first.
     * This ensures the output JAR is readable by [java.util.jar.JarInputStream],
     * which expects the manifest as the first entry.
     */
    @get:Input
    val reproducibleFileOrder: Property<Boolean>

    @get:Nested
    val json: JsonConfig

    @get:Nested
    val png: PngConfig

    @get:Nested
    val jij: JIJConfig

    @get:Nested
    val xml: XmlConfig

    @get:Nested
    val toml: TomlConfig

    @get:Nested
    val lvtStriping: LVTStripConfig

    @get:Nested
    val sourceFileStriping: SourceFileStripConfig
}
