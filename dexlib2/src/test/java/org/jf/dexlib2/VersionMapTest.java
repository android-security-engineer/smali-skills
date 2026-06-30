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

package org.jf.dexlib2;

import org.junit.Assert;
import org.junit.Test;

/**
 * Tests for {@link VersionMap}, including the extended mapping up to dex version 040 / API 35+.
 */
public class VersionMapTest {

    // ---- mapDexVersionToApi ----

    @Test
    public void testMapDexVersionToApi_LegacyVersions() {
        Assert.assertEquals(23, VersionMap.mapDexVersionToApi(35));
        Assert.assertEquals(25, VersionMap.mapDexVersionToApi(37));
        Assert.assertEquals(27, VersionMap.mapDexVersionToApi(38));
        Assert.assertEquals(28, VersionMap.mapDexVersionToApi(39));
    }

    @Test
    public void testMapDexVersionToApi_Dex040() {
        // dex version 040 was introduced in Android 11 (API 30)
        Assert.assertEquals(30, VersionMap.mapDexVersionToApi(40));
    }

    @Test
    public void testMapDexVersionToApi_UnsupportedVersions() {
        Assert.assertEquals(VersionMap.NO_VERSION, VersionMap.mapDexVersionToApi(34));
        Assert.assertEquals(VersionMap.NO_VERSION, VersionMap.mapDexVersionToApi(36));
        Assert.assertEquals(VersionMap.NO_VERSION, VersionMap.mapDexVersionToApi(41));
        Assert.assertEquals(VersionMap.NO_VERSION, VersionMap.mapDexVersionToApi(0));
        Assert.assertEquals(VersionMap.NO_VERSION, VersionMap.mapDexVersionToApi(-1));
    }

    // ---- mapApiToDexVersion ----

    @Test
    public void testMapApiToDexVersion_LegacyApis() {
        Assert.assertEquals(35, VersionMap.mapApiToDexVersion(15));
        Assert.assertEquals(35, VersionMap.mapApiToDexVersion(23));
        Assert.assertEquals(37, VersionMap.mapApiToDexVersion(24));
        Assert.assertEquals(37, VersionMap.mapApiToDexVersion(25));
        Assert.assertEquals(38, VersionMap.mapApiToDexVersion(26));
        Assert.assertEquals(38, VersionMap.mapApiToDexVersion(27));
        Assert.assertEquals(39, VersionMap.mapApiToDexVersion(28));
        Assert.assertEquals(39, VersionMap.mapApiToDexVersion(29));
    }

    @Test
    public void testMapApiToDexVersion_Api30Plus() {
        // API 30+ uses dex version 040
        Assert.assertEquals(40, VersionMap.mapApiToDexVersion(30));
        Assert.assertEquals(40, VersionMap.mapApiToDexVersion(33));
        Assert.assertEquals(40, VersionMap.mapApiToDexVersion(35));
    }

    // ---- Round-trip consistency ----

    @Test
    public void testRoundTrip_DexVersionToApiToDexVersion() {
        // For every supported dex version, mapping to api and back should be the identity.
        int[] supportedDexVersions = {35, 37, 38, 39, 40};
        for (int dexVersion : supportedDexVersions) {
            int api = VersionMap.mapDexVersionToApi(dexVersion);
            Assert.assertNotEquals("dex version " + dexVersion + " should map to a valid api",
                    VersionMap.NO_VERSION, api);
            // Note: round-trip is not strictly identity because multiple dex versions can map
            // to a range of apis. We verify the forward mapping is consistent instead:
            // mapping the resulting api back should yield a dex version that maps to the same api.
            int dexBack = VersionMap.mapApiToDexVersion(api);
            int apiBack = VersionMap.mapDexVersionToApi(dexBack);
            Assert.assertEquals("round-trip api mismatch for dex version " + dexVersion,
                    api, apiBack);
        }
    }

    // ---- mapArtVersionToApi / mapApiToArtVersion ----

    @Test
    public void testMapArtVersionToApi_LegacyVersions() {
        Assert.assertEquals(19, VersionMap.mapArtVersionToApi(7));
        Assert.assertEquals(21, VersionMap.mapArtVersionToApi(39));
        Assert.assertEquals(22, VersionMap.mapArtVersionToApi(45));
        Assert.assertEquals(23, VersionMap.mapArtVersionToApi(64));
        Assert.assertEquals(24, VersionMap.mapArtVersionToApi(79));
        Assert.assertEquals(26, VersionMap.mapArtVersionToApi(124));
        Assert.assertEquals(27, VersionMap.mapArtVersionToApi(131));
        Assert.assertEquals(28, VersionMap.mapArtVersionToApi(138));
        Assert.assertEquals(29, VersionMap.mapArtVersionToApi(170));
    }

    @Test
    public void testMapArtVersionToApi_Api30Plus() {
        // ART version 188 corresponds to Android 11 (API 30)
        Assert.assertEquals(30, VersionMap.mapArtVersionToApi(188));
        Assert.assertEquals(31, VersionMap.mapArtVersionToApi(189));
        Assert.assertEquals(31, VersionMap.mapArtVersionToApi(200));
    }

    @Test
    public void testMapApiToArtVersion_LegacyApis() {
        Assert.assertEquals(7, VersionMap.mapApiToArtVersion(19));
        Assert.assertEquals(7, VersionMap.mapApiToArtVersion(20));
        Assert.assertEquals(39, VersionMap.mapApiToArtVersion(21));
        Assert.assertEquals(45, VersionMap.mapApiToArtVersion(22));
        Assert.assertEquals(64, VersionMap.mapApiToArtVersion(23));
        Assert.assertEquals(79, VersionMap.mapApiToArtVersion(24));
        Assert.assertEquals(79, VersionMap.mapApiToArtVersion(25));
        Assert.assertEquals(124, VersionMap.mapApiToArtVersion(26));
        Assert.assertEquals(131, VersionMap.mapApiToArtVersion(27));
        Assert.assertEquals(138, VersionMap.mapApiToArtVersion(28));
        Assert.assertEquals(170, VersionMap.mapApiToArtVersion(29));
    }

    @Test
    public void testMapApiToArtVersion_Api30Plus() {
        Assert.assertEquals(188, VersionMap.mapApiToArtVersion(30));
        // API 31+ returns a conservative 188 (latest stable ART opcode set)
        Assert.assertEquals(188, VersionMap.mapApiToArtVersion(31));
        Assert.assertEquals(188, VersionMap.mapApiToArtVersion(35));
    }

    @Test
    public void testMapApiToArtVersion_BelowMin() {
        Assert.assertEquals(VersionMap.NO_VERSION, VersionMap.mapApiToArtVersion(18));
        Assert.assertEquals(VersionMap.NO_VERSION, VersionMap.mapApiToArtVersion(0));
    }

    // ---- Opcodes integration ----

    @Test
    public void testOpcodesForApi_30_SupportsDex040() {
        // Opcodes.forApi(30) should succeed and produce a usable Opcodes instance
        Opcodes opcodes = Opcodes.forApi(30);
        Assert.assertEquals(30, opcodes.api);
    }

    @Test
    public void testOpcodesForDexVersion_040_Supported() {
        // dex version 040 should now be supported (previously threw RuntimeException)
        Opcodes opcodes = Opcodes.forDexVersion(40);
        Assert.assertEquals(30, opcodes.api);
    }
}
