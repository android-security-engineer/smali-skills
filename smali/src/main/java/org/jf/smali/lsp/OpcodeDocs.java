/*
 * Copyright 2024, the smali-skills fork.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 *
 *     * Redistributions of source code must retain the above copyright
 * notice, this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above
 * copyright notice, this list of conditions and the following disclaimer
 * in the documentation and/or other materials provided with the
 * distribution.
 *     * Neither the name of Google Inc. nor the names of its
 * contributors may be used to endorse or promote products derived from
 * this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
 * A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
 * OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 * THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package org.jf.smali.lsp;

import org.jf.dexlib2.Opcode;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;

/**
 * Provides human-readable hover documentation for smali opcodes and directives.
 *
 * <p>A curated map covers the most common opcode families with a one-line
 * description; anything else that dexlib2 recognizes as a real {@link Opcode}
 * gets a generic "valid Dalvik opcode" note so hover still works for the long
 * tail without hand-writing 200+ entries.
 */
public final class OpcodeDocs {
    private OpcodeDocs() {}

    private static final Map<String, String> CURATED = new HashMap<>();
    private static final Map<String, String> DIRECTIVES = new HashMap<>();

    static {
        // Moves & constants.
        CURATED.put("move", "Move the contents of one non-object register into another.");
        CURATED.put("move-wide", "Move a wide (64-bit) value between register pairs.");
        CURATED.put("move-object", "Move an object reference between registers.");
        CURATED.put("move-result", "Move the single-word result of the previous invoke/filled-new-array into a register.");
        CURATED.put("move-result-wide", "Move the wide result of the previous invoke into a register pair.");
        CURATED.put("move-result-object", "Move the object result of the previous invoke into a register.");
        CURATED.put("move-exception", "Save the just-caught exception into a register (first insn of a catch block).");
        CURATED.put("const", "Move a 32-bit literal into a register.");
        CURATED.put("const/4", "Move a 4-bit signed literal into a register.");
        CURATED.put("const/16", "Move a 16-bit signed literal into a register.");
        CURATED.put("const/high16", "Move a literal into the high 16 bits of a register (low bits zeroed).");
        CURATED.put("const-wide", "Move a 64-bit literal into a register pair.");
        CURATED.put("const-string", "Load a reference to a string constant into a register.");
        CURATED.put("const-class", "Load a reference to a Class object into a register.");

        // Object / array.
        CURATED.put("new-instance", "Construct a new, uninitialized instance of a class into a register.");
        CURATED.put("new-array", "Construct a new array of the given type and size.");
        CURATED.put("array-length", "Store the length of an array into a register.");
        CURATED.put("check-cast", "Throw ClassCastException unless the register holds an instance of the given type.");
        CURATED.put("instance-of", "Store 1 if the object is an instance of the given type, else 0.");
        CURATED.put("aget", "Load a 32-bit array element into a register.");
        CURATED.put("aput", "Store a register into a 32-bit array element.");
        CURATED.put("fill-array-data", "Fill an array with the static data pointed to by the payload label.");

        // Field access.
        CURATED.put("iget", "Read a 32-bit instance field into a register.");
        CURATED.put("iput", "Write a register into a 32-bit instance field.");
        CURATED.put("sget", "Read a 32-bit static field into a register.");
        CURATED.put("sput", "Write a register into a 32-bit static field.");

        // Invokes.
        CURATED.put("invoke-virtual", "Invoke a virtual method with normal virtual dispatch.");
        CURATED.put("invoke-super", "Invoke the superclass implementation of a virtual method.");
        CURATED.put("invoke-direct", "Invoke a non-static direct method (private or a constructor).");
        CURATED.put("invoke-static", "Invoke a static method.");
        CURATED.put("invoke-interface", "Invoke an interface method with interface dispatch.");
        CURATED.put("invoke-polymorphic", "Invoke a signature-polymorphic method (MethodHandle.invoke/invokeExact).");
        CURATED.put("invoke-custom", "Invoke a dynamically-linked call site produced by a bootstrap method.");

        // Control flow.
        CURATED.put("goto", "Unconditionally branch to the target label.");
        CURATED.put("if-eq", "Branch if two registers are equal.");
        CURATED.put("if-ne", "Branch if two registers are not equal.");
        CURATED.put("if-eqz", "Branch if a register equals zero/null.");
        CURATED.put("if-nez", "Branch if a register is non-zero/non-null.");
        CURATED.put("packed-switch", "Multi-way branch over a contiguous range of keys.");
        CURATED.put("sparse-switch", "Multi-way branch over an arbitrary set of keys.");
        CURATED.put("return", "Return a 32-bit value from the method.");
        CURATED.put("return-wide", "Return a 64-bit value from the method.");
        CURATED.put("return-object", "Return an object reference from the method.");
        CURATED.put("return-void", "Return from a void method.");
        CURATED.put("throw", "Throw the exception object held in a register.");
        CURATED.put("nop", "No operation.");

        // Directives.
        DIRECTIVES.put(".class", "Declares the class being defined, with its access flags and type descriptor.");
        DIRECTIVES.put(".super", "Declares the superclass of the current class.");
        DIRECTIVES.put(".implements", "Declares an interface implemented by the current class.");
        DIRECTIVES.put(".source", "Records the original source file name for debug info.");
        DIRECTIVES.put(".field", "Declares a field, with access flags, name, type and optional value.");
        DIRECTIVES.put(".method", "Begins a method definition; closed by .end method.");
        DIRECTIVES.put(".registers", "Declares the total number of registers used by the method.");
        DIRECTIVES.put(".locals", "Declares the number of local (non-parameter) registers.");
        DIRECTIVES.put(".param", "Names/annotates a method parameter register.");
        DIRECTIVES.put(".line", "Associates the following instructions with a source line number.");
        DIRECTIVES.put(".catch", "Declares a try/catch handler for a specific exception type.");
        DIRECTIVES.put(".catchall", "Declares a catch-all (finally-style) handler.");
        DIRECTIVES.put(".annotation", "Declares an annotation; closed by .end annotation.");
        DIRECTIVES.put(".prologue", "Marks the end of the method prologue for debug info.");
        DIRECTIVES.put(".array-data", "Payload of static array data for fill-array-data.");
        DIRECTIVES.put(".packed-switch", "Payload table for packed-switch.");
        DIRECTIVES.put(".sparse-switch", "Payload table for sparse-switch.");
    }

