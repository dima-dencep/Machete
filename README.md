# Machete

A Gradle plugin that optimizes the size of output JARs through individual file optimizations.
Inspired by the [Detonater](https://github.com/EnnuiL/Detonater) project. Fork of [P03W/Machete](https://github.com/P03W/Machete).

Applying the plugin gives you the `OptimizeJarsTask` task type and the `machete { }` configuration
block; you register the task yourself against exactly the jars you want optimized (see **Usage**).
Optimization runs fully in memory (jars are never extracted to disk) and is compatible with the
Gradle configuration cache.

**Works best on resource-heavy projects.** Code-heavy ones will see minimal improvement.

### Optimizations

- **JSON** — whitespace stripping via a custom formatter
- **XML** — whitespace and comment removal
- **TOML** — comment and blank line removal
- **PNG** — lossless optimization via [pngtastic](https://github.com/depsypher/pngtastic) (zopfli compressor)
- **Nested JARs** — unpacked and optimized recursively

Disabled by default (lossy):

- **LVT stripping** — removes Local Variable Table (breaks "helpful NPEs" in Java 14+)
- **Source file stripping** — removes SourceFile attribute (breaks file names in stack traces)

### Installation

Plugin ID: `org.redlance.dima_dencep.gradle.machete`

```kotlin
// settings.gradle.kts
pluginManagement {
    repositories {
        maven("https://repo.redlance.org/public")
        gradlePluginPortal()
    }
}
```

```kotlin
// build.gradle.kts
plugins {
    id("org.redlance.dima_dencep.gradle.machete") version "1.0.3"
}
```

### Usage

Register one `OptimizeJarsTask` per jar you want optimized — it takes one `input` jar and writes one
`output` jar — then wire it into your build:

```kotlin
// build.gradle.kts
import io.github.p03w.machete.tasks.OptimizeJarsTask

val optimizeJar = tasks.register<OptimizeJarsTask>("optimizeJar") {
    input.set(tasks.jar.flatMap { it.archiveFile })
    output.set(layout.buildDirectory.file("libs/${'$'}{project.name}-optimized.jar"))
}

// Run it whenever it fits your build, e.g. after `assemble`
tasks.named("assemble") { finalizedBy(optimizeJar) }
```

`dumpTasksWithOutputJars` prints every task that produces a jar, to help you decide what to wire in.

### Configuration

All options live directly on the task. Set them per task, or apply project-wide defaults to every
`OptimizeJarsTask` with `configureEach`:

```kotlin
// build.gradle.kts
import io.github.p03w.machete.tasks.OptimizeJarsTask
import io.github.p03w.machete.config.optimizations.PngConfig

tasks.withType<OptimizeJarsTask>().configureEach {
    // Preserve original file timestamps (default: false — constant timestamp for reproducibility)
    preserveFileTimestamps = true

    // Sort JAR entries so META-INF/MANIFEST.MF comes first (default: true)
    reproducibleFileOrder = false

    // Toggle individual optimizations (all enabled by default)
    json.enabled = false
    xml.enabled = false
    toml.enabled = false
    png.enabled = false
    jij.enabled = false

    // Lossy optimizations (disabled by default)
    lvtStriping.enabled = true
    sourceFileStriping.enabled = true

    // PNG options
    png.compressionLevel = 9       // null to try all levels and pick best
    png.removeGamma = true          // remove gamma correction info
    png.compressor = PngConfig.Compressor.ZOPFLI  // NONE or ZOPFLI (default)
    png.compressorIterations = 32

    // Extra file extensions to process (all optimization types support this)
    json.extraFileExtensions.add("mcmeta")
    png.extraFileExtensions.add("tga")
    jij.extraFileExtensions.add("zip")
}
```

To disable optimization for a build, disable the task the standard Gradle way
(`tasks.named("optimizeJar") { enabled = false }`).
