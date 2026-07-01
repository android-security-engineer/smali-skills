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

package org.jf.baksmali.transform;

import org.jf.dexlib2.Opcode;
import org.jf.dexlib2.ReferenceType;
import org.jf.dexlib2.ValueType;
import org.jf.dexlib2.iface.DexFile;
import org.jf.dexlib2.iface.instruction.Instruction;
import org.jf.dexlib2.iface.instruction.OneRegisterInstruction;
import org.jf.dexlib2.iface.instruction.ReferenceInstruction;
import org.jf.dexlib2.iface.reference.StringReference;
import org.jf.dexlib2.iface.value.EncodedValue;
import org.jf.dexlib2.iface.value.StringEncodedValue;
import org.jf.dexlib2.immutable.instruction.ImmutableInstruction21c;
import org.jf.dexlib2.immutable.instruction.ImmutableInstruction31c;
import org.jf.dexlib2.immutable.reference.ImmutableStringReference;
import org.jf.dexlib2.immutable.value.ImmutableStringEncodedValue;
import org.jf.dexlib2.rewriter.DexRewriter;
import org.jf.dexlib2.rewriter.EncodedValueRewriter;
import org.jf.dexlib2.rewriter.InstructionRewriter;
import org.jf.dexlib2.rewriter.Rewriter;
import org.jf.dexlib2.rewriter.RewriterModule;
import org.jf.dexlib2.rewriter.Rewriters;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A pure, I/O-free string-constant rewrite over a {@link DexFile}. It applies an ordered list of
 * {@link Rule}s to every string literal in the dex — both {@code const-string}/{@code const-string/jumbo}
 * instructions and string-typed {@code encoded values} (e.g. {@code static final String} initializers).
 *
 * <p>The string-reference position of the dexlib2 rewriter framework is a pass-through (the framework
 * only rewrites type/field/method references), so this class supplies its own {@link InstructionRewriter}
 * and {@link EncodedValueRewriter} overrides that rebuild the affected elements with the replacement text.
 *
 * <p>Typical uses: neutralize a hard-coded URL/API-key placeholder, redirect a logging tag, or swap a
 * feature-flag string for instrumentation. The {@link #replace(String)} logic is separated from the dex
 * plumbing so it can be unit-tested directly.
 */
public class StringReplaceTransform {

    /** A single replacement rule: either a literal substring swap or a regex substitution. */
    public static class Rule {
        private final boolean regex;
        private final String from;
        private final String to;
        private final Pattern pattern;

        private Rule(boolean regex, @Nonnull String from, @Nonnull String to) {
            this.regex = regex;
            this.from = from;
            this.to = to;
            this.pattern = regex ? Pattern.compile(from) : null;
        }

        /** A literal, all-occurrences substring replacement (no regex interpretation). */
        @Nonnull public static Rule literal(@Nonnull String from, @Nonnull String to) {
            return new Rule(false, from, to);
        }

        /** A regex replacement; {@code to} may reference capture groups with {@code $1} etc. */
        @Nonnull public static Rule regex(@Nonnull String pattern, @Nonnull String replacement) {
            return new Rule(true, pattern, replacement);
        }

        @Nonnull String apply(@Nonnull String input) {
            if (regex) {
                return pattern.matcher(input).replaceAll(to);
            }
            // Literal replacement without regex semantics on either side.
            if (from.isEmpty()) {
                return input;
            }
            return input.replace(from, to);
        }
    }

    private final List<Rule> rules;

    public StringReplaceTransform(@Nonnull List<Rule> rules) {
        this.rules = rules;
    }

    /**
     * Applies every configured rule, in order, to a single string. A later rule sees the output of the
     * earlier ones.
     */
    @Nonnull public String replace(@Nonnull String input) {
        String result = input;
        for (Rule rule : rules) {
            result = rule.apply(result);
        }
        return result;
    }

    /**
     * @return a lazily-rewritten view of {@code in} with every string constant passed through
     *         {@link #replace(String)}.
     */
    @Nonnull
    public DexFile apply(@Nonnull DexFile in) {
        RewriterModule module = new RewriterModule() {
            @Nonnull @Override
            public Rewriter<Instruction> getInstructionRewriter(@Nonnull Rewriters rewriters) {
                return new InstructionRewriter(rewriters) {
                    @Nonnull @Override public Instruction rewrite(@Nonnull Instruction instruction) {
                        if (instruction instanceof ReferenceInstruction
                                && instruction instanceof OneRegisterInstruction
                                && ((ReferenceInstruction) instruction).getReferenceType() == ReferenceType.STRING) {
                            String original = ((StringReference) ((ReferenceInstruction) instruction)
                                    .getReference()).getString();
                            String replaced = replace(original);
                            if (!replaced.equals(original)) {
                                Opcode opcode = instruction.getOpcode();
                                int registerA = ((OneRegisterInstruction) instruction).getRegisterA();
                                ImmutableStringReference newRef = new ImmutableStringReference(replaced);
                                if (opcode == Opcode.CONST_STRING) {
                                    return new ImmutableInstruction21c(opcode, registerA, newRef);
                                }
                                // CONST_STRING_JUMBO (format 31c)
                                return new ImmutableInstruction31c(opcode, registerA, newRef);
                            }
                        }
                        return super.rewrite(instruction);
                    }
                };
            }

            @Nonnull @Override
            public Rewriter<EncodedValue> getEncodedValueRewriter(@Nonnull Rewriters rewriters) {
                return new EncodedValueRewriter(rewriters) {
                    @Nonnull @Override public EncodedValue rewrite(@Nonnull EncodedValue encodedValue) {
                        if (encodedValue.getValueType() == ValueType.STRING) {
                            String original = ((StringEncodedValue) encodedValue).getValue();
                            String replaced = replace(original);
                            if (!replaced.equals(original)) {
                                return new ImmutableStringEncodedValue(replaced);
                            }
                            return encodedValue;
                        }
                        return super.rewrite(encodedValue);
                    }
                };
            }
        };
        return new DexRewriter(module).getDexFileRewriter().rewrite(in);
    }
}
