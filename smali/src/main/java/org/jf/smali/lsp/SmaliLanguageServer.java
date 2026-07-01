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

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.jf.smali.format.SmaliFormatter;

import javax.annotation.Nullable;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A minimal, dependency-light Language Server for smali source.
 *
 * <p>It speaks LSP over stdio using the standard {@code Content-Length}-framed
 * JSON-RPC envelope, hand-rolled on top of the Gson dependency the project
 * already ships (no LSP4J / network dependency). All real analysis is delegated
 * to {@link SmaliAnalyzer}; this class is purely the transport + dispatch layer.
 *
 * <p>Supported requests: {@code initialize}, {@code textDocument/documentSymbol},
 * {@code textDocument/hover}, {@code shutdown}. Supported notifications:
 * {@code initialized}, {@code textDocument/didOpen|didChange|didClose},
 * {@code exit}. Diagnostics are pushed via {@code textDocument/publishDiagnostics}
 * whenever a document is opened or changed.
 */
public class SmaliLanguageServer {

    private final InputStream in;
    private final OutputStream out;
    private final Gson gson = new Gson();

    /** uri -> current document text. */
    private final Map<String, String> documents = new HashMap<>();

    private int apiLevel = SmaliAnalyzer.DEFAULT_API_LEVEL;
    private boolean shutdownRequested = false;

    public SmaliLanguageServer(InputStream in, OutputStream out) {
        this.in = in;
        this.out = out;
    }

    public static void main(String[] args) throws IOException {
        new SmaliLanguageServer(System.in, System.out).run();
    }

    /** Runs the read/dispatch loop until {@code exit} (or EOF). */
    public void run() throws IOException {
        String content;
        while ((content = readMessage(in)) != null) {
            JsonObject message;
            try {
                message = new JsonParser().parse(content).getAsJsonObject();
            } catch (RuntimeException ex) {
                continue; // ignore malformed frames
            }
            boolean exit = handle(message);
            if (exit) {
                return;
            }
        }
    }

    /**
     * Handles one decoded JSON-RPC message. Returns true if the server should
     * terminate (the {@code exit} notification).
     */
    boolean handle(JsonObject message) throws IOException {
        String method = message.has("method") ? message.get("method").getAsString() : null;
        if (method == null) {
            return false; // a response; we never send requests, so ignore
        }
        JsonElement id = message.get("id");
        JsonObject params = message.has("params") && message.get("params").isJsonObject()
                ? message.getAsJsonObject("params") : new JsonObject();

        switch (method) {
            case "initialize":
                applyInitializeOptions(params);
                sendResult(id, buildInitializeResult());
                return false;
            case "initialized":
                return false;
            case "shutdown":
                shutdownRequested = true;
                sendResult(id, JsonNull.INSTANCE);
                return false;
            case "exit":
                return true;
            case "textDocument/didOpen":
                didOpen(params);
                return false;
            case "textDocument/didChange":
                didChange(params);
                return false;
            case "textDocument/didClose":
                didClose(params);
                return false;
            case "textDocument/documentSymbol":
                sendResult(id, documentSymbol(params));
                return false;
            case "textDocument/hover":
                sendResult(id, hover(params));
                return false;
            case "textDocument/formatting":
                sendResult(id, formatting(params));
                return false;
            default:
                if (id != null && !id.isJsonNull()) {
                    // Respond with an empty result to unknown requests so clients
                    // that block on a reply don't hang.
                    sendResult(id, JsonNull.INSTANCE);
                }
                return false;
        }
    }

    // -- request/notification handlers --------------------------------------

    private void applyInitializeOptions(JsonObject params) {
        if (params.has("initializationOptions") && params.get("initializationOptions").isJsonObject()) {
            JsonObject opts = params.getAsJsonObject("initializationOptions");
            if (opts.has("apiLevel")) {
                try {
                    apiLevel = opts.get("apiLevel").getAsInt();
                } catch (RuntimeException ignored) {
                    // keep default
                }
            }
        }
    }

