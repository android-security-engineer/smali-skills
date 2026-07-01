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

package org.jf.baksmali.diff;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import org.jf.dexlib2.iface.ClassDef;
import org.jf.dexlib2.iface.DexFile;
import org.jf.dexlib2.iface.Method;
import org.jf.dexlib2.iface.MethodImplementation;
import org.jf.dexlib2.iface.instruction.Instruction;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * A semantic (opcode-level) diff between two dex files: which classes and methods were added,
 * removed, or changed.
 *
 * <p>Classes are keyed by their type descriptor; methods by their canonical smali descriptor
 * {@code Lcls;->name(params)ret}. A method is considered <em>changed</em> when its opcode sequence
 * differs between the two files — register allocation, debug info (line numbers, locals), and
 * instruction offsets are deliberately ignored, so cosmetic recompilation noise does not register
 * as a semantic change. A method with a body in one file and no body (abstract/native) in the other
 * also counts as changed.
 *
 * <p>This model is pure and I/O-free; the {@code baksmali diff} command handles loading and
 * printing. Output is deterministic (all collections are sorted).
 */
public class DexDiff {

    private final List<String> addedClasses = new ArrayList<>();
    private final List<String> removedClasses = new ArrayList<>();
    private final List<ClassDiff> changedClasses = new ArrayList<>();

    /**
     * The per-class breakdown of method-level changes, for a class present in both dex files.
     */
    public static class ClassDiff {
        public final String type;
        public final List<String> addedMethods = new ArrayList<>();
        public final List<String> removedMethods = new ArrayList<>();
        public final List<String> changedMethods = new ArrayList<>();

        ClassDiff(@Nonnull String type) {
            this.type = type;
        }

        boolean isEmpty() {
            return addedMethods.isEmpty() && removedMethods.isEmpty() && changedMethods.isEmpty();
        }
    }

    /**
     * Computes the semantic diff of {@code oldDex} → {@code newDex}.
     */
    @Nonnull
    public static DexDiff compute(@Nonnull DexFile oldDex, @Nonnull DexFile newDex) {
        DexDiff diff = new DexDiff();

        Map<String, ClassDef> oldClasses = byType(oldDex);
        Map<String, ClassDef> newClasses = byType(newDex);

        for (String type : newClasses.keySet()) {
            if (!oldClasses.containsKey(type)) {
                diff.addedClasses.add(type);
            }
        }
        for (String type : oldClasses.keySet()) {
            if (!newClasses.containsKey(type)) {
                diff.removedClasses.add(type);
            }
        }

        // Classes present in both: compare their methods by opcode signature.
        for (Map.Entry<String, ClassDef> entry : oldClasses.entrySet()) {
            String type = entry.getKey();
            ClassDef newClassDef = newClasses.get(type);
            if (newClassDef == null) {
                continue;
            }
            ClassDiff classDiff = compareMethods(type, entry.getValue(), newClassDef);
            if (!classDiff.isEmpty()) {
                diff.changedClasses.add(classDiff);
            }
        }
        return diff;
    }

    @Nonnull
    private static ClassDiff compareMethods(@Nonnull String type,
                                            @Nonnull ClassDef oldClassDef,
                                            @Nonnull ClassDef newClassDef) {
        ClassDiff classDiff = new ClassDiff(type);

        // descriptor -> opcode signature ("" means present but bodyless; null key absent)
        Map<String, String> oldSigs = signatures(oldClassDef);
        Map<String, String> newSigs = signatures(newClassDef);

        for (Map.Entry<String, String> e : newSigs.entrySet()) {
            if (!oldSigs.containsKey(e.getKey())) {
                classDiff.addedMethods.add(e.getKey());
            }
        }
        for (Map.Entry<String, String> e : oldSigs.entrySet()) {
            String newSig = newSigs.get(e.getKey());
            if (newSig == null) {
                classDiff.removedMethods.add(e.getKey());
            } else if (!newSig.equals(e.getValue())) {
                classDiff.changedMethods.add(e.getKey());
            }
        }
        return classDiff;
    }

