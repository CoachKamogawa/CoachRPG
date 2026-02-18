package com.magicera.guilds;

import com.magicera.guilds.commands.DuelCommand;
import com.magicera.guilds.commands.GuildCommand;
import com.magicera.guilds.commands.PartyCommand;
import com.magicera.guilds.storage.Storage;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

public final class MagicEraGuildsPlugin extends JavaPlugin {

    private Storage storage;

    public Storage storage() {
        return storage;
    }

    @Override
    public void onEnable() {
        try {
            saveDefaultConfig();

            storage = new Storage(this);
            storage.load();

            if (getCommand("guild") != null) getCommand("guild").setExecutor(new GuildCommand(this));
            if (getCommand("party") != null) getCommand("party").setExecutor(new PartyCommand(this));
            if (getCommand("duel") != null) getCommand("duel").setExecutor(new DuelCommand(this));

            int intervalSeconds = Math.max(30, getConfig().getInt("data.save-interval-seconds", 120));
            Bukkit.getScheduler().runTaskTimerAsynchronously(this, () -> {
                try { storage.save(); } catch (Exception ignored) {}
            }, 20L * intervalSeconds, 20L * intervalSeconds);

            getLogger().info("====================================");
            getLogger().info("CoachRPG loaded successfully");
            getLogger().info("Author: Coach Kamogawa");
            getLogger().info("Guilds loaded: " + storage.allGuilds().size());
            getLogger().info("====================================");

        } catch (Exception e) {
            getLogger().severe("MagicEraGuilds FAILED to load: " + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()));
            Bukkit.getPluginManager().disablePlugin(this);
        }
    }

    @Override
    public void onDisable() {
        if (storage != null) {
            try { storage.save(); } catch (Exception ignored) {}
        }
        getLogger().info("MagicEraGuilds disabled.");
    }
}