    private JsonObject buildInitializeResult() {
        JsonObject capabilities = new JsonObject();
        // textDocumentSync: 1 = full document sync on every change.
        capabilities.addProperty("textDocumentSync", 1);
        capabilities.addProperty("documentSymbolProvider", true);
        capabilities.addProperty("hoverProvider", true);
        capabilities.addProperty("documentFormattingProvider", true);

        JsonObject serverInfo = new JsonObject();
        serverInfo.addProperty("name", "smali-language-server");

        JsonObject result = new JsonObject();
        result.add("capabilities", capabilities);
        result.add("serverInfo", serverInfo);
        return result;
    }

    private void didOpen(JsonObject params) throws IOException {
        JsonObject doc = params.getAsJsonObject("textDocument");
        String uri = doc.get("uri").getAsString();
        String text = doc.get("text").getAsString();
        documents.put(uri, text);
        publishDiagnostics(uri, text);
    }

    private void didChange(JsonObject params) throws IOException {
        JsonObject doc = params.getAsJsonObject("textDocument");
        String uri = doc.get("uri").getAsString();
        JsonArray changes = params.getAsJsonArray("contentChanges");
        if (changes != null && changes.size() > 0) {
            // Full-sync mode: the last change carries the entire document text.
            JsonObject last = changes.get(changes.size() - 1).getAsJsonObject();
            String text = last.get("text").getAsString();
            documents.put(uri, text);
            publishDiagnostics(uri, text);
        }
    }

    private void didClose(JsonObject params) {
        JsonObject doc = params.getAsJsonObject("textDocument");
        String uri = doc.get("uri").getAsString();
        documents.remove(uri);
    }

    private JsonElement documentSymbol(JsonObject params) {
        String uri = params.getAsJsonObject("textDocument").get("uri").getAsString();
        String text = documents.getOrDefault(uri, "");
        List<LspModels.DocumentSymbol> symbols = new SmaliAnalyzer(apiLevel).documentSymbols(text);
        return gson.toJsonTree(symbols);
    }

    private JsonElement hover(JsonObject params) {
        String uri = params.getAsJsonObject("textDocument").get("uri").getAsString();
        String text = documents.get(uri);
        if (text == null) {
            return JsonNull.INSTANCE;
        }
        JsonObject position = params.getAsJsonObject("position");
        int line = position.get("line").getAsInt();
        int character = position.get("character").getAsInt();

        String word = wordAt(text, line, character);
        String doc = OpcodeDocs.lookup(word);
        if (doc == null) {
            return JsonNull.INSTANCE;
        }
        LspModels.Hover result = new LspModels.Hover(new LspModels.MarkupContent("markdown", doc));
        return gson.toJsonTree(result);
    }

    /**
     * Handles {@code textDocument/formatting}: reformats the whole document with
     * {@link SmaliFormatter} and returns a single full-range {@code TextEdit}. If the document is
     * unknown or already formatted, returns an empty edit list.
     */
    private JsonElement formatting(JsonObject params) {
        String uri = params.getAsJsonObject("textDocument").get("uri").getAsString();
        String text = documents.get(uri);
        JsonArray edits = new JsonArray();
        if (text == null) {
            return edits;
        }
        String formatted = new SmaliFormatter().format(text);
        if (formatted.equals(text)) {
            return edits;
        }

        // A single edit replacing the entire document. The end position is the start of the line
        // just past the last one, which addresses the whole buffer regardless of a trailing newline.
        int lineCount = text.split("\n", -1).length;
        JsonObject start = new JsonObject();
        start.addProperty("line", 0);
        start.addProperty("character", 0);
        JsonObject end = new JsonObject();
        end.addProperty("line", lineCount);
        end.addProperty("character", 0);
        JsonObject range = new JsonObject();
        range.add("start", start);
        range.add("end", end);

        JsonObject edit = new JsonObject();
        edit.add("range", range);
        edit.addProperty("newText", formatted);
        edits.add(edit);
        return edits;
    }

    private void publishDiagnostics(String uri, String text) throws IOException {
        List<LspModels.Diagnostic> diagnostics = new SmaliAnalyzer(apiLevel).diagnostics(text);
        JsonObject params = new JsonObject();
        params.addProperty("uri", uri);
        params.add("diagnostics", gson.toJsonTree(diagnostics));
        sendNotification("textDocument/publishDiagnostics", params);
    }

