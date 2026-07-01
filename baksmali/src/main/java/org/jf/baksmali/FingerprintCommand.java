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
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.jf.baksmali.fingerprint.Fingerprint;
import org.jf.dexlib2.DexFileFactory;
import org.jf.dexlib2.dexbacked.DexBackedDexFile;
import org.jf.dexlib2.iface.ClassDef;
import org.jf.dexlib2.iface.Method;
import org.jf.util.jcommander.ExtendedParameters;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * {@code baksmali fingerprint} — opcode-based, rename-invariant fingerprints for library/clone
 * identification.
 *
 * <p>Two modes:
 * <ul>
 *   <li><b>List</b> (default): print a fingerprint hash per method/class/dex ({@code --level}).
 *       Because the hash is derived only from opcodes, renaming leaves it unchanged.</li>
 *   <li><b>Match</b> ({@code --match ref.dex}): for each input class, find the most similar class in
 *       a reference dex by opcode n-gram Jaccard similarity, reporting pairs at or above
 *       {@code --min-similarity}. Point {@code --match} at a known library's dex to spot that library
 *       inside an app even after obfuscation.</li>
 * </ul>
 *
 * <pre>
 *   baksmali fingerprint app.apk                              # per-class hashes
 *   baksmali fingerprint app.apk --level method --format json
 *   baksmali fingerprint app.apk --match okhttp.dex --min-similarity 0.9
 * </pre>
 */
@Parameters(commandDescription = "Opcode-based rename-invariant fingerprints; match against a reference dex to identify libraries/clones.")
@ExtendedParameters(
        commandName = "fingerprint",
        commandAliases = { "fp" })
public class FingerprintCommand extends DexInputCommand {

    @Parameter(names = {"-h", "-?", "--help"}, help = true,
            description = "Show usage information")
    private boolean help;

    @ParametersDelegate
    private OutputFormatArguments outputFormat = new OutputFormatArguments();

    @Parameter(names = {"--level"},
            description = "Granularity for list mode: method, class, or dex (default: class).")
    private String level = "class";

    @Parameter(names = {"--class"},
            description = "Regex restricting which classes (by type descriptor) are fingerprinted/matched.")
    private String classRegex;

    @Parameter(names = {"--ngram"},
            description = "Opcode n-gram size used for --match similarity (default: 3).")
    private int ngram = 3;

    @Parameter(names = {"--match"},
            description = "A reference dex/apk to match input classes against by n-gram similarity.")
    private String matchRef;

    @Parameter(names = {"--min-similarity"},
            description = "Minimum n-gram Jaccard similarity [0,1] to report a match (default: 0.85).")
    private double minSimilarity = 0.85;

    public FingerprintCommand(@Nonnull List<JCommander> commandAncestors) {
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

        loadDexFile(inputList.get(0));
        Pattern classPattern = classRegex == null || classRegex.isEmpty() ? null : Pattern.compile(classRegex);

        if (matchRef != null) {
            runMatch(classPattern);
        } else {
            runList(classPattern);
        }
    }

    // -- list mode -----------------------------------------------------------

    private void runList(@Nullable Pattern classPattern) {
        String lvl = level == null ? "class" : level.toLowerCase();
        switch (lvl) {
            case "dex":
                listDex();
                return;
            case "method":
                listMethods(classPattern);
                return;
            case "class":
                listClasses(classPattern);
                return;
            default:
                System.err.println("Unknown --level '" + level + "' (expected method, class, or dex).");
                usage();
        }
    }

    private void listDex() {
        String hash = Fingerprint.dexHash(dexFile);
        if (outputFormat.isJson()) {
            JsonObject obj = new JsonObject();
            obj.addProperty("level", "dex");
            obj.addProperty("fingerprint", hash);
            System.out.println(gson().toJson(obj));
        } else {
            System.out.println(hash + "  " + (inputEntry != null ? inputEntry : inputFile.getName()));
        }
    }

