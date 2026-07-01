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

import org.jf.dexlib2.AccessFlags;
import org.jf.dexlib2.iface.ClassDef;
import org.jf.dexlib2.iface.DexFile;
import org.jf.dexlib2.iface.Field;
import org.jf.dexlib2.iface.Method;
import org.jf.dexlib2.rewriter.ClassDefRewriter;
import org.jf.dexlib2.rewriter.DexRewriter;
import org.jf.dexlib2.rewriter.FieldRewriter;
import org.jf.dexlib2.rewriter.MethodRewriter;
import org.jf.dexlib2.rewriter.Rewriter;
import org.jf.dexlib2.rewriter.RewriterModule;
import org.jf.dexlib2.rewriter.Rewriters;

import javax.annotation.Nonnull;

/**
 * A pure, I/O-free access-flag rewrite over a {@link DexFile}: it can <em>publicize</em>
 * (clear {@code private}/{@code protected}, set {@code public}) and/or <em>definalize</em>
 * (clear {@code final}) every class, method, and field.
 *
 * <p>This is the reverse-engineering staple behind {@code baksmali unlock}: making otherwise
 * inaccessible members reachable so patched or instrumented code can call them, or so a class can
 * be subclassed. The transformation is applied lazily through the dexlib2
 * {@link org.jf.dexlib2.rewriter rewriter} framework, so no bytes are materialized until the caller
 * writes the result out.
 *
 * <p>The flag arithmetic in {@link #rewriteFlags(int)} is deliberately separated from the dex
 * plumbing so it can be unit-tested directly.
 */
public class AccessFlagTransform {

    private final boolean publicize;
    private final boolean definalize;

    /**
     * @param publicize  clear {@code private}/{@code protected} and set {@code public}
     * @param definalize clear the {@code final} modifier
     */
    public AccessFlagTransform(boolean publicize, boolean definalize) {
        this.publicize = publicize;
        this.definalize = definalize;
    }

    /**
     * Applies the configured transformation to a single access-flag bitmask.
     */
    public int rewriteFlags(int flags) {
        if (publicize) {
            flags &= ~(AccessFlags.PRIVATE.getValue() | AccessFlags.PROTECTED.getValue());
            flags |= AccessFlags.PUBLIC.getValue();
        }
        if (definalize) {
            flags &= ~AccessFlags.FINAL.getValue();
        }
        return flags;
    }

    /**
     * @return a lazily-rewritten view of {@code in} with the configured flag changes applied to
     *         every class, method, and field.
     */
    @Nonnull
    public DexFile apply(@Nonnull DexFile in) {
        RewriterModule module = new RewriterModule() {
            @Nonnull @Override
            public Rewriter<ClassDef> getClassDefRewriter(@Nonnull Rewriters rewriters) {
                return new ClassDefRewriter(rewriters) {
                    @Nonnull @Override public ClassDef rewrite(@Nonnull ClassDef classDef) {
                        return new RewrittenClassDef(classDef) {
                            @Override public int getAccessFlags() {
                                return rewriteFlags(this.classDef.getAccessFlags());
                            }
                        };
                    }
                };
            }

            @Nonnull @Override
            public Rewriter<Field> getFieldRewriter(@Nonnull Rewriters rewriters) {
                return new FieldRewriter(rewriters) {
                    @Nonnull @Override public Field rewrite(@Nonnull Field field) {
                        return new RewrittenField(field) {
                            @Override public int getAccessFlags() {
                                return rewriteFlags(this.field.getAccessFlags());
                            }
                        };
                    }
                };
            }

            @Nonnull @Override
            public Rewriter<Method> getMethodRewriter(@Nonnull Rewriters rewriters) {
                return new MethodRewriter(rewriters) {
                    @Nonnull @Override public Method rewrite(@Nonnull Method method) {
                        return new RewrittenMethod(method) {
                            @Override public int getAccessFlags() {
                                return rewriteFlags(this.method.getAccessFlags());
                            }
                        };
                    }
                };
            }
        };
        return new DexRewriter(module).getDexFileRewriter().rewrite(in);
    }
}
