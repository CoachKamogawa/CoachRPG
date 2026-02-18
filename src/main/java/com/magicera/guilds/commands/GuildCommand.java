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
        // Existing guild features
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

            sender.sendMessage("§aCreated guild: §r" + g.getName() + " §7[" + g.getPrefix() + "§7] §7Alignment: §f" + masterAlign.name());
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
                sender.sendMessage("§cThat player is out of alignment and cannot join this guild.");
                return true;
            }

            plugin.inviteManager().setInvite(target.getUniqueId(), guild.getId(), inviter.getUniqueId());

            sender.sendMessage("§aInvited §f" + target.getName() + " §ato §r" + guild.getName() + " §7[" + guild.getPrefix() + "§7]");
            target.sendMessage("§7[§bMagicEra§7] §fYou were invited to join §r" + guild.getName() + " §7[" + guild.getPrefix() + "§7]");
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
                sender.sendMessage("§cYou are out of alignment and cannot join this guild.");
                return true;
            }

            pd.setGuildId(guild.getId());
            pd.setOutOfAlignmentSinceEpochMs(null);
            guild.setRole(player.getUniqueId(), GuildRole.MEMBER);

            plugin.inviteManager().clearInvite(player);
            plugin.storage().save();

            sender.sendMessage("§aYou joined §r" + guild.getName() + " §7[" + guild.getPrefix() + "§7]");

            Bukkit.broadcastMessage("§b[Magic Era] §f" + player.getName() + " has joined " + Text.color(guild.getName()) + "§f.");

            if (plugin.alignmentWatcher() != null) {
                plugin.alignmentWatcher().checkAndWarn(player, false);
            }

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

            Bukkit.broadcastMessage("§7[§bMagicEra§7] §cGuild disbanded: §r" + g.getName());
            return true;
        }

        sender.sendMessage("§cUnknown subcommand.");
        sendHelp(sender);
        return true;
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage("§7/guild menu");
        sender.sendMessage("§7/guild create \"<name>\" <displayName>");
        sender.sendMessage("§7Example: §f/guild create \"White Rose\" &aWR");
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

    // ----- helpers -----

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
