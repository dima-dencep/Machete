package io.github.p03w.machete.patches

import io.github.p03w.machete.config.MacheteConfig
import org.gradle.api.Project

/**
 * A compatibility tweak applied to every [io.github.p03w.machete.tasks.OptimizeJarsTask] when the
 * surrounding project matches some condition (e.g. a modding plugin is present).
 */
interface Patch {
    fun shouldApply(project: Project): Boolean
    fun apply(config: MacheteConfig)
}
