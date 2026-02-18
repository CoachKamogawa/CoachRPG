package com.magicera.guilds.commands;

import com.magicera.guilds.MagicEraGuildsPlugin;
import com.magicera.guilds.data.Guild;
import com.magicera.guilds.data.GuildAlignment;
import com.magicera.guilds.data.GuildRole;
import com.magicera.guilds.data.PlayerData;
import com.magicera.guilds.guilds.InviteManager;
import com.magicera.guilds.util.AlignmentUtil;
import com.magicera.guilds.util.Text;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.text.DecimalFormat;
import java.util.UUID;

public final class GuildCommand implements CommandExecutor {

    private final MagicEraGuildsPlugin plugin;
    private final DecimalFormat moneyFmt = new DecimalFormat("#,##0.00");

    public GuildCommand(MagicEraGuildsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

        // -------------------------------------------------
        // /guild  → open menu for players, help for console
        // -------------------------------------------------
        if (args.length == 0) {
            if (sender instanceof Player player) {
                player.openInventory(
                        com.magicera.guilds.gui.Menus.mainMenu(plugin, player.getUniqueId())
                );
            } else {
                sender.sendMessage("§7/guild menu");
                sender.sendMessage("§7/guild create \"<name>\" <displayName>");
                sender.sendMessage("§7/guild invite <player>");
                sender.sendMessage("§7/guild accept");
                sender.sendMessage("§7/guild deny");
                sender.sendMessage("§7/guild disband");
                sender.sendMessage("§7/guild bank");
                sender.sendMessage("§7/guild deposit <amount>");
                sender.sendMessage("§7/guild withdraw <amount>");
                sender.sendMessage("§7/guild tax <0-9>");
                sender.sendMessage("§7/guild reload");
            }
            return true;
        }

        String sub = args[0].toLowerCase();

        // /guild menu
        if (sub.equals("menu")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage("§cPlayers only.");
                return true;
            }
            player.openInventory(
                    com.magicera.guilds.gui.Menus.mainMenu(plugin, player.getUniqueId())
            );
            return true;
        }

        // reload
        if (sub.equals("reload")) {
            if (!sender.hasPermission("magicera.admin")) {
                sender.sendMessage("§cNo permission.");
                return true;
            }
            plugin.reloadConfig();
            plugin.storage().load();
            sender.sendMessage("§aReloaded guild data.");
            return true;
        }

        // -------------------------------------------------
        // BANK / ECONOMY
        // -------------------------------------------------

        if (sub.equals("bank") || sub.equals("balance")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage("§cPlayers only.");
                return true;
            }

            PlayerData pd = plugin.storage().getOrCreatePlayer(player.getUniqueId());
            if (pd.getGuildId() == null) {
                sender.sendMessage("§cYou are not in a guild.");
                return true;
            }

            Guild g = plugin.storage().getGuild(pd.getGuildId());
            if (g == null) {
                pd.setGuildId(null);
                plugin.storage().save();
                sender.sendMessage("§cYour guild data was missing.");
                return true;
            }

            sender.sendMessage("§7Guild bank balance: §a$" + fmt(g.getBankBalance()));
            sender.sendMessage("§7Guild tax: §e" + g.getTaxPercent() + "%");
            return true;
        }

        if (sub.equals("deposit")) {
            if (!(sender instanceof Player player)) return true;
            if (args.length < 2) {
                player.sendMessage("§cUsage: /guild deposit <amount>");
                return true;
            }

            Economy econ = plugin.economy() == null ? null : plugin.economy().econ();
            if (econ == null) {
                player.sendMessage("§cEconomy not available.");
                return true;
            }

            double amount = parseMoney(args[1]);
            if (amount <= 0) {
                player.sendMessage("§cInvalid amount.");
                return true;
            }

            PlayerData pd = plugin.storage().getOrCreatePlayer(player.getUniqueId());
            Guild g = plugin.storage().getGuild(pd.getGuildId());
            if (g == null) {
                player.sendMessage("§cYou are not in a guild.");
                return true;
            }

            if (!econ.has(player, amount)) {
                player.sendMessage("§cYou don't have enough money.");
                return true;
            }

            EconomyResponse r = econ.withdrawPlayer(player, amount);
            if (!r.transactionSuccess()) {
                player.sendMessage("§cTransaction failed.");
                return true;
            }

            g.setBankBalance(g.getBankBalance() + amount);
            plugin.storage().save();

            player.sendMessage("§aDeposited §f$" + fmt(amount) +
                    " §aNew balance: §f$" + fmt(g.getBankBalance()));
            return true;
        }

        if (sub.equals("withdraw")) {
            if (!(sender instanceof Player player)) return true;
            if (args.length < 2) {
                player.sendMessage("§cUsage: /guild withdraw <amount>");
                return true;
