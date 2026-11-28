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

package org.geysermc.geyser.platform.spigot;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.Plugin;
import org.cloudburstmc.protocol.bedrock.data.command.CommandData;
import org.cloudburstmc.protocol.bedrock.data.command.CommandEnumConstraint;
import org.cloudburstmc.protocol.bedrock.data.command.CommandEnumData;
import org.cloudburstmc.protocol.bedrock.data.command.CommandOverloadData;
import org.cloudburstmc.protocol.bedrock.data.command.CommandParamData;
import org.cloudburstmc.protocol.bedrock.data.command.CommandPermission;
import org.cloudburstmc.protocol.bedrock.packet.AvailableCommandsPacket;
import org.geysermc.geyser.GeyserImpl;
import org.geysermc.geyser.session.GeyserSession;

import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class EssentialsBedrockTabBridge implements Listener {
    private static final String HOMES_ENUM = "ess_homes";
    private static final String WARPS_ENUM = "ess_warps";

    private final Plugin plugin;
    private final GeyserImpl geyser;

    public EssentialsBedrockTabBridge(Plugin plugin, GeyserImpl geyser) {
        this.plugin = plugin;
        this.geyser = geyser;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        Bukkit.getScheduler().runTaskLater(plugin, () -> sendInitialCommands(player), 40L);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        String msg = event.getMessage().toLowerCase(Locale.ROOT);
        if (msg.startsWith("/sethome") || msg.startsWith("/delhome")
                || msg.startsWith("/setwarp") || msg.startsWith("/delwarp")) {
            Player player = event.getPlayer();
            if (msg.startsWith("/setwarp") || msg.startsWith("/delwarp")) {
                Bukkit.getScheduler().runTaskLater(plugin, this::updateAllOnlineWarps, 10L);
            } else {
                Bukkit.getScheduler().runTaskLater(plugin, () -> updatePlayerHomes(player), 10L);
            }
        }
    }

    private void sendInitialCommands(Player player) {
        if (!player.isOnline()) {
            return;
        }
        GeyserSession session = geyser.connectionByUuid(player.getUniqueId());
        if (session == null) {
            return;
        }

        Plugin ess = Bukkit.getPluginManager().getPlugin("Essentials");
        if (ess == null || !ess.isEnabled()) {
            return;
        }

        List<String> homes = getPlayerHomes(ess, player.getUniqueId());
        List<String> warps = getWarps(ess);

        AvailableCommandsPacket packet = new AvailableCommandsPacket();
        packet.getCommands().add(createCommand("home", "Teleport to a home", HOMES_ENUM, homes));
        packet.getCommands().add(createCommand("delhome", "Delete a home", HOMES_ENUM, homes));
        packet.getCommands().add(createCommand("warp", "Teleport to a warp", WARPS_ENUM, warps));
        packet.getCommands().add(createCommand("delwarp", "Delete a warp", WARPS_ENUM, warps));

        session.sendUpstreamPacket(packet);
    }

    private void updatePlayerHomes(Player player) {
        sendInitialCommands(player);
    }

    private void updateAllOnlineWarps() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            sendInitialCommands(player);
        }
    }

    private CommandData createCommand(String name, String description, String enumName, List<String> values) {
        CommandParamData param = new CommandParamData();
        param.setName("name");
        param.setOptional(true);
        param.setEnumData(new CommandEnumData(enumName, toEnumMap(values), true));

        CommandOverloadData overload = new CommandOverloadData(false, new CommandParamData[]{param});
        return new CommandData(
                name,
                description,
                Collections.emptySet(),
                CommandPermission.ANY,
                null,
                Collections.emptyList(),
                new CommandOverloadData[]{overload}
        );
    }

    private Map<String, Set<CommandEnumConstraint>> toEnumMap(List<String> values) {
        Map<String, Set<CommandEnumConstraint>> map = new LinkedHashMap<>();
        for (String value : values) {
            map.put(value, Collections.emptySet());
        }
        return map;
    }

    @SuppressWarnings("unchecked")
    private List<String> getPlayerHomes(Plugin ess, UUID uuid) {
        try {
            Method getUser = ess.getClass().getMethod("getUser", UUID.class);
            Object user = getUser.invoke(ess, uuid);
            if (user == null) {
                return Collections.emptyList();
            }
            Method getHomes = user.getClass().getMethod("getHomes");
            Object result = getHomes.invoke(user);
            if (result instanceof List<?>) {
                return ((List<?>) result).stream().map(Object::toString).toList();
            }
        } catch (Throwable ignored) {
        }
        return Collections.emptyList();
    }

    @SuppressWarnings("unchecked")
    private List<String> getWarps(Plugin ess) {
        try {
            Method getWarps = ess.getClass().getMethod("getWarps");
            Object warpsObj = getWarps.invoke(ess);
            if (warpsObj == null) {
                return Collections.emptyList();
            }
            Method getList = warpsObj.getClass().getMethod("getList");
            Object result = getList.invoke(warpsObj);
            if (result instanceof Collection<?>) {
                return ((Collection<?>) result).stream().map(Object::toString).toList();
            }
        } catch (Throwable ignored) {
        }
        return Collections.emptyList();
    }
}
