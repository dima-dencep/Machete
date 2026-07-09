package io.github.p03w.machete.tasks

import io.github.p03w.machete.config.MacheteConfig
import io.github.p03w.machete.core.JarOptimizer
import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

/**
 * Optimizes a single jar ([input]) into another ([output]).
 *
 * All optimization settings are properties of this task (via [MacheteConfig]) — there is no separate
 * extension. Register one per jar you want optimized and set project-wide defaults through
 * `tasks.withType(OptimizeJarsTask::class).configureEach { }`:
 *
 * ```
 * val optimizeJar = tasks.register<OptimizeJarsTask>("optimizeJar") {
 *     input.set(tasks.jar.flatMap { it.archiveFile })
 *     output.set(layout.buildDirectory.file("libs/app-optimized.jar"))
 * }
 * ```
 */
@CacheableTask
abstract class OptimizeJarsTask : DefaultTask(), MacheteConfig {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val input: RegularFileProperty

    @get:OutputFile
    abstract val output: RegularFileProperty

    init {
        preserveFileTimestamps.convention(false)
        reproducibleFileOrder.convention(true)
    }

    @TaskAction
    fun optimize() {
        JarOptimizer(this, logger).optimize(input.get().asFile, output.get().asFile)
    }
}
