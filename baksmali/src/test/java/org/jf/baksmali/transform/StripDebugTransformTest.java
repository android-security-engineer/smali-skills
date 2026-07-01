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

import com.google.common.collect.Iterables;
import org.jf.dexlib2.Opcode;
import org.jf.dexlib2.Opcodes;
import org.jf.dexlib2.iface.ClassDef;
import org.jf.dexlib2.iface.DexFile;
import org.jf.dexlib2.iface.Method;
import org.jf.dexlib2.iface.MethodImplementation;
import org.jf.dexlib2.iface.debug.DebugItem;
import org.jf.dexlib2.iface.instruction.Instruction;
import org.jf.dexlib2.immutable.ImmutableDexFile;
import org.jf.smali.SmaliTestUtils;
import org.junit.Assert;
import org.junit.Test;

import java.util.Collections;

/**
 * Tests for {@link StripDebugTransform}, behind {@code baksmali strip-debug}.
 */
public class StripDebugTransformTest {

    private DexFile compileFixture() throws Exception {
        String smali =
                ".class public Lcom/example/Debuggy;\n" +
                ".super Ljava/lang/Object;\n" +
                "\n" +
                ".method public static add(II)I\n" +
                "    .registers 4\n" +
                "    .param p0, \"a\"\n" +
                "    .param p1, \"b\"\n" +
                "    .line 10\n" +
                "    add-int v0, p0, p1\n" +
                "    .local v0, \"sum\":I\n" +
                "    .line 11\n" +
                "    return v0\n" +
                ".end method\n";
        ClassDef classDef = SmaliTestUtils.compileSmali(smali);
        return new ImmutableDexFile(Opcodes.forApi(15), Collections.singleton(classDef));
    }

    private Method theMethod(DexFile dexFile) {
        ClassDef classDef = dexFile.getClasses().iterator().next();
        return classDef.getMethods().iterator().next();
    }

    @Test
    public void fixture_hasDebugItemsBeforeStripping() throws Exception {
        MethodImplementation impl = theMethod(compileFixture()).getImplementation();
        Assert.assertNotNull(impl);
        Assert.assertTrue("fixture should carry debug items to make the test meaningful",
                Iterables.size(impl.getDebugItems()) > 0);
    }

    @Test
    public void apply_removesAllDebugItems() throws Exception {
        DexFile out = new StripDebugTransform().apply(compileFixture());
        MethodImplementation impl = theMethod(out).getImplementation();
        Assert.assertNotNull(impl);
        Assert.assertEquals(0, Iterables.size(impl.getDebugItems()));
    }

    @Test
    public void apply_preservesInstructions() throws Exception {
        DexFile before = compileFixture();
        int beforeCount = Iterables.size(theMethod(before).getImplementation().getInstructions());

        DexFile out = new StripDebugTransform().apply(compileFixture());
        MethodImplementation impl = theMethod(out).getImplementation();

        Assert.assertEquals("stripping debug must not touch executable bytecode",
                beforeCount, Iterables.size(impl.getInstructions()));

        boolean sawAddInt = false;
        for (Instruction instruction : impl.getInstructions()) {
            if (instruction.getOpcode() == Opcode.ADD_INT) {
                sawAddInt = true;
            }
        }
        Assert.assertTrue("the add-int instruction should remain", sawAddInt);
    }

    @Test
    public void apply_survivesMaterialization() throws Exception {
        DexFile rewritten = new StripDebugTransform().apply(compileFixture());
        DexFile materialized = new ImmutableDexFile(Opcodes.forApi(15), rewritten.getClasses());
        MethodImplementation impl = theMethod(materialized).getImplementation();
        Assert.assertNotNull(impl);
        Assert.assertEquals(0, Iterables.size(impl.getDebugItems()));
    }
}
