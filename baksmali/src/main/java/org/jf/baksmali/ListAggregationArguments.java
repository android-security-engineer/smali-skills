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

import com.beust.jcommander.Parameter;

/**
 * Shared command-line arguments for the {@code list} subcommands that control aggregation:
 * a {@code --count} mode that emits only a total, and a {@code --group-by} mode that buckets
 * items by a key and emits per-bucket counts.
 *
 * <p>These options are orthogonal to {@link OutputFormatArguments} (text vs JSON) and may be
 * combined with it. They are intended for commands that enumerate a flat collection of items
 * (classes, methods, fields, strings, types).
 */
public class ListAggregationArguments {

    /**
     * The key to bucket items by when {@code --group-by} is used. Currently only {@code class}
     * is supported, which groups methods/fields by their defining class.
     */
    public enum GroupBy {
        NONE,
        CLASS
    }

    @Parameter(names = {"--count"},
            description = "Only output the total count of items, not the items themselves.")
    private boolean count = false;

    @Parameter(names = {"--group-by"},
            description = "Bucket items by the given key and output per-bucket counts. " +
                    "Currently only 'class' is supported (groups methods/fields by defining class).")
    private String groupBy;

    /**
     * @return true if {@code --count} was requested.
     */
    public boolean isCount() {
        return count;
    }

    /**
     * @return the requested grouping key, or {@link GroupBy#NONE} if none.
     */
    public GroupBy getGroupBy() {
        if (groupBy == null) {
            return GroupBy.NONE;
        }
        switch (groupBy.toLowerCase()) {
            case "class":
                return GroupBy.CLASS;
            default:
                return GroupBy.NONE;
        }
    }

    /**
     * @return true if any aggregation mode is active ({@code --count} or {@code --group-by}).
     */
    public boolean isAggregating() {
        return count || getGroupBy() != GroupBy.NONE;
    }
}
