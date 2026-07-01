/*
 * Copyright 2024, the smali-skills fork.
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

package org.jf.smali.lsp;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.Assert;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class SmaliLanguageServerTest {

    // -- framing -------------------------------------------------------------

    @Test
    public void framing_roundTrips() throws IOException {
        String payload = "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\"}";
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        SmaliLanguageServer.writeMessage(out, payload);

        byte[] framed = out.toByteArray();
        String header = new String(framed, 0, 30, StandardCharsets.US_ASCII);
        Assert.assertTrue("expected a Content-Length header, got: " + header,
                header.startsWith("Content-Length: "));

        String decoded = SmaliLanguageServer.readMessage(new ByteArrayInputStream(framed));
        Assert.assertEquals(payload, decoded);
    }

    @Test
    public void readMessage_returnsNullAtEof() throws IOException {
        Assert.assertNull(SmaliLanguageServer.readMessage(
                new ByteArrayInputStream(new byte[0])));
    }

    @Test
    public void readMessage_handlesMultipleFramesInSequence() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        SmaliLanguageServer.writeMessage(out, "{\"a\":1}");
        SmaliLanguageServer.writeMessage(out, "{\"b\":2}");
        InputStream in = new ByteArrayInputStream(out.toByteArray());

        Assert.assertEquals("{\"a\":1}", SmaliLanguageServer.readMessage(in));
        Assert.assertEquals("{\"b\":2}", SmaliLanguageServer.readMessage(in));
        Assert.assertNull(SmaliLanguageServer.readMessage(in));
    }

    // -- word extraction -----------------------------------------------------

    @Test
    public void wordAt_findsOpcodeUnderCursor() {
        String text = "    const-string v0, \"hi\"\n    return-void\n";
        Assert.assertEquals("const-string", SmaliLanguageServer.wordAt(text, 0, 6));
        Assert.assertEquals("return-void", SmaliLanguageServer.wordAt(text, 1, 8));
    }

    @Test
    public void wordAt_findsDirective() {
        String text = ".method public foo()V\n";
        Assert.assertEquals(".method", SmaliLanguageServer.wordAt(text, 0, 2));
    }

    @Test
    public void wordAt_outOfRangeReturnsNull() {
        String text = "nop\n";
        Assert.assertNull(SmaliLanguageServer.wordAt(text, 5, 0));
    }

    // -- end-to-end dispatch over a real stdio pipe --------------------------

    @Test
    public void initializeThenDidOpen_repliesAndPublishesDiagnostics() throws IOException {
        String badDoc = ".class public Lcom/example/Broken;\\n"; // missing .super
        List<String> requests = new ArrayList<>();
        requests.add("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":{}}");
        requests.add("{\"jsonrpc\":\"2.0\",\"method\":\"initialized\",\"params\":{}}");
        requests.add("{\"jsonrpc\":\"2.0\",\"method\":\"textDocument/didOpen\",\"params\":{" +
                "\"textDocument\":{\"uri\":\"file:///Broken.smali\",\"languageId\":\"smali\"," +
                "\"version\":1,\"text\":\"" + badDoc + "\"}}}");
        requests.add("{\"jsonrpc\":\"2.0\",\"method\":\"exit\",\"params\":{}}");

        ByteArrayOutputStream framedIn = new ByteArrayOutputStream();
        for (String r : requests) {
            SmaliLanguageServer.writeMessage(framedIn, r);
        }

        ByteArrayOutputStream serverOut = new ByteArrayOutputStream();
        new SmaliLanguageServer(new ByteArrayInputStream(framedIn.toByteArray()), serverOut).run();

        List<JsonObject> responses = drain(serverOut.toByteArray());

        JsonObject initResult = findById(responses, 1);
        Assert.assertNotNull("expected an initialize response", initResult);
        Assert.assertTrue(initResult.getAsJsonObject("result")
                .getAsJsonObject("capabilities").get("documentSymbolProvider").getAsBoolean());

        JsonObject publish = findByMethod(responses, "textDocument/publishDiagnostics");
        Assert.assertNotNull("expected a publishDiagnostics notification", publish);
        JsonArray diags = publish.getAsJsonObject("params").getAsJsonArray("diagnostics");
        Assert.assertTrue("expected the missing-.super diagnostic", diags.size() > 0);
    }

    private static List<JsonObject> drain(byte[] framed) throws IOException {
        List<JsonObject> messages = new ArrayList<>();
        InputStream in = new ByteArrayInputStream(framed);
        String content;
        while ((content = SmaliLanguageServer.readMessage(in)) != null) {
            messages.add(new JsonParser().parse(content).getAsJsonObject());
        }
        return messages;
    }

    private static JsonObject findById(List<JsonObject> messages, int id) {
        for (JsonObject m : messages) {
            if (m.has("id") && !m.get("id").isJsonNull() && m.get("id").getAsInt() == id) {
                return m;
            }
        }
        return null;
    }

    private static JsonObject findByMethod(List<JsonObject> messages, String method) {
        for (JsonObject m : messages) {
            if (m.has("method") && method.equals(m.get("method").getAsString())) {
                return m;
            }
        }
        return null;
    }
}
