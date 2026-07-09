package io.github.p03w.machete

import org.gradle.testkit.runner.GradleRunner
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.util.jar.JarFile

/**
 * Drives the plugin inside a real Gradle build (via TestKit) to prove that a self-registered
 * [io.github.p03w.machete.tasks.OptimizeJarsTask] is compatible with the configuration cache and
 * actually optimizes its inputs.
 */
class ConfigurationCacheFunctionalTest {
    private fun writeSampleProject(dir: File) {
        dir.resolve("settings.gradle.kts").writeText("""rootProject.name = "sample"""")
        dir.resolve("build.gradle.kts").writeText(
            """
            import io.github.p03w.machete.tasks.OptimizeJarsTask

            plugins {
                java
                id("org.redlance.dima_dencep.gradle.machete")
            }

            // Projects wire up their own optimize task: one input jar -> one output jar.
            val optimizeJar = tasks.register<OptimizeJarsTask>("optimizeJar") {
                input.set(tasks.jar.flatMap { it.archiveFile })
                output.set(layout.buildDirectory.file("libs/sample-optimized.jar"))
            }

            tasks.named("assemble") { finalizedBy(optimizeJar) }
            """.trimIndent()
        )

        val resources = dir.resolve("src/main/resources")
        resources.mkdirs()
        // A minifiable resource so the task has something to actually optimize.
        resources.resolve("data.json").writeText("""{ "hello" : "world" , "n" : 1 }""")
    }

    private fun runner(dir: File) = GradleRunner.create()
        .withProjectDir(dir)
        .withPluginClasspath()
        .forwardOutput()

    @Test
    fun `optimize task supports the configuration cache`(@TempDir projectDir: File) {
        writeSampleProject(projectDir)

        val args = arrayOf("optimizeJar", "--configuration-cache", "--stacktrace")

        // First run stores the configuration cache; it fails here if the task graph has any
        // configuration-cache problems (they are errors by default).
        val first = runner(projectDir).withArguments(*args).build()
        assertTrue(
            first.output.contains("Configuration cache entry stored"),
            "First run should store a configuration cache entry:\n${first.output}"
        )

        // Second run must reuse it — proof there were no problems that invalidate the entry.
        val second = runner(projectDir).withArguments(*args).build()
        assertTrue(
            second.output.contains("Reusing configuration cache") ||
                second.output.contains("Configuration cache entry reused"),
            "Second run should reuse the configuration cache:\n${second.output}"
        )

        // Sanity check: the resource inside the optimized jar was actually minified.
        val jar = projectDir.resolve("build/libs/sample-optimized.jar")
        assertTrue(jar.exists(), "expected optimized jar at $jar")
        JarFile(jar).use { jf ->
            val entry = jf.getJarEntry("data.json")
            assertNotNull(entry, "data.json should be present in the jar")
            val content = jf.getInputStream(entry).use { it.readBytes().decodeToString() }
            assertEquals("""{"hello":"world","n":1}""", content, "json should have been minified")
        }
    }
}
