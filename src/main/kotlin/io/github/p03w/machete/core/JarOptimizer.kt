package io.github.p03w.machete.core

import io.github.p03w.machete.config.MachetePluginExtension
import io.github.p03w.machete.core.passes.*
import io.github.p03w.machete.util.entryExtension
import io.github.p03w.machete.util.entryName
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.gradle.api.Project
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.OutputStream
import java.util.Collections
import java.util.GregorianCalendar
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream
import java.util.jar.Manifest
import java.util.zip.Deflater
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream

/**
 * Optimizes a jar without ever extracting its entries onto the filesystem.
 *
 * The top-level jar is processed as a **bounded stream**: entry names and order come from the
 * archive's central directory ([ZipFile]) without decompressing anything up front, entries are
 * optimized through a sliding window of at most [windowSize] workers, and results are written
 * straight into the output [JarOutputStream]. Peak heap therefore scales with the window size, not
 * with the size of the jar — important because this plugin targets large, resource-heavy jars.
 *
 * Because entries are keyed and written case-sensitively and never land on a (case-insensitive)
 * filesystem, entries whose names differ only in case — e.g. `a.class` and `A.class`, common in
 * obfuscated jars — are preserved instead of silently collapsing into one.
 *
 * Nested jars (jar-in-jar) are optimized in memory, one nested archive at a time; parallelism comes
 * from the outer window processing several entries — including several nested jars — at once.
 */
