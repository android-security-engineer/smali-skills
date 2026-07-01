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

package org.jf.baksmali.graph;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.jf.dexlib2.iface.ClassDef;
import org.jf.smali.SmaliTestUtils;
import org.junit.Assert;
import org.junit.Test;

import java.util.Collections;
import java.util.List;

/**
 * Tests for {@link CallGraph}, behind {@code baksmali callgraph}.
 */
public class CallGraphTest {

    /** A class whose {@code a()} calls {@code b()}, and {@code b()} calls {@code System.currentTimeMillis()}. */
    private ClassDef compileFixture() throws Exception {
        String smali =
                ".class public Lcom/example/Graph;\n" +
                ".super Ljava/lang/Object;\n" +
                "\n" +
                ".method public a()V\n" +
                "    .registers 1\n" +
                "    invoke-virtual {p0}, Lcom/example/Graph;->b()V\n" +
                "    return-void\n" +
                ".end method\n" +
                "\n" +
                ".method public b()V\n" +
                "    .registers 2\n" +
                "    invoke-static {}, Ljava/lang/System;->currentTimeMillis()J\n" +
                "    return-void\n" +
                ".end method\n";
        return SmaliTestUtils.compileSmali(smali);
    }

    @Test
    public void descriptor_matchesSmaliShape() {
        String d = CallGraph.descriptor("Lcom/example/Graph;", "a",
                Collections.<CharSequence>emptyList(), "V");
        Assert.assertEquals("Lcom/example/Graph;->a()V", d);
    }

    @Test
    public void build_capturesInvokeEdges() throws Exception {
        CallGraph graph = CallGraph.build(Collections.singleton(compileFixture()));

        List<String> aCallees = graph.getCallees("Lcom/example/Graph;->a()V");
        Assert.assertEquals(Collections.singletonList("Lcom/example/Graph;->b()V"), aCallees);

        List<String> bCallees = graph.getCallees("Lcom/example/Graph;->b()V");
        Assert.assertEquals(
                Collections.singletonList("Ljava/lang/System;->currentTimeMillis()J"), bCallees);

        Assert.assertEquals(2, graph.edgeCount());
    }

    @Test
    public void build_includesCalleesAsNodesEvenWhenNotDefined() throws Exception {
        CallGraph graph = CallGraph.build(Collections.singleton(compileFixture()));
        Assert.assertTrue(graph.getNodes().contains("Ljava/lang/System;->currentTimeMillis()J"));
        // a, b, and System.currentTimeMillis => 3 distinct nodes.
        Assert.assertEquals(3, graph.getNodes().size());
    }

    @Test
    public void toJson_hasNodesAndEdges() throws Exception {
        CallGraph graph = CallGraph.build(Collections.singleton(compileFixture()));
        JsonObject root = new JsonParser().parse(graph.toJson()).getAsJsonObject();

        Assert.assertEquals(3, root.getAsJsonArray("nodes").size());
        Assert.assertEquals(2, root.getAsJsonArray("edges").size());

        JsonObject firstEdge = root.getAsJsonArray("edges").get(0).getAsJsonObject();
        Assert.assertTrue(firstEdge.has("from"));
        Assert.assertTrue(firstEdge.has("to"));
    }

    @Test
    public void toDot_isWellFormed() throws Exception {
        String dot = CallGraph.build(Collections.singleton(compileFixture())).toDot();
        Assert.assertTrue(dot.startsWith("digraph callgraph {"));
        Assert.assertTrue(dot.trim().endsWith("}"));
        Assert.assertTrue(dot.contains("->"));
        Assert.assertTrue(dot.contains("Lcom/example/Graph;->a()V"));
    }

    @Test
    public void toMermaid_isWellFormed() throws Exception {
        String mermaid = CallGraph.build(Collections.singleton(compileFixture())).toMermaid();
        Assert.assertTrue(mermaid.startsWith("graph TD"));
        Assert.assertTrue(mermaid.contains("-->"));
    }
}