    /**
     * Returns Markdown hover text for {@code word}, or {@code null} if the word is
     * not a recognized opcode or directive.
     */
    @Nullable
    public static String lookup(@Nullable String word) {
        if (word == null || word.isEmpty()) {
            return null;
        }

        if (word.startsWith(".")) {
            String doc = DIRECTIVES.get(word);
            if (doc != null) {
                return "**" + word + "** (directive)\n\n" + doc;
            }
            return null;
        }

        String curated = CURATED.get(word);
        if (curated != null) {
            return "**" + word + "**\n\n" + curated;
        }

        // Fall back to the family root, so e.g. `iget-object`, `iget-wide` reuse
        // the `iget` blurb without a dedicated entry.
        int slash = word.indexOf('/');
        String base = slash >= 0 ? word.substring(0, slash) : word;
        int lastDash = base.lastIndexOf('-');
        if (lastDash > 0) {
            String family = CURATED.get(base.substring(0, lastDash));
            if (family != null) {
                return "**" + word + "**\n\n" + family;
            }
        }
        String rootDoc = CURATED.get(base);
        if (rootDoc != null) {
            return "**" + word + "**\n\n" + rootDoc;
        }

        // Last resort: is it any opcode dexlib2 knows about?
        if (isKnownOpcode(word)) {
            return "**" + word + "**\n\nA Dalvik bytecode opcode. See the "
                    + "[Dalvik bytecode reference]"
                    + "(https://source.android.com/devices/tech/dalvik/dalvik-bytecode.html).";
        }

        return null;
    }

    private static boolean isKnownOpcode(String word) {
        String enumName = word.toUpperCase().replace('-', '_').replace('/', '_');
        try {
            Opcode.valueOf(enumName);
            return true;
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }
}