    @Nonnull
    private static Map<String, ClassDef> byType(@Nonnull DexFile dexFile) {
        Map<String, ClassDef> map = new TreeMap<>();
        for (ClassDef classDef : dexFile.getClasses()) {
            map.put(classDef.getType(), classDef);
        }
        return map;
    }

    @Nonnull
    private static Map<String, String> signatures(@Nonnull ClassDef classDef) {
        Map<String, String> map = new TreeMap<>();
        for (Method method : classDef.getMethods()) {
            map.put(descriptor(method), opcodeSignature(method));
        }
        return map;
    }

    /**
     * The opcode-only signature of a method body: comma-joined opcode names, or the empty string for
     * an abstract/native method with no implementation.
     */
    @Nonnull
    private static String opcodeSignature(@Nonnull Method method) {
        MethodImplementation impl = method.getImplementation();
        if (impl == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (Instruction instruction : impl.getInstructions()) {
            if (sb.length() > 0) {
                sb.append(',');
            }
            sb.append(instruction.getOpcode().name);
        }
        return sb.toString();
    }

    @Nonnull
    private static String descriptor(@Nonnull Method method) {
        StringBuilder sb = new StringBuilder();
        sb.append(method.getDefiningClass()).append("->").append(method.getName()).append('(');
        for (CharSequence param : method.getParameterTypes()) {
            sb.append(param);
        }
        sb.append(')').append(method.getReturnType());
        return sb.toString();
    }

    // -- accessors -----------------------------------------------------------

    @Nonnull public List<String> getAddedClasses() {
        return new ArrayList<>(addedClasses);
    }

    @Nonnull public List<String> getRemovedClasses() {
        return new ArrayList<>(removedClasses);
    }

    @Nonnull public List<ClassDiff> getChangedClasses() {
        return new ArrayList<>(changedClasses);
    }

    /** @return true when the two dex files are semantically identical at the opcode level. */
    public boolean isEmpty() {
        return addedClasses.isEmpty() && removedClasses.isEmpty() && changedClasses.isEmpty();
    }

    // -- exporters -----------------------------------------------------------

    /**
     * Human-readable text report. Empty diff yields a single "no differences" line.
     */
    @Nonnull
    public String toText() {
        if (isEmpty()) {
            return "No semantic differences.\n";
        }
        StringBuilder sb = new StringBuilder();
        for (String type : addedClasses) {
            sb.append("+ class ").append(type).append('\n');
        }
        for (String type : removedClasses) {
            sb.append("- class ").append(type).append('\n');
        }
        for (ClassDiff classDiff : changedClasses) {
            sb.append("~ class ").append(classDiff.type).append('\n');
            for (String m : classDiff.addedMethods) {
                sb.append("    + ").append(m).append('\n');
            }
            for (String m : classDiff.removedMethods) {
                sb.append("    - ").append(m).append('\n');
            }
            for (String m : classDiff.changedMethods) {
                sb.append("    ~ ").append(m).append('\n');
            }
        }
        return sb.toString();
    }

    /**
     * JSON report:
     * {@code {"addedClasses":[...],"removedClasses":[...],
     *          "changedClasses":[{"type":..,"addedMethods":[..],"removedMethods":[..],"changedMethods":[..]}]}}.
     */
    @Nonnull
    public String toJson() {
        JsonObject root = new JsonObject();
        root.add("addedClasses", stringArray(addedClasses));
        root.add("removedClasses", stringArray(removedClasses));

        JsonArray changed = new JsonArray();
        for (ClassDiff classDiff : changedClasses) {
            JsonObject obj = new JsonObject();
            obj.addProperty("type", classDiff.type);
            obj.add("addedMethods", stringArray(classDiff.addedMethods));
            obj.add("removedMethods", stringArray(classDiff.removedMethods));
            obj.add("changedMethods", stringArray(classDiff.changedMethods));
            changed.add(obj);
        }
        root.add("changedClasses", changed);
        return new GsonBuilder().disableHtmlEscaping().create().toJson(root);
    }

    @Nonnull
    private static JsonArray stringArray(@Nonnull List<String> values) {
        JsonArray array = new JsonArray();
        for (String value : values) {
            array.add(new JsonPrimitive(value));
        }
        return array;
    }
}
