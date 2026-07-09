package io.github.p03w.machete.core

import io.github.p03w.machete.tasks.OptimizeJarsTask
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.jar.JarEntry
import java.util.jar.JarFile
import java.util.jar.JarInputStream
import java.util.jar.JarOutputStream
import java.util.jar.Manifest

class JarOptimizerTest {
    private val project = ProjectBuilder.builder().build()

    // The task itself is the MacheteConfig; conventions come from its init block.
    private val config = project.tasks.create("optimize", OptimizeJarsTask::class.java)

    private fun createTestJar(file: File, entries: Map<String, Pair<ByteArray, Long>>) {
        JarOutputStream(file.outputStream().buffered()).use { jar ->
            entries.forEach { (name, pair) ->
                val (content, timestamp) = pair
                val entry = JarEntry(name)
                entry.time = timestamp
                jar.putNextEntry(entry)
                jar.write(content)
                jar.closeEntry()
            }
        }
    }

    private fun optimize(sourceJar: File, outputJar: File) {
        JarOptimizer(config, project.logger).optimize(sourceJar, outputJar)
    }

    @Test
    fun `preserves timestamps when preserveFileTimestamps is true`(@TempDir tempDir: File) {
        config.preserveFileTimestamps.set(true)
        config.png.enabled.set(false)

        val originalTimestamp = 1_700_000_000_000L // Nov 14, 2023
        val sourceJar = tempDir.resolve("input.jar")
        createTestJar(sourceJar, mapOf(
            "test.json" to Pair("""{"key":"value"}""".toByteArray(), originalTimestamp)
        ))

        val outputJar = tempDir.resolve("output.jar")
        optimize(sourceJar, outputJar)

        JarFile(outputJar).use { jar ->
            val entry = jar.getJarEntry("test.json")
            assertNotNull(entry)
            println("preserveFileTimestamps=true: original=$originalTimestamp, result=${entry.time}")
            assertEquals(originalTimestamp, entry.time)
        }
    }

    @Test
    fun `uses constant timestamp when preserveFileTimestamps is false`(@TempDir tempDir: File) {
        config.preserveFileTimestamps.set(false)
        config.png.enabled.set(false)

        val sourceJar = tempDir.resolve("input.jar")
        createTestJar(sourceJar, mapOf(
            "a.json" to Pair("""{ "key" : "value" }""".toByteArray(), 1_700_000_000_000L),
            "b.json" to Pair("""{ "other" : "data" }""".toByteArray(), 1_600_000_000_000L)
        ))

        val outputJar = tempDir.resolve("output.jar")
        optimize(sourceJar, outputJar)

        JarFile(outputJar).use { jar ->
            jar.entries().asSequence().forEach { entry ->
                println("preserveFileTimestamps=false: ${entry.name} timestamp=${entry.time} (constant=${JarOptimizer.CONSTANT_TIMESTAMP})")
                assertEquals(JarOptimizer.CONSTANT_TIMESTAMP, entry.time,
                    "Entry ${entry.name} should have constant timestamp")
            }
        }
    }

    @Test
    fun `reproducible builds produce identical jars`(@TempDir tempDir: File) {
        config.preserveFileTimestamps.set(false)
        config.png.enabled.set(false)

        val content = """{ "key" : "value" }""".toByteArray()

        // Build 1
        val sourceJar1 = tempDir.resolve("input1.jar")
        createTestJar(sourceJar1, mapOf("test.json" to Pair(content, 1_700_000_000_000L)))
        val output1 = tempDir.resolve("output1.jar")
        optimize(sourceJar1, output1)

        // Build 2 — different source timestamp
        val sourceJar2 = tempDir.resolve("input2.jar")
        createTestJar(sourceJar2, mapOf("test.json" to Pair(content, 1_600_000_000_000L)))
        val output2 = tempDir.resolve("output2.jar")
        optimize(sourceJar2, output2)

        val bytes1 = output1.readBytes()
        val bytes2 = output2.readBytes()
        println("Reproducibility: output1=${bytes1.size} bytes, output2=${bytes2.size} bytes, identical=${bytes1.contentEquals(bytes2)}")
        assertArrayEquals(bytes1, bytes2, "JARs with preserveFileTimestamps=false should be identical regardless of source timestamps")
    }

    @Test
    fun `MANIFEST_MF is first entry when reproducibleFileOrder is enabled`(@TempDir tempDir: File) {
        config.preserveFileTimestamps.set(false)
        config.reproducibleFileOrder.set(true)
        config.png.enabled.set(false)

        // Create a JAR where MANIFEST.MF is NOT the first entry
        val manifest = Manifest()
        manifest.mainAttributes.putValue("Manifest-Version", "1.0")
        manifest.mainAttributes.putValue("Main-Class", "com.example.Main")

        val sourceJar = tempDir.resolve("input.jar")
        JarOutputStream(sourceJar.outputStream().buffered()).use { jar ->
            // Write other entries first, before the manifest
            jar.putNextEntry(JarEntry("com/example/Main.class"))
            jar.write(byteArrayOf(0xCA.toByte(), 0xFE.toByte(), 0xBA.toByte(), 0xBE.toByte()))
            jar.closeEntry()

            jar.putNextEntry(JarEntry("data/config.json"))
            jar.write("""{"key":"value"}""".toByteArray())
            jar.closeEntry()

            // Write manifest last
            jar.putNextEntry(JarEntry("META-INF/MANIFEST.MF"))
            manifest.write(jar)
            jar.closeEntry()
        }

        val outputJar = tempDir.resolve("output.jar")
        optimize(sourceJar, outputJar)

        // Verify entry order: MANIFEST.MF should be first
        JarFile(outputJar).use { jar ->
            val entryNames = jar.entries().asSequence().map { it.name }.toList()
            println("Entry order: $entryNames")
            assertEquals("META-INF/MANIFEST.MF", entryNames.first(),
                "META-INF/MANIFEST.MF should be the first entry in the JAR")
        }

        // Verify JarInputStream can read the manifest
        JarInputStream(outputJar.inputStream().buffered()).use { jis ->
            assertNotNull(jis.manifest, "JarInputStream should be able to read the manifest")
            assertEquals("com.example.Main", jis.manifest.mainAttributes.getValue("Main-Class"))
        }
    }

