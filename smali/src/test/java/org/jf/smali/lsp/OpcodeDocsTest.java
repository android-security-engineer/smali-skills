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

import org.junit.Assert;
import org.junit.Test;

public class OpcodeDocsTest {

    @Test
    public void curatedOpcode_returnsDoc() {
        String doc = OpcodeDocs.lookup("invoke-virtual");
        Assert.assertNotNull(doc);
        Assert.assertTrue(doc.contains("virtual"));
    }

    @Test
    public void directive_returnsDoc() {
        String doc = OpcodeDocs.lookup(".method");
        Assert.assertNotNull(doc);
        Assert.assertTrue(doc.contains("directive"));
    }

    @Test
    public void opcodeFamilyMemberFallsBackToRoot() {
        // No dedicated `iget-object` entry, but `iget` exists -> family fallback.
        String doc = OpcodeDocs.lookup("iget-object");
        Assert.assertNotNull(doc);
    }

    @Test
    public void knownButUncuratedOpcode_getsGenericDoc() {
        // `neg-int` is a real opcode with no curated blurb -> generic fallback.
        String doc = OpcodeDocs.lookup("neg-int");
        Assert.assertNotNull(doc);
        Assert.assertTrue(doc.toLowerCase().contains("opcode"));
    }

    @Test
    public void unknownWord_returnsNull() {
        Assert.assertNull(OpcodeDocs.lookup("definitely-not-an-opcode"));
        Assert.assertNull(OpcodeDocs.lookup(".notadirective"));
        Assert.assertNull(OpcodeDocs.lookup(""));
        Assert.assertNull(OpcodeDocs.lookup(null));
    }
}
