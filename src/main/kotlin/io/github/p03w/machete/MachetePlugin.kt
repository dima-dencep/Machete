@file:Suppress("unused")

package io.github.p03w.machete

import io.github.p03w.machete.patches.patches
import io.github.p03w.machete.tasks.DumpTasksWithOutputJarsTask
import io.github.p03w.machete.tasks.OptimizeJarsTask
import org.gradle.api.Plugin
import org.gradle.api.Project

class MachetePlugin : Plugin<Project> {
    override fun apply(project: Project) {
        // Machete does not hook build tasks automatically, and there is no `machete { }` extension:
        // projects register their own OptimizeJarsTask(s) and configure them directly (or set
        // project-wide defaults via `tasks.withType(OptimizeJarsTask::class).configureEach { }`).
        project.tasks.withType(OptimizeJarsTask::class.java).configureEach { task ->
            task.group = "machete"
            task.description = "Optimizes a jar through per-file optimizations and stronger compression"

            // Compatibility patches, e.g. treat .mcmeta as JSON when a modding plugin is present
            patches.forEach { patch ->
                if (patch.shouldApply(project)) {
                    project.logger.info("Applying patch ${patch::class.simpleName} to ${task.name}")
                    patch.apply(task)
                }
            }
        }

        project.tasks.register("dumpTasksWithOutputJars", DumpTasksWithOutputJarsTask::class.java)
    }
}
