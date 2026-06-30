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

package org.jf.baksmali.output;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import org.jf.baksmali.ListAggregationArguments;
import org.jf.baksmali.OutputFormatArguments;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Renders aggregated views (count, group-by) of a list of items, in either text or JSON.
 *
 * <p>Used by the {@code list} subcommands when {@code --count} or {@code --group-by} is active.
 * The caller supplies the total item count and — when grouping by class — a map from class key
 * to per-class count. This class handles the formatting details so the commands stay thin.
 */
public class AggregatingOutput {

    private final OutputFormatArguments outputFormat;

    public AggregatingOutput(@Nonnull OutputFormatArguments outputFormat) {
        this.outputFormat = outputFormat;
    }

    /**
     * Renders the {@code --count} result.
     */
    public void renderCount(int count) {
        if (outputFormat.isJson()) {
            JsonObject obj = new JsonObject();
            obj.addProperty("count", count);
            System.out.println(obj);
        } else {
            System.out.println(count);
        }
    }

    /**
     * Renders the {@code --group-by} result.
     *
     * @param groupCounts ordered map of group key &rarr; count.
     */
    public void renderGroupBy(@Nonnull Map<String, Integer> groupCounts) {
        if (outputFormat.isJson()) {
            JsonArray array = new JsonArray();
            for (Map.Entry<String, Integer> entry : groupCounts.entrySet()) {
                JsonObject obj = new JsonObject();
                obj.addProperty("group", entry.getKey());
                obj.addProperty("count", entry.getValue());
                array.add(obj);
            }
            System.out.println(array);
        } else {
            for (Map.Entry<String, Integer> entry : groupCounts.entrySet()) {
                System.out.println(entry.getValue() + "\t" + entry.getKey());
            }
        }
    }

    /**
     * Convenience: groups a list of items by a key extractor and renders the result.
     */
    public <T> void renderGroupedBy(@Nonnull List<T> items,
                                    @Nonnull java.util.function.Function<T, String> keyExtractor,
                                    @Nullable String totalLabel) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (T item : items) {
            String key = keyExtractor.apply(item);
            counts.merge(key, 1, Integer::sum);
        }
        renderGroupBy(counts);
    }

    /**
     * @return a {@link JsonPrimitive} wrapper, exposed for callers building their own arrays.
     */
    public static JsonPrimitive primitive(@Nonnull String value) {
        return new JsonPrimitive(value);
    }
}
