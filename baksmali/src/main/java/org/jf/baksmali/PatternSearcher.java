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

import org.jf.baksmali.formatter.BaksmaliFormatter;
import org.jf.dexlib2.Opcode;
import org.jf.dexlib2.iface.ClassDef;
import org.jf.dexlib2.iface.Method;
import org.jf.dexlib2.iface.MethodImplementation;
import org.jf.dexlib2.iface.instruction.Instruction;
import org.jf.dexlib2.iface.instruction.ReferenceInstruction;
import org.jf.dexlib2.iface.reference.Reference;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * Searches method instruction streams for a contiguous opcode pattern, supporting a single
 * {@code *} wildcard token that matches any one opcode.
 *
 * <p>This is the engine behind {@code baksmali search --opcode}. It walks every class &rarr;
 * method &rarr; instruction, and at each starting position attempts to match the pattern against
 * the following instructions. Matches carry the method descriptor, the code offset of the first
 * matched instruction, and the formatted instructions that matched.
 */
public class PatternSearcher {

    /**
     * A single pattern match: the method that contains it, the code offset of the first matched
     * instruction, and the formatted text of the matched instructions.
     */
    public static class Match {
        @Nonnull public final String caller;
        public final int codeOffset;
        @Nonnull public final List<String> instructions;

        public Match(@Nonnull String caller, int codeOffset, @Nonnull List<String> instructions) {
            this.caller = caller;
            this.codeOffset = codeOffset;
            this.instructions = instructions;
        }
    }

    private final BaksmaliFormatter formatter;

    public PatternSearcher() {
        this(new BaksmaliFormatter());
    }

    public PatternSearcher(@Nonnull BaksmaliFormatter formatter) {
        this.formatter = formatter;
    }

    /**
     * Parses a comma-separated opcode pattern (e.g. {@code "const-string,invoke-virtual"}) into
     * a list of tokens. The token {@code *} matches any single opcode. Whitespace around tokens
     * is trimmed. An empty pattern yields an empty list.
     */
    @Nonnull
    public static List<String> parsePattern(@Nullable String pattern) {
        List<String> tokens = new ArrayList<>();
        if (pattern == null || pattern.trim().isEmpty()) {
            return tokens;
        }
        for (String token : pattern.split(",")) {
            tokens.add(token.trim());
        }
        return tokens;
    }

    /**
     * Searches all methods in the given classes for occurrences of the opcode pattern.
     *
     * @return the list of matches, in walk order.
     */
    @Nonnull
    public List<Match> search(@Nonnull Iterable<? extends ClassDef> classes,
                              @Nonnull List<String> pattern) {
        List<Match> matches = new ArrayList<>();
        if (pattern.isEmpty()) {
            return matches;
        }
        for (ClassDef classDef : classes) {
            String definingClass = classDef.getType();
            for (Method method : classDef.getMethods()) {
                MethodImplementation impl = method.getImplementation();
                if (impl == null) {
                    continue;
                }
                searchMethod(definingClass, method, impl, pattern, matches);
            }
        }
        return matches;
    }

    private void searchMethod(@Nonnull String definingClass, @Nonnull Method method,
                              @Nonnull MethodImplementation impl, @Nonnull List<String> pattern,
                              @Nonnull List<Match> out) {
        // Materialize the instruction list (with their code offsets) so we can do sliding-window
        // subsequence matching.
        List<Instruction> instructions = new ArrayList<>();
        List<Integer> offsets = new ArrayList<>();
        int codeOffset = 0;
        for (Instruction instruction : impl.getInstructions()) {
            instructions.add(instruction);
            offsets.add(codeOffset);
            codeOffset += instruction.getCodeUnits();
        }

        String caller = buildCallerDescriptor(definingClass, method);
        int patternLen = pattern.size();

        for (int start = 0; start + patternLen <= instructions.size(); start++) {
            if (matchesAt(instructions, start, pattern)) {
                List<String> matchedText = new ArrayList<>(patternLen);
                for (int i = 0; i < patternLen; i++) {
                    matchedText.add(formatInstruction(instructions.get(start + i)));
                }
                out.add(new Match(caller, offsets.get(start), matchedText));
                // Advance by 1 to find overlapping matches too.
            }
        }
    }

    private static boolean matchesAt(@Nonnull List<Instruction> instructions, int start,
                                     @Nonnull List<String> pattern) {
        for (int i = 0; i < pattern.size(); i++) {
            String token = pattern.get(i);
            if (token.equals("*")) {
                continue;
            }
            Opcode opcode = instructions.get(start + i).getOpcode();
            if (!token.equalsIgnoreCase(opcode.name)) {
                return false;
            }
        }
        return true;
    }

    @Nonnull
    private String formatInstruction(@Nonnull Instruction instruction) {
        if (instruction instanceof ReferenceInstruction) {
            Reference reference = ((ReferenceInstruction) instruction).getReference();
            // e.g. "invoke-virtual {v0}, Ljava/lang/StringBuilder;->append(...)..."
            return instruction.getOpcode().name + " " + formatter.getReference(reference);
        }
        return instruction.getOpcode().name;
    }

    @Nonnull
    private String buildCallerDescriptor(@Nonnull String definingClass, @Nonnull Method method) {
        StringBuilder sb = new StringBuilder();
        sb.append(formatter.getType(definingClass)).append("->").append(method.getName()).append("(");
        for (CharSequence param : method.getParameterTypes()) {
            sb.append(param);
        }
        sb.append(")").append(method.getReturnType());
        return sb.toString();
    }
}
