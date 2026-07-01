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

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import com.beust.jcommander.ParametersDelegate;
import org.jf.baksmali.diff.DexDiff;
import org.jf.dexlib2.dexbacked.DexBackedDexFile;
import org.jf.util.jcommander.ExtendedParameters;

import javax.annotation.Nonnull;
import java.util.List;

/**
 * {@code baksmali diff} — semantic (opcode-level) diff between two dex/apk files.
 *
 * <p>Reports classes added/removed between OLD and NEW, and for classes present in both, the
 * methods that were added, removed, or changed. A method counts as changed when its opcode
 * sequence differs; register allocation, debug info, and offsets are ignored, so recompilation
 * noise does not show up as a change.
 *
 * <pre>
 *   baksmali diff old.apk new.apk                 # text report
 *   baksmali diff old.apk new.apk --format json   # machine-readable
 * </pre>
 *
 * <p>Exit code is 0 when the files are semantically identical and 1 when they differ, so the
 * command can gate scripts (e.g. {@code baksmali diff a b && echo unchanged}).
 */
@Parameters(commandDescription = "Semantic (opcode-level) diff of two dex/apk files: added/removed/changed classes and methods.")
@ExtendedParameters(
        commandName = "diff")
public class DiffCommand extends DexInputCommand {

    @Parameter(names = {"-h", "-?", "--help"}, help = true,
            description = "Show usage information")
    private boolean help;

    @ParametersDelegate
    private OutputFormatArguments outputFormat = new OutputFormatArguments();

    public DiffCommand(@Nonnull List<JCommander> commandAncestors) {
        super(commandAncestors);
    }

    @Override public void run() {
        if (help || inputList == null || inputList.size() != 2) {
            if (inputList != null && inputList.size() == 1) {
                System.err.println("Two dex/apk files are required: OLD and NEW.");
            } else if (inputList != null && inputList.size() > 2) {
                System.err.println("Too many files specified; exactly two are required: OLD and NEW.");
            }
            usage();
            return;
        }

        // Load OLD via the shared base helper, NEW via a second isolated load.
        loadDexFile(inputList.get(0));
        DexBackedDexFile oldDex = dexFile;

        loadDexFile(inputList.get(1));
        DexBackedDexFile newDex = dexFile;

        DexDiff diff = DexDiff.compute(oldDex, newDex);

        if (outputFormat.isJson()) {
            System.out.println(diff.toJson());
        } else {
            System.out.print(diff.toText());
        }

        if (!diff.isEmpty()) {
            System.exit(1);
        }
    }
}
