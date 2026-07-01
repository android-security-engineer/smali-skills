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

package org.jf.baksmali.diff;

import org.jf.dexlib2.Opcodes;
import org.jf.dexlib2.iface.ClassDef;
import org.jf.dexlib2.iface.DexFile;
import org.jf.dexlib2.immutable.ImmutableDexFile;
import org.jf.smali.SmaliTestUtils;
import org.junit.Assert;
import org.junit.Test;

import java.util.Collections;

/**
 * Tests for {@link DexDiff}, behind {@code baksmali diff}.
 */
public class DexDiffTest {

    private DexFile dexOf(String smali) throws Exception {
        ClassDef classDef = SmaliTestUtils.compileSmali(smali);
        return new ImmutableDexFile(Opcodes.forApi(15), Collections.singleton(classDef));
    }

    private static final String OLD =
            ".class public Lcom/example/A;\n" +
            ".super Ljava/lang/Object;\n" +
            ".method public foo()I\n" +
            "    .registers 1\n" +
            "    const/4 v0, 0x1\n" +
            "    return v0\n" +
            ".end method\n" +
            ".method public gone()V\n" +
            "    .registers 1\n" +
            "    return-void\n" +
            ".end method\n";

    @Test
    public void identical_hasNoDifferences() throws Exception {
        DexDiff diff = DexDiff.compute(dexOf(OLD), dexOf(OLD));
        Assert.assertTrue(diff.isEmpty());
        Assert.assertEquals("No semantic differences.\n", diff.toText());
    }

    @Test
    public void detectsAddedAndRemovedClasses() throws Exception {
        String newClass =
                ".class public Lcom/example/B;\n" +
                ".super Ljava/lang/Object;\n" +
                ".method public bar()V\n" +
                "    .registers 1\n" +
                "    return-void\n" +
                ".end method\n";
        DexDiff diff = DexDiff.compute(dexOf(OLD), dexOf(newClass));
        Assert.assertEquals(Collections.singletonList("Lcom/example/B;"), diff.getAddedClasses());
        Assert.assertEquals(Collections.singletonList("Lcom/example/A;"), diff.getRemovedClasses());
        Assert.assertTrue(diff.getChangedClasses().isEmpty());
    }

    @Test
    public void detectsChangedRemovedAndAddedMethods() throws Exception {
        // Same class type, but: foo body changes (const/4 0x1 -> 0x2), gone() removed, added() new.
        String modified =
                ".class public Lcom/example/A;\n" +
                ".super Ljava/lang/Object;\n" +
                ".method public foo()I\n" +
                "    .registers 1\n" +
                "    const/16 v0, 0x100\n" +
                "    return v0\n" +
                ".end method\n" +
                ".method public added()V\n" +
                "    .registers 1\n" +
                "    return-void\n" +
                ".end method\n";
        DexDiff diff = DexDiff.compute(dexOf(OLD), dexOf(modified));

        Assert.assertTrue(diff.getAddedClasses().isEmpty());
        Assert.assertTrue(diff.getRemovedClasses().isEmpty());
        Assert.assertEquals(1, diff.getChangedClasses().size());

        DexDiff.ClassDiff classDiff = diff.getChangedClasses().get(0);
        Assert.assertEquals("Lcom/example/A;", classDiff.type);
        Assert.assertEquals(Collections.singletonList("Lcom/example/A;->added()V"),
                classDiff.addedMethods);
        Assert.assertEquals(Collections.singletonList("Lcom/example/A;->gone()V"),
                classDiff.removedMethods);
        Assert.assertEquals(Collections.singletonList("Lcom/example/A;->foo()I"),
                classDiff.changedMethods);
    }

    @Test
    public void ignoresRegisterAndDebugNoise() throws Exception {
        // Same opcodes as OLD's foo(), but with an added .line (debug info) and different register
        // count — must NOT register as a change since the opcode sequence is identical.
        String noisy =
                ".class public Lcom/example/A;\n" +
                ".super Ljava/lang/Object;\n" +
                ".method public foo()I\n" +
                "    .registers 3\n" +
                "    .line 42\n" +
                "    const/4 v2, 0x1\n" +
                "    return v2\n" +
                ".end method\n" +
                ".method public gone()V\n" +
                "    .registers 1\n" +
                "    return-void\n" +
                ".end method\n";
        DexDiff diff = DexDiff.compute(dexOf(OLD), dexOf(noisy));
        Assert.assertTrue("register/debug-only changes should not be a semantic diff", diff.isEmpty());
    }

    @Test
    public void jsonIsWellFormed() throws Exception {
        String modified =
                ".class public Lcom/example/A;\n" +
                ".super Ljava/lang/Object;\n" +
                ".method public foo()I\n" +
                "    .registers 1\n" +
                "    const/16 v0, 0x100\n" +
                "    return v0\n" +
                ".end method\n" +
                ".method public gone()V\n" +
                "    .registers 1\n" +
                "    return-void\n" +
                ".end method\n";
        DexDiff diff = DexDiff.compute(dexOf(OLD), dexOf(modified));
        String json = diff.toJson();
        Assert.assertTrue(json.contains("\"changedClasses\""));
        Assert.assertTrue(json.contains("Lcom/example/A;->foo()I"));
        // Valid JSON object parse.
        new com.google.gson.JsonParser().parse(json).getAsJsonObject();
    }
}
