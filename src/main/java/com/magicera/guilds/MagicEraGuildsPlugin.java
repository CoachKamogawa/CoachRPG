package com.magicera.guilds;

import com.magicera.guilds.commands.DuelCommand;
import com.magicera.guilds.commands.GuildCommand;
import com.magicera.guilds.commands.PartyCommand;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

public final class MagicEraGuildsPlugin extends JavaPlugin {

    @Override
    public void onEnable() {
        try {
            saveDefaultConfig();

            // Register commands
            if (getCommand("guild") != null) getCommand("guild").setExecutor(new GuildCommand(this));
            if (getCommand("party") != null) getCommand("party").setExecutor(new PartyCommand(this));
            if (getCommand("duel") != null) getCommand("duel").setExecutor(new DuelCommand(this));

            getLogger().info("====================================");
            getLogger().info("MagicEraGuilds loaded successfully");
            getLogger().info("Author: Coach Kamogawa");
            getLogger().info("Version: " + getDescription().getVersion());
            getLogger().info("====================================");

        } catch (Exception e) {
            getLogger().severe("MagicEraGuilds FAILED to load: " + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()));
            Bukkit.getPluginManager().disablePlugin(this);
        }
    }

    @Override
    public void onDisable() {
        getLogger().info("MagicEraGuilds disabled.");
    }
}
