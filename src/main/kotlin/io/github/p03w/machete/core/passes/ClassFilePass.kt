package io.github.p03w.machete.core.passes

import io.github.p03w.machete.config.MacheteConfig
import io.github.p03w.machete.util.entryExtension
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.tree.ClassNode
import org.slf4j.Logger
import java.util.*

object ClassFilePass : JarOptimizationPass {
    private enum class StripData {
        LVT,
        SOURCE_FILE
    }

    override fun shouldRunOnFile(name: String, config: MacheteConfig, log: Logger): Boolean {
        return name.entryExtension == "class" && (
                config.sourceFileStriping.enabled.get() ||
                config.lvtStriping.enabled.get()
        )
    }

    override fun processFile(name: String, bytes: ByteArray, config: MacheteConfig, log: Logger): ByteArray {
        val toStrip = EnumSet.noneOf(StripData::class.java)
        if (config.lvtStriping.enabled.get())        toStrip.add(StripData.LVT)
        if (config.sourceFileStriping.enabled.get()) toStrip.add(StripData.SOURCE_FILE)

        if (toStrip.isEmpty()) return bytes

        val reader = ClassReader(bytes)

        val node = ClassNode()
        reader.accept(node, 0)

        if (toStrip.contains(StripData.SOURCE_FILE)) node.sourceFile = null

        if (toStrip.contains(StripData.LVT)) {
            node.methods.forEach {
                it.localVariables?.clear()
            }
        }

        val writer = ClassWriter(0)
        node.accept(writer)

        return writer.toByteArray()
    }
}
