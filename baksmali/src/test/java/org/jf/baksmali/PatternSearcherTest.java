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
import org.jf.smali.SmaliTestUtils;
import org.junit.Assert;
import org.junit.Test;

import java.util.Collections;
import java.util.List;

/**
 * Tests for {@link PatternSearcher}, the opcode-pattern matcher behind
 * {@code baksmali search --opcode}.
 */
public class PatternSearcherTest {

    private ClassDef compileFixture() throws Exception {
        // describe() contains: new-instance, invoke-direct(<init>), const-string, invoke-virtual(append),
        //   invoke-virtual(toString), move-result-object, return-object
        String smali =
                ".class public Lcom/example/SearchTarget;\n" +
                ".super Ljava/lang/Object;\n" +
                "\n" +
                ".method public constructor <init>()V\n" +
                "    .registers 1\n" +
                "    invoke-direct {p0}, Ljava/lang/Object;-><init>()V\n" +
                "    return-void\n" +
                ".end method\n" +
                "\n" +
                ".method public describe()Ljava/lang/String;\n" +
                "    .registers 2\n" +
                "    new-instance v0, Ljava/lang/StringBuilder;\n" +
                "    invoke-direct {v0}, Ljava/lang/StringBuilder;-><init>()V\n" +
                "    const-string v1, \"hello\"\n" +
                "    invoke-virtual {v0, v1}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;\n" +
                "    invoke-virtual {v0}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;\n" +
                "    move-result-object v0\n" +
                "    return-object v0\n" +
                ".end method\n";
        return SmaliTestUtils.compileSmali(smali);
    }

    @Test
    public void testParsePattern() {
        Assert.assertTrue(PatternSearcher.parsePattern(null).isEmpty());
        Assert.assertTrue(PatternSearcher.parsePattern("").isEmpty());
        Assert.assertTrue(PatternSearcher.parsePattern("   ").isEmpty());

        List<String> tokens = PatternSearcher.parsePattern("const-string,invoke-virtual");
        Assert.assertEquals(2, tokens.size());
        Assert.assertEquals("const-string", tokens.get(0));
        Assert.assertEquals("invoke-virtual", tokens.get(1));

        // Whitespace around tokens is trimmed.
        List<String> wild = PatternSearcher.parsePattern(" const-string , * , invoke-virtual ");
        Assert.assertEquals(3, wild.size());
        Assert.assertEquals("const-string", wild.get(0));
        Assert.assertEquals("*", wild.get(1));
        Assert.assertEquals("invoke-virtual", wild.get(2));
    }

    @Test
    public void testSingleOpcodeMatch() throws Exception {
        PatternSearcher searcher = new PatternSearcher(new BaksmaliFormatter());
        List<PatternSearcher.Match> matches = searcher.search(
                Collections.singleton(compileFixture()),
                PatternSearcher.parsePattern("const-string"));

        Assert.assertEquals("expected exactly one const-string", 1, matches.size());
        PatternSearcher.Match m = matches.get(0);
        Assert.assertEquals("Lcom/example/SearchTarget;->describe()Ljava/lang/String;", m.caller);
        Assert.assertEquals(1, m.instructions.size());
        Assert.assertTrue(m.instructions.get(0).startsWith("const-string"));
    }

    @Test
    public void testSequenceMatch() throws Exception {
        PatternSearcher searcher = new PatternSearcher(new BaksmaliFormatter());
        // const-string followed immediately by invoke-virtual(append) — present in describe().
        List<PatternSearcher.Match> matches = searcher.search(
                Collections.singleton(compileFixture()),
                PatternSearcher.parsePattern("const-string,invoke-virtual"));

        Assert.assertEquals(1, matches.size());
        PatternSearcher.Match m = matches.get(0);
        Assert.assertEquals(2, m.instructions.size());
        Assert.assertTrue(m.instructions.get(0).startsWith("const-string"));
        Assert.assertTrue(m.instructions.get(1).startsWith("invoke-virtual"));
    }

    @Test
    public void testWildcardMatch() throws Exception {
        PatternSearcher searcher = new PatternSearcher(new BaksmaliFormatter());
        // const-string, *, invoke-virtual: the '*' matches invoke-virtual(append)? No — append IS
        // invoke-virtual. The sequence is: const-string, invoke-virtual(append), invoke-virtual(toString).
        // So const-string,*,invoke-virtual matches with '*' = invoke-virtual(append) and the third
        // being invoke-virtual(toString).
        List<PatternSearcher.Match> matches = searcher.search(
                Collections.singleton(compileFixture()),
                PatternSearcher.parsePattern("const-string,*,invoke-virtual"));

        Assert.assertEquals(1, matches.size());
        Assert.assertEquals(3, matches.get(0).instructions.size());
    }

    @Test
    public void testNoMatch() throws Exception {
        PatternSearcher searcher = new PatternSearcher(new BaksmaliFormatter());
        // invoke-super is not a real opcode in this fixture.
        List<PatternSearcher.Match> matches = searcher.search(
                Collections.singleton(compileFixture()),
                PatternSearcher.parsePattern("invoke-super"));
        Assert.assertTrue(matches.isEmpty());
    }

    @Test
    public void testEmptyPatternReturnsNothing() throws Exception {
        PatternSearcher searcher = new PatternSearcher(new BaksmaliFormatter());
        List<PatternSearcher.Match> matches = searcher.search(
                Collections.singleton(compileFixture()),
                Collections.<String>emptyList());
        Assert.assertTrue(matches.isEmpty());
    }

    @Test
    public void testCodeOffsetIsFirstMatchedInstruction() throws Exception {
        PatternSearcher searcher = new PatternSearcher(new BaksmaliFormatter());
        // The const-string in describe() is the 3rd instruction, so its code offset must be the
        // sum of code units of the two preceding instructions (new-instance + invoke-direct), i.e.
        // strictly positive and less than the method's total code size.
        List<PatternSearcher.Match> matches = searcher.search(
                Collections.singleton(compileFixture()),
                PatternSearcher.parsePattern("const-string"));

        Assert.assertEquals(1, matches.size());
        int offset = matches.get(0).codeOffset;
        // new-instance (2 units) + invoke-direct {v0}, <init> (3 units) = 5 code units = 0x5.
        Assert.assertEquals("const-string should start at offset 0x5", 0x5, offset);
    }

    @Test
    public void testOverlappingMatches() throws Exception {
        PatternSearcher searcher = new PatternSearcher(new BaksmaliFormatter());
        // invoke-virtual,invoke-virtual: there are two adjacent invoke-virtuals (append, toString).
        // The first match starts at append; since we advance by 1, the second potential start at
        // toString cannot form a 2-instruction sequence (toString is followed by move-result-object),
        // so we expect exactly one match.
        List<PatternSearcher.Match> matches = searcher.search(
                Collections.singleton(compileFixture()),
                PatternSearcher.parsePattern("invoke-virtual,invoke-virtual"));
        Assert.assertEquals(1, matches.size());
    }
}
