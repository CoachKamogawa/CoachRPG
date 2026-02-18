package com.magicera.guilds.commands;

import com.magicera.guilds.MagicEraGuildsPlugin;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class AlignCommand implements CommandExecutor {

    private final MagicEraGuildsPlugin plugin;

    public AlignCommand(MagicEraGuildsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cPlayers only.");
            return true;
        }

        player.openInventory(com.magicera.guilds.gui.Menus.alignmentMenu(plugin, player.getUniqueId()));
        return true;
    }
}

