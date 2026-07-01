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

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.Assert;
import org.junit.Test;

/**
 * Tests for {@link TransformReport}, the shared success-report renderer used by the write-back
 * transform commands (unlock/replace/strip-debug/patch).
 */
public class TransformReportTest {

    @Test
    public void base_seedsCommonFields() {
        JsonObject report = TransformReport.base("unlock", "app.apk", "out.dex");
        Assert.assertEquals("unlock", report.get("command").getAsString());
        Assert.assertEquals("app.apk", report.get("input").getAsString());
        Assert.assertEquals("out.dex", report.get("output").getAsString());
    }

    @Test
    public void render_jsonSerializesTheReportObject() {
        JsonObject report = TransformReport.base("patch", "app.apk", "patched.dex");
        report.addProperty("matched", 3);
        report.addProperty("return", "true");

        String out = TransformReport.render(true, report, "some human text");
        JsonObject parsed = new JsonParser().parse(out).getAsJsonObject();
        Assert.assertEquals("patch", parsed.get("command").getAsString());
        Assert.assertEquals(3, parsed.get("matched").getAsInt());
        Assert.assertEquals("true", parsed.get("return").getAsString());
    }

    @Test
    public void render_textReturnsHumanSentenceVerbatim() {
        JsonObject report = TransformReport.base("strip-debug", "app.apk", "out.dex");
        String human = "Wrote out.dex (debug info stripped).";
        Assert.assertEquals(human, TransformReport.render(false, report, human));
    }

    @Test
    public void render_jsonKeepsUrlsAndRegexesUnescaped() {
        // HTML escaping is disabled so replacement URLs / regex metacharacters stay legible.
        JsonObject report = TransformReport.base("replace", "app.apk", "out.dex");
        report.addProperty("to", "http://new.example?a=1&b=2");

        String out = TransformReport.render(true, report, "");
        Assert.assertTrue("URL should not be HTML-escaped: " + out,
                out.contains("http://new.example?a=1&b=2"));
    }
}
