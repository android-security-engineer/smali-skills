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
import com.google.common.io.Files;
import org.jf.smali.format.SmaliFiles;
import org.jf.smali.format.SmaliFormatter;
import org.jf.util.jcommander.Command;
import org.jf.util.jcommander.ExtendedParameter;
import org.jf.util.jcommander.ExtendedParameters;

import javax.annotation.Nonnull;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Applies the canonical smali formatting (see {@link SmaliFormatter}) to source files. By default it
 * prints the formatted result of a single file to stdout; {@code --write} rewrites files in place,
 * and {@code --check} reports (without modifying) which files are not already formatted.
 */
@Parameters(commandDescription = "Formats smali source files (whitespace + block indentation).")
@ExtendedParameters(
        commandName = "format",
        commandAliases = { "fmt" })
public class SmaliFormatCommand extends Command {

    @Parameter(names = {"-h", "-?", "--help"}, help = true,
            description = "Show usage information for this command.")
    private boolean help;

    @Parameter(names = {"-w", "--write"},
            description = "Rewrite each file in place with its formatted content.")
    private boolean write;

    @Parameter(names = "--check",
            description = "Do not write anything; exit with status 1 if any file is not already " +
                    "formatted, listing the offending files.")
    private boolean check;

    @Parameter(description = "The smali files to format. If a directory is specified, it is " +
            "recursively searched for .smali files. With neither --write nor --check, exactly one " +
            "file's formatted content is printed to stdout.")
    @ExtendedParameter(argumentNames = "[<file>|<dir>]+")
    private List<String> input;

    private final SmaliFormatter formatter = new SmaliFormatter();

    public SmaliFormatCommand(@Nonnull List<JCommander> commandAncestors) {
        super(commandAncestors);
    }

    @Override public void run() {
        if (help || input == null || input.isEmpty()) {
            usage();
            return;
        }

        List<File> files = SmaliFiles.collect(input);
        if (files.isEmpty()) {
            System.err.println("No .smali files found.");
            System.exit(1);
            return;
        }

        try {
            if (check) {
                runCheck(files);
            } else if (write) {
                runWrite(files);
            } else {
                runStdout(files);
            }
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }

    private void runCheck(@Nonnull List<File> files) throws IOException {
        int unformatted = 0;
        for (File file : files) {
            String original = read(file);
            if (!original.equals(formatter.format(original))) {
                System.out.println(file.getPath());
                unformatted++;
            }
        }
        if (unformatted > 0) {
            System.err.println(unformatted + " file(s) not formatted.");
            System.exit(1);
        }
    }

    private void runWrite(@Nonnull List<File> files) throws IOException {
        int changed = 0;
        for (File file : files) {
            String original = read(file);
            String formatted = formatter.format(original);
            if (!original.equals(formatted)) {
                Files.asCharSink(file, StandardCharsets.UTF_8).write(formatted);
                System.out.println("formatted " + file.getPath());
                changed++;
            }
        }
        System.err.println(changed + " file(s) changed, " + (files.size() - changed) + " unchanged.");
    }

    private void runStdout(@Nonnull List<File> files) throws IOException {
        if (files.size() != 1) {
            System.err.println("Refusing to print " + files.size() + " files to stdout; use --write " +
                    "to rewrite in place or --check to report, or pass a single file.");
            System.exit(1);
            return;
        }
        System.out.print(formatter.format(read(files.get(0))));
    }

    @Nonnull
    private static String read(@Nonnull File file) throws IOException {
        return Files.asCharSource(file, StandardCharsets.UTF_8).read();
    }
}
