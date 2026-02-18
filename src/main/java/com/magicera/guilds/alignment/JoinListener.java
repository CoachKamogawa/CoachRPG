package com.magicera.guilds.alignment;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

public final class JoinListener implements Listener {

    private final AlignmentWatcher watcher;

    // Delay after join (in ticks)
    // 20 ticks = 1 second
    private static final long JOIN_DELAY_TICKS = 40L; // 2 seconds

    public JoinListener(AlignmentWatcher watcher) {
        this.watcher = watcher;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player player = e.getPlayer();

        // Delay so this runs after other plugins' join messages
        Bukkit.getScheduler().runTaskLater(
                watcher.getPlugin(),
                () -> {
                    if (player.isOnline()) {
                        watcher.checkAndWarn(player, true);
                    }
                },
                JOIN_DELAY_TICKS
        );
    }
}
