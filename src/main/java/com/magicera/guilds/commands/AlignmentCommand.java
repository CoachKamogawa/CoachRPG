package com.magicera.guilds.commands;

import com.magicera.guilds.MagicEraGuildsPlugin;
import com.magicera.guilds.data.GuildAlignment;
import com.magicera.guilds.data.PlayerData;
import com.magicera.guilds.util.AlignmentUtil;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class AlignmentCommand implements CommandExecutor {

    private final MagicEraGuildsPlugin plugin;

    public AlignmentCommand(MagicEraGuildsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

        if (!sender.hasPermission("magicera.admin")) {
            sender.sendMessage("§cNo permission.");
            return true;
        }

        if (args.length < 2) {
            sender.sendMessage("§7/alignment get <player>");
            sender.sendMessage("§7/alignment set <player> <amount>");
            sender.sendMessage("§7/alignment add <player> <amount>");
            sender.sendMessage("§7Range: -100 to 100");
            return true;
        }

        String sub = args[0].toLowerCase();
        Player target = Bukkit.getPlayerExact(args[1]);

        if (target == null) {
            sender.sendMessage("§cPlayer must be online.");
            return true;
        }

        PlayerData pd = plugin.storage().getOrCreatePlayer(target.getUniqueId());

        if (sub.equals("get")) {
            int score = pd.getAlignmentScore();
            GuildAlignment group = AlignmentUtil.groupFromScore(score);
            sender.sendMessage("§a" + target.getName() + " alignment: §f" + score + " §7(" + group.name() + ")");
            return true;
        }

        if (args.length < 3) {
            sender.sendMessage("§cMissing amount.");
            return true;
        }

        int amount;
        try {
            amount = Integer.parseInt(args[2]);
        } catch (NumberFormatException e) {
            sender.sendMessage("§cAmount must be a number.");
            return true;
        }

        if (sub.equals("set")) {
            int newScore = clamp(amount, -100, 100);
            pd.setAlignmentScore(newScore);
            plugin.storage().save();

            GuildAlignment group = AlignmentUtil.groupFromScore(newScore);
            sender.sendMessage("§aSet " + target.getName() + " alignment to §f" + newScore + " §7(" + group.name() + ")");

            // Live check immediately
            if (plugin.alignmentWatcher() != null) {
                plugin.alignmentWatcher().checkAndWarn(target, false);
            }
            return true;
        }

        if (sub.equals("add")) {
            int newScore = clamp(pd.getAlignmentScore() + amount, -100, 100);
            pd.setAlignmentScore(newScore);
            plugin.storage().save();

            GuildAlignment group = AlignmentUtil.groupFromScore(newScore);
            sender.sendMessage("§aAdded " + amount + " to " + target.getName() + ". Now: §f" + newScore + " §7(" + group.name() + ")");

            // Live check immediately
            if (plugin.alignmentWatcher() != null) {
                plugin.alignmentWatcher().checkAndWarn(target, false);
            }
            return true;
        }

        sender.sendMessage("§cUnknown subcommand.");
        return true;
    }

    private int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }
}
