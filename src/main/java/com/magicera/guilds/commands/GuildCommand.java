package com.magicera.guilds.commands;

import com.magicera.guilds.MagicEraGuildsPlugin;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

public final class GuildCommand implements CommandExecutor {

    private final MagicEraGuildsPlugin plugin;

    public GuildCommand(MagicEraGuildsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        sender.sendMessage("§7[§bMagicEra§7] §f/guild is installed (foundation build).");
        sender.sendMessage("§7Next steps will add: create, menu, claims, chat.");
        return true;
    }
}
