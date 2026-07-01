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

package org.jf.smali;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.google.common.io.Files;
import org.jf.smali.format.SmaliFiles;
import org.jf.smali.format.SmaliLinter;
import org.jf.util.jcommander.Command;
import org.jf.util.jcommander.ExtendedParameter;
import org.jf.util.jcommander.ExtendedParameters;

import javax.annotation.Nonnull;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Reports text-level style issues in smali source (see {@link SmaliLinter}) — trailing whitespace,
 * tab/odd indentation, blank-line runs, CRLF, missing final newline. Prints one finding per line as
 * {@code path:line:col: [rule] message} (or JSON with {@code --format json}) and exits 1 if any file
 * has issues. Everything it flags is fixable with {@code smali format}.
 */
@Parameters(commandDescription = "Reports style issues in smali source files.")
@ExtendedParameters(
        commandName = "lint")
public class SmaliLintCommand extends Command {

    @Parameter(names = {"-h", "-?", "--help"}, help = true,
            description = "Show usage information for this command.")
    private boolean help;

    @Parameter(names = "--format",
            description = "Output format: text (default) or json.")
    @ExtendedParameter(argumentNames = "text|json")
    private String format = "text";

    @Parameter(description = "The smali files to lint. If a directory is specified, it is " +
            "recursively searched for .smali files.")
    @ExtendedParameter(argumentNames = "[<file>|<dir>]+")
    private List<String> input;

    private final SmaliLinter linter = new SmaliLinter();

    public SmaliLintCommand(@Nonnull List<JCommander> commandAncestors) {
        super(commandAncestors);
    }

    @Override public void run() {
        if (help || input == null || input.isEmpty()) {
            usage();
            return;
        }

        boolean json = "json".equalsIgnoreCase(format);
        if (!json && !"text".equalsIgnoreCase(format)) {
            System.err.println("Unknown --format '" + format + "'; expected text or json.");
            System.exit(1);
            return;
        }

        List<File> files = SmaliFiles.collect(input);
        if (files.isEmpty()) {
            System.err.println("No .smali files found.");
            System.exit(1);
            return;
        }

        try {
            int total = json ? runJson(files) : runText(files);
            if (total > 0) {
                System.exit(1);
            }
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }

    private int runText(@Nonnull List<File> files) throws IOException {
        int total = 0;
        for (File file : files) {
            List<SmaliLinter.Issue> issues = linter.lint(read(file));
            for (SmaliLinter.Issue issue : issues) {
                System.out.println(file.getPath() + ":" + issue);
                total++;
            }
        }
        System.err.println(total + " issue(s) across " + files.size() + " file(s).");
        return total;
    }

    private int runJson(@Nonnull List<File> files) throws IOException {
        int total = 0;
        JsonArray root = new JsonArray();
        for (File file : files) {
            for (SmaliLinter.Issue issue : linter.lint(read(file))) {
                JsonObject obj = new JsonObject();
                obj.add("file", new JsonPrimitive(file.getPath()));
                obj.add("line", new JsonPrimitive(issue.line));
                obj.add("column", new JsonPrimitive(issue.column));
                obj.add("rule", new JsonPrimitive(issue.rule));
                obj.add("severity", new JsonPrimitive(issue.severity));
                obj.add("message", new JsonPrimitive(issue.message));
                root.add(obj);
                total++;
            }
        }
        Gson gson = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
        System.out.println(gson.toJson(root));
        return total;
    }

    @Nonnull
    private static String read(@Nonnull File file) throws IOException {
        return Files.asCharSource(file, StandardCharsets.UTF_8).read();
    }
}
