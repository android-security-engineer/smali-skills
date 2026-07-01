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
import com.google.gson.JsonObject;
import org.jf.baksmali.output.TransformReport;
import org.jf.dexlib2.iface.DexFile;
import org.jf.dexlib2.writer.pool.DexPool;
import org.jf.util.jcommander.ExtendedParameter;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.util.List;

/**
 * Common machinery for the write-back transform commands ({@code unlock}, {@code replace},
 * {@code strip-debug}, {@code patch}).
 *
 * <p>Unlike the read-only query commands, these load a dex, apply a transformation to its
 * {@link DexFile} model (typically via the dexlib2 {@code rewriter} framework), and serialize the
 * result to a new dex with {@link DexPool}. This base adds the shared {@code -o/--output} option and
 * a {@link #writeResult(DexFile)} helper so subclasses only implement the transform itself.
 */
public abstract class DexTransformCommand extends DexInputCommand {

    @Parameter(names = {"-o", "--output"},
            description = "The path to write the transformed dex file to.")
    @ExtendedParameter(argumentNames = "file")
    protected String output = "out.dex";

    @ParametersDelegate
    protected final OutputFormatArguments outputFormatArguments = new OutputFormatArguments();

    public DexTransformCommand(@Nonnull List<JCommander> commandAncestors) {
        super(commandAncestors);
    }

    /**
     * Serializes a (usually rewritten) dex file to {@link #output} using the pool writer.
     */
    protected void writeResult(@Nonnull DexFile dexFile) {
        try {
            DexPool.writeTo(output, dexFile);
        } catch (IOException ex) {
            throw new RuntimeException("Failed to write output dex: " + output, ex);
        }
    }

    /**
     * Prints the one-line success report for a transform. In the default JSON mode {@code report}
     * is serialized; with {@code --format text} the {@code humanText} sentence is printed instead.
     * The {@code command}/{@code input}/{@code output} common fields should already be seeded via
     * {@link TransformReport#base(String, String, String)}.
     */
    protected void emitReport(@Nonnull JsonObject report, @Nonnull String humanText) {
        System.out.println(TransformReport.render(outputFormatArguments.isJson(), report, humanText));
    }
}
