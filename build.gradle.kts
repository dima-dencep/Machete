plugins {
    `java-gradle-plugin`
    kotlin("jvm") version "2.3.10"
    id("com.gradle.plugin-publish") version "2.0.0"
    id("com.gradleup.shadow") version "9.3.2"
}

group = "org.redlance.dima_dencep.gradle"
version = "2.0.1"
description = "A gradle plugin to optimize built jars through individual file optimizations and increased compression, works best on resource heavy jars"

//region Dependencies
repositories {
    mavenCentral()
}

dependencies {
    val asmVer = "9.9.1"
    shadow("org.ow2.asm:asm:$asmVer")
    shadow("org.ow2.asm:asm-tree:$asmVer")
    shadow("org.ow2.asm:asm-commons:$asmVer")
}
//endregion

//region Task Configure
tasks.shadowJar {
    configurations = listOf(
        project.configurations.getByName("shadow")
    )

    relocate("org.ow2.asm", "s_m.ow2.asm")

    minimize()
}

kotlin {
    jvmToolchain(17)
}

tasks.withType<GenerateModuleMetadata> {
    enabled = false
}

tasks.shadowJar {
    archiveBaseName.set("machete")
    archiveClassifier.set("")
    archiveVersion.set(project.version.toString())
}
//endregion

//region Plugin Configure
gradlePlugin {
    website = "https://github.com/dima-dencep/Machete"
    vcsUrl = "https://github.com/dima-dencep/Machete"

    plugins {
        create("machetePlugin") {
            id = "org.redlance.dima_dencep.gradle.machete"
            displayName = "Machete"
            description = project.description
            tags = listOf("jar", "build", "jvm", "compress", "optimize")
            implementationClass = "io.github.p03w.machete.MachetePlugin"
        }
    }
}
//endregion
