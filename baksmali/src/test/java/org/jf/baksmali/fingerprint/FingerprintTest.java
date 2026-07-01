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

package org.jf.baksmali.fingerprint;

import com.google.common.collect.Iterables;
import org.jf.dexlib2.iface.ClassDef;
import org.jf.dexlib2.iface.Method;
import org.jf.smali.SmaliTestUtils;
import org.junit.Assert;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Tests for {@link Fingerprint}, behind {@code baksmali fingerprint}.
 */
public class FingerprintTest {

    private ClassDef compile(String smali) throws Exception {
        return SmaliTestUtils.compileSmali(smali);
    }

    private Method onlyMethod(ClassDef classDef) {
        return Iterables.getOnlyElement(classDef.getMethods());
    }

    // -- rename invariance ---------------------------------------------------

    @Test
    public void methodHash_isRenameInvariant() throws Exception {
        String a =
                ".class public Lcom/example/A;\n" +
                ".super Ljava/lang/Object;\n" +
                ".method public foo()I\n" +
                "    .registers 1\n" +
                "    const/4 v0, 0x1\n" +
                "    return v0\n" +
                ".end method\n";
        // Same opcodes, different class name, method name, and register number.
        String b =
                ".class public Lorg/other/Renamed;\n" +
                ".super Ljava/lang/Object;\n" +
                ".method public bar()I\n" +
                "    .registers 2\n" +
                "    const/4 v1, 0x1\n" +
                "    return v1\n" +
                ".end method\n";
        String hashA = Fingerprint.methodHash(onlyMethod(compile(a)));
        String hashB = Fingerprint.methodHash(onlyMethod(compile(b)));
        Assert.assertEquals(hashA, hashB);
        Assert.assertEquals(16, hashA.length());
    }

    @Test
    public void methodHash_differsWhenOpcodesDiffer() throws Exception {
        String a =
                ".class public LA;\n.super Ljava/lang/Object;\n" +
                ".method public foo()I\n    .registers 1\n    const/4 v0, 0x1\n    return v0\n.end method\n";
        String b =
                ".class public LA;\n.super Ljava/lang/Object;\n" +
                ".method public foo()I\n    .registers 1\n    const/16 v0, 0x100\n    return v0\n.end method\n";
        Assert.assertNotEquals(Fingerprint.methodHash(onlyMethod(compile(a))),
                Fingerprint.methodHash(onlyMethod(compile(b))));
    }

    @Test
    public void classHash_isIndependentOfMethodOrder() throws Exception {
        String order1 =
                ".class public LA;\n.super Ljava/lang/Object;\n" +
                ".method public a()V\n    .registers 1\n    return-void\n.end method\n" +
                ".method public b()I\n    .registers 1\n    const/4 v0, 0x1\n    return v0\n.end method\n";
        String order2 =
                ".class public LB;\n.super Ljava/lang/Object;\n" +
                ".method public b()I\n    .registers 1\n    const/4 v0, 0x1\n    return v0\n.end method\n" +
                ".method public a()V\n    .registers 1\n    return-void\n.end method\n";
        Assert.assertEquals(Fingerprint.classHash(compile(order1)),
                Fingerprint.classHash(compile(order2)));
    }

    // -- n-grams -------------------------------------------------------------

    @Test
    public void ngrams_buildsSlidingWindow() {
        List<String> ops = Arrays.asList("const-string", "invoke-virtual", "move-result", "return");
        Map<String, Integer> bag = Fingerprint.ngrams(ops, 2);
        Assert.assertEquals(3, bag.size());
        Assert.assertEquals(Integer.valueOf(1), bag.get("const-string|invoke-virtual"));
        Assert.assertEquals(Integer.valueOf(1), bag.get("invoke-virtual|move-result"));
        Assert.assertEquals(Integer.valueOf(1), bag.get("move-result|return"));
    }

    @Test
    public void ngrams_shortBodyPadsToSingle() {
        Map<String, Integer> bag = Fingerprint.ngrams(Arrays.asList("return-void"), 3);
        Assert.assertEquals(1, bag.size());
        Assert.assertEquals(Integer.valueOf(1), bag.get("return-void"));
    }

    @Test
    public void jaccard_identicalIsOne_disjointIsZero() {
        List<String> ops = Arrays.asList("a", "b", "c", "d");
        Map<String, Integer> same = Fingerprint.ngrams(ops, 2);
        Assert.assertEquals(1.0, Fingerprint.jaccard(same, Fingerprint.ngrams(ops, 2)), 1e-9);

        Map<String, Integer> other = Fingerprint.ngrams(Arrays.asList("x", "y", "z"), 2);
        Assert.assertEquals(0.0, Fingerprint.jaccard(same, other), 1e-9);
    }

    @Test
    public void jaccard_partialOverlapIsBetween() {
        Map<String, Integer> a = Fingerprint.ngrams(Arrays.asList("a", "b", "c", "d"), 2); // ab bc cd
        Map<String, Integer> b = Fingerprint.ngrams(Arrays.asList("a", "b", "c", "e"), 2); // ab bc ce
        double score = Fingerprint.jaccard(a, b);
        // intersection {ab, bc}=2, union {ab,bc,cd,ce}=4 -> 0.5
        Assert.assertEquals(0.5, score, 1e-9);
    }

    // -- similarity survives a small edit ------------------------------------

    @Test
    public void classNgramProfile_toleratesSmallEdit() throws Exception {
        String original =
                ".class public LLib;\n.super Ljava/lang/Object;\n" +
                ".method public run()V\n" +
                "    .registers 2\n" +
                "    const/4 v0, 0x0\n" +
                "    const/4 v1, 0x1\n" +
                "    add-int v0, v0, v1\n" +
                "    add-int v0, v0, v1\n" +
                "    add-int v0, v0, v1\n" +
                "    return-void\n" +
                ".end method\n";
        // One extra add-int inserted: high n-gram overlap, so similarity should stay high.
        String edited =
                ".class public LRenamedLib;\n.super Ljava/lang/Object;\n" +
                ".method public go()V\n" +
                "    .registers 2\n" +
                "    const/4 v0, 0x0\n" +
                "    const/4 v1, 0x1\n" +
                "    add-int v0, v0, v1\n" +
                "    add-int v0, v0, v1\n" +
                "    add-int v0, v0, v1\n" +
                "    add-int v0, v0, v1\n" +
                "    return-void\n" +
                ".end method\n";
        Map<String, Integer> a = Fingerprint.classNgramProfile(compile(original), 3);
        Map<String, Integer> b = Fingerprint.classNgramProfile(compile(edited), 3);
        double score = Fingerprint.jaccard(a, b);
        Assert.assertTrue("expected high similarity after a one-instruction edit, got " + score,
                score > 0.6);
        // But the exact hash must differ, proving n-grams add fuzzy tolerance the hash lacks.
        Assert.assertNotEquals(Fingerprint.classHash(compile(original)),
                Fingerprint.classHash(compile(edited)));
    }
}
