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
import com.beust.jcommander.Parameters;
import com.beust.jcommander.ParametersDelegate;
import com.google.gson.JsonObject;
import org.jf.baksmali.formatter.BaksmaliFormatter;
import org.jf.baksmali.output.AggregatingOutput;
import org.jf.baksmali.output.JsonOutput;
import org.jf.dexlib2.iface.ClassDef;
import org.jf.util.jcommander.ExtendedParameters;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;

@Parameters(commandDescription = "Lists the classes in a dex file.")
@ExtendedParameters(
        commandName = "classes",
        commandAliases = { "class", "c" })
public class ListClassesCommand extends DexInputCommand {

    @Parameter(names = {"-h", "-?", "--help"}, help = true,
            description = "Show usage information")
    private boolean help;

    @ParametersDelegate
    private OutputFormatArguments outputFormat = new OutputFormatArguments();

    @ParametersDelegate
    private ListAggregationArguments aggregation = new ListAggregationArguments();

    public ListClassesCommand(@Nonnull List<JCommander> commandAncestors) {
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

        String input = inputList.get(0);
        loadDexFile(input);

        // --count: emit only the total number of classes.
        if (aggregation.isCount()) {
            int count = 0;
            for (ClassDef ignored : dexFile.getClasses()) {
                count++;
            }
            new AggregatingOutput(outputFormat).renderCount(count);
            return;
        }

        // --group-by is not meaningful for the class list itself (each class is its own bucket);
        // if requested, warn and fall through to the normal listing.
        if (aggregation.getGroupBy() != ListAggregationArguments.GroupBy.NONE) {
            System.err.println("--group-by has no effect on 'list classes'; ignoring.");
        }

        if (outputFormat.isJson()) {
            JsonOutput jsonOutput = new JsonOutput();
            List<JsonObject> objects = new ArrayList<>();
            for (ClassDef classDef : dexFile.getClasses()) {
                objects.add(jsonOutput.toJson(classDef));
            }
            System.out.println(jsonOutput.toJsonArray(objects));
            return;
        }

        BaksmaliFormatter formatter = new BaksmaliFormatter();

        for (ClassDef classDef: dexFile.getClasses()) {
            System.out.println(formatter.getType(classDef.getType()));
        }
    }
}
