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

import com.google.common.collect.Iterables;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.jf.dexlib2.Opcodes;
import org.jf.dexlib2.iface.ClassDef;
import org.jf.dexlib2.iface.DexFile;
import org.jf.dexlib2.immutable.ImmutableDexFile;
import org.jf.smali.SmaliTestUtils;
import org.junit.Assert;
import org.junit.Test;

import java.util.Collections;
import java.util.function.Function;

/**
 * Tests for {@link McpServer}: the JSON-RPC protocol layer and each exposed dex tool. The dex loader
 * is stubbed with an in-memory fixture, so tools run end-to-end without touching disk.
 */
public class McpServerTest {

    private static final String FIXTURE_SMALI =
            ".class public Lcom/example/Greeter;\n" +
            ".super Ljava/lang/Object;\n" +
            ".field public count:I\n" +
            ".method public greet()V\n" +
            "    .registers 2\n" +
            "    const-string v0, \"hello\"\n" +
            "    invoke-static {v0}, Lcom/example/Log;->d(Ljava/lang/String;)V\n" +
            "    return-void\n" +
            ".end method\n";

    private McpServer serverFor(String smali) throws Exception {
        ClassDef classDef = SmaliTestUtils.compileSmali(smali);
        DexFile dex = new ImmutableDexFile(Opcodes.forApi(15), Collections.singleton(classDef));
        Function<String, DexFile> loader = input -> dex;
        return new McpServer(15, loader);
    }

    private JsonObject request(String method, JsonObject params, int id) {
        JsonObject req = new JsonObject();
        req.addProperty("jsonrpc", "2.0");
        req.addProperty("id", id);
        req.addProperty("method", method);
        if (params != null) {
            req.add("params", params);
        }
        return req;
    }

    private JsonObject callTool(McpServer server, String tool, JsonObject arguments) {
        JsonObject params = new JsonObject();
        params.addProperty("name", tool);
        params.add("arguments", arguments);
        JsonObject response = server.handle(request("tools/call", params, 42));
        Assert.assertNotNull(response);
        return response.getAsJsonObject("result");
    }

    /** Extracts the single text content block from a tools/call result. */
    private String toolText(JsonObject result) {
        JsonArray content = result.getAsJsonArray("content");
        return content.get(0).getAsJsonObject().get("text").getAsString();
    }

    private JsonObject args(String... kv) {
        JsonObject obj = new JsonObject();
        for (int i = 0; i < kv.length; i += 2) {
            obj.addProperty(kv[i], kv[i + 1]);
        }
        return obj;
    }

    // -- protocol ------------------------------------------------------------

    @Test
    public void initialize_reportsToolsCapability() throws Exception {
        McpServer server = serverFor(FIXTURE_SMALI);
        JsonObject response = server.handle(request("initialize", new JsonObject(), 1));
        Assert.assertNotNull(response);
        Assert.assertEquals("2.0", response.get("jsonrpc").getAsString());
        Assert.assertEquals(1, response.get("id").getAsInt());
        JsonObject result = response.getAsJsonObject("result");
        Assert.assertEquals(McpServer.PROTOCOL_VERSION, result.get("protocolVersion").getAsString());
        Assert.assertTrue(result.getAsJsonObject("capabilities").has("tools"));
        Assert.assertEquals(McpServer.SERVER_NAME,
                result.getAsJsonObject("serverInfo").get("name").getAsString());
    }

    @Test
    public void notification_producesNoResponse() throws Exception {
        McpServer server = serverFor(FIXTURE_SMALI);
        JsonObject notification = new JsonObject();
        notification.addProperty("jsonrpc", "2.0");
        notification.addProperty("method", "notifications/initialized");
        Assert.assertNull(server.handle(notification));
    }

    @Test
    public void unknownMethod_returnsMethodNotFound() throws Exception {
        McpServer server = serverFor(FIXTURE_SMALI);
        JsonObject response = server.handle(request("no/such/method", null, 7));
        Assert.assertNotNull(response);
        Assert.assertEquals(-32601, response.getAsJsonObject("error").get("code").getAsInt());
    }

    @Test
    public void toolsList_advertisesAllToolsWithSchemas() throws Exception {
        McpServer server = serverFor(FIXTURE_SMALI);
        JsonObject response = server.handle(request("tools/list", new JsonObject(), 2));
        JsonArray tools = response.getAsJsonObject("result").getAsJsonArray("tools");
        Assert.assertEquals(4, tools.size());
        java.util.Set<String> names = new java.util.HashSet<>();
        for (JsonElement t : tools) {
            JsonObject tool = t.getAsJsonObject();
            names.add(tool.get("name").getAsString());
            // Every tool must carry a JSON-schema object with a properties map.
            JsonObject schema = tool.getAsJsonObject("inputSchema");
            Assert.assertEquals("object", schema.get("type").getAsString());
            Assert.assertTrue(schema.has("properties"));
            Assert.assertTrue(schema.getAsJsonArray("required").size() >= 1);
        }
        Assert.assertTrue(names.containsAll(java.util.Arrays.asList(
                "list_dex", "disassemble_class", "search_opcodes", "xref")));
    }

    // -- tools/call: list_dex ------------------------------------------------

