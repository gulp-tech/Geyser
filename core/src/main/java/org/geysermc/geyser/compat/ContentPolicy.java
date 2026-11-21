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

/**
 * Pure decision logic for the legacy-content guard, kept free of sessions, packets and logging so
 * it can be unit tested without a running server.
 */
public final class ContentPolicy {

    private ContentPolicy() {
    }

    /**
     * Decides whether content may be sent to a client.
     *
     * @param manifest the manifest to consult
     * @param kind the kind of content
     * @param key the content key, e.g. {@code minecraft:sulfur_cube}
     * @param protocolVersion the client's Bedrock protocol version
     * @return {@code true} if the client is expected to know the content
     */
    public static boolean isSupported(@NonNull LegacyContentManifest manifest, @NonNull ContentKind kind,
                                      @Nullable String key, int protocolVersion) {
        if (key == null || key.isEmpty() || manifest.isEmpty()) {
            return true;
        }

        Integer introducedIn = manifest.introducedIn(kind, key);
        if (introducedIn == null) {
            // Not listed - assume the client knows it. Never drop content we have no data about.
            return true;
        }
        return introducedIn <= protocolVersion;
    }
}
