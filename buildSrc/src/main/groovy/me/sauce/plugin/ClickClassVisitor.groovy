package me.sauce.plugin

import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes
import org.objectweb.asm.commons.AdviceAdapter

/**
 * @author sauce
 * @since 2019/1/19
 */
class ClickClassVisitor extends ClassVisitor implements Opcodes {
    private String mClassName
    private String mSuperName
    private String mInterfaces
    private ClassVisitor classVisitor
    final String click = "me/sauce/asmdemo/ToastClickListener"
    final String Log = "android/util/Log"

    ClickClassVisitor(final ClassVisitor classVisitor) {
        super(Opcodes.ASM7, classVisitor)
        this.classVisitor = classVisitor
    }

    @Override
    void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
        mClassName = name
        mSuperName = superName
        mInterfaces = interfaces
        super.visit(version, access, name, signature, superName, interfaces)
        println("开始扫描类：${mClassName}")
    }

    @Override
    MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {

        MethodVisitor methodVisitor = cv.visitMethod(access, name, descriptor, signature, exceptions)
        String nameDesc = name + descriptor

        if (nameDesc == 'onClick(Landroid/view/View;)V') {
            println("插入！")
            methodVisitor = new ClickMethodVisitor(methodVisitor, access, name, descriptor)
        }
        return methodVisitor
    }

    @Override
    void visitEnd() {
        super.visitEnd()
        println("结束扫描类：${mClassName}\n")
    }

    class ClickMethodVisitor extends AdviceAdapter {
        String methodName
        MethodVisitor methodVisitor

        protected ClickMethodVisitor(MethodVisitor methodVisitor, int access, String name, String descriptor) {
            super(Opcodes.ASM7, methodVisitor, access, name, descriptor)
            methodName = name
            this.methodVisitor = methodVisitor
        }

        @Override
        protected void onMethodEnter() {
            super.onMethodEnter()
            methodVisitor.visitVarInsn(ALOAD, 1)
            methodVisitor.visitLdcInsn("log hook")
            methodVisitor.visitLdcInsn("start")
            methodVisitor.visitMethodInsn(INVOKESTATIC, Log, "d", "(Ljava/lang/String;Ljava/lang/String;)I", false)
        }

        @Override
        protected void onMethodExit(int opcode) {
            super.onMethodExit(opcode)
            methodVisitor.visitVarInsn(ALOAD, 1)
            methodVisitor.visitMethodInsn(INVOKESTATIC, click, "trackViewOnClick", "(Landroid/view/View;)V", false)
            methodVisitor.visitVarInsn(ALOAD, 1)
            methodVisitor.visitLdcInsn("log hook")
            methodVisitor.visitLdcInsn("end")
            methodVisitor.visitMethodInsn(INVOKESTATIC, Log, "d", "(Ljava/lang/String;Ljava/lang/String;)I", false)

        }

        @Override
        void visitEnd() {
            println("结束扫描方法：${methodName}\n")
            super.visitEnd()
        }
    }
}