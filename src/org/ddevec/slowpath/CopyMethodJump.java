
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.util.ArrayList;

/**
 * Class that copies a method to a new method, jumping back to the exact
 * location, and restoring all local variable state.
 *
 *
 */

public class CopyMethodJump extends MethodVisitor implements Opcodes {
  int jumpIndex;

  public CopyMethodJump(int api, MethodVisitor mv,
      int jumpIndex) {
    super(api, mv);
    this.jumpIndex = jumpIndex;
  }

  // If we're visiting the to-be handled index, handle it
  @Override
  public void visitByteCodeIndex(int index) {
    if (index == jumpIndex) {
      // Okay... we copy in all the data here...
      // Ultimately, we need to construct 3 arguments:
      //    First -- the object arrays (arg0 and 1)
      //    Then, the bci (arg 3)
      // To do so, create an array of the objects
      // First, create a new "stack" array
      // First, load the size...
      //super.visitLdcInsn(stackVals.size());
      // Then, pop all stack elements into the "stack" array (construct objects
      //      containing them)
      //    NB: Keep "this" element (arg0)?

      // Now, load all locals and store them into the locals array

      // Finally, call the function with the arguments of: locals, stack, jump_idx
    }

    super.visitByteCodeIndex(index);
  }
}

