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

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;

/**
 * A text-level style linter for smali source. Every rule is reliably detectable from the raw text
 * (no bytecode parse), and each corresponds to something {@link SmaliFormatter} would fix — so
 * {@code smali format} cleans up exactly what {@code smali lint} reports.
 *
 * <p>Rules:
 * <ul>
 *   <li>{@code trailing-whitespace} — a line ends with spaces/tabs.</li>
 *   <li>{@code tab-indentation} — a line is indented with a tab.</li>
 *   <li>{@code indentation} — leading indent width is not a multiple of 4.</li>
 *   <li>{@code multiple-blank-lines} — two or more consecutive blank lines.</li>
 *   <li>{@code carriage-return} — a line carries a CR (CRLF line endings).</li>
 *   <li>{@code final-newline} — the file does not end with a newline.</li>
 * </ul>
 */
public class SmaliLinter {

    /** A single lint finding. Line and column are 1-based. */
    public static final class Issue {
        public final int line;
        public final int column;
        @Nonnull public final String rule;
        @Nonnull public final String message;
        @Nonnull public final String severity;

        public Issue(int line, int column, @Nonnull String rule, @Nonnull String message,
                     @Nonnull String severity) {
            this.line = line;
            this.column = column;
            this.rule = rule;
            this.message = message;
            this.severity = severity;
        }

        @Override public String toString() {
            return line + ":" + column + ": [" + rule + "] " + message;
        }
    }

    /**
     * Lints the given source and returns findings in source order.
     */
    @Nonnull
    public List<Issue> lint(@Nonnull String source) {
        List<Issue> issues = new ArrayList<>();
        if (source.isEmpty()) {
            return issues;
        }

        String[] lines = source.split("\n", -1);
        // A trailing "\n" produces an empty final element; its presence means the file ends with a
        // newline. If the last element is non-empty, the final newline is missing.
        boolean endsWithNewline = lines.length > 0 && lines[lines.length - 1].isEmpty();
        int contentLines = endsWithNewline ? lines.length - 1 : lines.length;

        int consecutiveBlank = 0;
        for (int i = 0; i < contentLines; i++) {
            String line = lines[i];
            int lineNo = i + 1;

            // carriage-return
            int cr = line.indexOf('\r');
            if (cr >= 0) {
                issues.add(new Issue(lineNo, cr + 1, "carriage-return",
                        "line has a carriage return (CRLF line ending)", "warning"));
            }

            String noCr = cr >= 0 ? line.replace("\r", "") : line;

            // blank-line run
            if (noCr.trim().isEmpty()) {
                consecutiveBlank++;
                if (consecutiveBlank >= 2) {
                    issues.add(new Issue(lineNo, 1, "multiple-blank-lines",
                            "more than one consecutive blank line", "warning"));
                }
                continue;
            }
            consecutiveBlank = 0;

            // trailing-whitespace
            int trailing = trailingWhitespaceStart(noCr);
            if (trailing >= 0) {
                issues.add(new Issue(lineNo, trailing + 1, "trailing-whitespace",
                        "line has trailing whitespace", "warning"));
            }

            // tab-indentation
            if (!noCr.isEmpty() && noCr.charAt(0) == '\t') {
                issues.add(new Issue(lineNo, 1, "tab-indentation",
                        "line is indented with a tab; use spaces", "warning"));
            }

            // indentation width multiple of 4
            int indentWidth = SmaliFormatter.leadingIndentWidth(noCr);
            if (indentWidth % 4 != 0) {
                issues.add(new Issue(lineNo, 1, "indentation",
                        "indent width " + indentWidth + " is not a multiple of 4", "warning"));
            }
        }

        if (!endsWithNewline) {
            int lastLine = Math.max(1, contentLines);
            int col = lines[contentLines - 1].length() + 1;
            issues.add(new Issue(lastLine, col, "final-newline",
                    "file does not end with a newline", "warning"));
        }

        return issues;
    }

    /** @return index of the first trailing-whitespace char, or -1 if none. */
    private static int trailingWhitespaceStart(@Nonnull String line) {
        int end = line.length();
        int i = end;
        while (i > 0) {
            char c = line.charAt(i - 1);
            if (c == ' ' || c == '\t') {
                i--;
            } else {
                break;
            }
        }
        return i < end ? i : -1;
    }
}
