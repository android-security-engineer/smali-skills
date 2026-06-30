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

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import org.jf.dexlib2.iface.ClassDef;
import org.jf.dexlib2.iface.Field;
import org.jf.dexlib2.iface.Method;
import org.jf.dexlib2.iface.reference.FieldReference;
import org.jf.dexlib2.iface.reference.MethodReference;
import org.jf.dexlib2.iface.reference.Reference;
import org.jf.dexlib2.iface.reference.StringReference;
import org.jf.dexlib2.iface.reference.TypeReference;

import javax.annotation.Nonnull;
import java.util.List;

/**
 * Serializes dex references and class definitions to JSON for machine consumption
 * (e.g. by AI agents or other tooling).
 *
 * <p>The JSON schema is intentionally flat and predictable. For example, a method reference
 * serializes to:
 * <pre>
 * {"class":"Lcom/Example;","name":"foo","parameters":["I"],"returnType":"V"}
 * </pre>
 */
public class JsonOutput {

    private final Gson gson;

    public JsonOutput() {
        this.gson = new GsonBuilder().disableHtmlEscaping().create();
    }

    /**
     * Serializes a reference to a JSON object based on its concrete type.
     */
    @Nonnull
    public JsonObject toJson(@Nonnull Reference reference) {
        if (reference instanceof MethodReference) {
            return toJson((MethodReference) reference);
        } else if (reference instanceof FieldReference) {
            return toJson((FieldReference) reference);
        } else if (reference instanceof StringReference) {
            return toJson((StringReference) reference);
        } else if (reference instanceof TypeReference) {
            return toJson((TypeReference) reference);
        } else {
            // Fallback: a single "value" field with the string representation.
            JsonObject obj = new JsonObject();
            obj.addProperty("value", reference.toString());
            return obj;
        }
    }

    @Nonnull
    public JsonObject toJson(@Nonnull MethodReference method) {
        JsonObject obj = new JsonObject();
        obj.addProperty("class", method.getDefiningClass());
        obj.addProperty("name", method.getName());
        JsonArray params = new JsonArray();
        for (CharSequence param : method.getParameterTypes()) {
            params.add(new JsonPrimitive(param.toString()));
        }
        obj.add("parameters", params);
        obj.addProperty("returnType", method.getReturnType());
        return obj;
    }

    @Nonnull
    public JsonObject toJson(@Nonnull FieldReference field) {
        JsonObject obj = new JsonObject();
        obj.addProperty("class", field.getDefiningClass());
        obj.addProperty("name", field.getName());
        obj.addProperty("type", field.getType());
        return obj;
    }

    @Nonnull
    public JsonObject toJson(@Nonnull StringReference string) {
        JsonObject obj = new JsonObject();
        obj.addProperty("string", string.getString());
        return obj;
    }

    @Nonnull
    public JsonObject toJson(@Nonnull TypeReference type) {
        JsonObject obj = new JsonObject();
        obj.addProperty("type", type.getType());
        return obj;
    }

    /**
     * Serializes a full class definition (including its declared methods and fields).
     */
    @Nonnull
    public JsonObject toJson(@Nonnull ClassDef classDef) {
        JsonObject obj = new JsonObject();
        obj.addProperty("type", classDef.getType());
        obj.addProperty("superclass", classDef.getSuperclass());
        obj.addProperty("accessFlags", classDef.getAccessFlags());

        JsonArray interfaces = new JsonArray();
        for (CharSequence iface : classDef.getInterfaces()) {
            interfaces.add(new JsonPrimitive(iface.toString()));
        }
        obj.add("interfaces", interfaces);

        JsonArray fields = new JsonArray();
        for (Field field : classDef.getFields()) {
            JsonObject f = new JsonObject();
            f.addProperty("name", field.getName());
            f.addProperty("type", field.getType());
            f.addProperty("accessFlags", field.getAccessFlags());
            fields.add(f);
        }
        obj.add("fields", fields);

        JsonArray methods = new JsonArray();
        for (Method method : classDef.getMethods()) {
            JsonObject m = new JsonObject();
            m.addProperty("name", method.getName());
            JsonArray params = new JsonArray();
            for (CharSequence param : method.getParameterTypes()) {
                params.add(new JsonPrimitive(param.toString()));
            }
            m.add("parameters", params);
            m.addProperty("returnType", method.getReturnType());
            m.addProperty("accessFlags", method.getAccessFlags());
            methods.add(m);
        }
        obj.add("methods", methods);

        return obj;
    }

    /**
     * Renders a list of arbitrary JSON-serializable objects as a JSON array string (one line).
     */
    @Nonnull
    public String toJsonArray(@Nonnull List<? extends JsonObject> objects) {
        JsonArray array = new JsonArray();
        for (JsonObject obj : objects) {
            array.add(obj);
        }
        return gson.toJson(array);
    }

    /**
     * Renders a single JSON object as a string.
     */
    @Nonnull
    public String toJsonString(@Nonnull JsonObject object) {
        return gson.toJson(object);
    }
}
