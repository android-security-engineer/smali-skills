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

import com.beust.jcommander.Parameter;
import com.beust.jcommander.ParametersDelegate;

/**
 * Shared command-line arguments for controlling the output format of list/xref/search commands.
 *
 * <p>Commands that produce tabular or enumerated output can delegate to this class via
 * {@code @ParametersDelegate} to gain a {@code --format} option that switches between
 * machine-readable JSON (the default, for AI Agent / scripting consumption) and
 * human-readable text (opt in with {@code --format text}).
 */
public class OutputFormatArguments {

    public enum Format {
        TEXT,
        JSON
    }

    @Parameter(names = {"--format"},
            description = "Output format: 'json' (default, machine-readable, for scripting/AI agents) or 'text' (human-readable).")
    private String format = "json";

    /**
     * @return the parsed output format. Defaults to {@link Format#JSON}; only an explicit
     * {@code text} selects {@link Format#TEXT} (unrecognized values fall back to JSON, matching
     * the default).
     */
    public Format getFormat() {
        if (format == null) {
            return Format.JSON;
        }
        switch (format.toLowerCase()) {
            case "text":
                return Format.TEXT;
            default:
                return Format.JSON;
        }
    }

    /**
     * @return true if JSON output was requested.
     */
    public boolean isJson() {
        return getFormat() == Format.JSON;
    }
}
