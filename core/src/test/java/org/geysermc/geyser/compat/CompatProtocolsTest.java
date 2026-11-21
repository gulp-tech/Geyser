/*
 * Copyright (c) 2019-2026 GeyserMC. http://geysermc.org
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 *
 * @author GeyserMC
 * @link https://github.com/GeyserMC/Geyser
 */

package org.geysermc.geyser.compat;

import org.cloudburstmc.protocol.bedrock.codec.BedrockCodec;
import org.geysermc.geyser.network.bedrock.GameProtocol;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Verifies that the first legacy family (1.26.30) is wired up: the codec is registered and the
 * version table agrees with it.
 */
class CompatProtocolsTest {

    private static final int PROTOCOL_1_26_30 = 1001;

    @Test
    void legacyFamiliesAreEnabledByDefault() {
        assertTrue(CompatProtocols.legacyProtocolsEnabled());
        assertTrue(CompatProtocols.familyEnabled("26_30"));
        // Upstream families must never be affected by the switch
        assertTrue(CompatProtocols.familyEnabled("26_60"));
    }

    @Test
    void versionTableAgreesWithTheCodec() {
        assertEquals(PROTOCOL_1_26_30, BedrockVersions.get().protocolOf("1.26.30"));
        assertEquals(PROTOCOL_1_26_30, BedrockVersions.get().protocolOf("26.30"));
    }

    @Test
    void legacyCodecIsRegistered() {
        assumeTrue(CompatProtocols.legacyProtocolsEnabled(), "legacy protocols disabled by property");

        BedrockCodec codec = GameProtocol.getBedrockCodec(PROTOCOL_1_26_30);
        assertNotNull(codec, "1.26.30 codec must be registered when legacy protocols are enabled");
        assertEquals(PROTOCOL_1_26_30, codec.getProtocolVersion());

        // The newest version must still be the default codec
        assertTrue(GameProtocol.DEFAULT_BEDROCK_PROTOCOL > PROTOCOL_1_26_30);
    }
}
