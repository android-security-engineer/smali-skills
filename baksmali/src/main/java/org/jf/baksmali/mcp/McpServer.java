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

package org.jf.baksmali.mcp;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import org.jf.baksmali.BaksmaliOptions;
import org.jf.baksmali.PatternSearcher;
import org.jf.baksmali.ReferenceFinder;
import org.jf.baksmali.Adaptors.ClassDefinition;
import org.jf.baksmali.formatter.BaksmaliWriter;
import org.jf.baksmali.output.JsonOutput;
import org.jf.dexlib2.DexFileFactory;
import org.jf.dexlib2.Opcodes;
import org.jf.dexlib2.iface.ClassDef;
import org.jf.dexlib2.iface.DexFile;
import org.jf.dexlib2.iface.Method;
import org.jf.dexlib2.iface.reference.FieldReference;
import org.jf.dexlib2.iface.reference.MethodReference;
import org.jf.dexlib2.iface.reference.Reference;
import org.jf.dexlib2.iface.reference.TypeReference;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * A minimal <a href="https://modelcontextprotocol.io">Model Context Protocol</a> (MCP) server that
 * exposes baksmali's read-only dex query capabilities as MCP tools, so an AI agent host (Claude
 * Desktop, IDE agents, etc.) can inspect a dex/apk without shelling out and parsing text.
 *
 * <p>Transport is newline-delimited JSON-RPC 2.0 over stdio (one JSON object per line) — the
 * lightweight framing MCP hosts use over stdio. No third-party MCP/JSON-RPC library is pulled in;
 * the protocol is hand-rolled on the Gson already present in the project, mirroring the approach in
 * {@code smali}'s {@code SmaliLanguageServer}.
 *
 * <p>The protocol layer ({@link #handle(JsonObject)}) is deliberately pure and side-effect-free so
 * it can be unit-tested without stdio; tool execution loads dex files through an injectable
 * {@link Function} loader (defaulting to {@link DexFileFactory}) so tests can feed in-memory
 * fixtures.
 *
 * <p>Exposed tools (all read-only):
 * <ul>
 *   <li>{@code list_dex} — list classes/methods/strings/fields/types (JSON).</li>
 *   <li>{@code disassemble_class} — smali text of a single class.</li>
 *   <li>{@code search_opcodes} — find methods matching an opcode pattern.</li>
 *   <li>{@code xref} — reverse cross-references to a method/field/type target.</li>
 * </ul>
 */
public class McpServer {

    /** MCP protocol revision this server implements. */
    public static final String PROTOCOL_VERSION = "2024-11-05";
    public static final String SERVER_NAME = "baksmali-mcp";

    @Nonnull private final Gson gson = new GsonBuilder().disableHtmlEscaping().create();
    @Nonnull private final JsonOutput jsonOutput = new JsonOutput();
    private final int apiLevel;
    @Nonnull private final Function<String, DexFile> dexLoader;

    /** Thrown by a tool implementation to signal a user-facing (non-protocol) error. */
    private static final class ToolException extends Exception {
        ToolException(String message) {
            super(message);
        }
    }

    public McpServer(int apiLevel) {
        this(apiLevel, defaultLoader(apiLevel));
    }

    /**
     * @param apiLevel   the dex API level (used for disassembly output + default opcodes).
     * @param dexLoader  loads a {@link DexFile} from an input path/spec; overridable for testing.
     */
    public McpServer(int apiLevel, @Nonnull Function<String, DexFile> dexLoader) {
        this.apiLevel = apiLevel;
        this.dexLoader = dexLoader;
    }

    @Nonnull
    private static Function<String, DexFile> defaultLoader(final int apiLevel) {
        final Opcodes opcodes = apiLevel >= 0 ? Opcodes.forApi(apiLevel) : null;
        return input -> {
            try {
                return DexFileFactory.loadDexFile(new File(input), opcodes);
            } catch (IOException ex) {
                throw new RuntimeException("Unable to load dex: " + input + " (" + ex.getMessage() + ")", ex);
            }
        };
    }

    // -- transport -----------------------------------------------------------

    /**
     * Runs the stdio read/dispatch loop until EOF. Each input line is one JSON-RPC message; each
     * non-null response is written as a single line. Notifications (no {@code id}) produce no output.
     */
    public void run(@Nonnull InputStream in, @Nonnull OutputStream out) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
        String line;
        while ((line = reader.readLine()) != null) {
            if (line.trim().isEmpty()) {
                continue;
            }
            JsonObject request;
            try {
                request = gson.fromJson(line, JsonObject.class);
            } catch (Exception ex) {
                writeMessage(out, error(JsonNull.INSTANCE, -32700, "Parse error: " + ex.getMessage()));
                continue;
            }
            if (request == null) {
                continue;
            }
            JsonObject response = handle(request);
            if (response != null) {
                writeMessage(out, response);
            }
        }
    }

    private void writeMessage(@Nonnull OutputStream out, @Nonnull JsonObject message) throws IOException {
        byte[] bytes = (gson.toJson(message) + "\n").getBytes(StandardCharsets.UTF_8);
        synchronized (out) {
            out.write(bytes);
            out.flush();
        }
    }

    // -- protocol ------------------------------------------------------------

    /**
     * Handles a single JSON-RPC request/notification and returns the response object, or
     * {@code null} for notifications (which get no reply). Pure: no I/O beyond the tool loader.
     */
    @Nullable
    public JsonObject handle(@Nonnull JsonObject request) {
        JsonElement id = request.has("id") ? request.get("id") : null;
        String method = request.has("method") && request.get("method").isJsonPrimitive()
                ? request.get("method").getAsString() : null;

        if (method == null) {
            return id == null ? null : error(id, -32600, "Invalid Request: missing method");
        }

        switch (method) {
            case "initialize":
                return result(id, initializeResult());
            case "tools/list":
                return result(id, toolsListResult());
            case "tools/call":
                return handleToolCall(id, request);
            case "ping":
                return result(id, new JsonObject());
            default:
                // notifications/* (e.g. notifications/initialized) and other notifications: no reply.
                if (id == null) {
                    return null;
                }
                return error(id, -32601, "Method not found: " + method);
        }
    }

    @Nonnull
    private JsonObject initializeResult() {
        JsonObject result = new JsonObject();
        result.addProperty("protocolVersion", PROTOCOL_VERSION);

        JsonObject capabilities = new JsonObject();
        JsonObject tools = new JsonObject();
        tools.addProperty("listChanged", false);
        capabilities.add("tools", tools);
        result.add("capabilities", capabilities);

        JsonObject serverInfo = new JsonObject();
        serverInfo.addProperty("name", SERVER_NAME);
        serverInfo.addProperty("version", org.jf.baksmali.Main.VERSION);
        result.add("serverInfo", serverInfo);
        return result;
    }

    @Nonnull
    private JsonObject toolsListResult() {
        JsonArray tools = new JsonArray();
        tools.add(tool("list_dex",
                "List entries in a dex/apk: classes, methods, strings, fields, or types (JSON array).",
                stringProp("input", "Path to the dex/apk/odex/oat file."),
                enumProp("type", "What to list.", "classes", "methods", "strings", "fields", "types"),
                required("input")));
        tools.add(tool("disassemble_class",
                "Disassemble a single class to smali text.",
                stringProp("input", "Path to the dex/apk file."),
                stringProp("class", "Class descriptor to disassemble, e.g. Lcom/example/Foo;."),
                required("input", "class")));
        tools.add(tool("search_opcodes",
                "Find methods whose instruction stream matches an opcode pattern " +
                        "(comma-separated; '*' matches any single opcode).",
                stringProp("input", "Path to the dex/apk file."),
                stringProp("opcode", "Opcode pattern, e.g. const-string,invoke-virtual."),
                required("input", "opcode")));
        tools.add(tool("xref",
                "Reverse cross-references: list every site that references a target method/field/type.",
                stringProp("input", "Path to the dex/apk file."),
                enumProp("kind", "Reference kind.", "callers", "field-refs", "type-refs"),
                stringProp("target", "Target descriptor (exact, or substring match)."),
                required("input", "target")));

        JsonObject result = new JsonObject();
        result.add("tools", tools);
        return result;
    }

    @Nonnull
    private JsonObject handleToolCall(@Nullable JsonElement id, @Nonnull JsonObject request) {
        JsonObject params = request.has("params") && request.get("params").isJsonObject()
                ? request.getAsJsonObject("params") : new JsonObject();
        String name = params.has("name") && params.get("name").isJsonPrimitive()
                ? params.get("name").getAsString() : null;
        JsonObject args = params.has("arguments") && params.get("arguments").isJsonObject()
                ? params.getAsJsonObject("arguments") : new JsonObject();

        if (name == null) {
            return result(id, toolResult("Missing tool name.", true));
        }
        try {
            String text = executeTool(name, args);
            return result(id, toolResult(text, false));
        } catch (ToolException ex) {
            return result(id, toolResult(ex.getMessage(), true));
        } catch (Exception ex) {
            return result(id, toolResult("Error: " + ex.getMessage(), true));
        }
    }

    // -- tools ---------------------------------------------------------------

    @Nonnull
    private String executeTool(@Nonnull String name, @Nonnull JsonObject args) throws Exception {
        switch (name) {
            case "list_dex":
                return toolList(args);
            case "disassemble_class":
                return toolDisassemble(args);
            case "search_opcodes":
                return toolSearch(args);
            case "xref":
                return toolXref(args);
            default:
                throw new ToolException("Unknown tool: " + name);
        }
    }

    @Nonnull
    private DexFile load(@Nonnull JsonObject args) throws ToolException {
        String input = requireString(args, "input");
        return dexLoader.apply(input);
    }

    @Nonnull
    private String toolList(@Nonnull JsonObject args) throws ToolException {
        DexFile dex = load(args);
        String type = optString(args, "type", "classes");
        List<JsonObject> rows = new ArrayList<>();
        switch (type) {
            case "classes":
            case "types": {
                JsonArray arr = new JsonArray();
                for (ClassDef classDef : sortedClasses(dex)) {
                    arr.add(new JsonPrimitive(classDef.getType()));
                }
                return gson.toJson(arr);
            }
            case "strings": {
                JsonArray arr = new JsonArray();
                for (String s : collectStrings(dex)) {
                    arr.add(new JsonPrimitive(s));
                }
                return gson.toJson(arr);
            }
            case "methods": {
                for (ClassDef classDef : sortedClasses(dex)) {
                    for (Method method : classDef.getMethods()) {
                        rows.add(jsonOutput.toJson((MethodReference) method));
                    }
                }
                return jsonOutput.toJsonArray(rows);
            }
            case "fields": {
                for (ClassDef classDef : sortedClasses(dex)) {
                    for (org.jf.dexlib2.iface.Field field : classDef.getFields()) {
                        rows.add(jsonOutput.toJson((FieldReference) field));
                    }
                }
                return jsonOutput.toJsonArray(rows);
            }
            default:
                throw new ToolException("Unknown list type: " + type +
                        " (expected classes|methods|strings|fields|types)");
        }
    }

    @Nonnull
    private String toolDisassemble(@Nonnull JsonObject args) throws ToolException, IOException {
        DexFile dex = load(args);
        String target = requireString(args, "class");
        ClassDef found = null;
        for (ClassDef classDef : dex.getClasses()) {
            if (classDef.getType().equals(target)) {
                found = classDef;
                break;
            }
        }
        if (found == null) {
            throw new ToolException("Class not found: " + target);
        }
        BaksmaliOptions options = new BaksmaliOptions();
        options.apiLevel = apiLevel >= 0 ? apiLevel : 15;
        ClassDefinition classDefinition = new ClassDefinition(options, found);
        StringWriter sw = new StringWriter();
        try (BaksmaliWriter writer = new BaksmaliWriter(sw, found.getType())) {
            classDefinition.writeTo(writer);
        }
        return sw.toString();
    }

    @Nonnull
    private String toolSearch(@Nonnull JsonObject args) throws ToolException {
        DexFile dex = load(args);
        String pattern = requireString(args, "opcode");
        List<String> opcodes = PatternSearcher.parsePattern(pattern);
        if (opcodes.isEmpty()) {
            throw new ToolException("Empty opcode pattern.");
        }
        PatternSearcher searcher = new PatternSearcher();
        List<PatternSearcher.Match> matches = searcher.search(dex.getClasses(), opcodes);
        JsonArray arr = new JsonArray();
        for (PatternSearcher.Match match : matches) {
            JsonObject obj = new JsonObject();
            obj.addProperty("caller", match.caller);
            obj.addProperty("codeOffset", match.codeOffset);
            JsonArray instructions = new JsonArray();
            for (String ins : match.instructions) {
                instructions.add(new JsonPrimitive(ins));
            }
            obj.add("instructions", instructions);
            arr.add(obj);
        }
        return gson.toJson(arr);
    }

    @Nonnull
    private String toolXref(@Nonnull JsonObject args) throws ToolException {
        DexFile dex = load(args);
        String target = requireString(args, "target");
        String kind = optString(args, "kind", "callers");
        Class<? extends Reference> referenceClass;
        switch (kind) {
            case "callers":
                referenceClass = MethodReference.class;
                break;
            case "field-refs":
                referenceClass = FieldReference.class;
                break;
            case "type-refs":
                referenceClass = TypeReference.class;
                break;
            default:
                throw new ToolException("Unknown xref kind: " + kind +
                        " (expected callers|field-refs|type-refs)");
        }

        ReferenceFinder finder = new ReferenceFinder();
        finder.index(dex.getClasses());

        JsonArray arr = new JsonArray();
        for (String key : finder.getTargets()) {
            List<ReferenceFinder.ReferenceSite> sites = finder.getSites(key);
            if (sites.isEmpty()) {
                continue;
            }
            if (!referenceClass.isInstance(sites.get(0).reference)) {
                continue;
            }
            if (!(key.equals(target) || key.contains(target))) {
                continue;
            }
            JsonObject entry = new JsonObject();
            entry.addProperty("target", key);
            JsonArray siteArr = new JsonArray();
            for (ReferenceFinder.ReferenceSite site : sites) {
                JsonObject s = new JsonObject();
                s.addProperty("caller", site.caller);
                s.addProperty("codeOffset", site.codeOffset);
                siteArr.add(s);
            }
            entry.add("sites", siteArr);
            arr.add(entry);
        }
        return gson.toJson(arr);
    }

    /** Collects the distinct string constants referenced across the dex, sorted. */
    @Nonnull
    private List<String> collectStrings(@Nonnull DexFile dex) {
        java.util.TreeSet<String> strings = new java.util.TreeSet<>();
        for (ClassDef classDef : dex.getClasses()) {
            for (Method method : classDef.getMethods()) {
                org.jf.dexlib2.iface.MethodImplementation impl = method.getImplementation();
                if (impl == null) {
                    continue;
                }
                for (org.jf.dexlib2.iface.instruction.Instruction ins : impl.getInstructions()) {
                    if (ins instanceof org.jf.dexlib2.iface.instruction.ReferenceInstruction) {
                        Reference ref = ((org.jf.dexlib2.iface.instruction.ReferenceInstruction) ins).getReference();
                        if (ref instanceof org.jf.dexlib2.iface.reference.StringReference) {
                            strings.add(((org.jf.dexlib2.iface.reference.StringReference) ref).getString());
                        }
                    }
                }
            }
        }
        return new ArrayList<>(strings);
    }

    @Nonnull
    private List<ClassDef> sortedClasses(@Nonnull DexFile dex) {
        List<ClassDef> classes = new ArrayList<>();
        for (ClassDef classDef : dex.getClasses()) {
            classes.add(classDef);
        }
        classes.sort((a, b) -> a.getType().compareTo(b.getType()));
        return classes;
    }

    // -- JSON-RPC helpers ----------------------------------------------------

    @Nonnull
    private JsonObject result(@Nullable JsonElement id, @Nonnull JsonElement result) {
        JsonObject response = new JsonObject();
        response.addProperty("jsonrpc", "2.0");
        response.add("id", id == null ? JsonNull.INSTANCE : id);
        response.add("result", result);
        return response;
    }

    @Nonnull
    private JsonObject error(@Nullable JsonElement id, int code, @Nonnull String message) {
        JsonObject response = new JsonObject();
        response.addProperty("jsonrpc", "2.0");
        response.add("id", id == null ? JsonNull.INSTANCE : id);
        JsonObject err = new JsonObject();
        err.addProperty("code", code);
        err.addProperty("message", message);
        response.add("error", err);
        return response;
    }

    /** Builds an MCP {@code tools/call} result: {@code {content:[{type:text,text}], isError}}. */
    @Nonnull
    private JsonObject toolResult(@Nonnull String text, boolean isError) {
        JsonObject result = new JsonObject();
        JsonArray content = new JsonArray();
        JsonObject block = new JsonObject();
        block.addProperty("type", "text");
        block.addProperty("text", text);
        content.add(block);
        result.add("content", content);
        result.addProperty("isError", isError);
        return result;
    }

    // -- tool schema builders ------------------------------------------------

    @Nonnull
    private JsonObject tool(@Nonnull String name, @Nonnull String description, @Nonnull JsonObject... parts) {
        JsonObject schema = new JsonObject();
        schema.addProperty("type", "object");
        JsonObject properties = new JsonObject();
        JsonArray required = new JsonArray();
        for (JsonObject part : parts) {
            if (part.has("__required")) {
                for (JsonElement e : part.getAsJsonArray("__required")) {
                    required.add(e);
                }
            } else {
                for (java.util.Map.Entry<String, JsonElement> e : part.entrySet()) {
                    properties.add(e.getKey(), e.getValue());
                }
            }
        }
        schema.add("properties", properties);
        schema.add("required", required);

        JsonObject tool = new JsonObject();
        tool.addProperty("name", name);
        tool.addProperty("description", description);
        tool.add("inputSchema", schema);
        return tool;
    }

    @Nonnull
    private JsonObject stringProp(@Nonnull String name, @Nonnull String description) {
        JsonObject prop = new JsonObject();
        prop.addProperty("type", "string");
        prop.addProperty("description", description);
        JsonObject wrapper = new JsonObject();
        wrapper.add(name, prop);
        return wrapper;
    }

    @Nonnull
    private JsonObject enumProp(@Nonnull String name, @Nonnull String description, @Nonnull String... values) {
        JsonObject prop = new JsonObject();
        prop.addProperty("type", "string");
        prop.addProperty("description", description);
        JsonArray enumArr = new JsonArray();
        for (String v : values) {
            enumArr.add(new JsonPrimitive(v));
        }
        prop.add("enum", enumArr);
        JsonObject wrapper = new JsonObject();
        wrapper.add(name, prop);
        return wrapper;
    }

    @Nonnull
    private JsonObject required(@Nonnull String... names) {
        JsonArray arr = new JsonArray();
        for (String n : names) {
            arr.add(new JsonPrimitive(n));
        }
        JsonObject wrapper = new JsonObject();
        wrapper.add("__required", arr);
        return wrapper;
    }

    // -- arg helpers ---------------------------------------------------------

    @Nonnull
    private String requireString(@Nonnull JsonObject args, @Nonnull String key) throws ToolException {
        if (!args.has(key) || !args.get(key).isJsonPrimitive()) {
            throw new ToolException("Missing required argument: " + key);
        }
        return args.get(key).getAsString();
    }

    @Nonnull
    private String optString(@Nonnull JsonObject args, @Nonnull String key, @Nonnull String fallback) {
        if (args.has(key) && args.get(key).isJsonPrimitive()) {
            return args.get(key).getAsString();
        }
        return fallback;
    }
}
