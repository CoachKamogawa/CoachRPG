package com.magicera.guilds.listeners;

import com.magicera.guilds.MagicEraGuildsPlugin;
import com.magicera.guilds.data.PlayerData;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class PlayerSeenListener implements Listener {

    private static final long OFFLINE_MESSAGE_DELAY_TICKS = 20L;

    private final MagicEraGuildsPlugin plugin;

    public PlayerSeenListener(MagicEraGuildsPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        PlayerData pd = plugin.storage().getOrCreatePlayer(e.getPlayer().getUniqueId());

        // Mark as seen now so “last online” isn't ancient
        pd.setLastSeenEpochMs(System.currentTimeMillis());

        List<String> delayedMessages = new ArrayList<>();

        // Offline tax notification
        double pending = pd.getPendingTaxNoticeAmount();
        if (pending > 0.0) {
            delayedMessages.add(
                    "§7[§aGuild§7] §eYou were taxed while offline: §f$" +
                            String.format(Locale.US, "%.2f", pending)
            );
            pd.setPendingTaxNoticeAmount(0.0);
        }

        // Pending guild messages (queued while offline)
        if (!pd.getPendingGuildMessages().isEmpty()) {
            for (String message : pd.getPendingGuildMessages()) {
                if (message == null || message.isBlank()) continue;
                delayedMessages.add(message);
            }
            pd.getPendingGuildMessages().clear();
        }

        if (!delayedMessages.isEmpty()) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (!e.getPlayer().isOnline()) return;
                for (String message : delayedMessages) {
                    e.getPlayer().sendMessage(message);
                }
            }, OFFLINE_MESSAGE_DELAY_TICKS);
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