    // -- word extraction -----------------------------------------------------

    /**
     * Returns the smali "word" (opcode/directive-style token) at the given
     * zero-based line/character, or null if the position isn't on a word. A word
     * is a run of characters valid in an opcode or a leading-dot directive:
     * letters, digits, and {@code - / .}.
     */
    @Nullable
    static String wordAt(String text, int line, int character) {
        String[] lines = text.split("\n", -1);
        if (line < 0 || line >= lines.length) {
            return null;
        }
        String lineText = lines[line];
        // Strip a trailing carriage return from CRLF documents.
        if (lineText.endsWith("\r")) {
            lineText = lineText.substring(0, lineText.length() - 1);
        }
        if (character < 0 || character > lineText.length()) {
            return null;
        }
        int start = character;
        while (start > 0 && isWordChar(lineText.charAt(start - 1))) {
            start--;
        }
        int end = character;
        while (end < lineText.length() && isWordChar(lineText.charAt(end))) {
            end++;
        }
        if (start == end) {
            return null;
        }
        return lineText.substring(start, end);
    }

    private static boolean isWordChar(char c) {
        return Character.isLetterOrDigit(c) || c == '-' || c == '/' || c == '.';
    }

    // -- JSON-RPC framing ----------------------------------------------------

    private void sendResult(@Nullable JsonElement id, JsonElement result) throws IOException {
        if (id == null) {
            return; // notification: no reply
        }
        JsonObject response = new JsonObject();
        response.addProperty("jsonrpc", "2.0");
        response.add("id", id);
        response.add("result", result == null ? JsonNull.INSTANCE : result);
        writeMessage(out, gson.toJson(response));
    }

    private void sendNotification(String method, JsonObject params) throws IOException {
        JsonObject notification = new JsonObject();
        notification.addProperty("jsonrpc", "2.0");
        notification.addProperty("method", method);
        notification.add("params", params);
        writeMessage(out, gson.toJson(notification));
    }

    /**
     * Reads one {@code Content-Length}-framed message from the stream, returning
     * its JSON body, or null on clean EOF.
     */
    @Nullable
    static String readMessage(InputStream in) throws IOException {
        int contentLength = -1;
        // Read headers line-by-line until a blank line.
        while (true) {
            String header = readHeaderLine(in);
            if (header == null) {
                return null; // EOF before any header
            }
            if (header.isEmpty()) {
                break; // end of headers
            }
            int colon = header.indexOf(':');
            if (colon > 0) {
                String name = header.substring(0, colon).trim();
                String value = header.substring(colon + 1).trim();
                if (name.equalsIgnoreCase("Content-Length")) {
                    try {
                        contentLength = Integer.parseInt(value);
                    } catch (NumberFormatException ex) {
                        contentLength = -1;
                    }
                }
            }
        }
        if (contentLength < 0) {
            return null;
        }
        byte[] body = new byte[contentLength];
        int read = 0;
        while (read < contentLength) {
            int r = in.read(body, read, contentLength - read);
            if (r < 0) {
                return null; // truncated
            }
            read += r;
        }
        return new String(body, StandardCharsets.UTF_8);
    }

    /** Reads a single CRLF- (or LF-) terminated header line as ASCII. */
    @Nullable
    private static String readHeaderLine(InputStream in) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        int c;
        boolean any = false;
        while ((c = in.read()) != -1) {
            any = true;
            if (c == '\n') {
                break;
            }
            if (c != '\r') {
                buffer.write(c);
            }
        }
        if (!any && buffer.size() == 0) {
            return null;
        }
        return new String(buffer.toByteArray(), StandardCharsets.US_ASCII);
    }

    /** Writes a JSON body with the LSP {@code Content-Length} framing. */
    static void writeMessage(OutputStream out, String json) throws IOException {
        byte[] body = json.getBytes(StandardCharsets.UTF_8);
        String header = "Content-Length: " + body.length + "\r\n\r\n";
        synchronized (out) {
            out.write(header.getBytes(StandardCharsets.US_ASCII));
            out.write(body);
            out.flush();
        }
    }
}
