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

import org.junit.Assert;
import org.junit.Test;

import java.util.List;

public class SmaliAnalyzerTest {

    private static final String VALID_CLASS =
            ".class public Lcom/example/Foo;\n" +
            ".super Ljava/lang/Object;\n" +
            "\n" +
            ".field public static count:I\n" +
            "\n" +
            ".method public bar(I)Ljava/lang/String;\n" +
            "    .registers 2\n" +
            "    const-string v0, \"hi\"\n" +
            "    return-object v0\n" +
            ".end method\n";

    @Test
    public void validSource_hasNoDiagnostics() {
        List<LspModels.Diagnostic> diagnostics = new SmaliAnalyzer().diagnostics(VALID_CLASS);
        Assert.assertTrue("expected no diagnostics, got: " + describe(diagnostics),
                diagnostics.isEmpty());
    }

    @Test
    public void validSource_producesClassMethodAndFieldSymbols() {
        List<LspModels.DocumentSymbol> symbols = new SmaliAnalyzer().documentSymbols(VALID_CLASS);

        Assert.assertEquals(1, symbols.size());
        LspModels.DocumentSymbol clazz = symbols.get(0);
        Assert.assertEquals("Lcom/example/Foo;", clazz.name);
        Assert.assertEquals(LspModels.SYMBOL_CLASS, clazz.kind);
        Assert.assertNotNull(clazz.children);

        boolean sawMethod = false;
        boolean sawField = false;
        for (LspModels.DocumentSymbol child : clazz.children) {
            if (child.kind == LspModels.SYMBOL_METHOD) {
                Assert.assertEquals("bar(I)Ljava/lang/String;", child.name);
                sawMethod = true;
            } else if (child.kind == LspModels.SYMBOL_FIELD) {
                Assert.assertEquals("count:I", child.name);
                sawField = true;
            }
        }
        Assert.assertTrue("expected a method symbol", sawMethod);
        Assert.assertTrue("expected a field symbol", sawField);
    }

    @Test
    public void classSymbolRangeUsesZeroBasedLine() {
        List<LspModels.DocumentSymbol> symbols = new SmaliAnalyzer().documentSymbols(VALID_CLASS);
        // The .class descriptor is on the first line -> LSP line 0.
        Assert.assertEquals(0, symbols.get(0).range.start.line);
    }

    @Test
    public void missingSuperDirective_producesDiagnostic() {
        String source = ".class public Lcom/example/Foo;\n";
        List<LspModels.Diagnostic> diagnostics = new SmaliAnalyzer().diagnostics(source);
        Assert.assertFalse("expected a diagnostic for the missing .super", diagnostics.isEmpty());
        for (LspModels.Diagnostic d : diagnostics) {
            Assert.assertEquals(LspModels.SEVERITY_ERROR, d.severity);
            Assert.assertNotNull(d.range);
            Assert.assertNotNull(d.message);
        }
    }

    @Test
    public void lexerError_producesDiagnosticWithPosition() {
        // A stray invalid directive triggers an InvalidToken from the lexer.
        String source =
                ".class public Lcom/example/Foo;\n" +
                ".super Ljava/lang/Object;\n" +
                ".notadirective\n";
        List<LspModels.Diagnostic> diagnostics = new SmaliAnalyzer().diagnostics(source);
        Assert.assertFalse("expected at least one diagnostic", diagnostics.isEmpty());

        boolean sawThirdLine = false;
        for (LspModels.Diagnostic d : diagnostics) {
            if (d.range.start.line == 2) { // zero-based -> third line
                sawThirdLine = true;
            }
        }
        Assert.assertTrue("expected a diagnostic anchored on the invalid third line", sawThirdLine);
    }

    @Test
    public void brokenSource_stillOutlinesTheValidClass() {
        // Trailing garbage shouldn't wipe out the class symbol.
        String source = VALID_CLASS + "this is not smali\n";
        List<LspModels.DocumentSymbol> symbols = new SmaliAnalyzer().documentSymbols(source);
        Assert.assertFalse("expected the recovered class symbol", symbols.isEmpty());
        Assert.assertEquals("Lcom/example/Foo;", symbols.get(0).name);
    }

    private static String describe(List<LspModels.Diagnostic> diagnostics) {
        StringBuilder sb = new StringBuilder();
        for (LspModels.Diagnostic d : diagnostics) {
            sb.append("\n  [").append(d.range.start.line).append(':')
                    .append(d.range.start.character).append("] ").append(d.message);
        }
        return sb.toString();
    }
}