    @Test
    fun `keeps entries whose names differ only in case`(@TempDir tempDir: File) {
        // Regression test: extracting a jar to a case-insensitive filesystem (macOS/Windows) used to
        // collapse entries like `a.class` and `A.class` — common in obfuscated jars — into one.
        // The in-memory optimizer must preserve both, with their content intact.
        config.preserveFileTimestamps.set(false)
        config.png.enabled.set(false)

        val lowerBytes = byteArrayOf(0xCA.toByte(), 0xFE.toByte(), 0xBA.toByte(), 0xBE.toByte(), 0x01)
        val upperBytes = byteArrayOf(0xCA.toByte(), 0xFE.toByte(), 0xBA.toByte(), 0xBE.toByte(), 0x02)

        val sourceJar = tempDir.resolve("input.jar")
        JarOutputStream(sourceJar.outputStream().buffered()).use { jar ->
            jar.putNextEntry(JarEntry("com/example/a.class"))
            jar.write(lowerBytes)
            jar.closeEntry()

            jar.putNextEntry(JarEntry("com/example/A.class"))
            jar.write(upperBytes)
            jar.closeEntry()
        }

        val outputJar = tempDir.resolve("output.jar")
        optimize(sourceJar, outputJar)

        // Read every entry back out, keyed case-sensitively
        val found = linkedMapOf<String, ByteArray>()
        JarInputStream(outputJar.inputStream().buffered()).use { jis ->
            var entry = jis.nextJarEntry
            while (entry != null) {
                if (!entry.isDirectory) found[entry.name] = jis.readBytes()
                entry = jis.nextJarEntry
            }
        }

        println("Case-sensitivity: recovered entries=${found.keys}")
        assertEquals(2, found.size, "Both case-variant entries must survive optimization")
        assertTrue(found.containsKey("com/example/a.class"), "lowercase a.class must survive")
        assertTrue(found.containsKey("com/example/A.class"), "uppercase A.class must survive")
        assertArrayEquals(lowerBytes, found["com/example/a.class"], "a.class content must be intact")
        assertArrayEquals(upperBytes, found["com/example/A.class"], "A.class content must be intact")
    }

    @Test
    fun `optimizes nested jars while preserving case-variant entries inside them`(@TempDir tempDir: File) {
        // jar-in-jar is enabled by default; a nested jar must be optimized (its json minified) yet
        // still round-trip both `a.class`/`A.class` variants.
        config.preserveFileTimestamps.set(false)
        config.png.enabled.set(false)

        val fatJson = """{ "key" : "value" }"""
        val lowerBytes = byteArrayOf(0xCA.toByte(), 0xFE.toByte(), 0xBA.toByte(), 0xBE.toByte(), 0x01)
        val upperBytes = byteArrayOf(0xCA.toByte(), 0xFE.toByte(), 0xBA.toByte(), 0xBE.toByte(), 0x02)

        // Build the inner jar in memory
        val innerBytes = ByteArrayOutputStream().use { baos ->
            JarOutputStream(baos.buffered()).use { jar ->
                jar.putNextEntry(JarEntry("data/config.json"))
                jar.write(fatJson.toByteArray())
                jar.closeEntry()
                jar.putNextEntry(JarEntry("pkg/a.class"))
                jar.write(lowerBytes)
                jar.closeEntry()
                jar.putNextEntry(JarEntry("pkg/A.class"))
                jar.write(upperBytes)
                jar.closeEntry()
            }
            baos.toByteArray()
        }

        val sourceJar = tempDir.resolve("outer.jar")
        JarOutputStream(sourceJar.outputStream().buffered()).use { jar ->
            jar.putNextEntry(JarEntry("META-INF/jars/inner.jar"))
            jar.write(innerBytes)
            jar.closeEntry()
        }

        val outputJar = tempDir.resolve("output.jar")
        optimize(sourceJar, outputJar)

        // Pull the (now optimized) nested jar back out of the output
        val optimizedInner = JarFile(outputJar).use { jar ->
            val entry = jar.getJarEntry("META-INF/jars/inner.jar")
            assertNotNull(entry, "nested jar must still be present")
            jar.getInputStream(entry).use { it.readBytes() }
        }
        println("JIJ: inner jar ${innerBytes.size} -> ${optimizedInner.size} bytes")

        val innerEntries = linkedMapOf<String, ByteArray>()
        JarInputStream(optimizedInner.inputStream()).use { jis ->
            var entry = jis.nextJarEntry
            while (entry != null) {
                if (!entry.isDirectory) innerEntries[entry.name] = jis.readBytes()
                entry = jis.nextJarEntry
            }
        }

        println("JIJ: recovered nested entries=${innerEntries.keys}")
        assertEquals("""{"key":"value"}""", innerEntries["data/config.json"]?.decodeToString(),
            "nested json should have been minified")
        assertTrue(innerEntries.containsKey("pkg/a.class"), "nested lowercase a.class must survive")
        assertTrue(innerEntries.containsKey("pkg/A.class"), "nested uppercase A.class must survive")
        assertArrayEquals(lowerBytes, innerEntries["pkg/a.class"], "nested a.class content must be intact")
        assertArrayEquals(upperBytes, innerEntries["pkg/A.class"], "nested A.class content must be intact")
    }
}
