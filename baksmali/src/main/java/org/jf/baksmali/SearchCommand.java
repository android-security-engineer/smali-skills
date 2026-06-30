/*
 * Copyright 2026, Google Inc.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms with or without
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
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import org.jf.baksmali.output.JsonOutput;
import org.jf.dexlib2.iface.ClassDef;
import org.jf.dexlib2.iface.Method;
import org.jf.util.jcommander.ExtendedParameters;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * {@code baksmali search} — pattern-based search over method instruction streams.
 *
 * <p>Three complementary selectors:
 * <ul>
 *   <li>{@code --opcode <seq>} — a comma-separated opcode pattern (e.g.
 *       {@code const-string,invoke-virtual}); {@code *} matches any single opcode.</li>
 *   <li>{@code --class <regex>} — restrict to classes whose type matches the regex.</li>
 *   <li>{@code --method <regex>} — restrict to methods whose name matches the regex.</li>
 * </ul>
 *
 * <p>When {@code --opcode} is given, matches are reported as {@code class->method @ offset}
 * plus the matched instructions. When only {@code --class}/{@code --method} are given, the
 * matching classes/methods are listed. Output supports {@code --format json}.
 *
 * <pre>
 *   baksmali search app.apk --opcode const-string,invoke-virtual
 *   baksmali search app.apk --class Lcom/.* --method onCreate
 * </pre>
 */
@Parameters(commandDescription = "Search method instruction streams by opcode pattern and/or class/method regex.")
@ExtendedParameters(
        commandName = "search",
        commandAliases = { "find" })
public class SearchCommand extends DexInputCommand {

    @Parameter(names = {"-h", "-?", "--help"}, help = true,
            description = "Show usage information")
    private boolean help;

    @ParametersDelegate
    private OutputFormatArguments outputFormat = new OutputFormatArguments();

    @Parameter(names = {"--opcode"},
            description = "Comma-separated opcode pattern to match (e.g. const-string,invoke-virtual). " +
                    "Use '*' to match any single opcode.")
    protected String opcodePattern;

    @Parameter(names = {"--class"},
            description = "Regex restricting matches to classes whose type descriptor matches.")
    protected String classRegex;

    @Parameter(names = {"--method"},
            description = "Regex restricting matches to methods whose name matches.")
    protected String methodRegex;

    public SearchCommand(@Nonnull List<JCommander> commandAncestors) {
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

        String input = inputList.get(0);
        loadDexFile(input);

        Pattern classPattern = compile(classRegex);
        Pattern methodPattern = compile(methodRegex);
        List<String> opcodes = PatternSearcher.parsePattern(opcodePattern);

        if (opcodes.isEmpty()) {
            // No opcode pattern: list matching classes/methods.
            listMatching(classPattern, methodPattern);
            return;
        }

        // Opcode search: walk classes, filtering by class/method regex, then run the searcher.
        PatternSearcher searcher = new PatternSearcher();
        List<PatternSearcher.Match> matches = new ArrayList<>();
        for (ClassDef classDef : dexFile.getClasses()) {
            if (classPattern != null && !classPattern.matcher(classDef.getType()).find()) {
                continue;
            }
            // The searcher walks methods itself and builds caller descriptors; we apply the
            // method-name filter after matching.
            for (PatternSearcher.Match m : searcher.search(java.util.Collections.singleton(classDef), opcodes)) {
                if (methodPattern != null && !methodPattern.matcher(extractMethodName(m.caller)).find()) {
                    continue;
                }
                matches.add(m);
            }
        }

        if (matches.isEmpty()) {
            if (outputFormat.isJson()) {
                System.out.println("[]");
            } else {
                System.err.println("No matches found.");
            }
            return;
        }

        if (outputFormat.isJson()) {
            renderJson(matches);
        } else {
            renderText(matches);
        }
    }

    private void listMatching(@Nonnull Pattern classPattern, @Nonnull Pattern methodPattern) {
        JsonOutput jsonOutput = outputFormat.isJson() ? new JsonOutput() : null;
        List<JsonObject> objects = jsonOutput != null ? new ArrayList<JsonObject>() : null;
        boolean any = false;
        for (ClassDef classDef : dexFile.getClasses()) {
            if (classPattern != null && !classPattern.matcher(classDef.getType()).find()) {
                continue;
            }
            for (Method method : classDef.getMethods()) {
                if (methodPattern != null && !methodPattern.matcher(method.getName()).find()) {
                    continue;
                }
                any = true;
                String caller = classDef.getType() + "->" + method.getName();
                if (outputFormat.isJson()) {
                    JsonObject obj = new JsonObject();
                    obj.addProperty("class", classDef.getType());
                    obj.addProperty("method", method.getName());
                    obj.addProperty("returnType", method.getReturnType());
                    objects.add(obj);
                } else {
                    System.out.println(caller);
                }
            }
        }
        if (!any) {
            if (outputFormat.isJson()) {
                System.out.println("[]");
            } else {
                System.err.println("No matching classes/methods found.");
            }
            return;
        }
        if (outputFormat.isJson()) {
            System.out.println(jsonOutput.toJsonArray(objects));
        }
    }

    private void renderText(@Nonnull List<PatternSearcher.Match> matches) {
        for (PatternSearcher.Match match : matches) {
            System.out.println(match.caller + " @ offset 0x" +
                    Integer.toHexString(match.codeOffset));
            for (String insn : match.instructions) {
                System.out.println("  " + insn);
            }
        }
    }

    private void renderJson(@Nonnull List<PatternSearcher.Match> matches) {
        JsonOutput jsonOutput = new JsonOutput();
        List<JsonObject> objects = new ArrayList<>();
        for (PatternSearcher.Match match : matches) {
            JsonObject obj = new JsonObject();
            obj.addProperty("caller", match.caller);
            obj.addProperty("offset", "0x" + Integer.toHexString(match.codeOffset));
            JsonArray insns = new JsonArray();
            for (String insn : match.instructions) {
                insns.add(new JsonPrimitive(insn));
            }
            obj.add("instructions", insns);
            objects.add(obj);
        }
        System.out.println(jsonOutput.toJsonArray(objects));
    }

    @Nonnull
    private static Pattern compile(@javax.annotation.Nullable String regex) {
        if (regex == null || regex.isEmpty()) {
            return null;
        }
        return Pattern.compile(regex);
    }

    @Nonnull
    private static String extractMethodName(@Nonnull String callerDescriptor) {
        // callerDescriptor is like "Lcls;->methodName(params)ret"
        int arrow = callerDescriptor.indexOf("->");
        if (arrow < 0) {
            return callerDescriptor;
        }
        int paren = callerDescriptor.indexOf('(', arrow);
        if (paren < 0) {
            return callerDescriptor.substring(arrow + 2);
        }
        return callerDescriptor.substring(arrow + 2, paren);
    }
}
