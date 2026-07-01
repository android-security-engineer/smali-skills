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
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.jf.baksmali.output.TransformReport;
import org.jf.baksmali.transform.StringReplaceTransform;
import org.jf.dexlib2.iface.DexFile;
import org.jf.util.jcommander.ExtendedParameter;
import org.jf.util.jcommander.ExtendedParameters;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;

/**
 * The {@code baksmali replace} command: batch-rewrite string constants throughout a dex.
 *
 * <p>Rewrites both {@code const-string}/{@code const-string/jumbo} instructions and string-typed
 * encoded values (e.g. {@code static final String} initializers). Rules are applied in the order
 * given, and each string is passed through every rule in turn.
 *
 * <pre>
 *   baksmali replace app.apk --from http://old.example --to http://new.example -o patched.dex
 *   baksmali replace app.apk --from DEBUG --to RELEASE --from v1 --to v2 -o patched.dex
 *   baksmali replace app.apk --regex "key_[0-9]+" --to REDACTED -o patched.dex
 * </pre>
 *
 * <p>{@code --from}/{@code --regex} and {@code --to} are paired positionally: the Nth {@code --to}
 * pairs with the Nth {@code --from}-or-{@code --regex} in command-line order.
 */
@Parameters(commandDescription = "Batch-replace string constants (const-string and string encoded values).")
@ExtendedParameters(
        commandName = "replace")
public class ReplaceCommand extends DexTransformCommand {

    @Parameter(names = {"-h", "-?", "--help"}, help = true,
            description = "Show usage information")
    private boolean help;

    @Parameter(names = {"--from"},
            description = "A literal source string to replace. Repeatable; pairs with the next --to.")
    @ExtendedParameter(argumentNames = "text")
    private List<String> from = new ArrayList<>();

    @Parameter(names = {"--regex"},
            description = "A regex source pattern to replace ($1 group refs allowed in --to). "
                    + "Repeatable; pairs with the next --to.")
    @ExtendedParameter(argumentNames = "pattern")
    private List<String> regex = new ArrayList<>();

    @Parameter(names = {"--to"},
            description = "The replacement string. Repeatable; the Nth --to pairs with the Nth "
                    + "--from/--regex in command-line order.")
    @ExtendedParameter(argumentNames = "text")
    private List<String> to = new ArrayList<>();

    public ReplaceCommand(@Nonnull List<JCommander> commandAncestors) {
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

        int ruleCount = from.size() + regex.size();
        if (ruleCount == 0) {
            System.err.println("At least one --from or --regex (with a matching --to) is required.");
            usage();
            return;
        }
        if (ruleCount != to.size()) {
            System.err.println("Each --from/--regex must be paired with exactly one --to (" +
                    ruleCount + " source pattern(s), " + to.size() + " replacement(s)).");
            usage();
            return;
        }

        // Pair replacements to sources: literal --from rules first, then --regex rules, each in the
        // order given. --to values are consumed in the same order. The JSON rules array is built in
        // the same pass so it mirrors the exact rules that get applied.
        List<StringReplaceTransform.Rule> rules = new ArrayList<>();
        JsonArray ruleReport = new JsonArray();
        int toIndex = 0;
        for (String literal : from) {
            String replacement = to.get(toIndex++);
            rules.add(StringReplaceTransform.Rule.literal(literal, replacement));
            ruleReport.add(ruleObject("literal", literal, replacement));
        }
        for (String pattern : regex) {
            String replacement = to.get(toIndex++);
            rules.add(StringReplaceTransform.Rule.regex(pattern, replacement));
            ruleReport.add(ruleObject("regex", pattern, replacement));
        }

        String input = inputList.get(0);
        loadDexFile(input);

        DexFile result = new StringReplaceTransform(rules).apply(dexFile);
        writeResult(result);

        JsonObject report = TransformReport.base("replace", input, output);
        report.addProperty("rules", rules.size());
        report.add("ruleDetails", ruleReport);
        emitReport(report, "Wrote " + output + " (" + rules.size() + " replacement rule(s) applied).");
    }

    private static JsonObject ruleObject(@Nonnull String type, @Nonnull String from, @Nonnull String to) {
        JsonObject o = new JsonObject();
        o.addProperty("type", type);
        o.addProperty("from", from);
        o.addProperty("to", to);
        return o;
    }
}
