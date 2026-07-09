package io.github.p03w.machete.tasks

import io.github.p03w.machete.config.MachetePluginExtension
import io.github.p03w.machete.core.JarOptimizer
import org.gradle.api.DefaultTask
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.TaskAction
import java.nio.file.Files
import java.nio.file.StandardCopyOption

abstract class OptimizeJarsTask : DefaultTask() {
    @get:Nested
    abstract val extension: Property<MachetePluginExtension>

    @TaskAction
    fun optimizeJars() {
        val config = extension.get()
        inputs.files.forEach { file ->
            val optimizer = JarOptimizer(config, project)

            val target = if (config.keepOriginal.get()) {
                file.resolveSibling(file.nameWithoutExtension + "-optimized.jar")
            } else {
                file
            }

            // We stream straight from `file` (via ZipFile) and cannot write over it while reading, so
            // the result goes to a sibling temp file that is atomically moved into place afterwards.
            // A failure mid-optimization therefore leaves the original jar untouched.
            val tmp = file.resolveSibling(file.name + ".machete-tmp")
            try {
                optimizer.optimize(file, tmp)
                Files.move(tmp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
            } finally {
                tmp.delete()
            }
        }
    }
}
