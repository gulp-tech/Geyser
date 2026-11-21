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

import org.cloudburstmc.protocol.bedrock.data.SoundEvent;
import org.cloudburstmc.protocol.bedrock.packet.AddEntityPacket;
import org.cloudburstmc.protocol.bedrock.packet.LevelSoundEventPacket;
import org.cloudburstmc.protocol.bedrock.packet.SpawnParticleEffectPacket;
import org.geysermc.geyser.util.JsonUtils;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests the legacy-content guard without a running server: the manifest parsing, the decision
 * policy and the packet inspection.
 */
class ContentGuardTest {

    /**
     * Manifest with three sections: a protocol number, a version name and a Geyser-style short
     * version name (the leading {@code 1.} is optional), plus a section of an unknown kind.
     */
    private static final String MANIFEST = """
            {
              "schema": 1,
              "contents": {
                "entity": { "minecraft:sulfur_cube": 2193 },
                "sound": { "eject_block": "1.26.60" },
                "particle": { "minecraft:sulfur_cube_particle": "26.60" },
                "unknown-kind": { "whatever": 1 }
              }
            }
            """;

    private static LegacyContentManifest manifest() {
        return LegacyContentManifest.parse(JsonUtils.parseJson(MANIFEST));
    }

    @Test
    void emptyManifestAllowsEverything() {
        LegacyContentManifest empty = LegacyContentManifest.empty();
        assertTrue(empty.isEmpty());
        assertTrue(ContentPolicy.isSupported(empty, ContentKind.ENTITY, "minecraft:sulfur_cube", 1000));
    }

    @Test
    void manifestParsesKnownKindsAndSkipsUnknownOnes() {
        LegacyContentManifest manifest = manifest();
        assertEquals(3, manifest.size());
        assertEquals(Integer.valueOf(2193), manifest.introducedIn(ContentKind.ENTITY, "minecraft:sulfur_cube"));
        // Sound keys are matched case-insensitively, because SoundEvent constants are upper case
        assertEquals(Integer.valueOf(2225), manifest.introducedIn(ContentKind.SOUND, "EJECT_BLOCK"));
        assertNull(manifest.introducedIn(ContentKind.ENTITY, "minecraft:pig"));
    }

    @Test
    void policyComparesClientProtocolWithIntroduction() {
        LegacyContentManifest manifest = manifest();

        assertFalse(ContentPolicy.isSupported(manifest, ContentKind.ENTITY, "minecraft:sulfur_cube", 2168));
        assertFalse(ContentPolicy.isSupported(manifest, ContentKind.ENTITY, "minecraft:sulfur_cube", 2192));
        assertTrue(ContentPolicy.isSupported(manifest, ContentKind.ENTITY, "minecraft:sulfur_cube", 2193));
        assertTrue(ContentPolicy.isSupported(manifest, ContentKind.ENTITY, "minecraft:sulfur_cube", 2300));

        // Content the manifest does not know about is never dropped
        assertTrue(ContentPolicy.isSupported(manifest, ContentKind.ENTITY, "minecraft:pig", 1000));
        // Unknown kind/key combinations and empty keys are safe
        assertTrue(ContentPolicy.isSupported(manifest, ContentKind.PARTICLE, "minecraft:pig", 1000));
        assertTrue(ContentPolicy.isSupported(manifest, ContentKind.SOUND, "", 1000));
        assertTrue(ContentPolicy.isSupported(manifest, ContentKind.SOUND, null, 1000));
    }

    @Test
    void versionNamesResolveThroughTheProtocolTable() {
        // The table is generated from the protocol library, so these must be stable
        assertEquals(766, BedrockVersions.get().protocolOf("1.21.50"));
        assertEquals(766, BedrockVersions.get().protocolOf("1.21.50".toLowerCase()));
        // "26.60" and "1.26.60" are the same version, spelled differently by Geyser and the library
        assertEquals(2225, BedrockVersions.get().protocolOf("26.60"));
        assertEquals(2225, BedrockVersions.get().protocolOf("1.26.60"));
        assertEquals(-1, BedrockVersions.get().protocolOf("9.99"));
        assertEquals(-1, BedrockVersions.get().protocolOf(null));
        assertEquals("1.21.50", BedrockVersions.get().versionOf(766));
        assertNull(BedrockVersions.get().versionOf(1));
    }

