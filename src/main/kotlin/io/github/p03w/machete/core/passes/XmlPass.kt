package io.github.p03w.machete.core.passes

import io.github.p03w.machete.config.MachetePluginExtension
import io.github.p03w.machete.core.libs.xml.XMLMinifier
import org.gradle.api.Project
import org.slf4j.Logger
import java.io.File

object XmlPass : JarOptimizationPass {
    override fun shouldRunOnFile(file: File, config: MachetePluginExtension, log: Logger): Boolean {
        val ext = file.extension
        return ext == "xml" || config.xml.extraFileExtensions.get().contains(ext)
    }

    override fun processFile(file: File, config: MachetePluginExtension, log: Logger, workDir: File, project: Project) {
        try {
            val original = file.readText()
            val minified = XMLMinifier(original).toString()
            if (minified.length < original.length) {
                file.writeText(minified)
            }
        } catch (err: Throwable) {
            log.warn("Failed to optimize ${file.relativeTo(workDir).path}")
            err.printStackTrace()
        }
    }
}