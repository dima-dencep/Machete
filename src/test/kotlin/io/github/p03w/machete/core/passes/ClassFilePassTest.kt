package io.github.p03w.machete.core.passes

import io.github.p03w.machete.config.MachetePluginExtension
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.objectweb.asm.*
import org.objectweb.asm.tree.ClassNode
import java.io.File

class ClassFilePassTest {
    private val project = ProjectBuilder.builder().build()
    private val extension = project.extensions.create("machete", MachetePluginExtension::class.java)

    private fun createTestClass(): ByteArray {
        val cw = ClassWriter(ClassWriter.COMPUTE_FRAMES)
        cw.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, "TestClass", null, "java/lang/Object", null)
        cw.visitSource("TestClass.java", null)

        val mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "test", "()V", null, null)
        mv.visitCode()

        val start = Label()
        val end = Label()
        mv.visitLabel(start)
        mv.visitVarInsn(Opcodes.ALOAD, 0)
        mv.visitInsn(Opcodes.POP)
        mv.visitInsn(Opcodes.RETURN)
        mv.visitLabel(end)

        mv.visitLocalVariable("this", "LTestClass;", null, start, end, 0)
        mv.visitMaxs(1, 1)
        mv.visitEnd()

        cw.visitEnd()
        return cw.toByteArray()
    }

    @Test
    fun `strips source file attribute`(@TempDir workDir: File) {
        extension.sourceFileStriping.enabled.set(true)
        extension.lvtStriping.enabled.set(false)

        val file = workDir.resolve("TestClass.class")
        val original = createTestClass()
        file.writeBytes(original)

        assertTrue(ClassFilePass.shouldRunOnFile(file, extension, project.logger))
        ClassFilePass.processFile(file, extension, project.logger, workDir, project)

        val result = file.readBytes()
        println("CLASS ${file.name} (strip source): ${original.size} -> ${result.size} bytes (saved ${original.size - result.size})")

        val node = ClassNode()
        ClassReader(result).accept(node, 0)
        assertNull(node.sourceFile)
    }

    @Test
    fun `strips local variable table`(@TempDir workDir: File) {
        extension.sourceFileStriping.enabled.set(false)
        extension.lvtStriping.enabled.set(true)

        val file = workDir.resolve("TestClass.class")
        val original = createTestClass()
        file.writeBytes(original)

        ClassFilePass.processFile(file, extension, project.logger, workDir, project)

        val result = file.readBytes()
        println("CLASS ${file.name} (strip LVT): ${original.size} -> ${result.size} bytes (saved ${original.size - result.size})")

        val node = ClassNode()
        ClassReader(result).accept(node, 0)
        node.methods.forEach { method ->
            assertTrue(method.localVariables == null || method.localVariables.isEmpty())
        }
    }

    @Test
    fun `ignores non-class files`(@TempDir workDir: File) {
        val file = workDir.resolve("test.txt")
        assertFalse(ClassFilePass.shouldRunOnFile(file, extension, project.logger))
    }
}
