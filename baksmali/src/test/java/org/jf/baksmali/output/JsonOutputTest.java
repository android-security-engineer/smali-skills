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
import com.google.gson.JsonParser;
import org.jf.dexlib2.iface.ClassDef;
import org.jf.dexlib2.iface.Field;
import org.jf.dexlib2.iface.Method;
import org.jf.dexlib2.iface.reference.FieldReference;
import org.jf.dexlib2.iface.reference.MethodReference;
import org.jf.dexlib2.iface.reference.StringReference;
import org.jf.dexlib2.iface.reference.TypeReference;
import org.jf.dexlib2.immutable.reference.ImmutableFieldReference;
import org.jf.dexlib2.immutable.reference.ImmutableMethodReference;
import org.jf.dexlib2.immutable.reference.ImmutableStringReference;
import org.jf.dexlib2.immutable.reference.ImmutableTypeReference;
import org.jf.smali.SmaliTestUtils;
import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * Tests for {@link JsonOutput}, verifying the JSON schema produced for each reference type
 * and for full class definitions.
 */
public class JsonOutputTest {

    private final JsonOutput jsonOutput = new JsonOutput();

    // ---- Reference serialization ----

    @Test
    public void testMethodReference() {
        MethodReference ref = new ImmutableMethodReference(
                "Lcom/Example;", "foo", java.util.Arrays.asList("I", "Ljava/lang/String;"), "V");

        JsonObject obj = jsonOutput.toJson(ref);

        Assert.assertEquals("Lcom/Example;", obj.get("class").getAsString());
        Assert.assertEquals("foo", obj.get("name").getAsString());
        Assert.assertEquals("V", obj.get("returnType").getAsString());

        JsonArray params = obj.getAsJsonArray("parameters");
        Assert.assertEquals(2, params.size());
        Assert.assertEquals("I", params.get(0).getAsString());
        Assert.assertEquals("Ljava/lang/String;", params.get(1).getAsString());
    }

    @Test
    public void testFieldReference() {
        FieldReference ref = new ImmutableFieldReference("Lcom/Example;", "count", "I");

        JsonObject obj = jsonOutput.toJson(ref);

        Assert.assertEquals("Lcom/Example;", obj.get("class").getAsString());
        Assert.assertEquals("count", obj.get("name").getAsString());
        Assert.assertEquals("I", obj.get("type").getAsString());
    }

    @Test
    public void testStringReference() {
        StringReference ref = new ImmutableStringReference("hello world");

        JsonObject obj = jsonOutput.toJson(ref);

        Assert.assertEquals("hello world", obj.get("string").getAsString());
    }

    @Test
    public void testTypeReference() {
        TypeReference ref = new ImmutableTypeReference("Lcom/Example;");

        JsonObject obj = jsonOutput.toJson(ref);

        Assert.assertEquals("Lcom/Example;", obj.get("type").getAsString());
    }

    @Test
    public void testMethodReference_NoParameters() {
        MethodReference ref = new ImmutableMethodReference(
                "Lcom/Example;", "bar", java.util.Collections.<String>emptyList(), "I");

        JsonObject obj = jsonOutput.toJson(ref);

        Assert.assertEquals(0, obj.getAsJsonArray("parameters").size());
        Assert.assertEquals("I", obj.get("returnType").getAsString());
    }

    // ---- Polymorphic dispatch ----

    @Test
    public void testPolymorphicDispatch() {
        // toJson(Reference) should dispatch to the correct concrete serializer.
        JsonObject methodJson = jsonOutput.toJson((org.jf.dexlib2.iface.reference.Reference)
                new ImmutableMethodReference("LA;", "m", java.util.Collections.<String>emptyList(), "V"));
        Assert.assertTrue(methodJson.has("returnType"));
        Assert.assertFalse(methodJson.has("type"));

        JsonObject fieldJson = jsonOutput.toJson((org.jf.dexlib2.iface.reference.Reference)
                new ImmutableFieldReference("LA;", "f", "I"));
        Assert.assertTrue(fieldJson.has("type"));
        Assert.assertFalse(fieldJson.has("returnType"));

        JsonObject stringJson = jsonOutput.toJson((org.jf.dexlib2.iface.reference.Reference)
                new ImmutableStringReference("s"));
        Assert.assertTrue(stringJson.has("string"));

        JsonObject typeJson = jsonOutput.toJson((org.jf.dexlib2.iface.reference.Reference)
                new ImmutableTypeReference("LA;"));
        Assert.assertTrue(typeJson.has("type"));
        Assert.assertEquals("LA;", typeJson.get("type").getAsString());
    }

