package com.magicera.guilds.alignment;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

public final class JoinListener implements Listener {

    private final AlignmentWatcher watcher;

    public JoinListener(AlignmentWatcher watcher) {
        this.watcher = watcher;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        watcher.checkAndWarn(e.getPlayer(), true);
    }
}