    @Test
    public void listDex_methods_returnsMethodJson() throws Exception {
        McpServer server = serverFor(FIXTURE_SMALI);
        JsonObject result = callTool(server, "list_dex", args("input", "x.dex", "type", "methods"));
        Assert.assertFalse(result.get("isError").getAsBoolean());
        JsonArray methods = new JsonParser().parse(toolText(result)).getAsJsonArray();
        Assert.assertEquals(1, methods.size());
        JsonObject m = methods.get(0).getAsJsonObject();
        Assert.assertEquals("Lcom/example/Greeter;", m.get("class").getAsString());
        Assert.assertEquals("greet", m.get("name").getAsString());
    }

    @Test
    public void listDex_strings_collectsConstStrings() throws Exception {
        McpServer server = serverFor(FIXTURE_SMALI);
        JsonObject result = callTool(server, "list_dex", args("input", "x.dex", "type", "strings"));
        JsonArray strings = new JsonParser().parse(toolText(result)).getAsJsonArray();
        boolean hasHello = false;
        for (JsonElement e : strings) {
            if (e.getAsString().equals("hello")) {
                hasHello = true;
            }
        }
        Assert.assertTrue("expected the const-string \"hello\" to be listed", hasHello);
    }

    @Test
    public void listDex_classes_defaultsToClasses() throws Exception {
        McpServer server = serverFor(FIXTURE_SMALI);
        JsonObject result = callTool(server, "list_dex", args("input", "x.dex"));
        JsonArray classes = new JsonParser().parse(toolText(result)).getAsJsonArray();
        Assert.assertEquals("Lcom/example/Greeter;", classes.get(0).getAsString());
    }

    // -- tools/call: disassemble_class ---------------------------------------

    @Test
    public void disassembleClass_returnsSmaliText() throws Exception {
        McpServer server = serverFor(FIXTURE_SMALI);
        JsonObject result = callTool(server, "disassemble_class",
                args("input", "x.dex", "class", "Lcom/example/Greeter;"));
        Assert.assertFalse(result.get("isError").getAsBoolean());
        String smali = toolText(result);
        Assert.assertTrue(smali.contains(".class public Lcom/example/Greeter;"));
        Assert.assertTrue(smali.contains(".method public greet()V"));
        Assert.assertTrue(smali.contains("const-string"));
    }

    @Test
    public void disassembleClass_missingClass_isToolError() throws Exception {
        McpServer server = serverFor(FIXTURE_SMALI);
        JsonObject result = callTool(server, "disassemble_class",
                args("input", "x.dex", "class", "Lcom/nope/Missing;"));
        Assert.assertTrue(result.get("isError").getAsBoolean());
        Assert.assertTrue(toolText(result).contains("Class not found"));
    }

    // -- tools/call: search_opcodes ------------------------------------------

    @Test
    public void searchOpcodes_findsPattern() throws Exception {
        McpServer server = serverFor(FIXTURE_SMALI);
        JsonObject result = callTool(server, "search_opcodes",
                args("input", "x.dex", "opcode", "const-string,invoke-static"));
        JsonArray matches = new JsonParser().parse(toolText(result)).getAsJsonArray();
        Assert.assertEquals(1, matches.size());
        Assert.assertTrue(matches.get(0).getAsJsonObject().get("caller").getAsString()
                .contains("greet"));
    }

    @Test
    public void searchOpcodes_noMatch_returnsEmptyArray() throws Exception {
        McpServer server = serverFor(FIXTURE_SMALI);
        JsonObject result = callTool(server, "search_opcodes",
                args("input", "x.dex", "opcode", "move-exception,throw"));
        Assert.assertEquals(0, new JsonParser().parse(toolText(result)).getAsJsonArray().size());
    }

    // -- tools/call: xref ----------------------------------------------------

    @Test
    public void xref_callers_findsInvokeSite() throws Exception {
        McpServer server = serverFor(FIXTURE_SMALI);
        JsonObject result = callTool(server, "xref",
                args("input", "x.dex", "kind", "callers", "target", "Lcom/example/Log;->d"));
        JsonArray targets = new JsonParser().parse(toolText(result)).getAsJsonArray();
        Assert.assertEquals(1, targets.size());
        JsonObject entry = targets.get(0).getAsJsonObject();
        Assert.assertTrue(entry.get("target").getAsString().contains("Lcom/example/Log;->d"));
        Assert.assertEquals(1, entry.getAsJsonArray("sites").size());
    }

    // -- tools/call: errors --------------------------------------------------

    @Test
    public void unknownTool_isToolError() throws Exception {
        McpServer server = serverFor(FIXTURE_SMALI);
        JsonObject result = callTool(server, "bogus_tool", args("input", "x.dex"));
        Assert.assertTrue(result.get("isError").getAsBoolean());
        Assert.assertTrue(toolText(result).contains("Unknown tool"));
    }

    @Test
    public void missingRequiredArg_isToolError() throws Exception {
        McpServer server = serverFor(FIXTURE_SMALI);
        JsonObject result = callTool(server, "disassemble_class", args("input", "x.dex"));
        Assert.assertTrue(result.get("isError").getAsBoolean());
        Assert.assertTrue(toolText(result).contains("class"));
    }

    /** Sanity: the fixture compiles to exactly one class with one method. */
    @Test
    public void fixture_isWellFormed() throws Exception {
        ClassDef classDef = SmaliTestUtils.compileSmali(FIXTURE_SMALI);
        Assert.assertEquals("Lcom/example/Greeter;", classDef.getType());
        Assert.assertEquals(1, Iterables.size(classDef.getMethods()));
    }
}