class JarOptimizer(
    private val config: MachetePluginExtension,
    private val project: Project,
) {
    private val log = project.logger

    private val passes = buildList {
        if (config.png.enabled.get()) add(PngPass)
        if (config.json.enabled.get()) add(JsonPass)
        if (config.xml.enabled.get()) add(XmlPass)
        if (config.toml.enabled.get()) add(TomlPass)
        add(ClassFilePass)
    }

    // How many entries may be in flight (read + optimized + awaiting write) at once. This — not the
    // jar size — is what bounds heap usage.
    private val windowSize = Runtime.getRuntime().availableProcessors().coerceAtLeast(2)

    private class OutEntry(val name: String, val bytes: ByteArray, val time: Long)

    fun optimize(input: File, output: File) {
        output.outputStream().buffered().use { optimize(input, it) }
    }

    /**
     * Optimizes [input], streaming the resulting jar to [output]. Holds only about [windowSize]
     * entries in memory at any moment.
     */
    fun optimize(input: File, output: OutputStream) {
        ZipFile(input).use { zip ->
            val files = Collections.list(zip.entries()).filter { !it.isDirectory }

            val manifestBytes = files
                .firstOrNull { it.name.equals(MANIFEST_PATH, ignoreCase = true) }
                ?.let { zip.getInputStream(it).use { stream -> stream.readBytes() } }
            val toIgnore = detectSignedFiles(manifestBytes)

            val ordered = order(files) { it.name }

            JarOutputStream(output).use { jarOut ->
                jarOut.setLevel(Deflater.BEST_COMPRESSION)
                streamThrough(ordered, toIgnore, jarOut) { entry ->
                    zip.getInputStream(entry).use { it.readBytes() }
                }
            }
        }
    }

    private fun streamThrough(
        ordered: List<ZipEntry>,
        toIgnore: Set<String>,
        jarOut: JarOutputStream,
        read: (ZipEntry) -> ByteArray
    ) = runBlocking(Dispatchers.Default) {
        val stripTimestamps = !config.preserveFileTimestamps.get()
        val window = ArrayDeque<Deferred<OutEntry>>()

        suspend fun writeNext() {
            val out = window.removeFirst().await()
            val jarEntry = JarEntry(out.name)
            jarEntry.time = if (stripTimestamps) CONSTANT_TIMESTAMP else out.time
            jarOut.putNextEntry(jarEntry)
            jarOut.write(out.bytes)
            jarOut.closeEntry()
        }

        for (entry in ordered) {
            val name = entry.name
            val simpleName = name.entryName
            if (simpleName in OS_JUNK) continue

            val time = entry.time
            val ignored = simpleName in toIgnore
            // ZipFile.getInputStream is safe for concurrent use, so the read can overlap with the
            // CPU-heavy passes running on other window slots.
            window.addLast(async {
                val bytes = read(entry)
                OutEntry(name, if (ignored) bytes else transform(name, bytes), time)
            })

            if (window.size >= windowSize) writeNext()
        }
        while (window.isNotEmpty()) writeNext()
    }

    /**
     * Runs every applicable pass over a single entry's bytes. Nested jars take precedence and are
     * recursed into instead. Safe to call concurrently — passes are stateless singletons.
     */
    private fun transform(name: String, bytes: ByteArray): ByteArray {
        if (config.jij.enabled.get() && isJarInJar(name)) {
            return optimizeNestedJar(bytes)
        }

        var result = bytes
        passes.forEach { pass ->
            if (pass.shouldRunOnFile(name, config, log)) {
                result = pass.processFile(name, result, config, log)
            }
        }
        return result
    }

    /**
     * Optimizes a nested jar entirely in memory (no temp files, no per-entry extraction) and returns
     * the repacked bytes. Entries are processed sequentially: parallelism across nested jars comes
     * from the caller's window, and avoiding nested coroutine dispatch keeps the worker pool free.
     */
    private fun optimizeNestedJar(bytes: ByteArray): ByteArray {
        val entries = LinkedHashMap<String, OutEntry>()
        ZipInputStream(bytes.inputStream()).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                if (!entry.isDirectory) {
                    // For exact-duplicate names the last one wins; case-different names stay distinct.
                    entries[entry.name] = OutEntry(entry.name, zip.readBytes(), entry.time)
                }
                entry = zip.nextEntry
            }
        }

        val manifestBytes = entries.values
            .firstOrNull { it.name.equals(MANIFEST_PATH, ignoreCase = true) }
            ?.bytes
        val toIgnore = detectSignedFiles(manifestBytes)

        val stripTimestamps = !config.preserveFileTimestamps.get()
        val ordered = order(entries.values.toList()) { it.name }

        return ByteArrayOutputStream().use { baos ->
            JarOutputStream(baos).use { jarOut ->
                jarOut.setLevel(Deflater.NO_COMPRESSION)
                ordered.forEach { entry ->
                    val simpleName = entry.name.entryName
                    if (simpleName in OS_JUNK) return@forEach

                    val out = if (simpleName in toIgnore) entry.bytes else transform(entry.name, entry.bytes)
                    val jarEntry = JarEntry(entry.name)
                    jarEntry.time = if (stripTimestamps) CONSTANT_TIMESTAMP else entry.time
                    jarOut.putNextEntry(jarEntry)
                    jarOut.write(out)
                    jarOut.closeEntry()
                }
            }
            baos.toByteArray()
        }
    }

    private fun isJarInJar(name: String): Boolean {
        if (name.entryExtension == "jar") return true
        val simpleName = name.entryName
        return config.jij.extraFileExtensions.get().any { simpleName.contains(it) }
    }

    private fun detectSignedFiles(manifestBytes: ByteArray?): Set<String> {
        if (manifestBytes == null) return emptySet()

        val ignore = mutableSetOf<String>()
        val manifest = Manifest(manifestBytes.inputStream())
        manifest.entries.forEach { (name, attributes) ->
            // File is signed! The JVM throws nasty errors if we touch a signed file at all
            if (attributes.keys.any { it.toString().contains("Digest") }) {
                val simpleName = name.entryName
                ignore.add(simpleName)
                log.info("[${project.name}] Will skip file $simpleName as it is signed")
            }
        }
        return ignore
    }

    private fun <T> order(items: List<T>, name: (T) -> String): List<T> {
        if (!config.reproducibleFileOrder.get()) return items
        // sortedWith is stable, so within-group order is preserved
        return items.sortedWith(compareBy {
            val path = name(it)
            when {
                path.equals(MANIFEST_PATH, ignoreCase = true) -> 0
                path.startsWith("META-INF/", ignoreCase = true) -> 1
                else -> 2
            }
        })
    }

    companion object {
        private const val MANIFEST_PATH = "META-INF/MANIFEST.MF"

        // Feb 1, 1980 — same constant Gradle uses for reproducible builds
        val CONSTANT_TIMESTAMP = GregorianCalendar(1980, 1, 1, 0, 0, 0).timeInMillis

        // OS metadata files that should never end up in a JAR
        private val OS_JUNK = setOf(".DS_Store", "Thumbs.db", "desktop.ini")
    }
}
