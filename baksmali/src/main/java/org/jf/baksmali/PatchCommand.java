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
import com.google.gson.JsonObject;
import org.jf.baksmali.output.TransformReport;
import org.jf.baksmali.transform.ForceReturnTransform;
import org.jf.dexlib2.iface.DexFile;
import org.jf.util.jcommander.ExtendedParameters;

import javax.annotation.Nonnull;
import java.util.List;

/**
 * The {@code baksmali patch} command: force selected methods to return immediately.
 *
 * <p>Every method whose defining class matches {@code --class} and whose name matches
 * {@code --method} has its body replaced by a minimal {@code return <value>}. The value must be
 * compatible with the method's return type:
 * <ul>
 *   <li>{@code void} — for {@code V} methods</li>
 *   <li>{@code true}/{@code false}/{@code 0}/{@code 1} — for numeric/boolean returns</li>
 *   <li>{@code null} — for object/array returns</li>
 * </ul>
 *
 * <pre>
 *   baksmali patch app.apk --method 'isPremium' --return true -o patched.dex
 *   baksmali patch app.apk --class 'Lcom/drm/.*' --method 'check' --return void -o patched.dex
 * </pre>
 */
@Parameters(commandDescription = "Force selected methods to return immediately (bypass checks / neuter callbacks).")
@ExtendedParameters(
        commandName = "patch")
public class PatchCommand extends DexTransformCommand {

    @Parameter(names = {"-h", "-?", "--help"}, help = true,
            description = "Show usage information")
    private boolean help;

    @Parameter(names = {"--class"},
            description = "Regex matched against the defining class descriptor (default: any class).")
    private String classRegex;

    @Parameter(names = {"--method"},
            description = "Regex matched against the method name (default: any method).")
    private String methodRegex;

    @Parameter(names = {"--return"},
            description = "Value to force-return: void, true, false, 0, 1, or null (must fit the return type).")
    private String returnValue;

    public PatchCommand(@Nonnull List<JCommander> commandAncestors) {
        super(commandAncestors);
    }

    @Override public void run() {
        if (help || inputList == null || inputList.isEmpty()) {
            usage();
            return;
        }

        if (inputList.size() > 1) {
            System.err.println("Too many files specified");
            usage();
            return;
        }

        if (returnValue == null) {
            System.err.println("--return is required (void, true, false, 0, 1, or null).");
            usage();
            return;
        }

        if (classRegex == null && methodRegex == null) {
            System.err.println("At least one of --class or --method is required to target the patch.");
            usage();
            return;
        }

        ForceReturnTransform.ReturnValue value;
        try {
            value = ForceReturnTransform.parseValue(returnValue);
        } catch (IllegalArgumentException ex) {
            System.err.println(ex.getMessage());
            usage();
            return;
        }

        String input = inputList.get(0);
        loadDexFile(input);

        ForceReturnTransform transform = new ForceReturnTransform(classRegex, methodRegex, value);
        int matchCount = transform.countMatches(dexFile);
        if (matchCount == 0) {
            System.err.println("No concrete methods matched the given --class/--method filters; nothing to patch.");
            return;
        }

        DexFile result;
        try {
            result = transform.apply(dexFile);
            // Force evaluation of every rewritten body so return-type mismatches surface before writing.
            writeResult(result);
        } catch (IllegalArgumentException ex) {
            System.err.println("Patch failed: " + ex.getMessage());
            return;
        }

        JsonObject report = TransformReport.base("patch", input, output);
        report.addProperty("matched", matchCount);
        report.addProperty("return", returnValue);
        if (classRegex != null) {
            report.addProperty("classFilter", classRegex);
        }
        if (methodRegex != null) {
            report.addProperty("methodFilter", methodRegex);
        }
        emitReport(report, "Wrote " + output + " (" + matchCount +
                " method(s) forced to return " + returnValue + ").");
    }
}
