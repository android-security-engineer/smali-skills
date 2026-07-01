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

import java.util.List;

/**
 * Tests for {@link SmaliLinter}: each text-level style rule, and the property that a formatted
 * document is lint-clean.
 */
public class SmaliLinterTest {

    private final SmaliLinter linter = new SmaliLinter();
    private final SmaliFormatter formatter = new SmaliFormatter();

    private boolean hasRule(List<SmaliLinter.Issue> issues, String rule) {
        for (SmaliLinter.Issue issue : issues) {
            if (issue.rule.equals(rule)) {
                return true;
            }
        }
        return false;
    }

    private SmaliLinter.Issue firstOf(List<SmaliLinter.Issue> issues, String rule) {
        for (SmaliLinter.Issue issue : issues) {
            if (issue.rule.equals(rule)) {
                return issue;
            }
        }
        return null;
    }

    @Test
    public void flagsTrailingWhitespace() {
        List<SmaliLinter.Issue> issues = linter.lint(".class public LA;   \n");
        SmaliLinter.Issue issue = firstOf(issues, "trailing-whitespace");
        Assert.assertNotNull(issue);
        Assert.assertEquals(1, issue.line);
        Assert.assertEquals(18, issue.column); // 1-based: char after ".class public LA;"
    }

    @Test
    public void flagsTabIndentation() {
        List<SmaliLinter.Issue> issues = linter.lint(".method x\n\treturn-void\n.end method\n");
        Assert.assertTrue(hasRule(issues, "tab-indentation"));
    }

    @Test
    public void flagsBadIndentWidth() {
        // Three-space indent is not a multiple of 4.
        List<SmaliLinter.Issue> issues = linter.lint(".method x\n   return-void\n.end method\n");
        SmaliLinter.Issue issue = firstOf(issues, "indentation");
        Assert.assertNotNull(issue);
        Assert.assertEquals(2, issue.line);
    }

    @Test
    public void flagsMultipleBlankLines() {
        List<SmaliLinter.Issue> issues = linter.lint(".class LA;\n\n\n.super LB;\n");
        Assert.assertTrue(hasRule(issues, "multiple-blank-lines"));
    }

    @Test
    public void flagsMissingFinalNewline() {
        List<SmaliLinter.Issue> issues = linter.lint(".class public LA;");
        Assert.assertTrue(hasRule(issues, "final-newline"));
    }

    @Test
    public void flagsCarriageReturn() {
        List<SmaliLinter.Issue> issues = linter.lint(".class public LA;\r\n");
        Assert.assertTrue(hasRule(issues, "carriage-return"));
    }

    @Test
    public void cleanSourceHasNoIssues() {
        String clean =
                ".class public LA;\n" +
                ".super Ljava/lang/Object;\n" +
                ".method public foo()V\n" +
                "    .registers 1\n" +
                "    return-void\n" +
                ".end method\n";
        Assert.assertTrue(linter.lint(clean).isEmpty());
    }

    @Test
    public void emptyInputHasNoIssues() {
        Assert.assertTrue(linter.lint("").isEmpty());
    }

    // -- the key invariant tying formatter and linter together ---------------

    @Test
    public void formattedOutputIsLintClean() {
        String messy =
                "\n.class public LA;   \n" +
                ".super Ljava/lang/Object;\n" +
                ".method public foo()V\n" +
                "\treturn-void\n" +
                "\n\n" +
                ".end method";
        String formatted = formatter.format(messy);
        List<SmaliLinter.Issue> issues = linter.lint(formatted);
        Assert.assertTrue("formatter output should be lint-clean, but got: " + issues,
                issues.isEmpty());
    }
}
