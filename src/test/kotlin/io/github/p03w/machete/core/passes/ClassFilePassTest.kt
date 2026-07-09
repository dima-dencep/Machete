package io.github.p03w.machete.core.passes

import io.github.p03w.machete.config.MachetePluginExtension
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.objectweb.asm.*
import org.objectweb.asm.tree.ClassNode

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
    fun `strips source file attribute`() {
        extension.sourceFileStriping.enabled.set(true)
        extension.lvtStriping.enabled.set(false)

        val original = createTestClass()

        assertTrue(ClassFilePass.shouldRunOnFile("TestClass.class", extension, project.logger))
        val result = ClassFilePass.processFile("TestClass.class", original, extension, project.logger)

        println("CLASS TestClass.class (strip source): ${original.size} -> ${result.size} bytes (saved ${original.size - result.size})")

        val node = ClassNode()
        ClassReader(result).accept(node, 0)
        assertNull(node.sourceFile)
    }

    @Test
    fun `strips local variable table`() {
        extension.sourceFileStriping.enabled.set(false)
        extension.lvtStriping.enabled.set(true)

        val original = createTestClass()
        val result = ClassFilePass.processFile("TestClass.class", original, extension, project.logger)

        println("CLASS TestClass.class (strip LVT): ${original.size} -> ${result.size} bytes (saved ${original.size - result.size})")

        val node = ClassNode()
        ClassReader(result).accept(node, 0)
        node.methods.forEach { method ->
            assertTrue(method.localVariables == null || method.localVariables.isEmpty())
        }
    }

    @Test
    fun `ignores non-class files`() {
        assertFalse(ClassFilePass.shouldRunOnFile("test.txt", extension, project.logger))
    }
}
