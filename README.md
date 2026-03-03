# Machete

A Gradle plugin that optimizes the size of output JARs through individual file optimizations.
Inspired by the [Detonater](https://github.com/EnnuiL/Detonater) project. Fork of [P03W/Machete](https://github.com/P03W/Machete).

Simply applying the plugin should be enough in a standard environment — it collects output JARs from known artifact-producing tasks and optimizes them after the `assemble` task.

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
    id("org.redlance.dima_dencep.gradle.machete") version "1.0.2"
}
```

### Configuration

All options are configured via the `machete` block:

```kotlin
machete {
    // Tasks whose output JARs to optimize (default: jar, remapJar, shadowJar)
    tasks.set(setOf("jar", "remapJar", "shadowJar"))

    // Disable the plugin entirely (e.g. for local builds)
    enabled = false

    // Keep original files alongside optimized ones
    keepOriginal = true

    // Preserve original file timestamps (default: false — constant timestamp for reproducibility)
    preserveFileTimestamps = true

    // Sort JAR entries so META-INF/MANIFEST.MF comes first (default: true)
    reproducibleFileOrder = false

    // Task to finalize after (default: "assemble", empty string to disable)
    finalizeAfter = ""

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
