package com.magicera.guilds.listeners;

import com.magicera.guilds.MagicEraGuildsPlugin;
import com.magicera.guilds.data.PlayerData;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;

public final class PvpKdaListener implements Listener {

    private final MagicEraGuildsPlugin plugin;

    public PvpKdaListener(MagicEraGuildsPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        Player killer = victim.getKiller();
        if (killer == null || killer.getUniqueId().equals(victim.getUniqueId())) return;

        PlayerData killerData = plugin.storage().getOrCreatePlayer(killer.getUniqueId());
        PlayerData victimData = plugin.storage().getOrCreatePlayer(victim.getUniqueId());
        killerData.incrementPvpKills();
        victimData.incrementPvpDeaths();
        plugin.storage().markDirty();
    }
}
