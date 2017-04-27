package org.ddevec.slowpath;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.util.ArrayList;

/**
 * Class that copies a method to a new method, jumping back to the exact
 * location, and restoring all local variable state.
 */

public class CreateShadowMethod extends MethodVisitor implements Opcodes {
    public CreateShadowMethod(int api, MethodVisitor mv) {
        super(api, mv);
    }
}

