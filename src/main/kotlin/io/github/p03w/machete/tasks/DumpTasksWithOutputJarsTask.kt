package io.github.p03w.machete.tasks

import org.gradle.api.DefaultTask
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault

@DisableCachingByDefault(because = "Diagnostic task that only prints information")
abstract class DumpTasksWithOutputJarsTask : DefaultTask() {
    init {
        // This diagnostic task walks the whole project's task graph at execution time, which is
        // fundamentally incompatible with the configuration cache.
        notCompatibleWithConfigurationCache("Inspects every task's outputs at execution time")
    }

    @TaskAction
    fun dumpTasksWithOutputJars() {
        project.tasks.forEach { task ->
            val files = task.outputs.files.filter { it.extension == "jar" }
            if (files.isEmpty.not()) {
                val output = buildString {
                    appendLine(task.name)
                    files.map { it.path }.forEach {
                        appendLine("- $it")
                    }
                }
                println(output)
            }
        }
    }
}
