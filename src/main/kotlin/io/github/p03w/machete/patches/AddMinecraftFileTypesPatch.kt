package io.github.p03w.machete.patches

import io.github.p03w.machete.config.MacheteConfig
import org.gradle.api.Project

object AddMinecraftFileTypesPatch : Patch {
    private val minecraftPlugins = setOf(
        "net.minecraftforge.gradle",
        "fabric-loom",
        "org.quiltmc.loom",
        "net.neoforged.moddev",
        "xyz.wagyourtail.unimined",
        "net.fabricmc.fabric-loom-remap",
        "net.fabricmc.fabric-loom"
    )

    override fun shouldApply(project: Project): Boolean {
        return minecraftPlugins.any {
            project.plugins.hasPlugin(it)
        }
    }

    override fun apply(config: MacheteConfig) {
        config.json.extraFileExtensions.add("mcmeta")
    }
}
