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

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import org.jf.dexlib2.iface.ClassDef;
import org.jf.dexlib2.iface.Method;
import org.jf.dexlib2.iface.MethodImplementation;
import org.jf.dexlib2.iface.instruction.Instruction;
import org.jf.dexlib2.iface.instruction.ReferenceInstruction;
import org.jf.dexlib2.iface.reference.MethodReference;
import org.jf.dexlib2.iface.reference.Reference;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A method-level call graph over a set of classes: nodes are method descriptors and a directed edge
 * {@code A -> B} means method {@code A}'s body contains an invoke of method {@code B}.
 *
 * <p>Built by walking every class &rarr; method &rarr; instruction and recording each
 * {@link MethodReference} carried by an invoke instruction. Only methods that actually appear as a
 * caller or callee become nodes, so the graph stays proportional to the reachable call structure
 * rather than to the full method table.
 *
 * <p>Descriptors use the canonical smali shape {@code Lcom/Pkg/Cls;->name(params)ReturnType}. The
 * graph can be exported as JSON (for tooling/AI agents), Graphviz DOT, or Mermaid.
 *
 * <p>This model is pure and I/O-free; the {@code baksmali callgraph} command handles loading and
 * printing.
 */
public class CallGraph {

    // caller descriptor -> ordered set of callee descriptors
    private final Map<String, Set<String>> edges = new LinkedHashMap<>();
    private final Set<String> nodes = new LinkedHashSet<>();

    /**
     * Builds a call graph from the given classes.
     */
    @Nonnull
    public static CallGraph build(@Nonnull Iterable<? extends ClassDef> classes) {
        CallGraph graph = new CallGraph();
        for (ClassDef classDef : classes) {
            for (Method method : classDef.getMethods()) {
                MethodImplementation impl = method.getImplementation();
                if (impl == null) {
                    continue;
                }
                String caller = descriptor(method.getDefiningClass(), method.getName(),
                        method.getParameterTypes(), method.getReturnType());
                for (Instruction instruction : impl.getInstructions()) {
                    if (!(instruction instanceof ReferenceInstruction)) {
                        continue;
                    }
                    Reference reference = ((ReferenceInstruction) instruction).getReference();
                    if (reference instanceof MethodReference) {
                        MethodReference callee = (MethodReference) reference;
                        graph.addEdge(caller, descriptor(callee.getDefiningClass(), callee.getName(),
                                callee.getParameterTypes(), callee.getReturnType()));
                    }
                }
            }
        }
        return graph;
    }

    private void addEdge(@Nonnull String caller, @Nonnull String callee) {
        nodes.add(caller);
        nodes.add(callee);
        edges.computeIfAbsent(caller, k -> new LinkedHashSet<>()).add(callee);
    }

    /**
     * Builds the canonical smali method descriptor {@code Lcls;->name(params)ret}.
     */
    @Nonnull
    public static String descriptor(@Nonnull String definingClass, @Nonnull String name,
                                    @Nonnull List<? extends CharSequence> parameterTypes,
                                    @Nonnull String returnType) {
        StringBuilder sb = new StringBuilder();
        sb.append(definingClass).append("->").append(name).append('(');
        for (CharSequence param : parameterTypes) {
            sb.append(param);
        }
        sb.append(')').append(returnType);
        return sb.toString();
    }

    /** @return every node (caller or callee), in first-seen order. */
    @Nonnull
    public List<String> getNodes() {
        return new ArrayList<>(nodes);
    }

    /** @return the callees invoked directly by {@code caller}, in first-seen order. */
    @Nonnull
    public List<String> getCallees(@Nonnull String caller) {
        Set<String> callees = edges.get(caller);
        return callees == null ? new ArrayList<String>() : new ArrayList<>(callees);
    }

    /** @return the total number of directed edges. */
    public int edgeCount() {
        int count = 0;
        for (Set<String> callees : edges.values()) {
            count += callees.size();
        }
        return count;
    }

    // -- exporters -----------------------------------------------------------

    /**
     * JSON: {@code {"nodes":[...],"edges":[{"from":"...","to":"..."}]}}.
     */
    @Nonnull
    public String toJson() {
        JsonObject root = new JsonObject();
        JsonArray nodeArray = new JsonArray();
        for (String node : nodes) {
            nodeArray.add(new JsonPrimitive(node));
        }
        root.add("nodes", nodeArray);

        JsonArray edgeArray = new JsonArray();
        for (Map.Entry<String, Set<String>> entry : edges.entrySet()) {
            for (String callee : entry.getValue()) {
                JsonObject edge = new JsonObject();
                edge.addProperty("from", entry.getKey());
                edge.addProperty("to", callee);
                edgeArray.add(edge);
            }
        }
        root.add("edges", edgeArray);
        return new GsonBuilder().disableHtmlEscaping().create().toJson(root);
    }

    /**
     * Graphviz DOT.
     */
    @Nonnull
    public String toDot() {
        StringBuilder sb = new StringBuilder();
        sb.append("digraph callgraph {\n");
        for (Map.Entry<String, Set<String>> entry : edges.entrySet()) {
            for (String callee : entry.getValue()) {
                sb.append("  ").append(quoteDot(entry.getKey()))
                        .append(" -> ").append(quoteDot(callee)).append(";\n");
            }
        }
        sb.append("}\n");
        return sb.toString();
    }

    /**
     * Mermaid flowchart.
     */
    @Nonnull
    public String toMermaid() {
        StringBuilder sb = new StringBuilder();
        sb.append("graph TD\n");
        for (Map.Entry<String, Set<String>> entry : edges.entrySet()) {
            for (String callee : entry.getValue()) {
                sb.append("  ").append(quoteMermaid(entry.getKey()))
                        .append(" --> ").append(quoteMermaid(callee)).append("\n");
            }
        }
        return sb.toString();
    }

    @Nonnull
    private static String quoteDot(@Nonnull String label) {
        return "\"" + label.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    @Nonnull
    private static String quoteMermaid(@Nonnull String label) {
        // Mermaid node id + bracketed quoted label; the id must be stable and simple, so hash-derive
        // a short id and carry the full descriptor as the display label.
        String id = "n" + Integer.toHexString(label.hashCode() & 0x7fffffff);
        String display = label.replace("\"", "'");
        return id + "[\"" + display + "\"]";
    }
}
