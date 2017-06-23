/**
 * black Magic.
 *
 * EOM
 */

package org.ddevec.slowpath.instr;

import rr.org.objectweb.asm.ClassReader;
import rr.org.objectweb.asm.ClassWriter;

public interface IVisitProvider {
    public abstract ClassWriter doVisit(ClassReader cr);
}

