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

import java.util.Set;

/**
 * Switches for the legacy-version support work.
 *
 * <p>Every version family outside the upstream window is registered through this class, so a
 * server owner can fall back to plain upstream behaviour without swapping jars:
 * {@code -Dgeyser.compat.disable-legacy-protocols=true}.</p>
 *
 * <p>The class is deliberately tiny and static: it is read from the static initialisers of the
 * protocol registry and the palette populators, which run before any configuration is loaded.</p>
 */
public final class CompatProtocols {

    /**
     * Whether extra protocol families are registered at all.
     */
    private static final boolean LEGACY_PROTOCOLS = !Boolean.getBoolean("geyser.compat.disable-legacy-protocols");

    /**
     * Version families added by this fork, in the {@code 26_30} naming used by the palette
     * resources. Upstream families are never listed here, so the switch cannot accidentally
     * disable them.
     */
    private static final Set<String> LEGACY_FAMILIES = Set.of("26_30");

    private CompatProtocols() {
    }

    /**
     * @return whether legacy protocol families should be registered
     */
    public static boolean legacyProtocolsEnabled() {
        return LEGACY_PROTOCOLS;
    }

    /**
     * Whether the given version family should be loaded.
     *
     * @param paletteSuffix the resource suffix used by the palette files, e.g. {@code 26_30}
     * @return whether the family is enabled
     */
    public static boolean familyEnabled(String paletteSuffix) {
        return !LEGACY_FAMILIES.contains(paletteSuffix) || LEGACY_PROTOCOLS;
    }
}
