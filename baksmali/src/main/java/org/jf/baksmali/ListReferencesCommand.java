/*
 * Copyright 2016, Google Inc.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 *
 * Redistributions of source code must retain the above copyright
 * notice, this list of conditions and the following disclaimer.
 * Redistributions in binary form must reproduce the above
 * copyright notice, this list of conditions and the following disclaimer
 * in the documentation and/or other materials provided with the
 * distribution.
 * Neither the name of Google Inc. nor the names of its
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
import org.jf.baksmali.formatter.BaksmaliFormatter;
import org.jf.baksmali.output.AggregatingOutput;
import org.jf.baksmali.output.JsonOutput;
import org.jf.dexlib2.iface.reference.FieldReference;
import org.jf.dexlib2.iface.reference.MethodReference;
import org.jf.dexlib2.iface.reference.Reference;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;

public abstract class ListReferencesCommand extends DexInputCommand {

    private final int referenceType;

    @Parameter(names = {"-h", "-?", "--help"}, help = true,
            description = "Show usage information")
    private boolean help;

    @ParametersDelegate
    private OutputFormatArguments outputFormat = new OutputFormatArguments();

    @ParametersDelegate
    private ListAggregationArguments aggregation = new ListAggregationArguments();

    public ListReferencesCommand(@Nonnull List<JCommander> commandAncestors, int referenceType) {
        super(commandAncestors);
        this.referenceType = referenceType;
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

        // Materialize the references so we can count/group without re-walking.
        List<Reference> references = new ArrayList<>();
        for (Reference reference : dexFile.getReferences(referenceType)) {
            references.add(reference);
        }

        if (aggregation.isCount()) {
            new AggregatingOutput(outputFormat).renderCount(references.size());
            return;
        }

        if (aggregation.getGroupBy() == ListAggregationArguments.GroupBy.CLASS) {
            // group-by class only makes sense for references that carry a defining class.
            if (referenceType != org.jf.dexlib2.ReferenceType.METHOD
                    && referenceType != org.jf.dexlib2.ReferenceType.FIELD) {
                System.err.println("--group-by class only applies to methods and fields; ignoring.");
            } else {
                AggregatingOutput agg = new AggregatingOutput(outputFormat);
                agg.renderGroupedBy(references, this::definingClassOf, null);
                return;
            }
        }

        if (outputFormat.isJson()) {
            JsonOutput jsonOutput = new JsonOutput();
            List<JsonObject> objects = new ArrayList<>();
            for (Reference reference : references) {
                objects.add(jsonOutput.toJson(reference));
            }
            System.out.println(jsonOutput.toJsonArray(objects));
            return;
        }

        BaksmaliFormatter formatter = new BaksmaliFormatter();

        for (Reference reference : references) {
            System.out.println(formatter.getReference(reference));
        }
    }

    @Nonnull
    private String definingClassOf(@Nonnull Reference reference) {
        if (reference instanceof MethodReference) {
            return ((MethodReference) reference).getDefiningClass();
        }
        if (reference instanceof FieldReference) {
            return ((FieldReference) reference).getDefiningClass();
        }
        return "(unknown)";
    }
}
