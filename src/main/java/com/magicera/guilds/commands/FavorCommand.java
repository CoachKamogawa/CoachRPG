package com.magicera.guilds.commands;

import com.magicera.guilds.MagicEraGuildsPlugin;
import com.magicera.guilds.data.PlayerData;
import com.magicera.guilds.gui.Menus;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class FavorCommand implements CommandExecutor {

    private final MagicEraGuildsPlugin plugin;

    public FavorCommand(MagicEraGuildsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

        // /favor -> open menu
        if (args.length == 0) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage("§7[§6Favor§7] §cPlayers only.");
                return true;
            }
            player.openInventory(Menus.favorMenu(plugin, player.getUniqueId()));
            return true;
        }

        // Admin tools (optional but useful): /favor set <player> <score>
        // Also supports /favor add <player> <amount>
        if (!sender.hasPermission("magicera.admin")) {
            sender.sendMessage("§7[§6Favor§7] §cNo permission.");
            return true;
        }

        String sub = args[0].toLowerCase();

        if (sub.equals("set")) {
            if (args.length < 3) {
                sender.sendMessage("§7[§6Favor§7] §cUsage: /favor set <player> <score>");
                sender.sendMessage("§7[§6Favor§7] §7Range: -100 to 100");
                return true;
            }

            Player target = Bukkit.getPlayerExact(args[1]);
            if (target == null) {
                sender.sendMessage("§7[§6Favor§7] §cPlayer must be online.");
                return true;
            }

            int score;
            try {
                score = Integer.parseInt(args[2]);
            } catch (NumberFormatException e) {
                sender.sendMessage("§7[§6Favor§7] §cScore must be a number (-100..100).");
                return true;
            }

            score = clamp(score, -100, 100);

            PlayerData pd = plugin.storage().getOrCreatePlayer(target.getUniqueId());
            pd.setAlignmentScore(score);
            plugin.storage().save();

            sender.sendMessage("§7[§6Favor§7] §aSet §f" + plugin.names().displayName(target) + "§a Favor to §f" + score + "§a.");
            target.sendMessage("§7[§6Favor§7] §fYour Favor is now §f" + score + "§f.");
            if (plugin.alignmentWatcher() != null) plugin.alignmentWatcher().checkAndWarn(target, false);
            return true;
        }

        if (sub.equals("add")) {
            if (args.length < 3) {
                sender.sendMessage("§7[§6Favor§7] §cUsage: /favor add <player> <amount>");
                sender.sendMessage("§7[§6Favor§7] §7Example: /favor add Kaosuu -5");
                return true;
            }

            Player target = Bukkit.getPlayerExact(args[1]);
            if (target == null) {
                sender.sendMessage("§7[§6Favor§7] §cPlayer must be online.");
                return true;
            }

            int add;
            try {
                add = Integer.parseInt(args[2]);
            } catch (NumberFormatException e) {
                sender.sendMessage("§7[§6Favor§7] §cAmount must be a number.");
                return true;
            }

            PlayerData pd = plugin.storage().getOrCreatePlayer(target.getUniqueId());
            int newScore = clamp(pd.getAlignmentScore() + add, -100, 100);
            pd.setAlignmentScore(newScore);
            plugin.storage().save();

            sender.sendMessage("§7[§6Favor§7] §aAdded §f" + add + "§a Favor to §f" + plugin.names().displayName(target) + "§a. New: §f" + newScore);
            target.sendMessage("§7[§6Favor§7] §fYour Favor changed by §f" + add + "§f. New: §f" + newScore);
            if (plugin.alignmentWatcher() != null) plugin.alignmentWatcher().checkAndWarn(target, false);
            return true;
        }

        sender.sendMessage("§7[§6Favor§7] §cUnknown subcommand.");
        sender.sendMessage("§7[§6Favor§7] §7/favor");
        sender.sendMessage("§7[§6Favor§7] §7/favor set <player> <score> §8(admin)");
        sender.sendMessage("§7[§6Favor§7] §7/favor add <player> <amount> §8(admin)");
        return true;
    }

    private int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }
}
