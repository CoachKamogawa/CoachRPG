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
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.text.DecimalFormat;
import java.util.*;
import java.util.stream.Collectors;

public final class GuildCommand implements TabExecutor {

    private final MagicEraGuildsPlugin plugin;
    private final DecimalFormat moneyFmt = new DecimalFormat("#,##0.00");

    public GuildCommand(MagicEraGuildsPlugin plugin) {
        this.plugin = plugin;
    }

    // -------------------------
    // EXECUTE
    // -------------------------

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

        // /guild -> HELP (players + console)
        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        String sub = args[0].toLowerCase();

        // /guild menu -> open GUI
        if (sub.equals("menu")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage("§cPlayers only.");
                return true;
            }
            player.openInventory(com.magicera.guilds.gui.Menus.mainMenu(plugin, player.getUniqueId()));
            return true;
        }

        // /guild reload
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

        // -------------------------
        // Guild bank / economy
        // -------------------------

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
            sender.sendMessage("§7Guild type: §f" + AlignmentUtil.guildTypeName(g.getAlignment()));
            return true;
        }

        if (sub.equals("deposit")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage("§cPlayers only.");
                return true;
            }
            if (args.length < 2) {
                sender.sendMessage("§cUsage: /guild deposit <amount>");
                return true;
            }

            Economy econ = (plugin.economy() == null) ? null : plugin.economy().econ();
            if (econ == null) {
                sender.sendMessage("§cEconomy is not available (Vault/EssentialsX missing).");
                return true;
            }

            double amount = parseMoney(args[1]);
            if (amount <= 0) {
                sender.sendMessage("§cAmount must be > 0.");
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

            if (!econ.has(player, amount)) {
                sender.sendMessage("§cYou don't have enough money.");
                return true;
            }

            EconomyResponse r = econ.withdrawPlayer(player, amount);
            if (!r.transactionSuccess()) {
                sender.sendMessage("§cDeposit failed: " + (r.errorMessage == null ? "unknown error" : r.errorMessage));
                return true;
            }

            g.setBankBalance(g.getBankBalance() + amount);
            plugin.storage().save();

            sender.sendMessage("§aDeposited §f$" + fmt(amount) + " §ainto guild bank. New balance: §f$" + fmt(g.getBankBalance()));
            return true;
        }

        if (sub.equals("withdraw")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage("§cPlayers only.");
                return true;
            }
            if (args.length < 2) {
                sender.sendMessage("§cUsage: /guild withdraw <amount>");
                return true;
            }

            Economy econ = (plugin.economy() == null) ? null : plugin.economy().econ();
            if (econ == null) {
                sender.sendMessage("§cEconomy is not available (Vault/EssentialsX missing).");
                return true;
            }

            double amount = parseMoney(args[1]);
            if (amount <= 0) {
                sender.sendMessage("§cAmount must be > 0.");
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

            GuildRole role = g.getMembers().get(player.getUniqueId());
            if (role != GuildRole.MASTER && role != GuildRole.OFFICER) {
                sender.sendMessage("§cOnly Guild Master or Officer can withdraw.");
                return true;
            }

            if (amount > g.getBankBalance()) {
                sender.sendMessage("§cGuild bank does not have enough funds.");
                return true;
            }

            // Officer limit: 25% of bank within 24 hours
            if (role == GuildRole.OFFICER) {
                long now = System.currentTimeMillis();
                long windowStart = g.getOfficerWithdrawWindowStartMs();
                long windowLen = 24L * 60L * 60L * 1000L;

                if (windowStart <= 0 || (now - windowStart) > windowLen) {
                    g.setOfficerWithdrawWindowStartMs(now);
                    g.setOfficerWithdrawUsed24h(0.0);
                }

                double limit = g.getBankBalance() * 0.25;
                double used = g.getOfficerWithdrawUsed24h();
                double remaining = Math.max(0.0, limit - used);

                if (amount > remaining) {
                    sender.sendMessage("§cOfficer withdrawal limit exceeded.");
                    sender.sendMessage("§7Remaining for this 24h window: §e$" + fmt(remaining) + " §7(25% cap)");
                    return true;
                }

                g.setOfficerWithdrawUsed24h(used + amount);
            }

            // remove from guild, pay player
            g.setBankBalance(g.getBankBalance() - amount);

            EconomyResponse r = econ.depositPlayer(player, amount);
            if (!r.transactionSuccess()) {
                // rollback
                g.setBankBalance(g.getBankBalance() + amount);
                if (role == GuildRole.OFFICER) {
                    g.setOfficerWithdrawUsed24h(Math.max(0.0, g.getOfficerWithdrawUsed24h() - amount));
                }
                plugin.storage().save();

                sender.sendMessage("§cWithdraw failed: " + (r.errorMessage == null ? "unknown error" : r.errorMessage));
                return true;
            }

            plugin.storage().save();
            sender.sendMessage("§aWithdrew §f$" + fmt(amount) + " §afrom guild bank. New balance: §f$" + fmt(g.getBankBalance()));
            return true;
        }

        if (sub.equals("tax")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage("§cPlayers only.");
                return true;
            }
            if (args.length < 2) {
                sender.sendMessage("§cUsage: /guild tax <0-9>");
                return true;
            }

            int pct;
            try {
                pct = Integer.parseInt(args[1]);
            } catch (NumberFormatException e) {
                sender.sendMessage("§cTax must be a number 0-9.");
                return true;
            }
            if (pct < 0 || pct > 9) {
                sender.sendMessage("§cTax must be between 0 and 9.");
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

            GuildRole role = g.getMembers().get(player.getUniqueId());
            if (role != GuildRole.MASTER) {
                sender.sendMessage("§cOnly the Guild Master can set tax.");
                return true;
            }

            g.setTaxPercent(pct);
            plugin.storage().save();
            sender.sendMessage("§aSet guild tax to §e" + pct + "%§a.");
            return true;
        }

        // -------------------------
        // Guild create / invite / accept / deny / disband
        // -------------------------

        if (sub.equals("create")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage("§cPlayers only.");
                return true;
            }

            ParsedCreate parsed = parseCreateArgs(args);
            if (parsed == null) {
                sender.sendMessage("§cUsage: /guild create \"<name>\" <displayName>");
                sender.sendMessage("§cExample: /guild create \"White Rose\" &aWR");
                return true;
            }

            PlayerData pd = plugin.storage().getOrCreatePlayer(player.getUniqueId());
            if (pd.getGuildId() != null) {
                sender.sendMessage("§cYou are already in a guild.");
                return true;
            }

            String rawName = parsed.guildName;
            String rawPrefix = parsed.displayName;

            String id = Text.normalizeId(rawName);
            if (id.isEmpty()) {
                sender.sendMessage("§cInvalid name.");
                return true;
            }
            if (plugin.storage().guildExists(id)) {
                sender.sendMessage("§cThat guild name is already taken.");
                return true;
            }

            String prefixStripped = Text.stripColors(rawPrefix);
            if (prefixStripped == null) prefixStripped = "";
            prefixStripped = prefixStripped.trim();

            if (prefixStripped.length() < 2 || prefixStripped.length() > 4) {
                sender.sendMessage("§cdisplayName must be 2-4 characters (colors allowed).");
                return true;
            }
            if (plugin.storage().prefixInUse(Text.color(rawPrefix))) {
                sender.sendMessage("§cThat displayName is already in use.");
                return true;
            }

            GuildAlignment masterAlign = AlignmentUtil.groupFromScore(pd.getAlignmentScore());

            Guild g = plugin.storage().createGuild(rawName, rawPrefix, player.getUniqueId());
            g.setAlignment(masterAlign);

            plugin.storage().save();

            sender.sendMessage("§aCreated guild: §r" + Text.color(g.getName()) + " §7[" + g.getPrefix() + "§7] §7Favor: §f"
                    + AlignmentUtil.displayName(masterAlign));
            return true;
        }

        if (sub.equals("invite")) {
            if (!(sender instanceof Player inviter)) {
                sender.sendMessage("§cPlayers only.");
                return true;
            }
            if (args.length < 2) {
                sender.sendMessage("§cUsage: /guild invite <player>");
                return true;
            }

            PlayerData inviterData = plugin.storage().getOrCreatePlayer(inviter.getUniqueId());
            if (inviterData.getGuildId() == null) {
                sender.sendMessage("§cYou are not in a guild.");
                return true;
            }

            Guild guild = plugin.storage().getGuild(inviterData.getGuildId());
            if (guild == null) {
                inviterData.setGuildId(null);
                plugin.storage().save();
                sender.sendMessage("§cYour guild data was missing.");
                return true;
            }

            GuildRole role = guild.getMembers().get(inviter.getUniqueId());
            if (role != GuildRole.MASTER && role != GuildRole.OFFICER) {
                sender.sendMessage("§cOnly Guild Master or Officer can invite.");
                return true;
            }

            Player target = Bukkit.getPlayerExact(args[1]);
            if (target == null) {
                sender.sendMessage("§cPlayer must be online.");
                return true;
            }

            if (target.getUniqueId().equals(inviter.getUniqueId())) {
                sender.sendMessage("§cYou cannot invite yourself.");
                return true;
            }

            PlayerData targetData = plugin.storage().getOrCreatePlayer(target.getUniqueId());
            if (targetData.getGuildId() != null) {
                sender.sendMessage("§cThat player is already in a guild.");
                return true;
            }

            GuildAlignment guildAlign = guild.getAlignment();
            GuildAlignment targetAlign = AlignmentUtil.groupFromScore(targetData.getAlignmentScore());

            if (targetAlign != GuildAlignment.NEUTRAL && targetAlign != guildAlign) {
                sender.sendMessage("§cThat player is out of favor and cannot join this guild.");
                return true;
            }

            plugin.inviteManager().setInvite(target.getUniqueId(), guild.getId(), inviter.getUniqueId());

            sender.sendMessage("§aInvited §f" + target.getName() + " §ato §r" + Text.color(guild.getName()) + " §7[" + guild.getPrefix() + "§7]");
            target.sendMessage("§7[§bMagicEra§7] §fYou were invited to join §r" + Text.color(guild.getName()) + " §7[" + guild.getPrefix() + "§7]");
            target.sendMessage("§7Type §a/guild accept §7or §c/guild deny");
            return true;
        }

        if (sub.equals("accept") || sub.equals("deny")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage("§cPlayers only.");
                return true;
            }

            PlayerData pd = plugin.storage().getOrCreatePlayer(player.getUniqueId());
            if (pd.getGuildId() != null) {
                sender.sendMessage("§cYou are already in a guild.");
                plugin.inviteManager().clearInvite(player);
                return true;
            }

            InviteManager.Invite inv = plugin.inviteManager().getInvite(player.getUniqueId());
            if (inv == null) {
                sender.sendMessage("§cYou have no pending guild invites (or it expired).");
                return true;
            }

            Guild guild = plugin.storage().getGuild(inv.guildId);
            if (guild == null) {
                plugin.inviteManager().clearInvite(player);
                sender.sendMessage("§cThat guild no longer exists.");
                return true;
            }

            if (sub.equals("deny")) {
                plugin.inviteManager().clearInvite(player);
                sender.sendMessage("§7Invite declined.");
                Player inviter = Bukkit.getPlayer(inv.inviter);
                if (inviter != null) inviter.sendMessage("§c" + player.getName() + " declined the guild invite.");
                return true;
            }

            GuildAlignment guildAlign = guild.getAlignment();
            GuildAlignment playerAlign = AlignmentUtil.groupFromScore(pd.getAlignmentScore());

            if (playerAlign == GuildAlignment.NEUTRAL && guildAlign != GuildAlignment.NEUTRAL) {
                int snapped = AlignmentUtil.snapScoreToGuild(guildAlign);
                pd.setAlignmentScore(snapped);
                playerAlign = AlignmentUtil.groupFromScore(pd.getAlignmentScore());
            }

            if (playerAlign != guildAlign) {
                plugin.inviteManager().clearInvite(player);
                sender.sendMessage("§cYou are out of favor and cannot join this guild.");
                return true;
            }

            pd.setGuildId(guild.getId());
            pd.setOutOfAlignmentSinceEpochMs(null);
            guild.setRole(player.getUniqueId(), GuildRole.MEMBER);

            plugin.inviteManager().clearInvite(player);
            plugin.storage().save();

            sender.sendMessage("§aYou joined §r" + Text.color(guild.getName()) + " §7[" + guild.getPrefix() + "§7]");
            Bukkit.broadcastMessage("§b[Magic Era] §f" + player.getName() + " has joined " + Text.color(guild.getName()) + "§f.");

            if (plugin.alignmentWatcher() != null) {
                plugin.alignmentWatcher().checkAndWarn(player, false);
            }

            return true;
        }


        if (sub.equals("leave")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage("§cPlayers only.");
                return true;
            }

            PlayerData pd = plugin.storage().getOrCreatePlayer(player.getUniqueId());
            if (pd.getGuildId() == null) {
                sender.sendMessage("§cYou are not in a guild.");
                return true;
            }

            Guild guild = plugin.storage().getGuild(pd.getGuildId());
            if (guild == null) {
                pd.setGuildId(null);
                plugin.storage().save();
                sender.sendMessage("§cYour guild data was missing.");
                return true;
            }

            GuildRole role = guild.getMembers().get(player.getUniqueId());
            if (role == GuildRole.MASTER) {
                sender.sendMessage("§cGuild Master cannot leave. Use /guild newmaster first or /guild disband.");
                return true;
            }

            guild.getMembers().remove(player.getUniqueId());
            pd.setGuildId(null);
            pd.setGuildTitle("");
            pd.setOutOfAlignmentSinceEpochMs(null);
            plugin.storage().save();

            sender.sendMessage("§aYou left guild §r" + Text.color(guild.getName()) + "§a.");
            return true;
        }

        if (sub.equals("kick")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage("§cPlayers only.");
                return true;
            }
            if (args.length < 2) {
                sender.sendMessage("§cUsage: /guild kick <player>");
                if (sender.hasPermission("magicera.admin")) sender.sendMessage("§cAdmin usage: /guild kick <player> <guild>");
                return true;
            }

            if (args.length >= 3 && sender.hasPermission("magicera.admin")) {
                UUID targetId = resolvePlayerUuid(args[1]);
                if (targetId == null) {
                    sender.sendMessage("§cUnknown player: " + args[1]);
                    return true;
                }

                String guildId = Text.normalizeId(args[2]);
                Guild guild = plugin.storage().getGuild(guildId);
                if (guild == null) {
                    sender.sendMessage("§cUnknown guild: " + args[2]);
                    return true;
                }

                PlayerData targetPd = plugin.storage().getOrCreatePlayer(targetId);
                GuildRole targetRole = guild.getMembers().get(targetId);
                if (targetRole == GuildRole.MASTER) {
                    sender.sendMessage("§cCannot admin-kick the Guild Master. Transfer master first.");
                    return true;
                }
                if (!guild.getMembers().containsKey(targetId) && !guild.getId().equals(targetPd.getGuildId())) {
                    sender.sendMessage("§cThat player is not in that guild.");
                    return true;
                }

                guild.getMembers().remove(targetId);
                if (guild.getId().equals(targetPd.getGuildId())) {
                    targetPd.setGuildId(null);
                    targetPd.setGuildTitle("");
                    targetPd.setOutOfAlignmentSinceEpochMs(null);
                }
                plugin.storage().save();
                sender.sendMessage("§aRemoved §f" + safeName(targetId) + " §afrom guild §r" + Text.color(guild.getName()) + "§a.");
                return true;
            }

            PlayerData actor = plugin.storage().getOrCreatePlayer(player.getUniqueId());
            if (actor.getGuildId() == null) {
                sender.sendMessage("§cYou are not in a guild.");
                return true;
            }

            Guild guild = plugin.storage().getGuild(actor.getGuildId());
            if (guild == null) {
                actor.setGuildId(null);
                plugin.storage().save();
                sender.sendMessage("§cYour guild data was missing.");
                return true;
            }

            GuildRole actorRole = guild.getMembers().get(player.getUniqueId());
            if (actorRole != GuildRole.MASTER) {
                sender.sendMessage("§cOnly the Guild Master can kick members.");
                return true;
            }

            UUID targetId = resolvePlayerUuid(args[1]);
            if (targetId == null) {
                sender.sendMessage("§cUnknown player: " + args[1]);
                return true;
            }
            if (targetId.equals(player.getUniqueId())) {
                sender.sendMessage("§cUse /guild leave if you want to leave.");
                return true;
            }

            GuildRole targetRole = guild.getMembers().get(targetId);
            if (targetRole == null) {
                sender.sendMessage("§cThat player is not in your guild.");
                return true;
            }

            guild.getMembers().remove(targetId);
            PlayerData targetPd = plugin.storage().getOrCreatePlayer(targetId);
            if (guild.getId().equals(targetPd.getGuildId())) {
                targetPd.setGuildId(null);
                targetPd.setGuildTitle("");
                targetPd.setOutOfAlignmentSinceEpochMs(null);
            }
            plugin.storage().save();

            String targetName = Bukkit.getOfflinePlayer(targetId).getName();
            if (targetName == null) targetName = targetId.toString();
            sender.sendMessage("§aKicked §f" + targetName + " §afrom the guild.");

            Player online = Bukkit.getPlayer(targetId);
            if (online != null) online.sendMessage("§cYou were kicked from guild " + Text.color(guild.getName()) + "§c.");
            return true;
        }

        if (sub.equals("newmaster")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage("§cPlayers only.");
                return true;
            }
            if (args.length < 2) {
                sender.sendMessage("§cUsage: /guild newmaster <player>");
                return true;
            }

            PlayerData actor = plugin.storage().getOrCreatePlayer(player.getUniqueId());
            if (actor.getGuildId() == null) {
                sender.sendMessage("§cYou are not in a guild.");
                return true;
            }

            Guild guild = plugin.storage().getGuild(actor.getGuildId());
            if (guild == null) {
                actor.setGuildId(null);
                plugin.storage().save();
                sender.sendMessage("§cYour guild data was missing.");
                return true;
            }

            GuildRole actorRole = guild.getMembers().get(player.getUniqueId());
            if (actorRole != GuildRole.MASTER) {
                sender.sendMessage("§cOnly the Guild Master can transfer leadership.");
                return true;
            }

            UUID targetId = resolvePlayerUuid(args[1]);
            if (targetId == null) {
                sender.sendMessage("§cUnknown player: " + args[1]);
                return true;
            }
            if (targetId.equals(player.getUniqueId())) {
                sender.sendMessage("§cYou are already the Guild Master.");
                return true;
            }

            if (guild.getMembers().get(targetId) == null) {
                sender.sendMessage("§cThat player is not in your guild.");
                return true;
            }

            guild.setRole(player.getUniqueId(), GuildRole.MEMBER);
            guild.setRole(targetId, GuildRole.MASTER);
            plugin.storage().save();

            String targetName = Bukkit.getOfflinePlayer(targetId).getName();
            if (targetName == null) targetName = targetId.toString();
            sender.sendMessage("§aGuild leadership transferred to §f" + targetName + "§a.");
            Player online = Bukkit.getPlayer(targetId);
            if (online != null) online.sendMessage("§aYou are now the Guild Master of " + Text.color(guild.getName()) + "§a.");
            return true;
        }

        if (sub.equals("title")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage("§cPlayers only.");
                return true;
            }
            if (args.length < 2) {
                sender.sendMessage("§cUsage: /guild title <text|clear>");
                return true;
            }

            PlayerData actor = plugin.storage().getOrCreatePlayer(player.getUniqueId());
            if (actor.getGuildId() == null) {
                sender.sendMessage("§cYou are not in a guild.");
                return true;
            }

            Guild guild = plugin.storage().getGuild(actor.getGuildId());
            if (guild == null) {
                actor.setGuildId(null);
                plugin.storage().save();
                sender.sendMessage("§cYour guild data was missing.");
                return true;
            }

            GuildRole actorRole = guild.getMembers().get(player.getUniqueId());
            if (actorRole != GuildRole.MASTER) {
                sender.sendMessage("§cOnly the Guild Master can set the guild title.");
                return true;
            }

            String value = joinArgs(args, 1);
            if (value.equalsIgnoreCase("clear")) {
                guild.setTitle("");
                plugin.storage().save();
                sender.sendMessage("§aGuild title cleared.");
                return true;
            }

            String stripped = Text.stripColors(value).trim();
            if (stripped.isEmpty() || stripped.length() > 24) {
                sender.sendMessage("§cGuild title must be 1-24 visible characters.");
                return true;
            }

            guild.setTitle(Text.color(value));
            plugin.storage().save();
            sender.sendMessage("§aGuild title set to: §r" + guild.getTitle());
            return true;
        }

        if (sub.equals("add") || sub.equals("adminadd") || sub.equals("adminkick")) {
            if (!sender.hasPermission("magicera.admin")) {
                sender.sendMessage("§cNo permission.");
                return true;
            }
            if (args.length < 3) {
                sender.sendMessage("§cUsage: /guild add <player> <guild>");
                sender.sendMessage("§cUsage: /guild adminkick <player> <guild>");
                return true;
            }

            UUID targetId = resolvePlayerUuid(args[1]);
            if (targetId == null) {
                sender.sendMessage("§cUnknown player: " + args[1]);
                return true;
            }

            String guildId = Text.normalizeId(args[2]);
            Guild guild = plugin.storage().getGuild(guildId);
            if (guild == null) {
                sender.sendMessage("§cUnknown guild: " + args[2]);
                return true;
            }

            PlayerData targetPd = plugin.storage().getOrCreatePlayer(targetId);
            Guild oldGuild = targetPd.getGuildId() == null ? null : plugin.storage().getGuild(targetPd.getGuildId());

            if (sub.equals("add") || sub.equals("adminadd")) {
                if (targetPd.getGuildId() != null && oldGuild != null) {
                    oldGuild.getMembers().remove(targetId);
                }
                targetPd.setGuildId(guild.getId());
                targetPd.setGuildTitle("");
                targetPd.setOutOfAlignmentSinceEpochMs(null);
                guild.setRole(targetId, GuildRole.MEMBER);
                plugin.storage().save();
                sender.sendMessage("§aAdded §f" + safeName(targetId) + " §ato guild §r" + Text.color(guild.getName()) + "§a.");
            } else {
                if (!guild.getMembers().containsKey(targetId) && !guild.getId().equals(targetPd.getGuildId())) {
                    sender.sendMessage("§cThat player is not in that guild.");
                    return true;
                }
                GuildRole targetRole = guild.getMembers().get(targetId);
                if (targetRole == GuildRole.MASTER) {
                    sender.sendMessage("§cCannot admin-kick the Guild Master. Transfer master first.");
                    return true;
                }
                guild.getMembers().remove(targetId);
                if (guild.getId().equals(targetPd.getGuildId())) {
                    targetPd.setGuildId(null);
                    targetPd.setGuildTitle("");
                    targetPd.setOutOfAlignmentSinceEpochMs(null);
                }
                plugin.storage().save();
                sender.sendMessage("§aRemoved §f" + safeName(targetId) + " §afrom guild §r" + Text.color(guild.getName()) + "§a.");
            }
            return true;
        }

        if (sub.equals("forcetax")) {
            if (!sender.hasPermission("magicera.admin")) {
                sender.sendMessage("§cNo permission.");
                return true;
            }
            plugin.runGuildTaxCycle(sender, true);
            return true;
        }

        if (sub.equals("disband")) {
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
                sender.sendMessage("§cGuild data was missing. You have been removed from the guild.");
                return true;
            }

            GuildRole role = g.getMembers().get(player.getUniqueId());
            if (role != GuildRole.MASTER) {
                sender.sendMessage("§cOnly the Guild Master can disband the guild.");
                return true;
            }

            for (UUID memberId : g.getMembers().keySet()) {
                PlayerData mpd = plugin.storage().getOrCreatePlayer(memberId);
                if (g.getId().equals(mpd.getGuildId())) {
                    mpd.setGuildId(null);
                    mpd.setOutOfAlignmentSinceEpochMs(null);
                }
            }

            plugin.storage().deleteGuild(g.getId());
            plugin.storage().save();

            Bukkit.broadcastMessage("§7[§bMagicEra§7] §cGuild disbanded: §r" + Text.color(g.getName()));
            return true;
        }

        sender.sendMessage("§cUnknown subcommand.");
        sendHelp(sender);
        return true;
    }

    // -------------------------
    // TAB COMPLETE
    // -------------------------

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        String input = args.length == 0 ? "" : args[args.length - 1];

        // /guild <sub>
        if (args.length == 1) {
            List<String> subs = new ArrayList<>(List.of(
                    "help", "menu",
                    "create", "invite", "accept", "leave", "kick", "newmaster", "title",  "deny", "disband",
                    "bank", "deposit", "withdraw", "tax"
            ));
            if (sender.hasPermission("magicera.admin")) {
                subs.add("reload");
                subs.add("add");
                subs.add("adminadd");
                subs.add("adminkick");
                subs.add("forcetax");
            }
            return filterPrefix(subs, input);
        }

        String sub = args[0].toLowerCase();

        // /guild invite <player>
        if ((sub.equals("invite") || sub.equals("kick") || sub.equals("newmaster")) && args.length == 2) {
            return filterPrefix(onlinePlayerNames(), input);
        }


        if ((sub.equals("add") || sub.equals("adminadd") || sub.equals("adminkick")) && args.length == 2) {
            return filterPrefix(onlinePlayerNames(), input);
        }

        if ((sub.equals("add") || sub.equals("adminadd") || sub.equals("adminkick")) && args.length == 3) {
            List<String> guildIds = plugin.storage().allGuilds().stream().map(Guild::getId).sorted().collect(Collectors.toList());
            return filterPrefix(guildIds, input);
        }


        if (sub.equals("kick") && args.length == 3 && sender.hasPermission("magicera.admin")) {
            List<String> guildIds = plugin.storage().allGuilds().stream().map(Guild::getId).sorted().collect(Collectors.toList());
            return filterPrefix(guildIds, input);
        }

        // /guild create "<name>" <displayName>
        // Can't tab-complete quoted name well. We can at least hint displayName spot:
        if (sub.equals("create") && args.length == 3) {
            return filterPrefix(List.of("&aWR", "&cDR", "&7IG"), input);
        }

        // /guild deposit <amount> or withdraw <amount> or tax <0-9>
        if ((sub.equals("deposit") || sub.equals("withdraw")) && args.length == 2) {
            return filterPrefix(List.of("100", "250", "500", "1000"), input);
        }
        if (sub.equals("tax") && args.length == 2) {
            return filterPrefix(List.of("0","1","2","3","4","5","6","7","8","9"), input);
        }

        // /guild help
        return Collections.emptyList();
    }

    private List<String> onlinePlayerNames() {
        return Bukkit.getOnlinePlayers().stream().map(Player::getName).sorted().collect(Collectors.toList());
    }

    private List<String> filterPrefix(Collection<String> options, String input) {
        String low = input == null ? "" : input.toLowerCase();
        return options.stream()
                .filter(s -> s.toLowerCase().startsWith(low))
                .sorted()
                .collect(Collectors.toList());
    }

    // -------------------------
    // HELP + PARSING
    // -------------------------

    private void sendHelp(CommandSender sender) {
        sender.sendMessage("§7/guild menu");
        sender.sendMessage("§7/guild create \"<name>\" <displayName>");
        sender.sendMessage("§7Example: §f/guild create \"White Rose\" &aWR");
        sender.sendMessage("§7/guild invite <player>");
        sender.sendMessage("§7/guild kick <player>");
        sender.sendMessage("§7/guild newmaster <player>");
        sender.sendMessage("§7/guild leave");
        sender.sendMessage("§7/guild title <text|clear>");
        sender.sendMessage("§7/guild accept");
        sender.sendMessage("§7/guild deny");
        sender.sendMessage("§7/guild disband");
        sender.sendMessage("§7/guild bank");
        sender.sendMessage("§7/guild deposit <amount>");
        sender.sendMessage("§7/guild withdraw <amount>");
        sender.sendMessage("§7/guild tax <0-9>");
        if (sender.hasPermission("magicera.admin")) {
            sender.sendMessage("§7/guild reload");
            sender.sendMessage("§7/guild add <player> <guild>");
            sender.sendMessage("§7/guild adminadd <player> <guild> §8(alias)");
            sender.sendMessage("§7/guild adminkick <player> <guild>");
            sender.sendMessage("§7/guild forcetax");
        }
    }

    private static final class ParsedCreate {
        final String guildName;
        final String displayName;
        ParsedCreate(String guildName, String displayName) {
            this.guildName = guildName;
            this.displayName = displayName;
        }
    }

    private ParsedCreate parseCreateArgs(String[] args) {
        if (args.length < 3) return null;

        String second = args[1];
        if (startsWithQuote(second)) {
            StringBuilder name = new StringBuilder(stripLeadingQuote(second));

            int i = 2;
            boolean closed = endsWithQuote(args[1]) && args[1].length() > 1;
            if (closed) {
                name = new StringBuilder(stripTrailingQuote(stripLeadingQuote(second)));
                i = 2;
            } else {
                while (i < args.length) {
                    name.append(" ").append(args[i]);
                    if (endsWithQuote(args[i])) {
                        closed = true;
                        break;
                    }
                    i++;
                }
            }

            if (!closed) return null;

            String rawName = stripTrailingQuote(name.toString());
            int prefixIndex = i + 1;
            if (prefixIndex >= args.length) return null;

            String rawPrefix = args[prefixIndex];
            return new ParsedCreate(rawName, rawPrefix);
        }

        return new ParsedCreate(args[1], args[2]);
    }

    private boolean startsWithQuote(String s) {
        return s.startsWith("\"") || s.startsWith("'");
    }

    private boolean endsWithQuote(String s) {
        return s.endsWith("\"") || s.endsWith("'");
    }

    private String stripLeadingQuote(String s) {
        if (s.startsWith("\"") || s.startsWith("'")) return s.substring(1);
        return s;
    }

    private String stripTrailingQuote(String s) {
        if (s.endsWith("\"") || s.endsWith("'")) return s.substring(0, s.length() - 1);
        return s;
    }

    private String joinArgs(String[] args, int start) {
        if (start >= args.length) return "";
        return String.join(" ", Arrays.copyOfRange(args, start, args.length)).trim();
    }

    private UUID resolvePlayerUuid(String name) {
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) return online.getUniqueId();

        for (OfflinePlayer off : Bukkit.getOfflinePlayers()) {
            if (off.getName() != null && off.getName().equalsIgnoreCase(name)) {
                return off.getUniqueId();
            }
        }
        return null;
    }

    private String safeName(UUID uuid) {
        String name = Bukkit.getOfflinePlayer(uuid).getName();
        return name == null ? uuid.toString() : name;
    }

    private String fmt(double d) {
        return moneyFmt.format(d);
    }

    private double parseMoney(String s) {
        try {
            String cleaned = s.replace(",", "").trim();
            return Double.parseDouble(cleaned);
        } catch (Exception e) {
            return -1.0;
        }
    }
}