    // ---- Array rendering ----

    @Test
    public void testToJsonArray() {
        List<JsonObject> objects = new ArrayList<>();
        objects.add(jsonOutput.toJson(new ImmutableStringReference("a")));
        objects.add(jsonOutput.toJson(new ImmutableStringReference("b")));

        String json = jsonOutput.toJsonArray(objects);

        JsonArray parsed = new JsonParser().parse(json).getAsJsonArray();
        Assert.assertEquals(2, parsed.size());
        Assert.assertEquals("a", parsed.get(0).getAsJsonObject().get("string").getAsString());
        Assert.assertEquals("b", parsed.get(1).getAsJsonObject().get("string").getAsString());
    }

    @Test
    public void testToJsonArray_Empty() {
        String json = jsonOutput.toJsonArray(new ArrayList<JsonObject>());
        Assert.assertEquals("[]", json);
    }

    // ---- Full class def (compiled from smali) ----

    @Test
    public void testClassDef() throws Exception {
        String smali =
                ".class public Lcom/example/Person;\n" +
                ".super Ljava/lang/Object;\n" +
                ".implements Ljava/lang/Runnable;\n" +
                "\n" +
                ".field public name:Ljava/lang/String;\n" +
                ".field private age:I\n" +
                "\n" +
                ".method public constructor <init>()V\n" +
                "    .registers 1\n" +
                "    invoke-direct {p0}, Ljava/lang/Object;-><init>()V\n" +
                "    return-void\n" +
                ".end method\n" +
                "\n" +
                ".method public run()V\n" +
                "    .registers 1\n" +
                "    return-void\n" +
                ".end method\n" +
                "\n" +
                ".method public greet(Ljava/lang/String;)I\n" +
                "    .registers 2\n" +
                "    const/4 v0, 0x0\n" +
                "    return v0\n" +
                ".end method\n";

        ClassDef classDef = SmaliTestUtils.compileSmali(smali);

        JsonObject obj = jsonOutput.toJson(classDef);

        Assert.assertEquals("Lcom/example/Person;", obj.get("type").getAsString());
        Assert.assertEquals("Ljava/lang/Object;", obj.get("superclass").getAsString());

        // interfaces
        JsonArray interfaces = obj.getAsJsonArray("interfaces");
        Assert.assertEquals(1, interfaces.size());
        Assert.assertEquals("Ljava/lang/Runnable;", interfaces.get(0).getAsString());

        // fields
        JsonArray fields = obj.getAsJsonArray("fields");
        Assert.assertEquals(2, fields.size());

        // methods — constructor, run, greet
        JsonArray methods = obj.getAsJsonArray("methods");
        Assert.assertEquals(3, methods.size());

        // Find the greet method and verify its parameters.
        JsonObject greet = null;
        for (int i = 0; i < methods.size(); i++) {
            JsonObject m = methods.get(i).getAsJsonObject();
            if ("greet".equals(m.get("name").getAsString())) {
                greet = m;
            }
        }
        Assert.assertNotNull("greet method not found", greet);
        Assert.assertEquals("I", greet.get("returnType").getAsString());
        JsonArray params = greet.getAsJsonArray("parameters");
        Assert.assertEquals(1, params.size());
        Assert.assertEquals("Ljava/lang/String;", params.get(0).getAsString());
    }

    @Test
    public void testClassDef_RoundtripsThroughJsonParser() throws Exception {
        // The output of toJsonString should be parseable JSON with no escaping surprises.
        String smali =
                ".class public Lcom/example/Quote;\n" +
                ".super Ljava/lang/Object;\n" +
                "\n" +
                ".method public escape()V\n" +
                "    .registers 2\n" +
                "    const-string v0, \"a\\\"b\\\\c\"\n" +
                "    return-void\n" +
                ".end method\n";

        ClassDef classDef = SmaliTestUtils.compileSmali(smali);
        JsonObject obj = jsonOutput.toJson(classDef);
        String json = jsonOutput.toJsonString(obj);

        // Re-parse — if escaping is broken this throws.
        JsonObject reparsed = new JsonParser().parse(json).getAsJsonObject();
        Assert.assertEquals("Lcom/example/Quote;", reparsed.get("type").getAsString());
    }
}
