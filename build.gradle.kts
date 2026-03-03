import org.jetbrains.kotlin.gradle.utils.extendsFrom

plugins {
    `java-gradle-plugin`
    kotlin("jvm") version "2.3.10"
    id("com.gradle.plugin-publish") version "2.0.0"
    id("com.gradleup.shadow") version "9.3.2"
}

group = "org.redlance.dima_dencep.gradle"
version = "1.0.2"
description = "A gradle plugin to optimize built jars through individual file optimizations and increased compression, works best on resource heavy jars"

//region Dependencies
repositories {
    mavenCentral()
}

configurations.testImplementation.extendsFrom(configurations.shadow)

dependencies {
    val asmVer = "9.9.1"
    shadow("org.ow2.asm:asm:$asmVer")
    shadow("org.ow2.asm:asm-tree:$asmVer")
    shadow("org.ow2.asm:asm-commons:$asmVer")
    shadow("com.github.depsypher:pngtastic:1.8")
    shadow("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")

    testImplementation(platform("org.junit:junit-bom:6.0.3"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
//endregion

//region Task Configure
tasks.shadowJar {
    configurations = listOf(
        project.configurations.getByName("shadow")
    )

    relocate("org.objectweb.asm", "s_m.asm")
    relocate("com.googlecode.pngtastic", "s_m.pngtastic")
    relocate("kotlinx.coroutines", "s_m.coroutines")

    minimize()
}

kotlin {
    jvmToolchain(17)
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        showStandardStreams = true
    }
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

publishing {
    repositories {
        maven {
            name = "RedlanceMinecraft"
            url = uri("https://repo.redlance.org/public")
            credentials {
                username = "dima_dencep"
                password = System.getenv("MAVEN_PASSWORD")
            }
        }
    }
}
//endregion
