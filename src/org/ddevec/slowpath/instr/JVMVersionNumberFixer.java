/******************************************************************************

	Copyright (c) 2010, Cormac Flanagan (University of California, Santa Cruz)
                    and Stephen Freund (Williams College) 

All rights reserved.  

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are
met:

 * Redistributions of source code must retain the above copyright
      notice, this list of conditions and the following disclaimer.

 * Redistributions in binary form must reproduce the above
      copyright notice, this list of conditions and the following
      disclaimer in the documentation and/or other materials provided
      with the distribution.

 * Neither the names of the University of California, Santa Cruz
      and Williams College nor the names of its contributors may be
      used to endorse or promote products derived from this software
      without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
"AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
(INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.

 ******************************************************************************/

package org.ddevec.slowpath.instr;
//package rr.instrument.classes;

import java.lang.reflect.Field;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;


/**
 * Some target programs (eg DaCapo benchmarks) may contain class files with an 
 * older version number, and those versions do not support some of the instruction
 * forms RR uses in the instrumentor.  That leads to verification errors.  
 * This class simply changes the version number embedded in old class files to 
 * the minimal version supporting what we need.
 */
public class JVMVersionNumberFixer extends ClassVisitor {

  public JVMVersionNumberFixer(int api, ClassVisitor cv) {
    super(api, cv);
  }

  @Override
  public void visit(int version, int access, String name, String signature,
      String superName, String[] interfaces) {
    /*
    System.err.println("    VERSION: " + (version&0xFFFF) + " 1.5: " +
        Opcodes.V1_5);
    */
    if ((version & 0xFFFF) < Opcodes.V1_5) {
      //System.err.println("~!~Updating to V1_5~!~");
      version = (version & ~0xFFFF) | Opcodes.V1_5;
    } else {
      //System.err.println("~!~ No Update Required ~!~");
    }
    super.visit(version, access, name, signature, superName, interfaces);
  }
}
