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

/**
 * The kinds of content that can be missing on an older Bedrock client.
 *
 * <p>Used as the top-level grouping in {@code compat/legacy-content.json}. Each kind maps a
 * stable, human-readable key (an entity identifier, a sound name, a particle identifier) to the
 * Bedrock protocol version in which that content was introduced.</p>
 */
public enum ContentKind {
    /**
     * Entities, keyed by their Bedrock identifier, e.g. {@code minecraft:sulfur_cube}.
     */
    ENTITY("entity"),
    /**
     * Level sound events, keyed by their Bedrock sound name, e.g. {@code eject_block}.
     */
    SOUND("sound"),
    /**
     * Particles, keyed by their Bedrock identifier, e.g. {@code minecraft:basic_flame_particle}.
     */
    PARTICLE("particle");

    private final String key;

    ContentKind(String key) {
        this.key = key;
    }

    public String key() {
        return key;
    }

    /**
     * Resolves a kind from its JSON key.
     *
     * @param key the key as written in the manifest
     * @return the matching kind, or {@code null} if the key is unknown
     */
    public static ContentKind fromKey(String key) {
        for (ContentKind kind : values()) {
            if (kind.key.equals(key)) {
                return kind;
            }
        }
        return null;
    }
}
