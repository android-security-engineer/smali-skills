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

import org.jf.baksmali.formatter.BaksmaliFormatter;
import org.jf.dexlib2.iface.ClassDef;
import org.jf.dexlib2.iface.Method;
import org.jf.dexlib2.iface.MethodImplementation;
import org.jf.dexlib2.iface.instruction.Instruction;
import org.jf.dexlib2.iface.instruction.ReferenceInstruction;
import org.jf.dexlib2.iface.reference.Reference;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds a reverse cross-reference index over a dex file: for every reference target, it collects
 * the list of (calling method, code offset) sites that reference it.
 *
 * <p>This is the query-layer foundation for the {@code baksmali xref} commands. It walks every
 * class &rarr; method &rarr; instruction, and for each {@link ReferenceInstruction} records the
 * site against a string key derived from the reference via a {@link BaksmaliFormatter}.
 */
public class ReferenceFinder {

    /**
     * A single site that references a target: the formatted descriptor of the containing method
     * and the byte offset (in code units) of the referencing instruction within that method's body.
     */
    public static class ReferenceSite {
        @Nonnull public final String caller;
        public final int codeOffset;
        @Nonnull public final Reference reference;

        public ReferenceSite(@Nonnull String caller, int codeOffset, @Nonnull Reference reference) {
            this.caller = caller;
            this.codeOffset = codeOffset;
            this.reference = reference;
        }
    }

    private final BaksmaliFormatter formatter;
    private final Map<String, List<ReferenceSite>> references = new LinkedHashMap<>();

    public ReferenceFinder() {
        this(new BaksmaliFormatter());
    }

    public ReferenceFinder(@Nonnull BaksmaliFormatter formatter) {
        this.formatter = formatter;
    }

    /**
     * Indexes all references reachable from the given classes.
     */
    public void index(@Nonnull Iterable<? extends ClassDef> classes) {
        for (ClassDef classDef : classes) {
            String definingClass = classDef.getType();
            for (Method method : classDef.getMethods()) {
                MethodImplementation impl = method.getImplementation();
                if (impl == null) {
                    continue;
                }
                indexMethod(definingClass, method, impl);
            }
        }
    }

    private void indexMethod(@Nonnull String definingClass, @Nonnull Method method,
                             @Nonnull MethodImplementation impl) {
        String caller = formatter.getType(definingClass) + "->" +
                method.getName() + "(" + join(method.getParameterTypes()) + ")" +
                method.getReturnType();
        int codeOffset = 0;
        for (Instruction instruction : impl.getInstructions()) {
            if (instruction instanceof ReferenceInstruction) {
                Reference reference = ((ReferenceInstruction) instruction).getReference();
                String key = formatter.getReference(reference);
                references.computeIfAbsent(key, k -> new ArrayList<>())
                        .add(new ReferenceSite(caller, codeOffset, reference));
            }
            codeOffset += instruction.getCodeUnits();
        }
    }

    private static String join(@Nonnull Iterable<? extends CharSequence> parts) {
        StringBuilder sb = new StringBuilder();
        for (CharSequence p : parts) {
            sb.append(p);
        }
        return sb.toString();
    }

    /**
     * @return the set of reference targets that have at least one site, in insertion order.
     */
    @Nonnull
    public List<String> getTargets() {
        return new ArrayList<>(references.keySet());
    }

    /**
     * @return the sites referencing the given target key, or an empty list if none.
     */
    @Nonnull
    public List<ReferenceSite> getSites(@Nonnull String targetKey) {
        List<ReferenceSite> sites = references.get(targetKey);
        return sites == null ? new ArrayList<ReferenceSite>() : sites;
    }

    /**
     * @return the full reverse map (target key &rarr; sites). Returned map is live; do not mutate.
     */
    @Nonnull
    public Map<String, List<ReferenceSite>> getReferences() {
        return references;
    }
}
