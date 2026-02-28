package com.magicera.guilds.listeners;

import com.magicera.guilds.MagicEraGuildsPlugin;
import com.magicera.guilds.data.Guild;
import com.magicera.guilds.data.PlayerData;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;

public final class GuildWarPowerListener implements Listener {
    private final MagicEraGuildsPlugin plugin;

    public GuildWarPowerListener(MagicEraGuildsPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        Player killer = victim.getKiller();
        if (killer == null || killer.getUniqueId().equals(victim.getUniqueId())) return;

        PlayerData victimPd = plugin.storage().getOrCreatePlayer(victim.getUniqueId());
        PlayerData killerPd = plugin.storage().getOrCreatePlayer(killer.getUniqueId());
        if (victimPd.getGuildId() == null || killerPd.getGuildId() == null) return;

        Guild victimGuild = plugin.storage().getGuild(victimPd.getGuildId());
        Guild killerGuild = plugin.storage().getGuild(killerPd.getGuildId());
        if (victimGuild == null || killerGuild == null || victimGuild.getId().equals(killerGuild.getId())) return;
        if (!victimGuild.getEnemies().contains(killerGuild.getId()) || !victimGuild.isInWar() || !killerGuild.isInWar()) return;

        double loss = plugin.territoryConfig().getDouble("warKillPowerLoss", 1.5);
        victimPd.setPower(victimPd.getPower() - loss);
        plugin.guildPower().handlePowerThresholds(victimGuild);
        plugin.storage().save();
    }
}
