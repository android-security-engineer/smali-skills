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
import com.beust.jcommander.ParametersDelegate;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.jf.baksmali.formatter.BaksmaliFormatter;
import org.jf.baksmali.output.JsonOutput;
import org.jf.dexlib2.iface.reference.FieldReference;
import org.jf.dexlib2.iface.reference.MethodReference;
import org.jf.dexlib2.iface.reference.Reference;
import org.jf.dexlib2.iface.reference.TypeReference;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;

/**
 * Common machinery for the {@code baksmali xref} subcommands.
 *
 * <p>Each concrete subclass is parameterized by a reference kind (method, field, or type), and
 * resolves the user-supplied target descriptor against the reverse index built by
 * {@link ReferenceFinder}. Output is rendered as text (default) or JSON via the shared
 * {@link OutputFormatArguments}.
 *
 * <pre>
 *   baksmali xref callers  &lt;dex&gt; --target Lcom/Example;-&gt;foo()V
 *   baksmali xref field-refs &lt;dex&gt; --target Lcom/Example;-&gt;count:I
 *   baksmali xref type-refs &lt;dex&gt; --target Lcom/Example;
 * </pre>
 */
public abstract class XrefTargetCommand extends DexInputCommand {

    @Parameter(names = {"-h", "-?", "--help"}, help = true,
            description = "Show usage information")
    private boolean help;

    @ParametersDelegate
    private OutputFormatArguments outputFormat = new OutputFormatArguments();

    /**
     * The target descriptor to look up, e.g. {@code Lcom/Example;->foo()V},
     * {@code Lcom/Example;->count:I}, or {@code Lcom/Example;}. If omitted, all targets of the
     * subcommand's reference kind are listed together with their sites.
     */
    @Parameter(names = {"--target"},
            description = "The reference target to look up (exact match, or substring if no exact match). " +
                    "If omitted, all targets of this reference kind are listed.")
    protected String target;

    public XrefTargetCommand(@Nonnull List<JCommander> commandAncestors) {
        super(commandAncestors);
    }

    /**
     * @return the reference class this subcommand matches (MethodReference, FieldReference, or
     *         TypeReference).
     */
    @Nonnull
    protected abstract Class<? extends Reference> referenceClass();

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

        BaksmaliFormatter formatter = new BaksmaliFormatter();
        ReferenceFinder finder = new ReferenceFinder(formatter);
        finder.index(dexFile.getClasses());

        Class<? extends Reference> kind = referenceClass();

        // Build the list of (targetKey, sites) pairs that match the requested kind and target.
        List<String> matchedTargets = new ArrayList<>();
        for (String key : finder.getTargets()) {
            List<ReferenceFinder.ReferenceSite> sites = finder.getSites(key);
            if (sites.isEmpty()) {
                continue;
            }
            // A target key maps to sites that all share the same reference; check the first.
            Reference ref = sites.get(0).reference;
            if (!kind.isInstance(ref)) {
                continue;
            }
            if (target != null && !matches(key, target)) {
                continue;
            }
            matchedTargets.add(key);
        }

        if (matchedTargets.isEmpty()) {
            if (outputFormat.isJson()) {
                System.out.println("[]");
            } else if (target != null) {
                System.err.println("No references found matching: " + target);
            } else {
                System.err.println("No references found.");
            }
            return;
        }

        if (outputFormat.isJson()) {
            renderJson(matchedTargets, finder);
        } else {
            renderText(matchedTargets, finder);
        }
    }

    /**
     * Exact match first, then a substring fallback so partial descriptors (e.g. dropping the
     * defining class) still resolve.
     */
    private static boolean matches(@Nonnull String key, @Nonnull String target) {
        return key.equals(target) || key.contains(target);
    }

    private void renderText(@Nonnull List<String> targets, @Nonnull ReferenceFinder finder) {
        for (String targetKey : targets) {
            System.out.println(targetKey);
            for (ReferenceFinder.ReferenceSite site : finder.getSites(targetKey)) {
                System.out.println("  " + site.caller + " @ offset 0x" +
                        Integer.toHexString(site.codeOffset));
            }
        }
    }

    private void renderJson(@Nonnull List<String> targets, @Nonnull ReferenceFinder finder) {
        JsonOutput jsonOutput = new JsonOutput();
        List<JsonObject> objects = new ArrayList<>();
        for (String targetKey : targets) {
            JsonObject entry = new JsonObject();
            entry.addProperty("target", targetKey);
            List<JsonObject> siteObjs = new ArrayList<>();
            for (ReferenceFinder.ReferenceSite site : finder.getSites(targetKey)) {
                JsonObject siteObj = new JsonObject();
                siteObj.addProperty("caller", site.caller);
                siteObj.addProperty("offset", "0x" + Integer.toHexString(site.codeOffset));
                siteObjs.add(siteObj);
            }
            JsonArray sitesArray = new JsonArray();
            for (JsonObject siteObj : siteObjs) {
                sitesArray.add(siteObj);
            }
            entry.add("sites", sitesArray);
            objects.add(entry);
        }
        System.out.println(jsonOutput.toJsonArray(objects));
    }
}
