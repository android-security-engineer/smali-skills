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
import org.jf.baksmali.transform.AccessFlagTransform;
import org.jf.dexlib2.iface.DexFile;
import org.jf.util.jcommander.ExtendedParameters;

import javax.annotation.Nonnull;
import java.util.List;

/**
 * The {@code baksmali unlock} command: batch-rewrite access flags across a dex.
 *
 * <p>Two independent modifications, each on by default (running with no flags applies both):
 * <ul>
 *   <li>{@code --public} — clear {@code private}/{@code protected} and set {@code public} on every
 *       class, method, and field, so hidden members become callable.</li>
 *   <li>{@code --no-final} — clear {@code final}, so classes can be subclassed and methods
 *       overridden.</li>
 * </ul>
 *
 * <pre>
 *   baksmali unlock app.apk -o unlocked.dex              # both (default)
 *   baksmali unlock app.apk --public -o public.dex       # publicize only
 *   baksmali unlock app.apk --no-final -o open.dex       # definalize only
 * </pre>
 */
@Parameters(commandDescription = "Batch-modify access flags: publicize and/or definalize every class, method and field.")
@ExtendedParameters(
        commandName = "unlock")
public class UnlockCommand extends DexTransformCommand {

    @Parameter(names = {"-h", "-?", "--help"}, help = true,
            description = "Show usage information")
    private boolean help;

    @Parameter(names = {"--public"},
            description = "Make every class/method/field public (clears private/protected).")
    private boolean publicize;

    @Parameter(names = {"--no-final"},
            description = "Remove the final modifier from every class/method/field.")
    private boolean definalize;

    public UnlockCommand(@Nonnull List<JCommander> commandAncestors) {
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

        // With no explicit selection, apply both transformations — the common "unlock everything".
        boolean doPublic = publicize;
        boolean doFinal = definalize;
        if (!doPublic && !doFinal) {
            doPublic = true;
            doFinal = true;
        }

        String input = inputList.get(0);
        loadDexFile(input);

        DexFile result = new AccessFlagTransform(doPublic, doFinal).apply(dexFile);
        writeResult(result);

        JsonObject report = TransformReport.base("unlock", input, output);
        report.addProperty("publicized", doPublic);
        report.addProperty("definalized", doFinal);
        String humanText = "Wrote " + output + " (" +
                (doPublic ? "publicized" : "") +
                (doPublic && doFinal ? ", " : "") +
                (doFinal ? "definalized" : "") + ").";
        emitReport(report, humanText);
    }
}
