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
import java.util.HashSet;
import java.util.Set;

/**
 * A deterministic, text-level formatter for smali source. It normalizes whitespace and re-indents
 * block structure without parsing the bytecode, so it never changes semantics and is idempotent
 * ({@code format(format(x)) == format(x)}).
 *
 * <p>What it does:
 * <ul>
 *   <li>Re-indents by block nesting depth using 4 spaces per level. Block openers
 *       ({@code .method}, {@code .annotation}, {@code .subannotation}, {@code .array-data},
 *       {@code .packed-switch}, {@code .sparse-switch}, and — when they carry a body —
 *       {@code .field}/{@code .param}) increase depth; their matching {@code .end ...} restores it.
 *       Single-statement debug directives like {@code .local}/{@code .end local} do not nest.</li>
 *   <li>Strips trailing whitespace, converts leading tabs to spaces.</li>
 *   <li>Collapses runs of blank lines to a single blank; trims leading/trailing blanks.</li>
 *   <li>Ensures the output ends with exactly one newline (empty input stays empty).</li>
 * </ul>
 *
 * <p>This is the canonical style {@code smali lint} checks for; {@code smali format} applies it and
 * the LSP {@code textDocument/formatting} request reuses it.
 */
public class SmaliFormatter {

    private static final String INDENT = "    ";
    private static final int TAB_WIDTH = 4;

    /** Block openers that always have a matching {@code .end ...}. */
    private static final Set<String> UNCONDITIONAL_OPENERS = new HashSet<>();
    /** Block openers that may also appear as a single line (no body). */
    private static final Set<String> CONDITIONAL_OPENERS = new HashSet<>();
    /** Directives that close a block and restore the previous indent depth. */
    private static final Set<String> CLOSERS = new HashSet<>();

    static {
        UNCONDITIONAL_OPENERS.add(".method");
        UNCONDITIONAL_OPENERS.add(".annotation");
        UNCONDITIONAL_OPENERS.add(".subannotation");
        UNCONDITIONAL_OPENERS.add(".array-data");
        UNCONDITIONAL_OPENERS.add(".packed-switch");
        UNCONDITIONAL_OPENERS.add(".sparse-switch");

        CONDITIONAL_OPENERS.add(".field");
        CONDITIONAL_OPENERS.add(".param");

        CLOSERS.add(".end method");
        CLOSERS.add(".end annotation");
        CLOSERS.add(".end subannotation");
        CLOSERS.add(".end field");
        CLOSERS.add(".end param");
        CLOSERS.add(".end array-data");
        CLOSERS.add(".end packed-switch");
        CLOSERS.add(".end sparse-switch");
    }

    /**
     * Formats the given smali source. Returns the canonical form.
     */
    @Nonnull
    public String format(@Nonnull String source) {
        // Split on \n keeping structure; handle CRLF by stripping trailing \r per line.
        String[] rawLines = source.split("\n", -1);
        StringBuilder out = new StringBuilder();

        int depth = 0;
        boolean pendingBlank = false;
        boolean wroteAny = false;

        for (int i = 0; i < rawLines.length; i++) {
            String line = stripTrailing(rawLines[i]);
            String trimmed = line.trim();

            if (trimmed.isEmpty()) {
                // Defer blank lines; a run collapses to a single blank between content.
                if (wroteAny) {
                    pendingBlank = true;
                }
                continue;
            }

            String directive = directiveOf(trimmed);
            if (CLOSERS.contains(directive)) {
                depth = Math.max(0, depth - 1);
            }

            if (pendingBlank) {
                out.append('\n');
                pendingBlank = false;
            }

            for (int d = 0; d < depth; d++) {
                out.append(INDENT);
            }
            out.append(trimmed).append('\n');
            wroteAny = true;

            if (UNCONDITIONAL_OPENERS.contains(directive)) {
                depth++;
            } else if (CONDITIONAL_OPENERS.contains(directive)
                    && hasBlockBody(rawLines, i, ".end " + directive.substring(1))) {
                depth++;
            }
        }

        return out.toString();
    }

    /**
     * Returns the block directive for a trimmed line: the first token, or {@code ".end <what>"} for
     * end directives. Non-directive lines (labels, instructions, comments) return the first token,
     * which simply won't match any opener/closer set.
     */
    @Nonnull
    private static String directiveOf(@Nonnull String trimmed) {
        int sp = indexOfWhitespace(trimmed);
        String first = sp < 0 ? trimmed : trimmed.substring(0, sp);
        if (!first.equals(".end")) {
            return first;
        }
        String rest = trimmed.substring(sp).trim();
        int sp2 = indexOfWhitespace(rest);
        String second = sp2 < 0 ? rest : rest.substring(0, sp2);
        return ".end " + second;
    }

    /**
     * Determines whether a conditional opener at {@code openerIdx} actually has a body, i.e. a
     * matching {@code endDirective} appears before the enclosing method ends or another opener of
     * the same kind begins. Only annotation sub-blocks may appear in between.
     */
    private static boolean hasBlockBody(@Nonnull String[] lines, int openerIdx,
                                        @Nonnull String endDirective) {
        String openerDirective = directiveOf(lines[openerIdx].trim());
        for (int j = openerIdx + 1; j < lines.length; j++) {
            String t = lines[j].trim();
            if (t.isEmpty() || t.startsWith("#")) {
                continue;
            }
            String d = directiveOf(t);
            if (d.equals(endDirective)) {
                return true;
            }
            // Hitting the method boundary or a fresh opener of the same kind means the opener at
            // openerIdx was a single line with no body.
            if (d.equals(".method") || d.equals(".end method") || d.equals(openerDirective)) {
                return false;
            }
        }
        return false;
    }

    @Nonnull
    private static String stripTrailing(@Nonnull String line) {
        int end = line.length();
        while (end > 0) {
            char c = line.charAt(end - 1);
            if (c == ' ' || c == '\t' || c == '\r') {
                end--;
            } else {
                break;
            }
        }
        return line.substring(0, end);
    }

    private static int indexOfWhitespace(@Nonnull String s) {
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == ' ' || c == '\t') {
                return i;
            }
        }
        return -1;
    }

    /** Exposed for the linter: expands leading tabs to spaces at {@link #TAB_WIDTH}. */
    public static int leadingIndentWidth(@Nonnull String line) {
        int width = 0;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == ' ') {
                width++;
            } else if (c == '\t') {
                width += TAB_WIDTH - (width % TAB_WIDTH);
            } else {
                break;
            }
        }
        return width;
    }
}
