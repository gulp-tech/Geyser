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

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;

import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * A table of content that is too new for some Bedrock clients.
 *
 * <p>Every entry maps a content key to the Bedrock <b>protocol version</b> in which that content
 * was introduced. If a client reports a protocol version lower than the recorded one, the content
 * is unknown to it and must not be sent - otherwise the client disconnects or crashes, which is
 * exactly what players report as "can't join / kicked when a sulfur cube spawns".</p>
 *
 * <p>Anything not listed in the manifest is treated as known. That is deliberate: an empty or
 * incomplete manifest must never change behaviour, so the guard degrades to a no-op instead of
 * breaking content that actually works.</p>
 */
public final class LegacyContentManifest {

    private static final LegacyContentManifest EMPTY = new LegacyContentManifest(Map.of());

    private final Map<ContentKind, Map<String, Integer>> contents;

    private LegacyContentManifest(@NonNull Map<ContentKind, Map<String, Integer>> contents) {
        this.contents = contents;
    }

    public static @NonNull LegacyContentManifest empty() {
        return EMPTY;
    }

    public boolean isEmpty() {
        return contents.isEmpty();
    }

    /**
     * Gets the protocol version in which the given content was introduced.
     *
     * @param kind the kind of content
     * @param key the content key
     * @return the protocol version, or {@code null} if the content is not listed
     */
    public @Nullable Integer introducedIn(@NonNull ContentKind kind, @NonNull String key) {
        Map<String, Integer> entries = contents.get(kind);
        if (entries == null) {
            return null;
        }
        return entries.get(normalize(key));
    }

    /**
     * Parses a manifest from its JSON representation.
     *
     * <pre>{@code
     * {
     *   "schema": 1,
     *   "contents": {
     *     "entity": { "minecraft:sulfur_cube": "1.26.50" },
     *     "sound":  { "eject_block": "26.60" }
     *   }
     * }
     * }</pre>
     *
     * <p>Values may be either a Bedrock version name ({@code "1.21.50"}, {@code "26.60"}) or a raw
     * protocol number ({@code 766}). Version names are resolved through {@link BedrockVersions}.</p>
     *
     * Unknown kinds, unresolvable versions and malformed entries are skipped rather than failing
     * startup - a bad manifest must not take the server down.
     *
     * @param root the parsed JSON object
     * @return the parsed manifest, never {@code null}
     */
    public static @NonNull LegacyContentManifest parse(@NonNull JsonObject root) {
        JsonElement contentsElement = root.get("contents");
        if (contentsElement == null || !contentsElement.isJsonObject()) {
            return EMPTY;
        }

        Map<ContentKind, Map<String, Integer>> contents = new EnumMap<>(ContentKind.class);
        for (Map.Entry<String, JsonElement> section : contentsElement.getAsJsonObject().entrySet()) {
            ContentKind kind = ContentKind.fromKey(section.getKey());
            if (kind == null || !section.getValue().isJsonObject()) {
                continue;
            }

            Map<String, Integer> entries = new HashMap<>();
            for (Map.Entry<String, JsonElement> entry : section.getValue().getAsJsonObject().entrySet()) {
                Integer introducedIn = readProtocol(entry.getValue());
                if (introducedIn != null) {
                    entries.put(normalize(entry.getKey()), introducedIn);
                }
            }
            if (!entries.isEmpty()) {
                contents.put(kind, Collections.unmodifiableMap(entries));
            }
        }

        if (contents.isEmpty()) {
            return EMPTY;
        }
        return new LegacyContentManifest(Collections.unmodifiableMap(contents));
    }

    /**
     * Reads a single manifest value: either a protocol number or a Bedrock version name.
     *
     * @param value the JSON value
     * @return the protocol version, or {@code null} if the value cannot be resolved
     */
    private static @Nullable Integer readProtocol(@NonNull JsonElement value) {
        try {
            if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber()) {
                return value.getAsInt();
            }
            if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()) {
                int protocol = BedrockVersions.get().protocolOf(value.getAsString());
                return protocol <= 0 ? null : protocol;
            }
        } catch (RuntimeException ignored) {
            // Malformed value - skip the entry, keep the rest of the manifest usable
        }
        return null;
    }

    /**
     * Content keys are matched case-insensitively, since Bedrock sound names are upper case in the
     * protocol library ({@code SoundEvent.EJECT_BLOCK}) but lower case on the wire.
     */
    private static @NonNull String normalize(@NonNull String key) {
        return key.toLowerCase(Locale.ROOT);
    }

    /**
     * @return the number of recorded content entries, for logging
     */
    public int size() {
        int size = 0;
        for (Map<String, Integer> entries : contents.values()) {
            size += entries.size();
        }
        return size;
    }
}
