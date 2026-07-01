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

import org.jf.dexlib2.iface.DexFile;
import org.jf.dexlib2.iface.MethodImplementation;
import org.jf.dexlib2.iface.debug.DebugItem;
import org.jf.dexlib2.rewriter.DexRewriter;
import org.jf.dexlib2.rewriter.MethodImplementationRewriter;
import org.jf.dexlib2.rewriter.Rewriter;
import org.jf.dexlib2.rewriter.RewriterModule;
import org.jf.dexlib2.rewriter.Rewriters;

import javax.annotation.Nonnull;
import java.util.Collections;

/**
 * A pure, I/O-free transform that strips all debug information from every method in a {@link DexFile}.
 *
 * <p>Dex debug items carry source line numbers, local-variable names/types/scopes, and the parameter
 * names surfaced in stack traces. Removing them shrinks the dex and frustrates casual reverse
 * engineering (no more {@code .line}/{@code .local} in the disassembly), while leaving the executable
 * bytecode untouched.
 *
 * <p>Implemented by overriding {@link MethodImplementationRewriter} so
 * {@link MethodImplementation#getDebugItems()} yields an empty sequence — a cleaner removal than
 * rewriting items one-by-one, which cannot drop them.
 */
public class StripDebugTransform {

    /**
     * @return a lazily-rewritten view of {@code in} in which every method implementation reports no
     *         debug items.
     */
    @Nonnull
    public DexFile apply(@Nonnull DexFile in) {
        RewriterModule module = new RewriterModule() {
            @Nonnull @Override
            public Rewriter<MethodImplementation> getMethodImplementationRewriter(
                    @Nonnull Rewriters rewriters) {
                return new MethodImplementationRewriter(rewriters) {
                    @Nonnull @Override
                    public MethodImplementation rewrite(@Nonnull MethodImplementation impl) {
                        return new RewrittenMethodImplementation(impl) {
                            @Nonnull @Override
                            public Iterable<? extends DebugItem> getDebugItems() {
                                return Collections.emptyList();
                            }
                        };
                    }
                };
            }
        };
        return new DexRewriter(module).getDexFileRewriter().rewrite(in);
    }
}