    private void listClasses(@Nullable Pattern classPattern) {
        JsonArray array = new JsonArray();
        for (ClassDef classDef : dexFile.getClasses()) {
            if (classPattern != null && !classPattern.matcher(classDef.getType()).find()) {
                continue;
            }
            String hash = Fingerprint.classHash(classDef);
            if (outputFormat.isJson()) {
                JsonObject obj = new JsonObject();
                obj.addProperty("class", classDef.getType());
                obj.addProperty("fingerprint", hash);
                array.add(obj);
            } else {
                System.out.println(hash + "  " + classDef.getType());
            }
        }
        if (outputFormat.isJson()) {
            System.out.println(gson().toJson(array));
        }
    }

    private void listMethods(@Nullable Pattern classPattern) {
        JsonArray array = new JsonArray();
        for (ClassDef classDef : dexFile.getClasses()) {
            if (classPattern != null && !classPattern.matcher(classDef.getType()).find()) {
                continue;
            }
            for (Method method : classDef.getMethods()) {
                String hash = Fingerprint.methodHash(method);
                String descriptor = descriptor(method);
                if (outputFormat.isJson()) {
                    JsonObject obj = new JsonObject();
                    obj.addProperty("method", descriptor);
                    obj.addProperty("fingerprint", hash);
                    array.add(obj);
                } else {
                    System.out.println(hash + "  " + descriptor);
                }
            }
        }
        if (outputFormat.isJson()) {
            System.out.println(gson().toJson(array));
        }
    }

    // -- match mode ----------------------------------------------------------

    private void runMatch(@Nullable Pattern classPattern) {
        DexBackedDexFile ref = loadReference(matchRef);
        if (ref == null) {
            return;
        }

        // Precompute reference class n-gram profiles once.
        List<String> refTypes = new ArrayList<>();
        List<Map<String, Integer>> refProfiles = new ArrayList<>();
        for (ClassDef classDef : ref.getClasses()) {
            refTypes.add(classDef.getType());
            refProfiles.add(Fingerprint.classNgramProfile(classDef, ngram));
        }

        JsonArray array = new JsonArray();
        boolean any = false;
        for (ClassDef classDef : dexFile.getClasses()) {
            if (classPattern != null && !classPattern.matcher(classDef.getType()).find()) {
                continue;
            }
            Map<String, Integer> profile = Fingerprint.classNgramProfile(classDef, ngram);
            if (profile.isEmpty()) {
                continue; // interface/empty class — nothing to match on
            }

            double bestScore = -1;
            String bestType = null;
            for (int i = 0; i < refProfiles.size(); i++) {
                double score = Fingerprint.jaccard(profile, refProfiles.get(i));
                if (score > bestScore) {
                    bestScore = score;
                    bestType = refTypes.get(i);
                }
            }

            if (bestType != null && bestScore >= minSimilarity) {
                any = true;
                if (outputFormat.isJson()) {
                    JsonObject obj = new JsonObject();
                    obj.addProperty("class", classDef.getType());
                    obj.addProperty("match", bestType);
                    obj.addProperty("similarity", round(bestScore));
                    array.add(obj);
                } else {
                    System.out.printf("%.3f  %s  ~=  %s%n", bestScore, classDef.getType(), bestType);
                }
            }
        }

        if (outputFormat.isJson()) {
            System.out.println(gson().toJson(array));
        } else if (!any) {
            System.err.println("No classes matched at similarity >= " + minSimilarity + ".");
        }
    }

    @Nullable
    private DexBackedDexFile loadReference(@Nonnull String path) {
        File file = new File(path);
        if (!file.exists()) {
            System.err.println("Can't find reference file: " + path);
            return null;
        }
        try {
            return DexFileFactory.loadDexFile(file, null);
        } catch (IOException ex) {
            System.err.println("Failed to read reference: " + ex.getMessage());
            return null;
        }
    }

    // -- helpers -------------------------------------------------------------

    private static double round(double value) {
        return Math.round(value * 1000.0) / 1000.0;
    }

    @Nonnull
    private static com.google.gson.Gson gson() {
        return new GsonBuilder().disableHtmlEscaping().create();
    }

    @Nonnull
    private static String descriptor(@Nonnull Method method) {
        StringBuilder sb = new StringBuilder();
        sb.append(method.getDefiningClass()).append("->").append(method.getName()).append('(');
        for (CharSequence param : method.getParameterTypes()) {
            sb.append(param);
        }
        sb.append(')').append(method.getReturnType());
        return sb.toString();
    }
}
