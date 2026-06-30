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

package org.jf.baksmali;

import org.jf.baksmali.formatter.BaksmaliFormatter;
import org.jf.dexlib2.iface.ClassDef;
import org.jf.dexlib2.iface.reference.FieldReference;
import org.jf.dexlib2.iface.reference.MethodReference;
import org.jf.dexlib2.iface.reference.TypeReference;
import org.jf.smali.SmaliTestUtils;
import org.junit.Assert;
import org.junit.Test;

import java.util.Collections;
import java.util.List;

/**
 * Tests for {@link ReferenceFinder}, the reverse cross-reference index that backs the
 * {@code baksmali xref} commands.
 */
public class ReferenceFinderTest {

    private static final String TARGET_CLASS_INIT = "Ljava/lang/Object;-><init>()V";

    private ClassDef compileFixture() throws Exception {
        // A single class with:
        //  - a constructor that calls Object.<init>
        //  - a getter and setter for an instance field
        //  - a method that news up a StringBuilder (type ref) and calls append (method ref)
        String smali =
                ".class public Lcom/example/XrefTarget;\n" +
                ".super Ljava/lang/Object;\n" +
                "\n" +
                ".field private value:Ljava/lang/String;\n" +
                "\n" +
                ".method public constructor <init>()V\n" +
                "    .registers 1\n" +
                "    invoke-direct {p0}, Ljava/lang/Object;-><init>()V\n" +
                "    return-void\n" +
                ".end method\n" +
                "\n" +
                ".method public getValue()Ljava/lang/String;\n" +
                "    .registers 1\n" +
                "    iget-object v0, p0, Lcom/example/XrefTarget;->value:Ljava/lang/String;\n" +
                "    return-object v0\n" +
                ".end method\n" +
                "\n" +
                ".method public setValue(Ljava/lang/String;)V\n" +
                "    .registers 2\n" +
                "    iput-object p1, p0, Lcom/example/XrefTarget;->value:Ljava/lang/String;\n" +
                "    return-void\n" +
                ".end method\n" +
                "\n" +
                ".method public describe()Ljava/lang/String;\n" +
                "    .registers 2\n" +
                "    new-instance v0, Ljava/lang/StringBuilder;\n" +
                "    invoke-direct {v0}, Ljava/lang/StringBuilder;-><init>()V\n" +
                "    const-string v1, \"x\"\n" +
                "    invoke-virtual {v0, v1}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;\n" +
                "    invoke-virtual {v0}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;\n" +
                "    move-result-object v0\n" +
                "    return-object v0\n" +
                ".end method\n";
        return SmaliTestUtils.compileSmali(smali);
    }

    @Test
    public void testFindsMethodCallers() throws Exception {
        ReferenceFinder finder = new ReferenceFinder(new BaksmaliFormatter());
        finder.index(Collections.singleton(compileFixture()));

        // Object.<init> is called by the constructor.
        List<ReferenceFinder.ReferenceSite> sites = finder.getSites(TARGET_CLASS_INIT);
        Assert.assertFalse("expected at least one caller of Object.<init>", sites.isEmpty());
        ReferenceFinder.ReferenceSite site = sites.get(0);
        Assert.assertEquals("Lcom/example/XrefTarget;-><init>()V", site.caller);
        Assert.assertTrue("caller should be a MethodReference",
                site.reference instanceof MethodReference);
    }

    @Test
    public void testFindsFieldReferences() throws Exception {
        ReferenceFinder finder = new ReferenceFinder(new BaksmaliFormatter());
        finder.index(Collections.singleton(compileFixture()));

        // The 'value' field is read by getValue and written by setValue.
        List<ReferenceFinder.ReferenceSite> sites =
                finder.getSites("Lcom/example/XrefTarget;->value:Ljava/lang/String;");
        Assert.assertEquals("expected 2 field access sites (read + write)", 2, sites.size());

        // First site: getValue reads at iget-object.
        Assert.assertEquals("Lcom/example/XrefTarget;->getValue()Ljava/lang/String;",
                sites.get(0).caller);
        Assert.assertTrue(sites.get(0).reference instanceof FieldReference);

        // Second site: setValue writes at iput-object.
        Assert.assertEquals("Lcom/example/XrefTarget;->setValue(Ljava/lang/String;)V",
                sites.get(1).caller);
        Assert.assertTrue(sites.get(1).reference instanceof FieldReference);
    }

    @Test
    public void testFindsTypeReferences() throws Exception {
        ReferenceFinder finder = new ReferenceFinder(new BaksmaliFormatter());
        finder.index(Collections.singleton(compileFixture()));

        // new-instance of StringBuilder should produce a type reference to Ljava/lang/StringBuilder;.
        List<ReferenceFinder.ReferenceSite> sites =
                finder.getSites("Ljava/lang/StringBuilder;");
        Assert.assertFalse("expected a type reference to StringBuilder", sites.isEmpty());
        ReferenceFinder.ReferenceSite site = sites.get(0);
        Assert.assertEquals("Lcom/example/XrefTarget;->describe()Ljava/lang/String;", site.caller);
        Assert.assertTrue(site.reference instanceof TypeReference);
    }

    @Test
    public void testUnknownTargetReturnsEmpty() throws Exception {
        ReferenceFinder finder = new ReferenceFinder(new BaksmaliFormatter());
        finder.index(Collections.singleton(compileFixture()));

        Assert.assertTrue(finder.getSites("Ldoes/NotExist;->nope()V").isEmpty());
    }

    @Test
    public void testGetTargetsContainsExpectedKeys() throws Exception {
        ReferenceFinder finder = new ReferenceFinder(new BaksmaliFormatter());
        finder.index(Collections.singleton(compileFixture()));

        List<String> targets = finder.getTargets();
        Assert.assertTrue("should contain Object.<init>", targets.contains(TARGET_CLASS_INIT));
        Assert.assertTrue("should contain the value field",
                targets.contains("Lcom/example/XrefTarget;->value:Ljava/lang/String;"));
        Assert.assertTrue("should contain StringBuilder.<init>",
                targets.contains("Ljava/lang/StringBuilder;-><init>()V"));
        Assert.assertTrue("should contain StringBuilder type",
                targets.contains("Ljava/lang/StringBuilder;"));
    }

    @Test
    public void testCodeOffsetAdvancesByInstructionSize() throws Exception {
        ReferenceFinder finder = new ReferenceFinder(new BaksmaliFormatter());
        finder.index(Collections.singleton(compileFixture()));

        // In setValue, the iput-object is the first instruction, so its offset is 0x0.
        List<ReferenceFinder.ReferenceSite> sites =
                finder.getSites("Lcom/example/XrefTarget;->value:Ljava/lang/String;");
        ReferenceFinder.ReferenceSite writeSite = sites.get(1); // setValue
        Assert.assertEquals(0, writeSite.codeOffset);
    }
}
