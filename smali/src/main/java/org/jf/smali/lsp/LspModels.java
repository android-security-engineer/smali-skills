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

import java.util.List;

/**
 * Plain data holders that mirror the (subset of) Language Server Protocol shapes
 * this server implements. They are serialized to/from JSON verbatim by Gson, so
 * every field name here must match the LSP wire name exactly.
 *
 * <p>All positions are zero-based (LSP convention): line 0 is the first line,
 * character 0 is the first column. Note that the ANTLR lexer/parser report
 * one-based lines and zero-based columns, so line numbers are adjusted by
 * {@link SmaliAnalyzer} on the way out.
 */
public final class LspModels {
    private LspModels() {}

    /** LSP DiagnosticSeverity enum values. */
    public static final int SEVERITY_ERROR = 1;
    public static final int SEVERITY_WARNING = 2;
    public static final int SEVERITY_INFORMATION = 3;
    public static final int SEVERITY_HINT = 4;

    /** LSP SymbolKind enum values (subset used here). */
    public static final int SYMBOL_CLASS = 5;
    public static final int SYMBOL_METHOD = 6;
    public static final int SYMBOL_FIELD = 8;

    public static final class Position {
        public int line;
        public int character;

        public Position() {}

        public Position(int line, int character) {
            this.line = line;
            this.character = character;
        }
    }

    public static final class Range {
        public Position start;
        public Position end;

        public Range() {}

        public Range(Position start, Position end) {
            this.start = start;
            this.end = end;
        }

        /** Convenience: a zero-width range collapsed at a single point. */
        public static Range at(int line, int character) {
            return new Range(new Position(line, character), new Position(line, character));
        }
    }

    public static final class Diagnostic {
        public Range range;
        public int severity;
        public String source;
        public String message;

        public Diagnostic() {}

        public Diagnostic(Range range, int severity, String source, String message) {
            this.range = range;
            this.severity = severity;
            this.source = source;
            this.message = message;
        }
    }

    /**
     * A hierarchical LSP DocumentSymbol. Classes are top level; their methods and
     * fields are nested in {@link #children}.
     */
    public static final class DocumentSymbol {
        public String name;
        public String detail;
        public int kind;
        public Range range;
        public Range selectionRange;
        public List<DocumentSymbol> children;

        public DocumentSymbol() {}

        public DocumentSymbol(String name, String detail, int kind, Range range) {
            this.name = name;
            this.detail = detail;
            this.kind = kind;
            this.range = range;
            this.selectionRange = range;
        }
    }

    /** LSP MarkupContent used for hover payloads. */
    public static final class MarkupContent {
        public String kind;
        public String value;

        public MarkupContent() {}

        public MarkupContent(String kind, String value) {
            this.kind = kind;
            this.value = value;
        }
    }

    public static final class Hover {
        public MarkupContent contents;

        public Hover() {}

        public Hover(MarkupContent contents) {
            this.contents = contents;
        }
    }
}
