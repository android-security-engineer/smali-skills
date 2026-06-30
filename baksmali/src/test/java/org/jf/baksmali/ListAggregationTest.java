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

import com.google.gson.JsonParser;
import org.jf.baksmali.output.AggregatingOutput;
import org.junit.Assert;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Arrays;
import java.util.List;

/**
 * Tests for the {@code --count} / {@code --group-by} aggregation machinery:
 * {@link ListAggregationArguments} (parameter parsing) and {@link AggregatingOutput} (rendering).
 */
public class ListAggregationTest {

    // ---- ListAggregationArguments parsing ----

    @Test
    public void testDefaults() {
        ListAggregationArguments args = new ListAggregationArguments();
        Assert.assertFalse(args.isCount());
        Assert.assertEquals(ListAggregationArguments.GroupBy.NONE, args.getGroupBy());
        Assert.assertFalse(args.isAggregating());
    }

    // Reflection-based setters, since the @Parameter fields are private and normally populated
    // by JCommander. This keeps the test free of a JCommander dependency while still exercising
    // the parsing logic.
    private static void setField(Object target, String fieldName, Object value) throws Exception {
        java.lang.reflect.Field f = target.getClass().getDeclaredField(fieldName);
        f.setAccessible(true);
        f.set(target, value);
    }

    @Test
    public void testCountFlag() throws Exception {
        ListAggregationArguments args = new ListAggregationArguments();
        setField(args, "count", true);
        Assert.assertTrue(args.isCount());
        Assert.assertTrue(args.isAggregating());
    }

    @Test
    public void testGroupByClassParsing() throws Exception {
        ListAggregationArguments args = new ListAggregationArguments();
        setField(args, "groupBy", "class");
        Assert.assertEquals(ListAggregationArguments.GroupBy.CLASS, args.getGroupBy());
        Assert.assertTrue(args.isAggregating());
    }

    @Test
    public void testGroupByCaseInsensitiveAndUnknown() throws Exception {
        ListAggregationArguments args = new ListAggregationArguments();
        setField(args, "groupBy", "CLASS");
        Assert.assertEquals(ListAggregationArguments.GroupBy.CLASS, args.getGroupBy());

        ListAggregationArguments args2 = new ListAggregationArguments();
        setField(args2, "groupBy", "nonsense");
        Assert.assertEquals(ListAggregationArguments.GroupBy.NONE, args2.getGroupBy());
        Assert.assertFalse(args2.isAggregating());
    }

    // ---- AggregatingOutput rendering (capturing stdout) ----

    private static String capture(Runnable fn) {
        PrintStream oldOut = System.out;
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        System.setOut(new PrintStream(baos));
        try {
            fn.run();
        } finally {
            System.setOut(oldOut);
        }
        return baos.toString().trim();
    }

    private static OutputFormatArguments fmt(boolean json) {
        OutputFormatArguments f = new OutputFormatArguments();
        try {
            java.lang.reflect.Field fmtField = OutputFormatArguments.class.getDeclaredField("format");
            fmtField.setAccessible(true);
            fmtField.set(f, json ? "json" : "text");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return f;
    }

    @Test
    public void testRenderCountText() {
        String out = capture(() -> new AggregatingOutput(fmt(false)).renderCount(42));
        Assert.assertEquals("42", out);
    }

    @Test
    public void testRenderCountJson() {
        String out = capture(() -> new AggregatingOutput(fmt(true)).renderCount(42));
        Assert.assertEquals(42, new JsonParser().parse(out).getAsJsonObject().get("count").getAsInt());
    }

    @Test
    public void testRenderGroupByText() {
        Map<String, Integer> counts = new LinkedHashMap<>();
        counts.put("LA;", 3);
        counts.put("LB;", 1);

        String out = capture(() -> new AggregatingOutput(fmt(false)).renderGroupBy(counts));
        // Each line is "count\tclass"; order preserved.
        String[] lines = out.split("\n");
        Assert.assertEquals(2, lines.length);
        Assert.assertEquals("3\tLA;", lines[0]);
        Assert.assertEquals("1\tLB;", lines[1]);
    }

    @Test
    public void testRenderGroupByJson() {
        Map<String, Integer> counts = new LinkedHashMap<>();
        counts.put("LA;", 3);
        counts.put("LB;", 1);

        String out = capture(() -> new AggregatingOutput(fmt(true)).renderGroupBy(counts));
        Assert.assertEquals("LA;", new JsonParser().parse(out).getAsJsonArray()
                .get(0).getAsJsonObject().get("group").getAsString());
        Assert.assertEquals(3, new JsonParser().parse(out).getAsJsonArray()
                .get(0).getAsJsonObject().get("count").getAsInt());
    }

    @Test
    public void testRenderGroupedByExtractor() {
        // Group a list of strings by their first character.
        List<String> items = Arrays.asList("apple", "avocado", "banana", "apricot");
        String out = capture(() ->
                new AggregatingOutput(fmt(false)).renderGroupedBy(items, s -> s.substring(0, 1), null));
        String[] lines = out.split("\n");
        // 'a' appears 3 times, 'b' once.
        Assert.assertEquals("3\ta", lines[0]);
        Assert.assertEquals("1\tb", lines[1]);
    }
}
