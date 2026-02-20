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
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.stream.Collectors;

public final class GuildCommand implements TabExecutor {

    private final MagicEraGuildsPlugin plugin;

    public GuildCommand(MagicEraGuildsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

        if (args.length == 0) {
            sendHelp(sender, 1);
            return true;
        }

        String sub = args[0].toLowerCase();

        // -------------------------
        // HELP / MENU
        // -------------------------
        if (sub.equals("help")) {
            int page = parseHelpPage(args, 1);
            sendHelp(sender, page);
            return true;
        }

        if (sub.matches("\\d+")) {
            sendHelp(sender, parseHelpPage(args, 0));
            return true;
        }

        if (sub.equals("menu")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage("§cPlayers only.");
                return true;
            }
            player.openInventory(com.magicera.guilds.gui.Menus.mainMenu(plugin, player.getUniqueId()));
            return true;
        }

        // -------------------------
        // CHAT
        // -------------------------
        if (sub.equals("chat")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage("§cPlayers only.");
                return true;
            }
            PlayerData pd = plugin.storage().getOrCreatePlayer(player.getUniqueId());
            if (pd.getGuildId() == null) {
                sender.sendMessage("§cYou are not in a guild.");
                return true;
            }
            pd.setGuildChatEnabled(!pd.isGuildChatEnabled());
            plugin.storage().save();
            sender.sendMessage(pd.isGuildChatEnabled() ? "§aGuild chat enabled." : "§eGuild chat disabled.");
            return true;
        }

        // -------------------------
        // HOME
        // -------------------------
        if (sub.equals("home")) {
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
            if (g == null || !g.hasHome()) {
                sender.sendMessage("§cYour guild has no home set.");
                return true;
            }
            org.bukkit.World world = Bukkit.getWorld(g.getHomeWorld());
            if (world == null) {
                sender.sendMessage("§cGuild home world is unavailable.");
                return true;
            }
            Location loc = new Location(world, g.getHomeX() + 0.5, g.getHomeY(), g.getHomeZ() + 0.5);
            player.teleport(loc);
            sender.sendMessage("§aTeleported to guild home.");
            return true;
        }

        if (sub.equals("sethome")) {
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
            if (g == null) return true;
            GuildRole role = g.getMembers().get(player.getUniqueId());
            if (role != GuildRole.MASTER) {
                sender.sendMessage("§cOnly the guild master can set home.");
                return true;
            }
            Location l = player.getLocation();
            g.setHome(l.getWorld().getName(), l.getBlockX(), l.getBlockY(), l.getBlockZ());
            g.addLogEntry("Home set by " + player.getName());
            plugin.storage().save();
            sender.sendMessage("§aGuild home set.");
            return true;
        }

        if (sub.equals("claimland")) {
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
            if (g == null) return true;
            GuildRole role = g.getMembers().get(player.getUniqueId());
            if (role != GuildRole.MASTER && !g.isMembersCanClaim()) {
                sender.sendMessage("§cGuild master has disabled member claims.");
                return true;
            }
            if (role != GuildRole.MASTER && pd.getPower() < 10.0) {
                sender.sendMessage("§cYou need at least 10 power to claim a chunk.");
                return true;
            }
            String key = Guild.chunkKey(player.getWorld().getName(), player.getChunk().getX(), player.getChunk().getZ());
            for (Guild other : plugin.storage().allGuilds()) {
                if (other.getClaimedChunks().contains(key)) {
                    sender.sendMessage("§cThat chunk is already claimed by " + other.getName());
                    return true;
                }
            }
            int max = maxClaims(g);
            if (g.getClaimedChunks().size() >= max) {
                sender.sendMessage("§cGuild claim cap reached (§f" + max + "§c). Increase guild power.");
                return true;
            }
            g.getClaimedChunks().add(key);
            g.addLogEntry("Claimed land at " + key + " by " + player.getName());
            plugin.storage().save();
            sender.sendMessage("§aChunk claimed for your guild.");
            return true;
        }

        if (sub.equals("claimtoggle")) {
            if (!(sender instanceof Player player)) return true;
            PlayerData pd = plugin.storage().getOrCreatePlayer(player.getUniqueId());
            Guild g = pd.getGuildId() == null ? null : plugin.storage().getGuild(pd.getGuildId());
            if (g == null) return true;
            if (g.getMembers().get(player.getUniqueId()) != GuildRole.MASTER) {
                sender.sendMessage("§cOnly the guild master can toggle member claims.");
                return true;
            }
            g.setMembersCanClaim(!g.isMembersCanClaim());
            plugin.storage().save();
            sender.sendMessage(g.isMembersCanClaim() ? "§aMembers can now claim chunks." : "§eMembers can no longer claim chunks.");
            return true;
        }

        if (sub.equals("ally") || sub.equals("war")) {
            if (!(sender instanceof Player player)) return true;
            if (args.length < 2) {
                sender.sendMessage("§cUsage: /guild " + sub + " <player|guild>");
                return true;
            }
            Guild actor = guildOf(player.getUniqueId());
            if (actor == null) {
                sender.sendMessage("§cYou are not in a guild.");
                return true;
            }
            if (actor.getMembers().get(player.getUniqueId()) != GuildRole.MASTER) {
                sender.sendMessage("§cOnly the guild master can use this command.");
                return true;
            }
            Guild target = resolveGuildTarget(args[1]);
            if (target == null || target.getId().equals(actor.getId())) {
                sender.sendMessage("§cInvalid target guild.");
                return true;
            }
            if (sub.equals("ally")) {
                actor.getAllies().add(target.getId());
                target.getAllies().add(actor.getId());
                actor.getEnemies().remove(target.getId());
                target.getEnemies().remove(actor.getId());
                actor.addLogEntry("Alliance formed with " + target.getName());
                target.addLogEntry("Alliance formed with " + actor.getName());
                plugin.storage().save();
                sender.sendMessage("§aAlliance formed with " + target.getName());
            } else {
                boolean sameFavor = actor.getAlignment() == target.getAlignment();
                if (sameFavor) {
                    sender.sendMessage("§eSame favor war request auto-accepted in this build.");
                }
                actor.getEnemies().add(target.getId());
                target.getEnemies().add(actor.getId());
                actor.getAllies().remove(target.getId());
                target.getAllies().remove(actor.getId());
                actor.setInWar(true);
                target.setInWar(true);
                long warEnd = System.currentTimeMillis() + (24L * 60L * 60L * 1000L);
                actor.setWarEndsAtEpochMs(warEnd);
                target.setWarEndsAtEpochMs(warEnd);
                plugin.storage().save();
                Bukkit.broadcastMessage("§7[§5Magic Era§7] §f" + Text.stripColors(actor.getName()) + " has declared war on " + Text.stripColors(target.getName()) + "!");
            }
            return true;
        }

        // -------------------------
        // RELOAD (admin)
        // -------------------------
        if (sub.equals("reload")) {
            if (!sender.hasPermission("magicera.admin")) {
                sender.sendMessage("§cNo permission.");
                return true;
            }
            plugin.reloadConfig();
            sender.sendMessage("§aConfig reloaded.");
            return true;
        }

        // -------------------------
        // BANK VIEW
        // -------------------------
        if (sub.equals("bank")) {
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

            sender.sendMessage("§7Guild Bank: §f$" + fmt(g.getBankBalance()));
            return true;
        }

        // -------------------------
        // DEPOSIT
        // -------------------------
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

            g.addLogEntry("Deposit: " + player.getName() + " $" + fmt(amount));
            sender.sendMessage("§aDeposited §f$" + fmt(amount) + " §ainto guild bank. New balance: §f$" + fmt(g.getBankBalance()));
            return true;
        }

        // -------------------------
        // WITHDRAW
        // -------------------------
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
            if (role == null) {
                sender.sendMessage("§cYou are not in that guild.");
                return true;
            }

            if (amount > g.getBankBalance()) {
                sender.sendMessage("§cNot enough funds in guild bank.");
                return true;
            }

            // Optional officer cap logic (if your Guild supports it)
            if (role == GuildRole.OFFICER) {
                double used = g.getOfficerWithdrawUsed24h();
                double cap = g.getBankBalance() * 0.25;
                double remaining = Math.max(0.0, cap - used);

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

            g.addLogEntry("Withdraw: " + player.getName() + " $" + fmt(amount));
            plugin.storage().save();
            sender.sendMessage("§aWithdrew §f$" + fmt(amount) + " §afrom guild bank. New balance: §f$" + fmt(g.getBankBalance()));
            return true;
        }

        // -------------------------
        // TAX
        // -------------------------
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
                sender.sendMessage("§cOnly the Guild Master can change the tax.");
                return true;
            }

            g.setTaxPercent(pct);
            g.addLogEntry("Tax set to " + pct + "% by " + player.getName());
            plugin.storage().save();
            sender.sendMessage("§aGuild tax set to §f" + pct + "%§a.");
            return true;
        }

        // -------------------------
        // CREATE
        // -------------------------
        if (sub.equals("create")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage("§cPlayers only.");
                return true;
            }

            PlayerData pd = plugin.storage().getOrCreatePlayer(player.getUniqueId());
            if (pd.getGuildId() != null) {
                sender.sendMessage("§cYou are already in a guild.");
                return true;
            }

            ParsedCreate parsed = parseCreateArgs(args);
            if (parsed == null) {
                sender.sendMessage("§cUsage: /guild create \"<name>\" <displayName>");
                sender.sendMessage("§7Example: §f/guild create \"White Rose\" &aWR");
                return true;
            }

            String rawName = parsed.guildName;
            String rawPrefix = parsed.displayName;

            if (!isBoldOnlyName(rawName)) {
                sender.sendMessage("§cGuild names may only use bold formatting (&l). No underline/italic/strikethrough/magic/reset.");
                return true;
            }

            String id = Text.normalizeId(rawName);
            if (id == null || id.isBlank()) {
                sender.sendMessage("§cInvalid guild name.");
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

            g.addLogEntry("Guild created by " + player.getName());
            plugin.storage().save();

            sender.sendMessage("§aCreated guild: §r" + Text.color(g.getName()) + " §7[" + g.getPrefix() + "§7] §7Favor: §f"
                    + AlignmentUtil.displayName(masterAlign));
            return true;
        }

        // -------------------------
        // INVITE
        // -------------------------
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

            GuildRole inviterRole = guild.getMembers().get(inviter.getUniqueId());
            if (inviterRole != GuildRole.MASTER && inviterRole != GuildRole.OFFICER) {
                sender.sendMessage("§cOnly the Guild Master or Officers can invite.");
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
            target.sendMessage("§7[§bMagic Era§7] §fYou were invited to join §r" + Text.color(guild.getName()) + " §7[" + guild.getPrefix() + "§7]");
            target.sendMessage("§7Type §a/guild accept §7or §c/guild deny");
            return true;
        }

        // -------------------------
        // ACCEPT / DENY
        // -------------------------
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
            guild.addLogEntry("Member joined: " + player.getName());

            plugin.inviteManager().clearInvite(player);
            plugin.storage().save();

            sender.sendMessage("§aYou joined §r" + Text.color(guild.getName()) + " §7[" + guild.getPrefix() + "§7]");
            Bukkit.broadcastMessage("§7[§bMagic Era§7] §f" + player.getName() + " has joined " + Text.color(guild.getName()) + "§f.");

            if (plugin.alignmentWatcher() != null) {
                plugin.alignmentWatcher().checkAndWarn(player, false);
            }

            return true;
        }

        // -------------------------
        // LEAVE
        // -------------------------
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
                sender.sendMessage("§cThe Guild Master cannot leave. Transfer master or disband.");
                return true;
            }

            guild.removeMember(player.getUniqueId());
            pd.setGuildId(null);
            pd.setGuildTitle("");
            pd.setOutOfAlignmentSinceEpochMs(null);
            guild.addLogEntry("Member left: " + player.getName());
            plugin.storage().save();

            sender.sendMessage("§aYou left the guild.");
            return true;
        }

        // -------------------------
        // KICK (master; admin variant supported via args)
        // -------------------------
        if (sub.equals("kick")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage("§cPlayers only.");
                return true;
            }
            if (args.length < 2) {
                sender.sendMessage("§cUsage: /guild kick <player>");
                sender.sendMessage("§7(Admin): /guild kick <player> <guild>");
                return true;
            }

            // Admin-kick a specific guild: /guild kick <player> <guild>
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

                guild.removeMember(targetId);
                if (guild.getId().equals(targetPd.getGuildId())) {
                    targetPd.setGuildId(null);
                    targetPd.setGuildTitle("");
                    targetPd.setOutOfAlignmentSinceEpochMs(null);
                }
                guild.addLogEntry("Admin kick: " + safeName(targetId));
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

            if (guild.getKickLockUntilEpochMs() > System.currentTimeMillis()) {
                sender.sendMessage("§cYou cannot kick members during the 24h impeachment lock.");
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

            guild.removeMember(targetId);
            PlayerData targetPd = plugin.storage().getOrCreatePlayer(targetId);
            if (guild.getId().equals(targetPd.getGuildId())) {
                targetPd.setGuildId(null);
                targetPd.setGuildTitle("");
                targetPd.setOutOfAlignmentSinceEpochMs(null);
            }
            guild.addLogEntry("Kick: " + safeName(targetId) + " by " + player.getName());
            plugin.storage().save();

            String targetName = Bukkit.getOfflinePlayer(targetId).getName();
            if (targetName == null) targetName = targetId.toString();
            sender.sendMessage("§aKicked §f" + targetName + " §afrom the guild.");

            Player online = Bukkit.getPlayer(targetId);
            if (online != null) online.sendMessage("§cYou were kicked from guild " + Text.color(guild.getName()) + "§c.");
            return true;
        }

        // -------------------------
        // PROMOTE
        // -------------------------
        if (sub.equals("promote")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage("§cPlayers only.");
                return true;
            }
            if (args.length < 2) {
                sender.sendMessage("§cUsage: /guild promote <player>");
                return true;
            }
            PlayerData actor = plugin.storage().getOrCreatePlayer(player.getUniqueId());
            if (actor.getGuildId() == null) {
                sender.sendMessage("§cYou are not in a guild.");
                return true;
            }
            Guild guild = plugin.storage().getGuild(actor.getGuildId());
            if (guild == null) return true;

            if (guild.getMembers().get(player.getUniqueId()) != GuildRole.MASTER) {
                sender.sendMessage("§cOnly the Guild Master can promote members.");
                return true;
            }
            UUID targetId = resolvePlayerUuid(args[1]);
            if (targetId == null || guild.getMembers().get(targetId) == null) {
                sender.sendMessage("§cThat player is not in your guild.");
                return true;
            }
            if (guild.getMembers().get(targetId) == GuildRole.MASTER) {
                sender.sendMessage("§cThat player is already the Guild Master.");
                return true;
            }
            guild.setRole(targetId, GuildRole.OFFICER);
            guild.addLogEntry("Promotion: " + safeName(targetId) + " -> OFFICER");
            plugin.storage().save();
            sender.sendMessage("§aPromoted §f" + safeName(targetId) + " §ato Officer.");
            return true;
        }

        // -------------------------
        // NEWMASTER
        // -------------------------
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

            if (guild.getKickLockUntilEpochMs() > System.currentTimeMillis()) {
                sender.sendMessage("§cYou cannot kick members during the 24h impeachment lock.");
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
            guild.addLogEntry("Master transferred: " + player.getName() + " -> " + safeName(targetId));
            plugin.storage().save();

            String targetName = Bukkit.getOfflinePlayer(targetId).getName();
            if (targetName == null) targetName = targetId.toString();
            sender.sendMessage("§aGuild leadership transferred to §f" + targetName + "§a.");
            Player online = Bukkit.getPlayer(targetId);
            if (online != null) online.sendMessage("§aYou are now the Guild Master of " + Text.color(guild.getName()) + "§a.");
            return true;
        }

        // -------------------------
        // TITLE (member titles, master/officer)
        // -------------------------
        if (sub.equals("title")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage("§cPlayers only.");
                return true;
            }
            if (args.length < 3) {
                sender.sendMessage("§cUsage: /guild title <player> <text|clear>");
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
            if (actorRole != GuildRole.MASTER && actorRole != GuildRole.OFFICER) {
                sender.sendMessage("§cOnly the Guild Master or Officers can set member titles.");
                return true;
            }

            UUID targetId = resolvePlayerUuid(args[1]);
            if (targetId == null || !guild.getMembers().containsKey(targetId)) {
                sender.sendMessage("§cThat player is not in your guild.");
                return true;
            }

            String value = joinArgs(args, 2);
            PlayerData targetPd = plugin.storage().getOrCreatePlayer(targetId);

            if (value.equalsIgnoreCase("clear")) {
                targetPd.setGuildTitle("");
                plugin.storage().save();
                sender.sendMessage("§aMember title cleared.");
                return true;
            }

            String stripped = Text.stripColors(value).trim();
            if (stripped.isEmpty() || stripped.length() > 24) {
                sender.sendMessage("§cMember title must be 1-24 visible characters.");
                return true;
            }

            targetPd.setGuildTitle(Text.color(value));
            plugin.storage().save();
            sender.sendMessage("§aMember title set for §f" + safeName(targetId) + "§a: §r" + targetPd.getGuildTitle());
            return true;
        }

        // -------------------------
        // IMPEACH
        // -------------------------
        if (sub.equals("impeach")) {
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
            if (guild == null) return true;

            int min = plugin.getConfig().getInt("guilds.impeach-min-members", 10);
            if (guild.getMembers().size() < min) {
                sender.sendMessage("§cYour guild needs at least " + min + " members to impeach.");
                return true;
            }

            if (args.length >= 2) {
                String vote = args[1].toLowerCase();
                if (vote.equals("remove") || vote.equals("keep")) {
                    guild.getImpeachmentVotes().put(player.getUniqueId(), vote.equals("remove"));
                    plugin.storage().save();
                    sender.sendMessage(vote.equals("remove") ? "§cYou voted to REMOVE the master." : "§aYou voted to KEEP the master.");
                    return true;
                }
            }

            if (guild.getImpeachmentStartedEpochMs() != null) {
                sender.sendMessage("§eImpeachment already active. Vote with /guild impeach <remove|keep>");
                return true;
            }

            guild.setImpeachmentStartedEpochMs(System.currentTimeMillis());
            guild.setKickLockUntilEpochMs(System.currentTimeMillis() + (24L * 60L * 60L * 1000L));
            guild.getImpeachmentVotes().clear();
            guild.addLogEntry("Impeachment started by " + player.getName());
            plugin.storage().save();

            for (UUID memberId : guild.getMembers().keySet()) {
                Player online = Bukkit.getPlayer(memberId);
                if (online != null) {
                    online.sendMessage("§7[§aGuild§7] §f" + player.getName() + " has initiated an impeachment.");
                    online.sendMessage("§7[§aGuild§7] §fYou must vote: /guild impeach <remove|keep>");
                    online.playSound(online.getLocation(), Sound.ENTITY_WITHER_SPAWN, 1f, 1f);
                }
            }
            return true;
        }

        // -------------------------
        // ADMIN ADD / KICK
        // -------------------------
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
                    oldGuild.removeMember(targetId);
                }
                targetPd.setGuildId(guild.getId());
                targetPd.setGuildTitle("");
                targetPd.setOutOfAlignmentSinceEpochMs(null);
                guild.setRole(targetId, GuildRole.MEMBER);
                guild.addLogEntry("Admin add: " + safeName(targetId));
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
                guild.removeMember(targetId);
                if (guild.getId().equals(targetPd.getGuildId())) {
                    targetPd.setGuildId(null);
                    targetPd.setGuildTitle("");
                    targetPd.setOutOfAlignmentSinceEpochMs(null);
                }
                guild.addLogEntry("Admin kick: " + safeName(targetId));
                plugin.storage().save();
                sender.sendMessage("§aRemoved §f" + safeName(targetId) + " §afrom guild §r" + Text.color(guild.getName()) + "§a.");
            }
            return true;
        }

        // -------------------------
        // FORCE TAX (admin)
        // -------------------------
        if (sub.equals("forcetax")) {
            if (!sender.hasPermission("magicera.admin")) {
                sender.sendMessage("§cNo permission.");
                return true;
            }
            plugin.runGuildTaxCycle(sender, true);
            return true;
        }

        // -------------------------
        // DISBAND (confirm)
        // -------------------------
        if (sub.equals("disband")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage("§cPlayers only.");
                return true;
            }
            if (args.length < 2 || !args[1].equalsIgnoreCase("confirm")) {
                sender.sendMessage("§cUsage: /guild disband confirm");
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

            for (UUID memberId : new ArrayList<>(g.getMembers().keySet())) {
                PlayerData mpd = plugin.storage().getOrCreatePlayer(memberId);
                if (g.getId().equals(mpd.getGuildId())) {
                    mpd.setGuildId(null);
                    mpd.setOutOfAlignmentSinceEpochMs(null);
                    mpd.setGuildTitle("");
                }
            }

            plugin.storage().deleteGuild(g.getId());
            plugin.storage().save();

            Bukkit.broadcastMessage("§7[§aGuild§7] §cGuild disbanded: §r" + Text.color(g.getName()));
            return true;
        }

        sender.sendMessage("§cUnknown subcommand.");
        sendHelp(sender, 1);
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
                    "help", "menu", "chat", "home", "sethome", "claimland", "claimtoggle", "ally", "war",
                    "create", "invite", "accept", "leave", "kick", "promote", "newmaster", "title", "deny", "disband", "impeach",
                    "bank", "deposit", "withdraw", "tax", "1", "2"
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

        // /guild invite <player> etc
        if ((sub.equals("invite") || sub.equals("kick") || sub.equals("newmaster") || sub.equals("promote") || sub.equals("title") || sub.equals("ally") || sub.equals("war")) && args.length == 2) {
            List<String> opts = new ArrayList<>(onlinePlayerNames());
            opts.addAll(plugin.storage().allGuilds().stream().map(Guild::getId).toList());
            return filterPrefix(opts, input);
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

        if (sub.equals("disband") && args.length == 2) {
            return filterPrefix(List.of("confirm"), input);
        }

        if (sub.equals("impeach") && args.length == 2) {
            return filterPrefix(List.of("remove", "keep"), input);
        }

        // /guild create "<name>" <displayName>
        if (sub.equals("create") && args.length == 3) {
            return filterPrefix(List.of("&aWR", "&cDR", "&7IG"), input);
        }

        // /guild deposit <amount> or withdraw <amount>
        if ((sub.equals("deposit") || sub.equals("withdraw")) && args.length == 2) {
            return filterPrefix(List.of("100", "250", "500", "1000"), input);
        }

        if (sub.equals("tax") && args.length == 2) {
            return filterPrefix(List.of("0","1","2","3","4","5","6","7","8","9"), input);
        }

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

    private void sendHelp(CommandSender sender, int page) {
        if (page <= 1) {
            sender.sendMessage("§8§m--------------------------------");
            sender.sendMessage("§7[§aGuild§7] §fGuild Commands §8(Page 1/2)");
            sender.sendMessage("§8§m--------------------------------");
            sender.sendMessage("§7/guild §8(opens menu)");
            sender.sendMessage("§7/guild menu");
            sender.sendMessage("§7/guild help §8or /guild <1|2>");
            sender.sendMessage("§7/guild create \"<name>\" <displayName>");
            sender.sendMessage("§7Example: §f/guild create &l&cFairy &l&eTail");
            sender.sendMessage("§7/guild invite <player>");
            sender.sendMessage("§7/guild accept");
            sender.sendMessage("§7/guild deny");
            sender.sendMessage("§7/guild leave");
            sender.sendMessage("§7/guild kick <player>");
            sender.sendMessage("§7/guild promote <player>");
            sender.sendMessage("§7/guild newmaster <player>");
            sender.sendMessage("§7/guild title <player> <text|clear>");
            sender.sendMessage("§7/guild disband confirm");
            return;
        }

        sender.sendMessage("§8§m--------------------------------");
        sender.sendMessage("§7[§aGuild§7] §fGuild Commands §8(Page 2/2)");
        sender.sendMessage("§8§m--------------------------------");
        sender.sendMessage("§7/guild bank");
        sender.sendMessage("§7/guild deposit <amount>");
        sender.sendMessage("§7/guild withdraw <amount>");
        sender.sendMessage("§7/guild tax <0-9>");
        sender.sendMessage("§7/guild home");
        sender.sendMessage("§7/guild sethome");
        sender.sendMessage("§7/guild claimland");
        sender.sendMessage("§7/guild claimtoggle");
        sender.sendMessage("§7/guild ally <player|guild>");
        sender.sendMessage("§7/guild war <player|guild>");
        sender.sendMessage("§7/guild chat");
        sender.sendMessage("§7/guild impeach §8(or /guild impeach <remove|keep>)");
        if (sender.hasPermission("magicera.admin")) {
            sender.sendMessage("§7/guild reload | add | adminadd | adminkick | forcetax");
        }
    }

    private int parseHelpPage(String[] args, int index) {
        if (args.length <= index) return 1;
        try {
            int p = Integer.parseInt(args[index]);
            return p <= 1 ? 1 : 2;
        } catch (NumberFormatException ignored) {
            return 1;
        }
    }

    private boolean isBoldOnlyName(String name) {
        String n = name.toLowerCase();
        return !(n.contains("&n") || n.contains("&m") || n.contains("&o") || n.contains("&k") || n.contains("&r"));
    }

    private Guild guildOf(UUID playerId) {
        PlayerData pd = plugin.storage().getOrCreatePlayer(playerId);
        return pd.getGuildId() == null ? null : plugin.storage().getGuild(pd.getGuildId());
    }

    private Guild resolveGuildTarget(String token) {
        Player online = Bukkit.getPlayerExact(token);
        if (online != null) {
            Guild g = guildOf(online.getUniqueId());
            if (g != null) return g;
        }
        return plugin.storage().getGuild(Text.normalizeId(token));
    }

    private int maxClaims(Guild guild) {
        int max = 4;
        for (UUID memberId : guild.getMembers().keySet()) {
            PlayerData pd = plugin.storage().getOrCreatePlayer(memberId);
            GuildRole role = guild.getMembers().get(memberId);
            if (role == GuildRole.MASTER) continue;
            max += (int) Math.floor(pd.getPower() / 10.0);
        }
        return max;
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
        // Expected: /guild create "<name with spaces>" <displayName>
        if (args.length < 3) return null;

        // If name isn't quoted, accept /guild create name prefix
        String joined = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
        String name;
        String prefix;

        int firstQuote = joined.indexOf('"');
        int secondQuote = firstQuote >= 0 ? joined.indexOf('"', firstQuote + 1) : -1;

        if (firstQuote >= 0 && secondQuote > firstQuote) {
            name = joined.substring(firstQuote + 1, secondQuote).trim();
            String after = joined.substring(secondQuote + 1).trim();
            if (after.isEmpty()) return null;
            prefix = after.split("\\s+")[0];
        } else {
            // fallback: name is args[1], prefix is args[2]
            if (args.length < 3) return null;
            name = args[1];
            prefix = args[2];
        }

        if (name == null || name.isBlank() || prefix == null || prefix.isBlank()) return null;
        return new ParsedCreate(name, prefix);
    }

    private String joinArgs(String[] args, int start) {
        if (start >= args.length) return "";
        return String.join(" ", Arrays.copyOfRange(args, start, args.length));
    }

    private UUID resolvePlayerUuid(String input) {
        if (input == null || input.isBlank()) return null;

        // UUID literal?
        try {
            return UUID.fromString(input);
        } catch (IllegalArgumentException ignored) {}

        Player online = Bukkit.getPlayerExact(input);
        if (online != null) return online.getUniqueId();

        OfflinePlayer off = Bukkit.getOfflinePlayer(input);
        if (off != null && (off.getName() != null || off.hasPlayedBefore())) {
            return off.getUniqueId();
        }

        return null;
    }

    private String safeName(UUID id) {
        if (id == null) return "Unknown";
        OfflinePlayer off = Bukkit.getOfflinePlayer(id);
        String n = off == null ? null : off.getName();
        return (n == null || n.isBlank()) ? id.toString() : n;
    }

    private double parseMoney(String raw) {
        if (raw == null) return 0.0;
        String cleaned = raw.replace("$", "").replace(",", "").trim();
        try {
            return Double.parseDouble(cleaned);
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }

    private String fmt(double v) {
        return String.format(Locale.US, "%.2f", v);
    }
}
