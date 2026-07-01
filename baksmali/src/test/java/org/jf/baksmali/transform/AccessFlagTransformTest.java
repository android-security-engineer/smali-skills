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

import org.jf.dexlib2.AccessFlags;
import org.jf.dexlib2.Opcodes;
import org.jf.dexlib2.iface.ClassDef;
import org.jf.dexlib2.iface.DexFile;
import org.jf.dexlib2.iface.Field;
import org.jf.dexlib2.iface.Method;
import org.jf.dexlib2.immutable.ImmutableDexFile;
import org.jf.smali.SmaliTestUtils;
import org.junit.Assert;
import org.junit.Test;

import java.util.Collections;

/**
 * Tests for {@link AccessFlagTransform}, the access-flag rewrite behind {@code baksmali unlock}.
 */
public class AccessFlagTransformTest {

    // -- pure flag arithmetic ------------------------------------------------

    @Test
    public void publicize_clearsPrivateAndProtected_setsPublic() {
        AccessFlagTransform t = new AccessFlagTransform(true, false);
        int privateFinal = AccessFlags.PRIVATE.getValue() | AccessFlags.FINAL.getValue();
        int result = t.rewriteFlags(privateFinal);

        Assert.assertTrue("public should be set", (result & AccessFlags.PUBLIC.getValue()) != 0);
        Assert.assertEquals("private should be cleared", 0, result & AccessFlags.PRIVATE.getValue());
        Assert.assertEquals("protected should be cleared", 0, result & AccessFlags.PROTECTED.getValue());
        Assert.assertTrue("final untouched when only publicizing",
                (result & AccessFlags.FINAL.getValue()) != 0);
    }

    @Test
    public void definalize_clearsFinal_leavesVisibility() {
        AccessFlagTransform t = new AccessFlagTransform(false, true);
        int protectedFinal = AccessFlags.PROTECTED.getValue() | AccessFlags.FINAL.getValue();
        int result = t.rewriteFlags(protectedFinal);

        Assert.assertEquals("final should be cleared", 0, result & AccessFlags.FINAL.getValue());
        Assert.assertTrue("protected preserved when only definalizing",
                (result & AccessFlags.PROTECTED.getValue()) != 0);
        Assert.assertEquals("public not forced", 0, result & AccessFlags.PUBLIC.getValue());
    }

    @Test
    public void both_publicizesAndDefinalizes() {
        AccessFlagTransform t = new AccessFlagTransform(true, true);
        int privateFinal = AccessFlags.PRIVATE.getValue() | AccessFlags.FINAL.getValue();
        int result = t.rewriteFlags(privateFinal);

        Assert.assertTrue((result & AccessFlags.PUBLIC.getValue()) != 0);
        Assert.assertEquals(0, result & AccessFlags.PRIVATE.getValue());
        Assert.assertEquals(0, result & AccessFlags.FINAL.getValue());
    }

    // -- end-to-end over a real dex model ------------------------------------

    private DexFile compileFixture() throws Exception {
        String smali =
                ".class public final Lcom/example/Locked;\n" +
                ".super Ljava/lang/Object;\n" +
                "\n" +
                ".field private final secret:I\n" +
                "\n" +
                ".method private final hidden()V\n" +
                "    .registers 1\n" +
                "    return-void\n" +
                ".end method\n";
        ClassDef classDef = SmaliTestUtils.compileSmali(smali);
        return new ImmutableDexFile(Opcodes.forApi(15), Collections.singleton(classDef));
    }

    @Test
    public void apply_publicizesAndDefinalizesEveryMember() throws Exception {
        DexFile out = new AccessFlagTransform(true, true).apply(compileFixture());

        ClassDef classDef = out.getClasses().iterator().next();
        Assert.assertTrue("class should be public",
                AccessFlags.PUBLIC.isSet(classDef.getAccessFlags()));
        Assert.assertFalse("class should not be final",
                AccessFlags.FINAL.isSet(classDef.getAccessFlags()));

        Field field = classDef.getFields().iterator().next();
        Assert.assertTrue("field should be public",
                AccessFlags.PUBLIC.isSet(field.getAccessFlags()));
        Assert.assertFalse("field should not be private",
                AccessFlags.PRIVATE.isSet(field.getAccessFlags()));
        Assert.assertFalse("field should not be final",
                AccessFlags.FINAL.isSet(field.getAccessFlags()));

        Method method = classDef.getMethods().iterator().next();
        Assert.assertTrue("method should be public",
                AccessFlags.PUBLIC.isSet(method.getAccessFlags()));
        Assert.assertFalse("method should not be private",
                AccessFlags.PRIVATE.isSet(method.getAccessFlags()));
        Assert.assertFalse("method should not be final",
                AccessFlags.FINAL.isSet(method.getAccessFlags()));
    }

    @Test
    public void apply_preservesMemberNamesAndStructure() throws Exception {
        DexFile out = new AccessFlagTransform(true, true).apply(compileFixture());
        ClassDef classDef = out.getClasses().iterator().next();

        Assert.assertEquals("Lcom/example/Locked;", classDef.getType());
        Assert.assertEquals("secret", classDef.getFields().iterator().next().getName());
        Assert.assertEquals("hidden", classDef.getMethods().iterator().next().getName());
    }
}
