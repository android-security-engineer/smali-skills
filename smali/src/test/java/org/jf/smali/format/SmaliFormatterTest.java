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

package org.jf.smali.format;

import org.junit.Assert;
import org.junit.Test;

/**
 * Tests for {@link SmaliFormatter}: re-indentation by block depth, whitespace normalization, and
 * idempotency.
 */
public class SmaliFormatterTest {

    private final SmaliFormatter formatter = new SmaliFormatter();

    private void assertIdempotent(String formatted) {
        Assert.assertEquals("format must be idempotent", formatted, formatter.format(formatted));
    }

    @Test
    public void reindentsMethodBodyToFourSpaces() {
        String messy =
                ".class public LA;\n" +
                ".super Ljava/lang/Object;\n" +
                ".method public foo()V\n" +
                "  .registers 1\n" +
                "     return-void\n" +
                ".end method\n";
        String expected =
                ".class public LA;\n" +
                ".super Ljava/lang/Object;\n" +
                ".method public foo()V\n" +
                "    .registers 1\n" +
                "    return-void\n" +
                ".end method\n";
        String out = formatter.format(messy);
        Assert.assertEquals(expected, out);
        assertIdempotent(out);
    }

    @Test
    public void nestedAnnotationInMethodGetsDeeperIndent() {
        String src =
                ".class public LA;\n" +
                ".super Ljava/lang/Object;\n" +
                ".method public foo()V\n" +
                ".registers 1\n" +
                ".annotation runtime Lp/Ann;\n" +
                "value = 1\n" +
                ".end annotation\n" +
                "return-void\n" +
                ".end method\n";
        String expected =
                ".class public LA;\n" +
                ".super Ljava/lang/Object;\n" +
                ".method public foo()V\n" +
                "    .registers 1\n" +
                "    .annotation runtime Lp/Ann;\n" +
                "        value = 1\n" +
                "    .end annotation\n" +
                "    return-void\n" +
                ".end method\n";
        String out = formatter.format(src);
        Assert.assertEquals(expected, out);
        assertIdempotent(out);
    }

    @Test
    public void fieldWithAnnotationBodyIsTreatedAsBlock() {
        String src =
                ".class public LA;\n" +
                ".super Ljava/lang/Object;\n" +
                ".field public x:I\n" +
                ".annotation runtime Lp/Ann;\n" +
                ".end annotation\n" +
                ".end field\n" +
                ".field public y:I\n";
        String expected =
                ".class public LA;\n" +
                ".super Ljava/lang/Object;\n" +
                ".field public x:I\n" +
                "    .annotation runtime Lp/Ann;\n" +
                "    .end annotation\n" +
                ".end field\n" +
                ".field public y:I\n";
        String out = formatter.format(src);
        Assert.assertEquals(expected, out);
        assertIdempotent(out);
    }

    @Test
    public void singleLineFieldDoesNotNestFollowingLines() {
        // A plain single-line .field must NOT indent whatever follows it.
        String src =
                ".class public LA;\n" +
                ".super Ljava/lang/Object;\n" +
                ".field public x:I\n" +
                ".field public y:I\n" +
                ".method public foo()V\n" +
                ".registers 0\n" +
                "return-void\n" +
                ".end method\n";
        String expected =
                ".class public LA;\n" +
                ".super Ljava/lang/Object;\n" +
                ".field public x:I\n" +
                ".field public y:I\n" +
                ".method public foo()V\n" +
                "    .registers 0\n" +
                "    return-void\n" +
                ".end method\n";
        Assert.assertEquals(expected, formatter.format(src));
    }

    @Test
    public void localDirectivesDoNotChangeDepth() {
        // .local / .end local are single-statement debug directives, not block delimiters.
        String src =
                ".method public foo()V\n" +
                ".registers 2\n" +
                ".local v0, \"x\":I\n" +
                "nop\n" +
                ".end local v0\n" +
                "return-void\n" +
                ".end method\n";
        String expected =
                ".method public foo()V\n" +
                "    .registers 2\n" +
                "    .local v0, \"x\":I\n" +
                "    nop\n" +
                "    .end local v0\n" +
                "    return-void\n" +
                ".end method\n";
        Assert.assertEquals(expected, formatter.format(src));
    }

    @Test
    public void packedSwitchPayloadIsIndented() {
        String src =
                ".method public foo()V\n" +
                ".registers 1\n" +
                ":pswitch_data\n" +
                ".packed-switch 0x0\n" +
                ":label\n" +
                ".end packed-switch\n" +
                ".end method\n";
        String expected =
                ".method public foo()V\n" +
                "    .registers 1\n" +
                "    :pswitch_data\n" +
                "    .packed-switch 0x0\n" +
                "        :label\n" +
                "    .end packed-switch\n" +
                ".end method\n";
        Assert.assertEquals(expected, formatter.format(src));
    }

    @Test
    public void stripsTrailingWhitespaceAndTabs() {
        String src = ".class public LA;   \n\t.super Ljava/lang/Object;\t\n";
        String expected = ".class public LA;\n.super Ljava/lang/Object;\n";
        Assert.assertEquals(expected, formatter.format(src));
    }

    @Test
    public void collapsesBlankRunsAndTrimsEdges() {
        String src = "\n\n.class public LA;\n\n\n\n.super Ljava/lang/Object;\n\n\n";
        String expected = ".class public LA;\n\n.super Ljava/lang/Object;\n";
        Assert.assertEquals(expected, formatter.format(src));
    }

    @Test
    public void ensuresSingleFinalNewline() {
        Assert.assertEquals(".class public LA;\n", formatter.format(".class public LA;"));
    }

    @Test
    public void emptyInputStaysEmpty() {
        Assert.assertEquals("", formatter.format(""));
        Assert.assertEquals("", formatter.format("\n\n\n"));
    }

    @Test
    public void handlesCrlfLineEndings() {
        String src = ".class public LA;\r\n.super Ljava/lang/Object;\r\n";
        String expected = ".class public LA;\n.super Ljava/lang/Object;\n";
        Assert.assertEquals(expected, formatter.format(src));
    }
}