    @Test
    void manifestAcceptsVersionNames() {
        LegacyContentManifest manifest = manifest();
        // "1.26.60" resolves to protocol 2225, the same value the numeric entries use
        assertEquals(Integer.valueOf(2225), manifest.introducedIn(ContentKind.SOUND, "eject_block"));
        // "26.60" is the Geyser spelling of the same version
        assertEquals(Integer.valueOf(2225), manifest.introducedIn(ContentKind.PARTICLE, "minecraft:sulfur_cube_particle"));
    }

    @Test
    void unresolvableVersionSkipsOnlyThatEntry() {
        LegacyContentManifest manifest = LegacyContentManifest.parse(JsonUtils.parseJson("""
                { "contents": { "entity": { "minecraft:test": "9.99", "minecraft:other": 2193 } } }
                """));
        assertNull(manifest.introducedIn(ContentKind.ENTITY, "minecraft:test"));
        assertEquals(Integer.valueOf(2193), manifest.introducedIn(ContentKind.ENTITY, "minecraft:other"));
    }

    @Test
    void malformedManifestDoesNotThrow() {
        LegacyContentManifest manifest = LegacyContentManifest.parse(JsonUtils.parseJson(
                "{ \"contents\": { \"entity\": { \"minecraft:test\": \"not-a-number\" }, \"sound\": 42 } }"));
        assertTrue(manifest.isEmpty());
    }

    @Test
    void packetsAreMappedToContentKeys() {
        AddEntityPacket entity = new AddEntityPacket();
        entity.setIdentifier("minecraft:sulfur_cube");
        ContentGuard.ContentKey entityKey = ContentGuard.keyOf(entity);
        assertNotNull(entityKey);
        assertEquals(ContentKind.ENTITY, entityKey.kind());
        assertEquals("minecraft:sulfur_cube", entityKey.key());

        LevelSoundEventPacket sound = new LevelSoundEventPacket();
        sound.setSound(SoundEvent.EJECT_BLOCK);
        ContentGuard.ContentKey soundKey = ContentGuard.keyOf(sound);
        assertNotNull(soundKey);
        assertEquals(ContentKind.SOUND, soundKey.kind());
        assertEquals("EJECT_BLOCK", soundKey.key());

        SpawnParticleEffectPacket particle = new SpawnParticleEffectPacket();
        particle.setIdentifier("minecraft:sulfur_cube_particle");
        ContentGuard.ContentKey particleKey = ContentGuard.keyOf(particle);
        assertNotNull(particleKey);
        assertEquals(ContentKind.PARTICLE, particleKey.kind());

        // Other packets are not guarded at all
        assertNull(ContentGuard.keyOf(new org.cloudburstmc.protocol.bedrock.packet.SetTimePacket()));
        // Empty identifiers are ignored instead of dropping the packet
        assertNull(ContentGuard.keyOf(new AddEntityPacket()));
    }

    @Test
    void guardUsesThePolicyForRealPackets() {
        LegacyContentManifest manifest = manifest();

        AddEntityPacket entity = new AddEntityPacket();
        entity.setIdentifier("minecraft:sulfur_cube");

        assertTrue(ContentGuard.isAllowed(manifest, entity, 2193));
        assertFalse(ContentGuard.isAllowed(manifest, entity, 2168));

        LevelSoundEventPacket sound = new LevelSoundEventPacket();
        sound.setSound(SoundEvent.EJECT_BLOCK);

        assertFalse(ContentGuard.isAllowed(manifest, sound, 2193));
        assertTrue(ContentGuard.isAllowed(manifest, sound, 2225));
    }
}
