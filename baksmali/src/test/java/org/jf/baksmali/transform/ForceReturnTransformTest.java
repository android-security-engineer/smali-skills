/*
 * Copyright 2026, Google Inc.
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

package org.jf.baksmali.transform;

import com.google.common.collect.Lists;
import org.jf.dexlib2.Opcode;
import org.jf.dexlib2.Opcodes;
import org.jf.dexlib2.iface.ClassDef;
import org.jf.dexlib2.iface.DexFile;
import org.jf.dexlib2.iface.Method;
import org.jf.dexlib2.iface.MethodImplementation;
import org.jf.dexlib2.iface.instruction.Instruction;
import org.jf.dexlib2.immutable.ImmutableDexFile;
import org.jf.smali.SmaliTestUtils;
import org.junit.Assert;
import org.junit.Test;

import java.util.Collections;
import java.util.List;

/**
 * Tests for {@link ForceReturnTransform}, behind {@code baksmali patch}.
 */
public class ForceReturnTransformTest {

    // -- pure body synthesis -------------------------------------------------

    @Test
    public void buildReturnBody_void() {
        ForceReturnTransform t = new ForceReturnTransform(null, null,
                ForceReturnTransform.ReturnValue.VOID);
        MethodImplementation impl = t.buildReturnBody("V");
        List<Instruction> ins = Lists.newArrayList(impl.getInstructions());
        Assert.assertEquals(1, ins.size());
        Assert.assertEquals(Opcode.RETURN_VOID, ins.get(0).getOpcode());
        Assert.assertEquals(0, impl.getRegisterCount());
    }

    @Test
    public void buildReturnBody_booleanTrue() {
        ForceReturnTransform t = new ForceReturnTransform(null, null,
                ForceReturnTransform.ReturnValue.TRUE);
        MethodImplementation impl = t.buildReturnBody("Z");
        List<Instruction> ins = Lists.newArrayList(impl.getInstructions());
        Assert.assertEquals(2, ins.size());
        Assert.assertEquals(Opcode.CONST_4, ins.get(0).getOpcode());
        Assert.assertEquals(Opcode.RETURN, ins.get(1).getOpcode());
        Assert.assertTrue(impl.getRegisterCount() >= 1);
    }

    @Test
    public void buildReturnBody_objectNull() {
        ForceReturnTransform t = new ForceReturnTransform(null, null,
                ForceReturnTransform.ReturnValue.NULL);
        MethodImplementation impl = t.buildReturnBody("Ljava/lang/String;");
        List<Instruction> ins = Lists.newArrayList(impl.getInstructions());
        Assert.assertEquals(Opcode.CONST_4, ins.get(0).getOpcode());
        Assert.assertEquals(Opcode.RETURN_OBJECT, ins.get(1).getOpcode());
    }

    @Test
    public void buildReturnBody_wideLong() {
        ForceReturnTransform t = new ForceReturnTransform(null, null,
                ForceReturnTransform.ReturnValue.ZERO);
        MethodImplementation impl = t.buildReturnBody("J");
        List<Instruction> ins = Lists.newArrayList(impl.getInstructions());
        Assert.assertEquals(Opcode.CONST_WIDE, ins.get(0).getOpcode());
        Assert.assertEquals(Opcode.RETURN_WIDE, ins.get(1).getOpcode());
        Assert.assertEquals(2, impl.getRegisterCount());
    }

    @Test(expected = IllegalArgumentException.class)
    public void buildReturnBody_nullOnPrimitiveRejected() {
        new ForceReturnTransform(null, null, ForceReturnTransform.ReturnValue.NULL)
                .buildReturnBody("I");
    }

    @Test(expected = IllegalArgumentException.class)
    public void buildReturnBody_voidRequestedOnObjectRejected() {
        new ForceReturnTransform(null, null, ForceReturnTransform.ReturnValue.VOID)
                .buildReturnBody("Ljava/lang/String;");
    }

    @Test
    public void parseValue_recognizesAllForms() {
        Assert.assertEquals(ForceReturnTransform.ReturnValue.VOID,
                ForceReturnTransform.parseValue("void"));
        Assert.assertEquals(ForceReturnTransform.ReturnValue.TRUE,
                ForceReturnTransform.parseValue("TRUE"));
        Assert.assertEquals(ForceReturnTransform.ReturnValue.NULL,
                ForceReturnTransform.parseValue("null"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void parseValue_rejectsGarbage() {
        ForceReturnTransform.parseValue("banana");
    }

    // -- end-to-end over a real dex model ------------------------------------

    private DexFile compileFixture() throws Exception {
        String smali =
                ".class public Lcom/example/Guard;\n" +
                ".super Ljava/lang/Object;\n" +
                "\n" +
                ".method public isPremium()Z\n" +
                "    .registers 2\n" +
                "    const/4 v0, 0x0\n" +
                "    return v0\n" +
                ".end method\n" +
                "\n" +
                ".method public untouched()Z\n" +
                "    .registers 2\n" +
                "    const/4 v0, 0x0\n" +
                "    return v0\n" +
                ".end method\n";
        ClassDef classDef = SmaliTestUtils.compileSmali(smali);
        return new ImmutableDexFile(Opcodes.forApi(15), Collections.singleton(classDef));
    }

    private Method methodNamed(DexFile dexFile, String name) {
        ClassDef classDef = dexFile.getClasses().iterator().next();
        for (Method method : classDef.getMethods()) {
            if (method.getName().equals(name)) {
                return method;
            }
        }
        throw new AssertionError("method not found: " + name);
    }

    private int firstConstLiteral(Method method) {
        for (Instruction instruction : method.getImplementation().getInstructions()) {
            if (instruction.getOpcode() == Opcode.CONST_4) {
                return (int) ((org.jf.dexlib2.iface.instruction.NarrowLiteralInstruction) instruction)
                        .getNarrowLiteral();
            }
        }
        throw new AssertionError("no const/4 in method " + method.getName());
    }

    @Test
    public void apply_patchesOnlyMatchingMethod() throws Exception {
        ForceReturnTransform t = new ForceReturnTransform(null, "isPremium",
                ForceReturnTransform.ReturnValue.TRUE);
        Assert.assertEquals(1, t.countMatches(compileFixture()));

        DexFile out = t.apply(compileFixture());

        // isPremium now returns 1 (true).
        Assert.assertEquals(1, firstConstLiteral(methodNamed(out, "isPremium")));
        // untouched still returns its original 0.
        Assert.assertEquals(0, firstConstLiteral(methodNamed(out, "untouched")));
    }

    @Test
    public void apply_survivesMaterialization() throws Exception {
        ForceReturnTransform t = new ForceReturnTransform(null, "isPremium",
                ForceReturnTransform.ReturnValue.TRUE);
        DexFile rewritten = t.apply(compileFixture());
        DexFile materialized = new ImmutableDexFile(Opcodes.forApi(15), rewritten.getClasses());
        Assert.assertEquals(1, firstConstLiteral(methodNamed(materialized, "isPremium")));
    }
}
