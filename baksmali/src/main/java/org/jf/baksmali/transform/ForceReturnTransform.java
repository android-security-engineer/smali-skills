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
import org.jf.dexlib2.iface.DexFile;
import org.jf.dexlib2.iface.Method;
import org.jf.dexlib2.iface.MethodImplementation;
import org.jf.dexlib2.immutable.instruction.ImmutableInstruction10x;
import org.jf.dexlib2.immutable.instruction.ImmutableInstruction11n;
import org.jf.dexlib2.immutable.instruction.ImmutableInstruction11x;
import org.jf.dexlib2.immutable.instruction.ImmutableInstruction31i;
import org.jf.dexlib2.immutable.instruction.ImmutableInstruction51l;
import org.jf.dexlib2.immutable.instruction.ImmutableInstruction;
import org.jf.dexlib2.immutable.ImmutableMethodImplementation;
import org.jf.dexlib2.rewriter.DexRewriter;
import org.jf.dexlib2.rewriter.MethodRewriter;
import org.jf.dexlib2.rewriter.Rewriter;
import org.jf.dexlib2.rewriter.RewriterModule;
import org.jf.dexlib2.rewriter.Rewriters;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * A pure, I/O-free method-body patch: it replaces the body of every method whose
 * {@code definingClass}/{@code name} matches the configured regexes with a minimal body that
 * <em>immediately returns</em> a chosen value of the method's own return type.
 *
 * <p>This "neuter the method" operation is a reverse-engineering staple: force a licence/root/SSL
 * check to {@code return true}, silence a callback with {@code return-void}, or make a getter yield
 * {@code null}. The replacement register set is rebuilt from scratch (a single low register suffices),
 * and debug info + try/catch blocks are dropped along with the old body.
 *
 * <p>The instruction-synthesis logic in {@link #buildReturnBody(String)} is separated from the dex
 * plumbing so it can be unit-tested directly. Only value kinds compatible with the method's return
 * type are accepted; an incompatible request throws {@link IllegalArgumentException}.
 */
public class ForceReturnTransform {

    /** The kind of value to return, as requested on the command line. */
    public enum ReturnValue {
        VOID, TRUE, FALSE, ZERO, ONE, NULL
    }

    private final Pattern classPattern;
    private final Pattern methodPattern;
    private final ReturnValue value;

    /**
     * @param classRegex  regex matched against the defining class descriptor (null = any class)
     * @param methodRegex regex matched against the method name (null = any method)
     * @param value       the value to force the matched methods to return
     */
    public ForceReturnTransform(String classRegex, String methodRegex, @Nonnull ReturnValue value) {
        this.classPattern = classRegex == null ? null : Pattern.compile(classRegex);
        this.methodPattern = methodRegex == null ? null : Pattern.compile(methodRegex);
        this.value = value;
    }

    /** Parses a command-line value spec (case-insensitive) into a {@link ReturnValue}. */
    @Nonnull
    public static ReturnValue parseValue(@Nonnull String spec) {
        switch (spec.toLowerCase()) {
            case "void": return ReturnValue.VOID;
            case "true": return ReturnValue.TRUE;
            case "false": return ReturnValue.FALSE;
            case "0": return ReturnValue.ZERO;
            case "1": return ReturnValue.ONE;
            case "null": return ReturnValue.NULL;
            default:
                throw new IllegalArgumentException(
                        "Unsupported --return value '" + spec + "'. Use one of: void, true, false, 0, 1, null.");
        }
    }

    private boolean matches(@Nonnull Method method) {
        if (classPattern != null && !classPattern.matcher(method.getDefiningClass()).find()) {
            return false;
        }
        if (methodPattern != null && !methodPattern.matcher(method.getName()).find()) {
            return false;
        }
        return true;
    }

    /**
     * Builds a fresh, immediately-returning body appropriate for {@code returnType}, using the
     * configured {@link ReturnValue}. Package-visible for testing.
     *
     * @throws IllegalArgumentException if the requested value is incompatible with the return type
     */
    @Nonnull
    MethodImplementation buildReturnBody(@Nonnull String returnType) {
        char kind = returnType.charAt(0);
        List<ImmutableInstruction> instructions = new ArrayList<>();
        int registerCount;

        switch (kind) {
            case 'V': {
                requireValue(ReturnValue.VOID, "void", returnType);
                instructions.add(new ImmutableInstruction10x(Opcode.RETURN_VOID));
                registerCount = 0;
                break;
            }
            case 'Z': case 'B': case 'S': case 'C': case 'I': {
                int literal = intLiteralFor(returnType);
                // const/4 covers 0/1 in a nibble; use const/4 for compactness.
                instructions.add(new ImmutableInstruction11n(Opcode.CONST_4, 0, literal));
                instructions.add(new ImmutableInstruction11x(Opcode.RETURN, 0));
                registerCount = 1;
                break;
            }
            case 'J': {
                long literal = wideLiteralFor(returnType);
                instructions.add(new ImmutableInstruction51l(Opcode.CONST_WIDE, 0, literal));
                instructions.add(new ImmutableInstruction11x(Opcode.RETURN_WIDE, 0));
                registerCount = 2;
                break;
            }
            case 'F': {
                int literal = intLiteralFor(returnType); // 0.0f/1.0f bit patterns handled below
                instructions.add(new ImmutableInstruction31i(Opcode.CONST, 0, floatBits(literal)));
                instructions.add(new ImmutableInstruction11x(Opcode.RETURN, 0));
                registerCount = 1;
                break;
            }
            case 'D': {
                int literal = intLiteralFor(returnType);
                instructions.add(new ImmutableInstruction51l(Opcode.CONST_WIDE, 0, doubleBits(literal)));
                instructions.add(new ImmutableInstruction11x(Opcode.RETURN_WIDE, 0));
                registerCount = 2;
                break;
            }
            case 'L': case '[': {
                requireValue(ReturnValue.NULL, "an object/array", returnType);
                instructions.add(new ImmutableInstruction11n(Opcode.CONST_4, 0, 0)); // null
                instructions.add(new ImmutableInstruction11x(Opcode.RETURN_OBJECT, 0));
                registerCount = 1;
                break;
            }
            default:
                throw new IllegalArgumentException("Unrecognized return type descriptor: " + returnType);
        }

        return new ImmutableMethodImplementation(registerCount, instructions, null, null);
    }

    private int intLiteralFor(@Nonnull String returnType) {
        switch (value) {
            case TRUE: case ONE: return 1;
            case FALSE: case ZERO: return 0;
            default:
                throw new IllegalArgumentException("Return type " + returnType +
                        " needs a numeric value (true/false/0/1), not " + value.name().toLowerCase() + ".");
        }
    }

    private long wideLiteralFor(@Nonnull String returnType) {
        return intLiteralFor(returnType);
    }

    private static int floatBits(int intValue) {
        return Float.floatToIntBits((float) intValue);
    }

    private static long doubleBits(int intValue) {
        return Double.doubleToLongBits((double) intValue);
    }

    private void requireValue(@Nonnull ReturnValue expected, @Nonnull String humanType,
                              @Nonnull String returnType) {
        if (value != expected) {
            throw new IllegalArgumentException("Return type " + returnType + " (" + humanType +
                    ") requires --return " + expected.name().toLowerCase() + ", not " +
                    value.name().toLowerCase() + ".");
        }
    }

    /**
     * @return a lazily-rewritten view of {@code in} where every matched method returns immediately.
     */
    @Nonnull
    public DexFile apply(@Nonnull DexFile in) {
        RewriterModule module = new RewriterModule() {
            @Nonnull @Override
            public Rewriter<Method> getMethodRewriter(@Nonnull Rewriters rewriters) {
                return new MethodRewriter(rewriters) {
                    @Nonnull @Override public Method rewrite(@Nonnull Method method) {
                        return new RewrittenMethod(method) {
                            @Override public MethodImplementation getImplementation() {
                                MethodImplementation original = this.method.getImplementation();
                                if (original == null || !matches(this.method)) {
                                    return original;
                                }
                                return buildReturnBody(this.method.getReturnType());
                            }
                        };
                    }
                };
            }
        };
        return new DexRewriter(module).getDexFileRewriter().rewrite(in);
    }

    /** @return the number of concrete methods in {@code in} that this transform would patch. */
    public int countMatches(@Nonnull DexFile in) {
        int count = 0;
        for (org.jf.dexlib2.iface.ClassDef classDef : in.getClasses()) {
            for (Method method : classDef.getMethods()) {
                if (method.getImplementation() != null && matches(method)) {
                    count++;
                }
            }
        }
        return count;
    }
}
