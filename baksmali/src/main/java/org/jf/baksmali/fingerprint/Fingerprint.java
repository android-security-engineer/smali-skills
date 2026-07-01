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

package org.jf.baksmali.fingerprint;

import org.jf.dexlib2.iface.ClassDef;
import org.jf.dexlib2.iface.DexFile;
import org.jf.dexlib2.iface.Method;
import org.jf.dexlib2.iface.MethodImplementation;
import org.jf.dexlib2.iface.instruction.Instruction;

import javax.annotation.Nonnull;
import java.nio.charset.Charset;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Opcode-based fingerprints for dex methods, classes, and whole dex files.
 *
 * <p>Every fingerprint is derived purely from the <em>opcode sequence</em> of method bodies, never
 * from names or references. This makes it <strong>rename-invariant</strong>: obfuscation that
 * renames classes/methods/fields (but leaves the bytecode intact) produces identical fingerprints,
 * which is what lets these be used for library/clone identification.
 *
 * <p>Two flavours are provided:
 * <ul>
 *   <li><b>Exact hash</b> ({@link #methodHash}/{@link #classHash}/{@link #dexHash}) — a short hex
 *       digest for exact matching and dedup. A class hash folds in its methods' hashes in a
 *       member-order-independent way.</li>
 *   <li><b>N-gram profile</b> ({@link #classNgramProfile}) — a bag of opcode n-grams supporting a
 *       {@link #jaccard weighted Jaccard similarity}, tolerant of small edits, for fuzzy matching of
 *       partially-modified code.</li>
 * </ul>
 *
 * <p>This model is pure and I/O-free.
 */
public class Fingerprint {

    private static final Charset UTF8 = Charset.forName("UTF-8");

    private Fingerprint() {}

    /**
     * The opcode names of a method body in program order; empty for abstract/native methods.
     */
    @Nonnull
    public static List<String> opcodeSequence(@Nonnull Method method) {
        List<String> opcodes = new ArrayList<>();
        MethodImplementation impl = method.getImplementation();
        if (impl != null) {
            for (Instruction instruction : impl.getInstructions()) {
                opcodes.add(instruction.getOpcode().name);
            }
        }
        return opcodes;
    }

    /**
     * A short (16 hex char) rename-invariant fingerprint of a method's opcode sequence.
     */
    @Nonnull
    public static String methodHash(@Nonnull Method method) {
        return shortHash(join(opcodeSequence(method)));
    }

    /**
     * A rename-invariant fingerprint of a class: the sorted multiset of its method hashes, hashed.
     * Independent of method declaration order, class name, and member names.
     */
    @Nonnull
    public static String classHash(@Nonnull ClassDef classDef) {
        List<String> methodHashes = new ArrayList<>();
        for (Method method : classDef.getMethods()) {
            methodHashes.add(methodHash(method));
        }
        java.util.Collections.sort(methodHashes);
        return shortHash(join(methodHashes));
    }

    /**
     * A rename-invariant fingerprint of a whole dex: the sorted multiset of its class hashes, hashed.
     */
    @Nonnull
    public static String dexHash(@Nonnull DexFile dexFile) {
        List<String> classHashes = new ArrayList<>();
        for (ClassDef classDef : dexFile.getClasses()) {
            classHashes.add(classHash(classDef));
        }
        java.util.Collections.sort(classHashes);
        return shortHash(join(classHashes));
    }

    // -- n-gram similarity ---------------------------------------------------

    /**
     * The opcode n-gram multiset of a single method body. Each n-gram is {@code n} consecutive
     * opcode names joined by {@code '|'}. A body shorter than {@code n} contributes a single padded
     * n-gram of its whole sequence so tiny methods still fingerprint.
     */
    @Nonnull
    public static Map<String, Integer> ngrams(@Nonnull List<String> opcodes, int n) {
        Map<String, Integer> bag = new TreeMap<>();
        if (n < 1) {
            n = 1;
        }
        if (opcodes.isEmpty()) {
            return bag;
        }
        if (opcodes.size() < n) {
            add(bag, join(opcodes));
            return bag;
        }
        for (int i = 0; i + n <= opcodes.size(); i++) {
            StringBuilder sb = new StringBuilder();
            for (int j = 0; j < n; j++) {
                if (j > 0) {
                    sb.append('|');
                }
                sb.append(opcodes.get(i + j));
            }
            add(bag, sb.toString());
        }
        return bag;
    }

    /**
     * The aggregate opcode n-gram profile of a class: the summed n-gram bags of all its methods.
     */
    @Nonnull
    public static Map<String, Integer> classNgramProfile(@Nonnull ClassDef classDef, int n) {
        Map<String, Integer> profile = new TreeMap<>();
        for (Method method : classDef.getMethods()) {
            for (Map.Entry<String, Integer> e : ngrams(opcodeSequence(method), n).entrySet()) {
                profile.merge(e.getKey(), e.getValue(), Integer::sum);
            }
        }
        return profile;
    }

    /**
     * Weighted Jaccard similarity of two n-gram multisets: {@code sum(min)/sum(max)}, in [0,1].
     * Two empty bags are defined as identical (1.0).
     */
    public static double jaccard(@Nonnull Map<String, Integer> a, @Nonnull Map<String, Integer> b) {
        if (a.isEmpty() && b.isEmpty()) {
            return 1.0;
        }
        long intersection = 0;
        long union = 0;
        java.util.Set<String> keys = new java.util.TreeSet<>();
        keys.addAll(a.keySet());
        keys.addAll(b.keySet());
        for (String key : keys) {
            int ca = a.getOrDefault(key, 0);
            int cb = b.getOrDefault(key, 0);
            intersection += Math.min(ca, cb);
            union += Math.max(ca, cb);
        }
        if (union == 0) {
            return 1.0;
        }
        return (double) intersection / (double) union;
    }

    // -- helpers -------------------------------------------------------------

    private static void add(@Nonnull Map<String, Integer> bag, @Nonnull String key) {
        bag.merge(key, 1, Integer::sum);
    }

    @Nonnull
    private static String join(@Nonnull List<String> parts) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(parts.get(i));
        }
        return sb.toString();
    }

    /**
     * SHA-256 of the input, truncated to the first 16 hex characters (64 bits) — plenty to avoid
     * accidental collisions across a single app while staying compact.
     */
    @Nonnull
    public static String shortHash(@Nonnull String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(input.getBytes(UTF8));
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 8; i++) {
                sb.append(Character.forDigit((bytes[i] >> 4) & 0xf, 16));
                sb.append(Character.forDigit(bytes[i] & 0xf, 16));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException ex) {
            // SHA-256 is guaranteed present on every JVM.
            throw new RuntimeException(ex);
        }
    }
}
