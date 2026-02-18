package com.magicera.guilds.listeners;

import com.magicera.guilds.MagicEraGuildsPlugin;
import com.magicera.guilds.data.PlayerData;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public final class PlayerSeenListener implements Listener {

    private final MagicEraGuildsPlugin plugin;

    public PlayerSeenListener(MagicEraGuildsPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        PlayerData pd = plugin.storage().getOrCreatePlayer(e.getPlayer().getUniqueId());
        // mark as seen now so “last online” isn't ancient
        pd.setLastSeenEpochMs(System.currentTimeMillis());
        plugin.storage().save();
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        PlayerData pd = plugin.storage().getOrCreatePlayer(e.getPlayer().getUniqueId());
        pd.setLastSeenEpochMs(System.currentTimeMillis());
        plugin.storage().save();
    }
}
