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

import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.cloudburstmc.protocol.bedrock.packet.AddEntityPacket;
import org.cloudburstmc.protocol.bedrock.packet.BedrockPacket;
import org.cloudburstmc.protocol.bedrock.packet.LevelSoundEventPacket;
import org.cloudburstmc.protocol.bedrock.packet.SpawnParticleEffectPacket;
import org.geysermc.geyser.GeyserImpl;
import org.geysermc.geyser.util.JsonUtils;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * Guards outgoing Bedrock packets against content the connected client is too old to know.
 *
 * <p>Bedrock clients do not gracefully ignore unknown content: an entity type, sound or particle
 * that does not exist in the client's version makes it drop the connection (and from the server's
 * point of view the player simply "cannot join"). Geyser's normal strategy is to only accept
 * clients whose version it fully supports; this guard is the other half of the plan - keep old
 * clients connected by <b>not sending</b> what they cannot understand.</p>
 *
 * <p>Data lives in {@code compat/legacy-content.json} (populated by the content-manifest generator
 * in milestone M2). The guard is a no-op while that manifest is empty, so enabling it cannot change
 * behaviour on its own. It can be disabled at runtime with
 * {@code -Dgeyser.compat.disable-content-guard=true}.</p>
 */
public final class ContentGuard {

    private static final String RESOURCE = "compat/legacy-content.json";

    /**
     * Whether the guard should run at all. Kept as a static final so the hot path is a single
     * boolean read when the guard is disabled.
     */
    private static final boolean ENABLED = !Boolean.getBoolean("geyser.compat.disable-content-guard");

    private static volatile LegacyContentManifest manifest;
    private static volatile boolean manifestLoaded;

    private ContentGuard() {
    }

    /**
     * Decides whether a packet may be sent to the client.
     *
     * @param packet the outgoing Bedrock packet
     * @param protocolVersion the client's Bedrock protocol version
     * @return {@code true} if the packet is safe to send
     */
    public static boolean shouldSend(@NonNull BedrockPacket packet, int protocolVersion) {
        if (!ENABLED || protocolVersion <= 0) {
            // Guard disabled, or the client's version is not known yet (login stage).
            return true;
        }

        LegacyContentManifest manifest = manifest();
        if (manifest.isEmpty()) {
            return true;
        }

        ContentKey content = keyOf(packet);
        if (content == null) {
            return true;
        }
        if (ContentPolicy.isSupported(manifest, content.kind(), content.key(), protocolVersion)) {
            return true;
        }

        GeyserImpl geyser = GeyserImpl.getInstance();
        if (geyser != null && geyser.getLogger() != null) {
            geyser.getLogger().debug("Compat: dropping " + content.kind().key() + " '" + content.key()
                    + "' for protocol " + protocolVersion + " (introduced in a newer version)");
        }
        return false;
    }

    /**
     * Applies a given manifest to a packet. Split out from {@link #shouldSend} so tests can exercise
     * the decision without loading the resource and without a {@link GeyserImpl} instance.
     *
     * @param manifest the manifest to consult
     * @param packet the outgoing packet
     * @param protocolVersion the client's protocol version
     * @return {@code true} if the packet is safe to send
     */
    static boolean isAllowed(@NonNull LegacyContentManifest manifest, @NonNull BedrockPacket packet,
                             int protocolVersion) {
        ContentKey content = keyOf(packet);
        if (content == null) {
            return true;
        }
        return ContentPolicy.isSupported(manifest, content.kind(), content.key(), protocolVersion);
    }

    /**
     * Extracts the guarded content from a packet, if the packet carries any.
     *
     * @param packet the packet to inspect
     * @return the content key, or {@code null} if this packet is not guarded
     */
    static @Nullable ContentKey keyOf(@NonNull BedrockPacket packet) {
        try {
            if (packet instanceof AddEntityPacket addEntity) {
                return content(ContentKind.ENTITY, addEntity.getIdentifier());
            }
            if (packet instanceof LevelSoundEventPacket sound) {
                if (sound.getSound() != null) {
                    return content(ContentKind.SOUND, sound.getSound().name());
                }
                return content(ContentKind.SOUND, sound.getIdentifier());
            }
            if (packet instanceof SpawnParticleEffectPacket particle) {
                return content(ContentKind.PARTICLE, particle.getIdentifier());
            }
        } catch (RuntimeException exception) {
            // A malformed packet must never break the session - fall through and send it.
            return null;
        }
        return null;
    }

    private static @Nullable ContentKey content(@NonNull ContentKind kind, @Nullable String key) {
        if (key == null || key.isEmpty()) {
            return null;
        }
        return new ContentKey(kind, key);
    }

    /**
     * Loads the manifest once, on first use. Failures are logged and turn the guard into a no-op.
     */
    private static @NonNull LegacyContentManifest manifest() {
        LegacyContentManifest current = manifest;
        if (manifestLoaded) {
            return current;
        }
        synchronized (ContentGuard.class) {
            if (!manifestLoaded) {
                manifest = load();
                manifestLoaded = true;
            }
            return manifest;
        }
    }

    private static @NonNull LegacyContentManifest load() {
        try (InputStream stream = ContentGuard.class.getClassLoader().getResourceAsStream(RESOURCE)) {
            if (stream == null) {
                return LegacyContentManifest.empty();
            }
            String json = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            return LegacyContentManifest.parse(JsonUtils.parseJson(json));
        } catch (Throwable throwable) {
            GeyserImpl geyser = GeyserImpl.getInstance();
            if (geyser != null && geyser.getLogger() != null) {
                geyser.getLogger().warning("Compat: could not read " + RESOURCE + ", content guard disabled: " + throwable);
            }
            return LegacyContentManifest.empty();
        }
    }

    /**
     * A piece of guarded content: its kind and its stable key.
     */
    record ContentKey(@NonNull ContentKind kind, @NonNull String key) {
    }
}
