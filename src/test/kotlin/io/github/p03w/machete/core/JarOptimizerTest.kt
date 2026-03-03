package io.github.p03w.machete.core

import io.github.p03w.machete.config.MachetePluginExtension
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.util.jar.JarEntry
import java.util.jar.JarFile
import java.util.jar.JarInputStream
import java.util.jar.JarOutputStream
import java.util.jar.Manifest

class JarOptimizerTest {
    private val project = ProjectBuilder.builder().build()
    private val extension = project.extensions.create("machete", MachetePluginExtension::class.java)

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

    @Test
    fun `preserves timestamps when preserveFileTimestamps is true`(@TempDir tempDir: File) {
        extension.preserveFileTimestamps.set(true)
        extension.png.enabled.set(false)

        val originalTimestamp = 1_700_000_000_000L // Nov 14, 2023
        val sourceJar = tempDir.resolve("input.jar")
        createTestJar(sourceJar, mapOf(
            "test.json" to Pair("""{"key":"value"}""".toByteArray(), originalTimestamp)
        ))

        val workDir = tempDir.resolve("work")
        workDir.mkdirs()

        val optimizer = JarOptimizer(workDir, sourceJar, extension, project)
        optimizer.unpack()
        optimizer.optimize()

        val outputJar = tempDir.resolve("output.jar")
        optimizer.repackTo(outputJar)

        JarFile(outputJar).use { jar ->
            val entry = jar.getJarEntry("test.json")
            assertNotNull(entry)
            println("preserveFileTimestamps=true: original=$originalTimestamp, result=${entry.time}")
            assertEquals(originalTimestamp, entry.time)
        }
    }

    @Test
    fun `uses constant timestamp when preserveFileTimestamps is false`(@TempDir tempDir: File) {
        extension.preserveFileTimestamps.set(false)
        extension.png.enabled.set(false)

        val sourceJar = tempDir.resolve("input.jar")
        createTestJar(sourceJar, mapOf(
            "a.json" to Pair("""{ "key" : "value" }""".toByteArray(), 1_700_000_000_000L),
            "b.json" to Pair("""{ "other" : "data" }""".toByteArray(), 1_600_000_000_000L)
        ))

        val workDir = tempDir.resolve("work")
        workDir.mkdirs()

        val optimizer = JarOptimizer(workDir, sourceJar, extension, project)
        optimizer.unpack()
        optimizer.optimize()

        val outputJar = tempDir.resolve("output.jar")
        optimizer.repackTo(outputJar)

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
        extension.preserveFileTimestamps.set(false)
        extension.png.enabled.set(false)

        val content = """{ "key" : "value" }""".toByteArray()

        // Build 1
        val sourceJar1 = tempDir.resolve("input1.jar")
        createTestJar(sourceJar1, mapOf("test.json" to Pair(content, 1_700_000_000_000L)))

        val workDir1 = tempDir.resolve("work1")
        workDir1.mkdirs()
        val optimizer1 = JarOptimizer(workDir1, sourceJar1, extension, project)
        optimizer1.unpack()
        optimizer1.optimize()
        val output1 = tempDir.resolve("output1.jar")
        optimizer1.repackTo(output1)

        // Build 2 — different source timestamp
        val sourceJar2 = tempDir.resolve("input2.jar")
        createTestJar(sourceJar2, mapOf("test.json" to Pair(content, 1_600_000_000_000L)))

        val workDir2 = tempDir.resolve("work2")
        workDir2.mkdirs()
        val optimizer2 = JarOptimizer(workDir2, sourceJar2, extension, project)
        optimizer2.unpack()
        optimizer2.optimize()
        val output2 = tempDir.resolve("output2.jar")
        optimizer2.repackTo(output2)

        val bytes1 = output1.readBytes()
        val bytes2 = output2.readBytes()
        println("Reproducibility: output1=${bytes1.size} bytes, output2=${bytes2.size} bytes, identical=${bytes1.contentEquals(bytes2)}")
        assertArrayEquals(bytes1, bytes2, "JARs with preserveFileTimestamps=false should be identical regardless of source timestamps")
    }

    @Test
    fun `MANIFEST_MF is first entry when reproducibleFileOrder is enabled`(@TempDir tempDir: File) {
        extension.preserveFileTimestamps.set(false)
        extension.reproducibleFileOrder.set(true)
        extension.png.enabled.set(false)

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

        val workDir = tempDir.resolve("work")
        workDir.mkdirs()

        val optimizer = JarOptimizer(workDir, sourceJar, extension, project)
        optimizer.unpack()
        optimizer.optimize()

        val outputJar = tempDir.resolve("output.jar")
        optimizer.repackTo(outputJar)

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
}
