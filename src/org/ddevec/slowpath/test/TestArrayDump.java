package org.ddevec.slowpath.test;

import java.util.*;
import rr.org.objectweb.asm.*;
public class TestArrayDump implements Opcodes {

public static byte[] dump () throws Exception {

ClassWriter cw = new ClassWriter(0);
FieldVisitor fv;
MethodVisitor mv;
AnnotationVisitor av0;

cw.visit(V1_6, ACC_SUPER, "TestArray", null, "java/lang/Object", null);

{
fv = cw.visitField(ACC_STATIC, "size", "I", null, null);
fv.visitEnd();
}
{
mv = cw.visitMethod(0, "<init>", "()V", null, null);
mv.visitCode();
mv.visitVarInsn(ALOAD, 0);
mv.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
mv.visitInsn(RETURN);
mv.visitMaxs(1, 1);
mv.visitEnd();
}
{
mv = cw.visitMethod(ACC_PUBLIC + ACC_STATIC, "main", "([Ljava/lang/String;)V", null, null);
mv.visitCode();
mv.visitFieldInsn(GETSTATIC, "TestArray", "size", "I");
mv.visitTypeInsn(ANEWARRAY, "java/lang/Object");
mv.visitVarInsn(ASTORE, 1);
mv.visitInsn(ICONST_0);
mv.visitVarInsn(ISTORE, 2);
Label l0 = new Label();
mv.visitLabel(l0);
mv.visitFrame(Opcodes.F_APPEND,2, new Object[] {"[Ljava/lang/Object;", Opcodes.INTEGER}, 0, null);
mv.visitVarInsn(ILOAD, 2);
mv.visitFieldInsn(GETSTATIC, "TestArray", "size", "I");
Label l1 = new Label();
mv.visitJumpInsn(IF_ICMPGE, l1);
mv.visitVarInsn(ALOAD, 1);
mv.visitVarInsn(ILOAD, 2);
mv.visitTypeInsn(NEW, "java/lang/Integer");
mv.visitInsn(DUP);
mv.visitVarInsn(ILOAD, 2);
mv.visitMethodInsn(INVOKESPECIAL, "java/lang/Integer", "<init>", "(I)V", false);
mv.visitInsn(AASTORE);
mv.visitIincInsn(2, 1);
mv.visitJumpInsn(GOTO, l0);
mv.visitLabel(l1);
mv.visitFrame(Opcodes.F_CHOP,1, null, 0, null);
mv.visitInsn(RETURN);
mv.visitMaxs(5, 3);
mv.visitEnd();
}
{
mv = cw.visitMethod(ACC_STATIC, "<clinit>", "()V", null, null);
mv.visitCode();
mv.visitIntInsn(BIPUSH, 50);
mv.visitFieldInsn(PUTSTATIC, "TestArray", "size", "I");
mv.visitInsn(RETURN);
mv.visitMaxs(1, 0);
mv.visitEnd();
}
cw.visitEnd();

return cw.toByteArray();
}
}
