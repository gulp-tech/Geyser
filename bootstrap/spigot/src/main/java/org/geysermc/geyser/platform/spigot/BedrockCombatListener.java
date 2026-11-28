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

import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerItemDamageEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerVelocityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

public final class BedrockCombatListener implements Listener {
    private static final double BEDROCK_ATTACK_SPEED = 1024.0;
    private static final double ARMOR_DAMAGE_DIVISOR = 1.2;
    private static final double MACE_CLICK_DAMAGE_MULTIPLIER = 0.5;
    private static final float MACE_SMASH_MIN_FALL_DISTANCE = 1.5f;
    private static final double MAX_KNOCKBACK_Y = 0.36;
    private static final long COMBAT_VELOCITY_WINDOW_MS = 100L;

    private final Map<UUID, Long> recentCombatHits = new ConcurrentHashMap<>();

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        applyAttackSpeed(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        applyAttackSpeed(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerChangedWorld(PlayerChangedWorldEvent event) {
        applyAttackSpeed(event.getPlayer());
    }

    private void applyAttackSpeed(Player player) {
        AttributeInstance attackSpeed = player.getAttribute(Attribute.ATTACK_SPEED);
        if (attackSpeed != null && attackSpeed.getBaseValue() < BEDROCK_ATTACK_SPEED) {
            attackSpeed.setBaseValue(BEDROCK_ATTACK_SPEED);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player attacker) {
            ItemStack weapon = attacker.getInventory().getItemInMainHand();
            if (weapon.getType() == Material.MACE && attacker.getFallDistance() < MACE_SMASH_MIN_FALL_DISTANCE) {
                event.setDamage(event.getDamage() * MACE_CLICK_DAMAGE_MULTIPLIER);
            }
        }

        if (event.getEntity() instanceof Player victim) {
            recentCombatHits.put(victim.getUniqueId(), System.currentTimeMillis());
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerVelocity(PlayerVelocityEvent event) {
        Player player = event.getPlayer();
        Long hitTime = recentCombatHits.get(player.getUniqueId());
        if (hitTime == null || (System.currentTimeMillis() - hitTime) > COMBAT_VELOCITY_WINDOW_MS) {
            return;
        }

        Vector velocity = event.getVelocity();
        if (velocity.getY() > MAX_KNOCKBACK_Y) {
            velocity.setY(MAX_KNOCKBACK_Y);
            event.setVelocity(velocity);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerItemDamage(PlayerItemDamageEvent event) {
        ItemStack item = event.getItem();
        if (!isArmor(item)) {
            return;
        }

        int originalDamage = event.getDamage();
        if (originalDamage <= 0) {
            return;
        }

        double scaled = originalDamage / ARMOR_DAMAGE_DIVISOR;
        int baseDamage = (int) Math.floor(scaled);
        double fractional = scaled - baseDamage;
        int finalDamage = baseDamage + (ThreadLocalRandom.current().nextDouble() < fractional ? 1 : 0);

        if (finalDamage <= 0) {
            event.setCancelled(true);
        } else {
            event.setDamage(finalDamage);
        }
    }

    private boolean isArmor(ItemStack item) {
        if (item == null) {
            return false;
        }
        String name = item.getType().name();
        return name.endsWith("_HELMET")
                || name.endsWith("_CHESTPLATE")
                || name.endsWith("_LEGGINGS")
                || name.endsWith("_BOOTS");
    }
}
