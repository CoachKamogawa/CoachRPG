package com.magicera.guilds.listeners;

import com.magicera.guilds.MagicEraGuildsPlugin;
import com.magicera.guilds.data.PlayerData;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Locale;

public final class PlayerSeenListener implements Listener {

    private final MagicEraGuildsPlugin plugin;

    public PlayerSeenListener(MagicEraGuildsPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        PlayerData pd = plugin.storage().getOrCreatePlayer(e.getPlayer().getUniqueId());

        // Mark as seen now so “last online” isn't ancient
        pd.setLastSeenEpochMs(System.currentTimeMillis());

        // Offline tax notification
        double pending = pd.getPendingTaxNoticeAmount();
        if (pending > 0.0) {
            e.getPlayer().sendMessage(
                    "§7[§aGuild§7] §eYou were taxed while offline: §f$" +
                    String.format(Locale.US, "%.2f", pending)
            );
            pd.setPendingTaxNoticeAmount(0.0);
        }

        plugin.storage().save();
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        PlayerData pd = plugin.storage().getOrCreatePlayer(e.getPlayer().getUniqueId());
        pd.setLastSeenEpochMs(System.currentTimeMillis());
        plugin.storage().save();
    }
}
