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

package org.jf.baksmali.output;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;

import javax.annotation.Nonnull;

/**
 * Builds the structured result report emitted by the write-back transform commands
 * ({@code unlock}, {@code replace}, {@code strip-debug}, {@code patch}).
 *
 * <p>Every transform prints exactly one report line on success. In the default JSON mode this is a
 * machine-readable object an AI agent / script can consume directly; with {@code --format text} it
 * is the original human-readable sentence. Both are produced from the same call so the two modes
 * never drift apart.
 *
 * <p>This class is pure (no I/O) and side-effect free, so it can be unit-tested in isolation from
 * the JCommander command wiring.
 */
public final class TransformReport {

    private TransformReport() {}

    /**
     * Seeds a report object with the fields common to every transform: the command name and the
     * input/output dex paths. Callers add command-specific fields before rendering.
     */
    @Nonnull
    public static JsonObject base(@Nonnull String command, @Nonnull String input, @Nonnull String output) {
        JsonObject report = new JsonObject();
        report.addProperty("command", command);
        report.addProperty("input", input);
        report.addProperty("output", output);
        return report;
    }

    /**
     * Renders a report for output. In JSON mode the {@code report} object is serialized (HTML
     * escaping disabled so URLs/regexes stay legible); in text mode the pre-built {@code humanText}
     * sentence is returned verbatim.
     */
    @Nonnull
    public static String render(boolean json, @Nonnull JsonObject report, @Nonnull String humanText) {
        if (json) {
            Gson gson = new GsonBuilder().disableHtmlEscaping().create();
            return gson.toJson(report);
        }
        return humanText;
    }
}
