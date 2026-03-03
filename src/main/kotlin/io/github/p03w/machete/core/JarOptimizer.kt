package io.github.p03w.machete.core

import io.github.p03w.machete.config.MachetePluginExtension
import io.github.p03w.machete.core.passes.*
import io.github.p03w.machete.util.allWithExtension
import io.github.p03w.machete.util.resolveAndMake
import io.github.p03w.machete.util.resolveAndMakeSiblingDir
import io.github.p03w.machete.util.unzip
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.gradle.api.Project
import java.io.File
import java.nio.file.Files
import java.util.GregorianCalendar
import java.util.jar.JarEntry
import java.util.jar.JarFile
import java.util.jar.JarOutputStream
import java.util.zip.Deflater
import java.util.zip.ZipInputStream

/**
 * Manages optimizing a jar
 */
class JarOptimizer(
    val workDir: File,
    val file: File,
    val config: MachetePluginExtension,
    val project: Project,
    val isChild: Boolean = false
) {
    private val children = mutableMapOf<String, File>()
    private val toIgnore = mutableListOf<String>()

    private val log = project.logger

    private val passes = buildList {
        if (config.png.enabled.get()) add(PngPass)
        if (config.json.enabled.get()) add(JsonPass)
        if (config.xml.enabled.get()) add(XmlPass)
        if (config.toml.enabled.get()) add(TomlPass)
        add(ClassFilePass)
    }

    fun unpack() {
        JarFile(file).use { jarFile ->
            jarFile.manifest?.entries?.forEach { (t, u) ->
                // File is signed! JVM will throw some nasty errors if we change this file at all and try to launch
                if (u.entries.find { it.key.toString().contains("Digest") } != null) {
                    toIgnore.add(t.split("/").last())
                    log.info("[${project.name}] Will skip file ${t.split("/").last()} as it is signed")
                }
            }
        }

        ZipInputStream(file.inputStream().buffered()).use {
            it.unzip(workDir)
        }
    }

    private fun optimizeJarInJar() {
        workDir.allWithExtension("jar", config.jij.extraFileExtensions.get(), toIgnore) { file ->
            val unpack =
                JarOptimizer(workDir.resolveAndMakeSiblingDir(file.nameWithoutExtension), file, config, project, true)
            unpack.unpack()
            unpack.optimize()

            val outJar = workDir.resolveAndMakeSiblingDir("tmpJars").resolveAndMake(file.name)

            unpack.repackTo(outJar)
            children[file.relativeTo(workDir).path] = outJar
        }
    }

    fun optimize() = runBlocking(Dispatchers.Default) {
        val files = workDir.walkBottomUp()
            .filter { it.isFile && !toIgnore.contains(it.name) && it.name !in OS_JUNK }
            .toList()

        files.map { file ->
            launch {
                passes.forEach { pass ->
                    if (pass.shouldRunOnFile(file, config, log)) {
                        pass.processFile(file, config, log, workDir, project)
                    }
                }
            }
        }.joinAll()

        if (config.jij.enabled.get()) optimizeJarInJar()
    }

    fun repackTo(file: File) {
        file.delete()
        val jar = JarOutputStream(file.outputStream().buffered())

        if (isChild) {
            jar.setLevel(Deflater.NO_COMPRESSION)
        } else {
            jar.setLevel(Deflater.BEST_COMPRESSION)
        }

        val stripTimestamps = !config.preserveFileTimestamps.get()

        jar.use {
            fun File.pathInJar(): String {
                return this.relativeTo(workDir).path.replace("\\", "/")
            }

            // .jars are handled by the children list, so that we can place them properly
            val files = workDir.walkBottomUp().toList().filter {
                it.isFile && it.name !in OS_JUNK && (it.extension != "jar" || !config.jij.enabled.get())
            }
            val sorted = if (config.reproducibleFileOrder.get()) {
                files.sortedWith(compareBy {
                    val path = it.relativeTo(workDir).path.replace("\\", "/")
                    when {
                        path.equals("META-INF/MANIFEST.MF", ignoreCase = true) -> 0
                        path.startsWith("META-INF/", ignoreCase = true) -> 1
                        else -> 2
                    }
                })
            } else files
            sorted.forEach { optimizedFile ->
                val entry = JarEntry(optimizedFile.pathInJar())
                if (stripTimestamps) {
                    entry.time = CONSTANT_TIMESTAMP
                } else {
                    entry.time = optimizedFile.lastModified()
                }
                jar.putNextEntry(entry)
                Files.copy(optimizedFile.toPath(), it)
                jar.closeEntry()
            }

            children.forEach { (path, childJar) ->
                val entry = JarEntry(path.replace("\\", "/"))
                if (stripTimestamps) entry.time = CONSTANT_TIMESTAMP
                jar.putNextEntry(entry)
                Files.copy(childJar.toPath(), it)
                jar.closeEntry()
            }
        }
    }

    companion object {
        // Feb 1, 1980 — same constant Gradle uses for reproducible builds
        val CONSTANT_TIMESTAMP = GregorianCalendar(1980, 1, 1, 0, 0, 0).timeInMillis

        // OS metadata files that should never end up in a JAR
        private val OS_JUNK = setOf(".DS_Store", "Thumbs.db", "desktop.ini")
    }
}
