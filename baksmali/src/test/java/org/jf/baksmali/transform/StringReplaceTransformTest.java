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

import org.jf.dexlib2.Opcode;
import org.jf.dexlib2.Opcodes;
import org.jf.dexlib2.iface.ClassDef;
import org.jf.dexlib2.iface.DexFile;
import org.jf.dexlib2.iface.Method;
import org.jf.dexlib2.iface.MethodImplementation;
import org.jf.dexlib2.iface.instruction.Instruction;
import org.jf.dexlib2.iface.instruction.ReferenceInstruction;
import org.jf.dexlib2.iface.reference.StringReference;
import org.jf.dexlib2.immutable.ImmutableDexFile;
import org.jf.smali.SmaliTestUtils;
import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Tests for {@link StringReplaceTransform}, the string-constant rewrite behind {@code baksmali replace}.
 */
public class StringReplaceTransformTest {

    // -- pure replacement logic ----------------------------------------------

    @Test
    public void literal_replacesAllOccurrences() {
        StringReplaceTransform t = new StringReplaceTransform(
                Collections.singletonList(StringReplaceTransform.Rule.literal("a", "X")));
        Assert.assertEquals("XbXcX", t.replace("abaca"));
    }

    @Test
    public void literal_leavesNonMatchesUntouched() {
        StringReplaceTransform t = new StringReplaceTransform(
                Collections.singletonList(StringReplaceTransform.Rule.literal("zzz", "X")));
        Assert.assertEquals("hello", t.replace("hello"));
    }

    @Test
    public void literal_doesNotInterpretRegexMetacharacters() {
        StringReplaceTransform t = new StringReplaceTransform(
                Collections.singletonList(StringReplaceTransform.Rule.literal("a.c", "X")));
        // "a.c" as a literal only matches the exact substring, not "abc".
        Assert.assertEquals("abc-X", t.replace("abc-a.c"));
    }

    @Test
    public void regex_supportsCaptureGroups() {
        StringReplaceTransform t = new StringReplaceTransform(
                Collections.singletonList(StringReplaceTransform.Rule.regex("key_([0-9]+)", "id=$1")));
        Assert.assertEquals("id=42 id=7", t.replace("key_42 key_7"));
    }

    @Test
    public void rules_applyInOrder_chained() {
        StringReplaceTransform t = new StringReplaceTransform(Arrays.asList(
                StringReplaceTransform.Rule.literal("A", "B"),
                StringReplaceTransform.Rule.literal("B", "C")));
        // First rule turns A->B, second rule then turns every B (including the new one) into C.
        Assert.assertEquals("CC", t.replace("AB"));
    }

    // -- end-to-end over a real dex model ------------------------------------

    private DexFile compileFixture() throws Exception {
        String smali =
                ".class public Lcom/example/Strings;\n" +
                ".super Ljava/lang/Object;\n" +
                "\n" +
                ".field public static final URL:Ljava/lang/String; = \"http://old.example/api\"\n" +
                "\n" +
                ".method public static tag()Ljava/lang/String;\n" +
                "    .registers 1\n" +
                "    const-string v0, \"http://old.example/log\"\n" +
                "    return-object v0\n" +
                ".end method\n";
        ClassDef classDef = SmaliTestUtils.compileSmali(smali);
        return new ImmutableDexFile(Opcodes.forApi(15), Collections.singleton(classDef));
    }

    private List<String> constStrings(ClassDef classDef) {
        List<String> out = new ArrayList<>();
        for (Method method : classDef.getMethods()) {
            MethodImplementation impl = method.getImplementation();
            if (impl == null) {
                continue;
            }
            for (Instruction instruction : impl.getInstructions()) {
                if (instruction.getOpcode() == Opcode.CONST_STRING
                        || instruction.getOpcode() == Opcode.CONST_STRING_JUMBO) {
                    out.add(((StringReference) ((ReferenceInstruction) instruction).getReference())
                            .getString());
                }
            }
        }
        return out;
    }

    @Test
    public void apply_rewritesConstStringInstructions() throws Exception {
        StringReplaceTransform t = new StringReplaceTransform(Collections.singletonList(
                StringReplaceTransform.Rule.literal("old.example", "new.example")));
        DexFile out = t.apply(compileFixture());
        ClassDef classDef = out.getClasses().iterator().next();

        List<String> strings = constStrings(classDef);
        Assert.assertEquals(1, strings.size());
        Assert.assertEquals("http://new.example/log", strings.get(0));
    }

    @Test
    public void apply_rewritesStringEncodedValues() throws Exception {
        StringReplaceTransform t = new StringReplaceTransform(Collections.singletonList(
                StringReplaceTransform.Rule.literal("old.example", "new.example")));
        DexFile out = t.apply(compileFixture());
        ClassDef classDef = out.getClasses().iterator().next();

        org.jf.dexlib2.iface.value.EncodedValue initial =
                classDef.getFields().iterator().next().getInitialValue();
        Assert.assertNotNull(initial);
        String value = ((org.jf.dexlib2.iface.value.StringEncodedValue) initial).getValue();
        Assert.assertEquals("http://new.example/api", value);
    }

    @Test
    public void apply_survivesSerializationRoundTrip() throws Exception {
        // A rewritten dex must remain writable/re-readable. Re-wrap through ImmutableDexFile, which
        // fully materializes every element (exercising the rewritten getters).
        StringReplaceTransform t = new StringReplaceTransform(Collections.singletonList(
                StringReplaceTransform.Rule.literal("old.example", "new.example")));
        DexFile rewritten = t.apply(compileFixture());
        DexFile materialized = new ImmutableDexFile(Opcodes.forApi(15), rewritten.getClasses());

        ClassDef classDef = materialized.getClasses().iterator().next();
        Assert.assertEquals("http://new.example/log", constStrings(classDef).get(0));
    }
}
