package com.magicera.guilds.commands;

import com.magicera.guilds.MagicEraGuildsPlugin;
import com.magicera.guilds.data.Guild;
import com.magicera.guilds.data.GuildAlignment;
import com.magicera.guilds.data.GuildRole;
import com.magicera.guilds.data.PlayerData;
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
            if (sender instanceof Player player) {
                player.openInventory(com.magicera.guilds.gui.Menus.mainMenu(plugin, player.getUniqueId()));
            } else {
                sendHelp(sender, 1);
            }
            return true;
        }

        String sub = args[0].toLowerCase();

        // -------------------------
        // HELP / MENU
        // -------------------------
        if (sub.equals("help") || sub.equals("?")) {
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
            String key = Guild.chunkKey(l.getWorld().getName(), l.getChunk().getX(), l.getChunk().getZ());
            if (!g.getClaimedChunks().contains(key)) {
                sender.sendMessage("§cGuild home must be set inside your guild claim.");
                return true;
            }
            g.setHome(l.getWorld().getName(), l.getBlockX(), l.getBlockY(), l.getBlockZ());
            g.addLogEntry("Home set by " + player.getName());
            plugin.storage().save();
            sender.sendMessage("§aGuild home set.");
            return true;
        }

        // -------------------------
        // CLAIM / OVERCLAIM
        // -------------------------
        if (sub.equals("claimhall")) {
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
            if (g.getMembers().get(player.getUniqueId()) != GuildRole.MASTER) {
                sender.sendMessage("§cOnly the guild master can claim the Guild Hall.");
                return true;
            }
            if (g.hasHall()) {
                sender.sendMessage("§cYour guild already has a Guild Hall. Use /guild movehall.");
                return true;
            }
            if (g.isInWar()) {
                sender.sendMessage("§7[§aGuild§7] §cYou cannot claim a Guild Hall during war.");
                return true;
            }

            String world = player.getWorld().getName();
            int cx = player.getChunk().getX();
            int cz = player.getChunk().getZ();
            Set<String> hall = hallArea(world, cx, cz);

            if (!isHallAreaAvailable(g, hall, Collections.emptySet())) {
                sender.sendMessage("§7[§aGuild§7] §cGuild Hall claim failed because part of the 3x3 is already claimed.");
                return true;
            }

            int allowed = plugin.guildPower().allowedChunks(g);
            Set<String> merged = new HashSet<>(g.getClaimedChunks());
            merged.addAll(hall);
            if (merged.size() > allowed) {
                sender.sendMessage("§7[§aGuild§7] §cGuild claim cap reached §f" + allowed + "§c. Reduce land before claiming hall.");
                return true;
            }

            for (String key : hall) g.claimChunk(key);
            g.setHall(world, cx, cz, hall);
            g.setHallLastMovedAtEpochMs(System.currentTimeMillis());
            g.addLogEntry("Guild Hall claimed at " + world + ":" + cx + ":" + cz + " by " + player.getName());
            plugin.guildPower().refreshUnstableClaims(g);
            plugin.storage().save();
            sender.sendMessage("§7[§aGuild§7] §aGuild Hall claimed as a 3x3 territory.");
            return true;
        }

        if (sub.equals("movehall")) {
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
            if (g.getMembers().get(player.getUniqueId()) != GuildRole.MASTER) {
                sender.sendMessage("§cOnly the guild master can move the Guild Hall.");
                return true;
            }
            if (!g.hasHall()) {
                sender.sendMessage("§7[§aGuild§7] §cYour guild does not have a Guild Hall. Use /guild claimhall first.");
                return true;
            }
            if (g.isInWar()) {
                sender.sendMessage("§7[§aGuild§7] §cYou cannot move your Guild Hall during war.");
                return true;
            }

            String world = player.getWorld().getName();
            if (!plugin.territoryConfig().getBoolean("allowCrossWorldHallMove", false)
                    && g.getHallWorld() != null
                    && !g.getHallWorld().equals(world)) {
                sender.sendMessage("§7[§aGuild§7] §cYou can only move your Guild Hall inside its current world.");
                return true;
            }

            long cooldownMs = Math.max(1, plugin.territoryConfig().getLong("moveHallCooldownHours", 24L)) * 60L * 60L * 1000L;
            Long lastMoved = g.getHallLastMovedAtEpochMs();
            if (lastMoved != null && (System.currentTimeMillis() - lastMoved) < cooldownMs) {
                sender.sendMessage("§7[§aGuild§7] §cGuild Hall move is on cooldown for §e"
                        + formatDuration(cooldownMs - (System.currentTimeMillis() - lastMoved)) + "§c.");
                return true;
            }

            int cx = player.getChunk().getX();
            int cz = player.getChunk().getZ();
            Set<String> newHall = hallArea(world, cx, cz);
            Set<String> oldHall = new HashSet<>(g.getHallChunks());

            if (!isHallAreaAvailable(g, newHall, oldHall)) {
                sender.sendMessage("§7[§aGuild§7] §cGuild Hall move failed because part of the 3x3 is already claimed.");
                return true;
            }

            int allowed = plugin.guildPower().allowedChunks(g);
            Set<String> projected = new HashSet<>(g.getClaimedChunks());
            projected.removeAll(oldHall);
            projected.addAll(newHall);
            if (projected.size() > allowed) {
                sender.sendMessage("§7[§aGuild§7] §cGuild claim cap reached §f" + allowed + "§c. Reduce land before moving hall.");
                return true;
            }

            for (String chunk : oldHall) g.unclaimChunk(chunk);
            for (String chunk : newHall) g.claimChunk(chunk);

            g.setHall(world, cx, cz, newHall);
            g.setHallLastMovedAtEpochMs(System.currentTimeMillis());
            g.addLogEntry("Guild Hall moved to " + world + ":" + cx + ":" + cz + " by " + player.getName());
            plugin.guildPower().refreshUnstableClaims(g);
            plugin.storage().save();
            sender.sendMessage("§7[§aGuild§7] §aGuild Hall moved to the new 3x3 territory.");
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
            if (!g.hasHall()) {
                sender.sendMessage("§cYour guild must claim a Guild Hall first using /guild claimhall.");
                return true;
            }

            String key = Guild.chunkKey(player.getWorld().getName(), player.getChunk().getX(), player.getChunk().getZ());

            Guild owner = null;
            for (Guild other : plugin.storage().allGuilds()) {
                if (other.getClaimedChunks().contains(key)) {
                    owner = other;
                    break;
                }
            }

            if (owner != null) {
                boolean atWar = g.getEnemies().contains(owner.getId());
                boolean allowWarOverclaims = plugin.territoryConfig().getBoolean("unstableClaimsDuringWar", true);
                boolean overclaimable = plugin.guildPower().canOverclaimChunk(owner, key) && (!allowWarOverclaims || atWar);
                if (!overclaimable) {
                    sender.sendMessage("§7[§aGuild§7] §cThat chunk is already claimed by " + owner.getName() + "§c.");
                    return true;
                }
                owner.unclaimChunk(key);
                owner.addLogEntry("Lost claim at " + key + " to overclaim by " + g.getName());
                plugin.guildPower().refreshUnstableClaims(owner);
                plugin.guildPower().handlePowerThresholds(owner);
            }

            int max = maxClaims(g);
            if (g.getClaimedChunks().size() >= max) {
                sender.sendMessage("§cGuild claim cap reached (§f" + max + "§c). Increase guild power.");
                return true;
            }

            g.claimChunk(key);
            plugin.guildPower().refreshUnstableClaims(g);
            g.addLogEntry("Claimed land at " + key + " by " + player.getName());
            plugin.guildPower().handlePowerThresholds(g);
            plugin.storage().save();

            sender.sendMessage("§aChunk claimed for your guild.");
            return true;
        }

        if (sub.equals("unclaim")) {
            if (!(sender instanceof Player player)) return true;
            PlayerData pd = plugin.storage().getOrCreatePlayer(player.getUniqueId());
            Guild g = pd.getGuildId() == null ? null : plugin.storage().getGuild(pd.getGuildId());
            if (g == null) {
                sender.sendMessage("§cYou are not in a guild.");
                return true;
            }
            GuildRole role = g.getMembers().get(player.getUniqueId());
            if (role != GuildRole.MASTER) {
                sender.sendMessage("§cOnly the guild master can unclaim land.");
                return true;
            }

            if (args.length >= 2 && args[1].equalsIgnoreCase("all")) {
                int removed = g.getClaimedChunks().size();
                g.clearAllClaims();
                g.clearHall();
                g.addLogEntry("Unclaimed all land by " + player.getName());
                plugin.guildPower().refreshUnstableClaims(g);
                plugin.guildPower().handlePowerThresholds(g);
                plugin.storage().save();
                sender.sendMessage("§aUnclaimed all guild land (§f" + removed + "§a chunks).");
                return true;
            }

            String key = Guild.chunkKey(player.getWorld().getName(), player.getChunk().getX(), player.getChunk().getZ());
            if (!g.unclaimChunk(key)) {
                sender.sendMessage("§cThis chunk is not claimed by your guild.");
                return true;
            }
            g.addLogEntry("Unclaimed land at " + key + " by " + player.getName());
            plugin.guildPower().refreshUnstableClaims(g);
            plugin.storage().save();
            sender.sendMessage("§aChunk unclaimed.");
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

        // -------------------------
        // POWER
        // -------------------------
        if (sub.equals("power")) {
            if (args.length == 1) {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage("§cPlayers only.");
                    return true;
                }
                PlayerData pd = plugin.storage().getOrCreatePlayer(player.getUniqueId());
                if (pd.getGuildId() == null) {
                    sender.sendMessage("§7Your Power: §f" + fmt(pd.getPower()));
                    sender.sendMessage("§7Guild Power: §fN/A");
                    return true;
                }
                Guild g = plugin.storage().getGuild(pd.getGuildId());
                if (g == null) {
                    sender.sendMessage("§7Your Power: §f" + fmt(pd.getPower()));
                    sender.sendMessage("§7Guild Power: §fN/A");
                    return true;
                }
                sender.sendMessage("§7Your Power: §f" + fmt(pd.getPower()));
                sender.sendMessage("§7Guild Power: §f" + fmt(guildPower(g)));
                return true;
            }

            if (!sender.hasPermission("magicera.admin")) {
                sender.sendMessage("§cNo permission.");
                return true;
            }
            if ((!args[1].equalsIgnoreCase("add") && !args[1].equalsIgnoreCase("remove")) || args.length < 3) {
                sender.sendMessage("§cUsage: /guild power <add|remove> <amount> [player]");
                return true;
            }

            double amount = parseMoney(args[2]);
            UUID targetId;
            if (args.length >= 4) {
                targetId = resolvePlayerUuid(args[3]);
                if (targetId == null) {
                    sender.sendMessage("§cUnknown player: " + args[3]);
                    return true;
                }
            } else if (sender instanceof Player player) {
                targetId = player.getUniqueId();
            } else {
                sender.sendMessage("§cConsole must specify a player.");
                return true;
            }

            if (amount <= 0.0) {
                sender.sendMessage("§cAmount must be > 0.");
                return true;
            }

            PlayerData tpd = plugin.storage().getOrCreatePlayer(targetId);
            double delta = args[1].equalsIgnoreCase("remove") ? -amount : amount;

            double newPower = Math.max(0.0, Math.min(plugin.guildPower().playerPowerMax(), tpd.getPower() + delta));
            tpd.setPower(newPower);

            if (tpd.getGuildId() != null) {
                Guild tg = plugin.storage().getGuild(tpd.getGuildId());
                if (tg != null) plugin.guildPower().handlePowerThresholds(tg);
            }

            plugin.storage().save();
            sender.sendMessage("§aPower updated for §f" + safeName(targetId) + "§a: §f" + fmt(tpd.getPower()));
            return true;
        }

        // -------------------------
        // FRIENDLY FIRE (guild-wide toggle)
        // -------------------------
        if (sub.equals("friendlyfire")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage("§cPlayers only.");
                return true;
            }
            if (args.length < 2 || (!args[1].equalsIgnoreCase("on") && !args[1].equalsIgnoreCase("off"))) {
                sender.sendMessage("§cUsage: /guild friendlyfire <on|off>");
                return true;
            }

            PlayerData pd = plugin.storage().getOrCreatePlayer(player.getUniqueId());
            Guild g = pd.getGuildId() == null ? null : plugin.storage().getGuild(pd.getGuildId());
            if (g == null) {
                sender.sendMessage("§cYou are not in a guild.");
                return true;
            }

            GuildRole role = g.getMembers().get(player.getUniqueId());
            if (role != GuildRole.MASTER) {
                sender.sendMessage("§cOnly the guild master can toggle friendly fire.");
                return true;
            }

            boolean enabled = args[1].equalsIgnoreCase("on");
            g.setFriendlyFireEnabled(enabled);
            plugin.storage().save();
            sender.sendMessage(enabled
                    ? "§eGuild friendly fire enabled. Guild members can now damage each other."
                    : "§aGuild friendly fire disabled. Guild members can no longer damage each other.");
            return true;
        }

        // -------------------------
        // ALLY FIRE (guild-wide toggle)
        // -------------------------
        if (sub.equals("allyfire")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage("§cPlayers only.");
                return true;
            }
            if (args.length < 2 || (!args[1].equalsIgnoreCase("on") && !args[1].equalsIgnoreCase("off"))) {
                sender.sendMessage("§cUsage: /guild allyfire <on|off>");
                return true;
            }

            PlayerData pd = plugin.storage().getOrCreatePlayer(player.getUniqueId());
            Guild g = pd.getGuildId() == null ? null : plugin.storage().getGuild(pd.getGuildId());
            if (g == null) {
                sender.sendMessage("§cYou are not in a guild.");
                return true;
            }

            GuildRole role = g.getMembers().get(player.getUniqueId());
            if (role != GuildRole.MASTER) {
                sender.sendMessage("§cOnly the guild master can toggle ally fire.");
                return true;
            }

            boolean enabled = args[1].equalsIgnoreCase("on");
            g.setAllyFireEnabled(enabled);
            plugin.storage().save();
            sender.sendMessage(enabled
                    ? "§eAlly fire enabled for your guild. Allied PvP/spells require both guilds to have ally fire on."
                    : "§aAlly fire disabled for your guild. Allied PvP/spells are now blocked.");
            return true;
        }

        // -------------------------
        // ALLY / UNALLY / WAR / TRUCE
        // -------------------------
        if (sub.equals("unally")) {
            if (!(sender instanceof Player player)) return true;
            if (args.length < 2) {
                sender.sendMessage("§cUsage: /guild unally <player|guild>");
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
            if (isOpposingSideConflict(actor, target)) {
                sender.sendMessage("§cYou cannot alter this treaty while your guilds are on opposing war sides.");
                return true;
            }
            actor.getAllies().remove(target.getId());
            target.getAllies().remove(actor.getId());
            plugin.storage().save();
            sender.sendMessage("§eAlliance ended with " + target.getName());
            return true;
        }

        if (sub.equals("truce")) {
            return handleTruceCommand(sender, args);
        }

        if (sub.equals("ally")) {
            return handleAllyCommand(sender, args);
        }

        if (sub.equals("war")) {
            return handleWarCommand(sender, args);
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
                sender.sendMessage("§cUsage: /guild tax <0-5>");
                return true;
            }

            int pct;
            try {
                pct = Integer.parseInt(args[1]);
            } catch (NumberFormatException e) {
                sender.sendMessage("§cTax must be a number 0-5.");
                return true;
            }
            if (pct < 0 || pct > 5) {
                sender.sendMessage("§cTax must be between 0 and 5.");
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
        // DESC
        // -------------------------
        if (sub.equals("desc")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage("§cPlayers only.");
                return true;
            }
            if (args.length < 2) {
                sender.sendMessage("§cUsage: /guild desc <description>");
                return true;
            }
            PlayerData pd = plugin.storage().getOrCreatePlayer(player.getUniqueId());
            Guild g = pd.getGuildId() == null ? null : plugin.storage().getGuild(pd.getGuildId());
            if (g == null) {
                sender.sendMessage("§cYou are not in a guild.");
                return true;
            }
            if (g.getMembers().get(player.getUniqueId()) != GuildRole.MASTER) {
                sender.sendMessage("§cOnly the Guild Master can set the guild description.");
                return true;
            }
            String desc = joinArgs(args, 1).trim();
            if (desc.length() > 80) desc = desc.substring(0, 80);
            g.setDescription(Text.color(desc));
            g.addLogEntry("Description updated by " + player.getName());
            plugin.storage().save();
            sender.sendMessage("§aGuild description updated.");
            return true;
        }

        // -------------------------
        // RENAME
        // -------------------------
        if (sub.equals("rename")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage("§cPlayers only.");
                return true;
            }

            PlayerData pd = plugin.storage().getOrCreatePlayer(player.getUniqueId());
            Guild g = pd.getGuildId() == null ? null : plugin.storage().getGuild(pd.getGuildId());
            if (g == null) {
                sender.sendMessage("§cYou are not in a guild.");
                return true;
            }
            if (g.getMembers().get(player.getUniqueId()) != GuildRole.MASTER) {
                sender.sendMessage("§cOnly the Guild Master can rename the guild.");
                return true;
            }
            if (args.length < 2) {
                sender.sendMessage("§cUsage: /guild rename \"<name>\"");
                return true;
            }

            String rawName = parseGuildNameArg(args, 1);
            if (rawName == null) {
                sender.sendMessage("§cUsage: /guild rename \"<name>\"");
                return true;
            }
            if (!isBoldOnlyName(rawName)) {
                sender.sendMessage("§cGuild names may only use bold formatting (&l). No underline/italic/strikethrough/magic/reset.");
                return true;
            }

            String newId = Text.normalizeId(rawName);
            if (newId == null || newId.isBlank()) {
                sender.sendMessage("§cInvalid guild name.");
                return true;
            }
            if (!newId.equals(g.getId()) && plugin.storage().guildExists(newId)) {
                sender.sendMessage("§cThat guild name is already taken.");
                return true;
            }

            String oldName = g.getName();
            g.setName(rawName);
            g.addLogEntry("Guild renamed from " + oldName + " to " + rawName + " by " + player.getName());
            plugin.storage().save();
            Bukkit.broadcastMessage("§7[§bMagic Era§7] " + oldName + " §fhas been renamed to " + rawName + "§f.");
            return true;
        }

        // -------------------------
        // INFO
        // -------------------------
        if (sub.equals("info")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage("§cPlayers only.");
                return true;
            }
            PlayerData pd = plugin.storage().getOrCreatePlayer(player.getUniqueId());
            Guild g = pd.getGuildId() == null ? null : plugin.storage().getGuild(pd.getGuildId());
            if (g == null) {
                sender.sendMessage("§cYou are not in a guild.");
                return true;
            }
            sendGuildInfo(player, g);
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

            Player target = findOnlinePlayerIgnoreCase(args[1]);
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

            int maxMembers = plugin.territoryConfig().getInt("maxGuildMembers", 45);
            if (guild.getMembers().size() >= maxMembers) {
                sender.sendMessage("§cYour guild is at the member cap (§f" + maxMembers + "§c).");
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

            com.magicera.guilds.guilds.InviteManager.Invite inv = plugin.inviteManager().getInvite(player.getUniqueId());
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

            int maxMembers = plugin.territoryConfig().getInt("maxGuildMembers", 45);
            if (guild.getMembers().size() >= maxMembers) {
                sender.sendMessage("§cThis guild is at the member cap (§f" + maxMembers + "§c).");
                plugin.inviteManager().clearInvite(player);
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
            if (args.length < 2) {
                sender.sendMessage("§cThis will disband your guild permanently.");
                sender.sendMessage("§7Vault money/items will be lost, and all members become guildless.");
                sender.sendMessage("§7Type §c/guild disband confirm §7to proceed.");
                return true;
            }
            if (!args[1].equalsIgnoreCase("confirm")) {
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

            cleanupGuildBeforeDisband(g);
            plugin.storage().deleteGuild(g.getId());
            plugin.storage().save();

            Bukkit.broadcastMessage("§7[§aGuild§7] " + g.getName() + " §fhas disbanded...");
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
                    "help", "menu", "chat", "home", "sethome", "claimland", "claimhall", "movehall", "claimtoggle", "unclaim", "ally", "unally", "war", "truce",
                    "create", "invite", "accept", "leave", "kick", "promote", "newmaster", "title", "deny", "disband", "impeach",
                    "bank", "deposit", "withdraw", "tax", "desc", "rename", "info", "power", "friendlyfire", "allyfire", "?", "1", "2"
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
        if ((sub.equals("invite") || sub.equals("kick") || sub.equals("newmaster") || sub.equals("promote") || sub.equals("title")
                || sub.equals("ally") || sub.equals("unally") || sub.equals("war") || sub.equals("truce")) && args.length == 2) {
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

        if ((sub.equals("war") || sub.equals("ally") || sub.equals("truce")) && args.length == 3 && args[1].equalsIgnoreCase("accept")) {
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

        if (sub.equals("unclaim") && args.length == 2) {
            return filterPrefix(List.of("all"), input);
        }

        if ((sub.equals("friendlyfire") || sub.equals("allyfire")) && args.length == 2) {
            return filterPrefix(List.of("on", "off"), input);
        }

        if (sub.equals("power") && args.length == 2 && sender.hasPermission("magicera.admin")) {
            return filterPrefix(List.of("add", "remove"), input);
        }

        if (sub.equals("power") && args.length == 3 && sender.hasPermission("magicera.admin")) {
            return filterPrefix(List.of("1", "2", "5", "10"), input);
        }

        if (sub.equals("power") && args.length == 4 && sender.hasPermission("magicera.admin")) {
            return filterPrefix(onlinePlayerNames(), input);
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
            return filterPrefix(List.of("0", "1", "2", "3", "4", "5"), input);
        }

        return Collections.emptyList();
    }

    private List<String> onlinePlayerNames() {
        return Bukkit.getOnlinePlayers().stream().map(Player::getName).sorted().collect(Collectors.toList());
    }

    private Player findOnlinePlayerIgnoreCase(String name) {
        if (name == null || name.isBlank()) return null;
        Player exact = Bukkit.getPlayerExact(name);
        if (exact != null) return exact;
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.getName().equalsIgnoreCase(name)) return p;
        }
        return null;
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
        Player player = sender instanceof Player p ? p : null;
        PlayerData pd = player == null ? null : plugin.storage().getOrCreatePlayer(player.getUniqueId());
        Guild g = (pd == null || pd.getGuildId() == null) ? null : plugin.storage().getGuild(pd.getGuildId());
        GuildRole role = (g == null || player == null) ? null : g.getMembers().get(player.getUniqueId());

        if (page <= 1) {
            sender.sendMessage("§8§m--------------------------------");
            sender.sendMessage("§7[§aGuild§7] §fGuild Commands §8(Page 1/2)");
            sender.sendMessage("§8§m--------------------------------");
            sender.sendMessage("§7/guild §8(opens menu)");
            sender.sendMessage("§7/guild menu");
            sender.sendMessage("§7/guild ? <page>");
            sender.sendMessage("§7/guild help §8or /guild <1|2>");
            if (g == null) sender.sendMessage("§7/guild create \"<name>\" <displayName>");
            if (g != null) {
                sender.sendMessage("§7/guild info");
                sender.sendMessage("§7/guild chat");
                sender.sendMessage("§7/guild home");
                sender.sendMessage("§7/guild leave");
            }
            if (role == GuildRole.MASTER || role == GuildRole.OFFICER) {
                sender.sendMessage("§7/guild invite <player>");
                sender.sendMessage("§7/guild kick <player>");
                sender.sendMessage("§7/guild promote <player>");
                sender.sendMessage("§7/guild title <player> <text|clear>");
            }
            if (role == GuildRole.MASTER) {
                sender.sendMessage("§7/guild newmaster <player>");
                sender.sendMessage("§7/guild desc <description>");
                sender.sendMessage("§7/guild rename \"<name>\"");
                sender.sendMessage("§7/guild disband §8(then confirm)");
            }
            return;
        }

        sender.sendMessage("§8§m--------------------------------");
        sender.sendMessage("§7[§aGuild§7] §fGuild Commands §8(Page 2/2)");
        sender.sendMessage("§8§m--------------------------------");
        if (g != null) {
            sender.sendMessage("§7/guild bank");
            sender.sendMessage("§7/guild deposit <amount>");
            sender.sendMessage("§7/guild withdraw <amount>");
            sender.sendMessage("§7/guild claimland");
            sender.sendMessage("§7/guild claimhall");
            sender.sendMessage("§7/guild movehall");
            sender.sendMessage("§7/guild unclaim §8(or /guild unclaim all)");
            sender.sendMessage("§7/guild power");
            sender.sendMessage("§7/guild friendlyfire <on|off>");
            sender.sendMessage("§7/guild allyfire <on|off>");
            sender.sendMessage("§7/guild ally <player|guild>");
            sender.sendMessage("§7/guild ally accept <guild>");
            sender.sendMessage("§7/guild unally <player|guild>");
            sender.sendMessage("§7/guild war <player|guild>");
            sender.sendMessage("§7/guild war accept <guild>");
            sender.sendMessage("§7/guild truce <player|guild>");
            sender.sendMessage("§7/guild truce accept <guild>");
            sender.sendMessage("§7/guild impeach §8(or /guild impeach <remove|keep>)");
        }
        if (role == GuildRole.MASTER) {
            sender.sendMessage("§7/guild tax <0-5>");
            sender.sendMessage("§7/guild sethome");
            sender.sendMessage("§7/guild claimtoggle");
        }
        if (sender.hasPermission("magicera.admin")) {
            sender.sendMessage("§7/guild reload | add | adminadd | adminkick | forcetax | power");
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
        Player online = findOnlinePlayerIgnoreCase(token);
        if (online != null) {
            Guild g = guildOf(online.getUniqueId());
            if (g != null) return g;
        }
        return plugin.storage().getGuild(Text.normalizeId(token));
    }

    private int maxClaims(Guild guild) {
        return plugin.guildPower().allowedChunks(guild);
    }

    private Set<String> hallArea(String world, int centerX, int centerZ) {
        Set<String> hall = new HashSet<>();
        for (int x = centerX - 1; x <= centerX + 1; x++) {
            for (int z = centerZ - 1; z <= centerZ + 1; z++) {
                hall.add(Guild.chunkKey(world, x, z));
            }
        }
        return hall;
    }

    private boolean isHallAreaAvailable(Guild actor, Set<String> targetHall, Set<String> oldHall) {
        for (String key : targetHall) {
            for (Guild other : plugin.storage().allGuilds()) {
                if (other.getId().equals(actor.getId())) continue;
                if (oldHall.contains(key)) continue;
                if (other.getClaimedChunks().contains(key)) return false;
            }
        }
        return true;
    }

    private void cleanupGuildBeforeDisband(Guild guild) {
        guild.clearHall();
        guild.clearAllClaims();
        guild.getWarningLastSent().clear();
        guild.getWarningSentWarSession().clear();

        String guildId = guild.getId();
        for (Guild other : plugin.storage().allGuilds()) {
            if (other.getId().equals(guildId)) continue;
            other.getAllies().remove(guildId);
            other.getEnemies().remove(guildId);
            other.getPendingAllyRequests().remove(guildId);
            other.getPendingWarRequests().remove(guildId);
            other.getPendingTruceRequests().remove(guildId);
        }
    }

    private void sendGuildInfo(Player viewer, Guild g) {
        viewer.sendMessage("§8§m--------------------------------");
        viewer.sendMessage("§7[§aGuild Info§7] §r" + Text.color(g.getName()));
        viewer.sendMessage("§7Description: §f" + (g.getDescription().isEmpty() ? "None" : g.getDescription()));
        viewer.sendMessage("§7Founded: §f" + new java.text.SimpleDateFormat("yyyy-MM-dd").format(new java.util.Date(g.getFoundedAtEpochMs())));

        Map<GuildRole, List<String>> byRole = new EnumMap<>(GuildRole.class);
        for (GuildRole r : GuildRole.values()) byRole.put(r, new ArrayList<>());
        for (Map.Entry<UUID, GuildRole> e : g.getMembers().entrySet()) {
            byRole.get(e.getValue()).add(safeName(e.getKey()));
        }
        for (GuildRole r : GuildRole.values()) {
            List<String> names = byRole.get(r);
            if (names.isEmpty()) continue;
            viewer.sendMessage("§7" + r.name() + ": §f" + String.join(", ", names));
        }

        double power = plugin.guildPower().guildPower(g);
        int maxPower = plugin.guildPower().maxGuildPower(g);
        int claimsUsed = g.getClaimedChunks().size();
        int claimsAllowed = plugin.guildPower().allowedChunks(g);

        String hallStatus = "None";
        if (g.hasHall()) {
            int atRisk = plugin.guildPower().hallAtRiskThreshold(g);
            int vulnerable = plugin.guildPower().hallVulnerableThreshold(g);
            if (power <= vulnerable) hallStatus = "Vulnerable";
            else if (power <= atRisk) hallStatus = "At Risk";
            else hallStatus = "Protected";
        }

        viewer.sendMessage("§7Bank: §f$" + fmt(g.getBankBalance()));
        viewer.sendMessage("§7Tax: §f" + g.getTaxPercent() + "%");
        viewer.sendMessage("§7Next tax: §f" + new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm").format(new java.util.Date(plugin.nextGuildTaxEpochMs())));
        viewer.sendMessage("§7Allies: §f" + formatGuildList(g.getAllies()));
        viewer.sendMessage("§7Enemies: §f" + formatGuildList(g.getEnemies()));
        viewer.sendMessage("§7Claims: §f" + claimsUsed + "§7/§f" + claimsAllowed);
        viewer.sendMessage("§7Hall Status: §f" + hallStatus);
        viewer.sendMessage("§7Guild Power: §f" + fmt(power) + "§7/§f" + maxPower);
        viewer.sendMessage("§8§m--------------------------------");
    }

    private String formatGuildList(Set<String> ids) {
        if (ids == null || ids.isEmpty()) return "None";
        List<String> names = new ArrayList<>();
        for (String id : ids) {
            Guild guild = plugin.storage().getGuild(id);
            names.add(guild == null ? id : Text.stripColors(guild.getName()));
        }
        return String.join(", ", names);
    }

    private boolean handleAllyCommand(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) return true;

        Guild actor = guildOf(player.getUniqueId());
        if (actor == null) {
            sender.sendMessage("§cYou are not in a guild.");
            return true;
        }
        if (actor.getMembers().get(player.getUniqueId()) != GuildRole.MASTER) {
            sender.sendMessage("§cOnly the guild master can use this command.");
            return true;
        }
        if (!actor.hasHall()) {
            sender.sendMessage("§7[§aGuild§7] §cYour guild must claim a Guild Hall before forming alliances.");
            return true;
        }

        if (args.length >= 3 && args[1].equalsIgnoreCase("accept")) {
            Guild requester = resolveGuildTarget(args[2]);
            if (requester == null || requester.getId().equals(actor.getId())) {
                sender.sendMessage("§cInvalid target guild.");
                return true;
            }
            if (!actor.getPendingAllyRequests().remove(requester.getId())) {
                sender.sendMessage("§cNo pending ally request from that guild.");
                return true;
            }
            if (!requester.hasHall()) {
                sender.sendMessage("§7[§aGuild§7] §cThat guild must claim a Guild Hall before forming alliances.");
                return true;
            }
            if (isOpposingSideConflict(actor, requester)) {
                sender.sendMessage("§cYou cannot form an alliance while your guilds are on opposing war sides.");
                return true;
            }

            requester.getPendingAllyRequests().remove(actor.getId());
            actor.getAllies().add(requester.getId());
            requester.getAllies().add(actor.getId());
            actor.getEnemies().remove(requester.getId());
            requester.getEnemies().remove(actor.getId());
            actor.addLogEntry("Alliance formed with " + requester.getName());
            requester.addLogEntry("Alliance formed with " + actor.getName());
            plugin.storage().save();

            Bukkit.broadcastMessage("§7[§bMagic Era§7] "
                    + Text.color(actor.getName())
                    + " §fand "
                    + Text.color(requester.getName())
                    + " §fhave formed an alliance!");
            return true;
        }

        if (args.length < 2) {
            sender.sendMessage("§cUsage: /guild ally <player|guild> or /guild ally accept <guild>");
            return true;
        }

        Guild target = resolveGuildTarget(args[1]);
        if (target == null || target.getId().equals(actor.getId())) {
            sender.sendMessage("§cInvalid target guild.");
            return true;
        }
        if (!target.hasHall()) {
            sender.sendMessage("§7[§aGuild§7] §cThat guild must claim a Guild Hall before forming alliances.");
            return true;
        }

        if (actor.getAllies().contains(target.getId())) {
            sender.sendMessage("§cYour guild is already allied with that guild.");
            return true;
        }
        if (isOpposingSideConflict(actor, target)) {
            sender.sendMessage("§cYou cannot request an alliance with a guild on an opposing war side.");
            return true;
        }

        if (!canAlly(actor, target)) {
            sender.sendMessage("§cYour guild type cannot ally with that guild type.");
            return true;
        }

        long now = System.currentTimeMillis();
        long cooldownUntil = actor.getAllyRequestCooldowns().getOrDefault(target.getId(), 0L);
        if (cooldownUntil > now) {
            sender.sendMessage("§cYou can request an alliance with " + target.getName() + " again in §f" + formatDuration(cooldownUntil - now) + "§c.");
            return true;
        }

        target.getPendingAllyRequests().add(actor.getId());
        actor.getAllyRequestCooldowns().put(target.getId(), now + (30L * 60L * 1000L));
        plugin.storage().save();

        sender.sendMessage("§7[§aGuild§7] §fYou have requested an alliance with " + Text.color(target.getName()) + "§f!");

        notifyGuildMaster(target, "§7[§bMagic Era§7] §e" + actor.getName() + " §fhas requested an alliance.");
        notifyGuildMaster(target, "§7Use §a/guild ally accept " + actor.getId() + " §7to accept.");

        return true;
    }

    private boolean handleWarCommand(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) return true;

        Guild actor = guildOf(player.getUniqueId());
        if (actor == null) {
            sender.sendMessage("§cYou are not in a guild.");
            return true;
        }
        if (actor.getMembers().get(player.getUniqueId()) != GuildRole.MASTER) {
            sender.sendMessage("§cOnly the guild master can use this command.");
            return true;
        }
        if (!actor.hasHall()) {
            sender.sendMessage("§7[§aGuild§7] §cYour guild must claim a Guild Hall before starting war.");
            return true;
        }

        if (args.length >= 3 && args[1].equalsIgnoreCase("accept")) {
            Guild requester = resolveGuildTarget(args[2]);
            if (requester == null || requester.getId().equals(actor.getId())) {
                sender.sendMessage("§cInvalid target guild.");
                return true;
            }
            if (!actor.getPendingWarRequests().contains(requester.getId())) {
                sender.sendMessage("§cNo pending war request from that guild.");
                return true;
            }
            if (!requester.hasHall()) {
                sender.sendMessage("§7[§aGuild§7] §cWar cannot begin. The attacking guild has no Guild Hall.");
                return true;
            }
            if (requester.getClaimedChunks().isEmpty()) {
                sender.sendMessage("§7[§aGuild§7] §cWar cannot begin. The attacking guild has no claimed land.");
                return true;
            }
            if (actor.getClaimedChunks().isEmpty()) {
                sender.sendMessage("§7[§aGuild§7] §cWar cannot begin. Your guild has no claimed land.");
                return true;
            }

            actor.getPendingWarRequests().remove(requester.getId());
            declareWar(requester, actor);
            plugin.storage().save();
            return true;
        }

        if (args.length < 2) {
            sender.sendMessage("§cUsage: /guild war <player|guild> or /guild war accept <guild>");
            return true;
        }

        Guild target = resolveGuildTarget(args[1]);
        if (target == null || target.getId().equals(actor.getId())) {
            sender.sendMessage("§cInvalid target guild.");
            return true;
        }

        if (actor.getEnemies().contains(target.getId())) {
            sender.sendMessage("§cYour guild is already at war with that guild.");
            return true;
        }
        if (!target.hasHall()) {
            sender.sendMessage("§7[§aGuild§7] §cWar cannot begin. Target guild has no Guild Hall.");
            return true;
        }
        if (actor.getClaimedChunks().isEmpty()) {
            sender.sendMessage("§7[§aGuild§7] §cWar cannot begin. Your guild has no claimed land.");
            return true;
        }
        if (target.getClaimedChunks().isEmpty()) {
            sender.sendMessage("§7[§aGuild§7] §cWar cannot begin. Target guild has no claimed land.");
            return true;
        }

        if (actor.getAlignment() == GuildAlignment.HONORABLE) {
            long now = System.currentTimeMillis();
            long cooldownUntil = actor.getWarRequestCooldowns().getOrDefault(target.getId(), 0L);
            if (cooldownUntil > now) {
                sender.sendMessage("§cYou can request war with " + target.getName() + " again in §f" + formatDuration(cooldownUntil - now) + "§c.");
                return true;
            }

            target.getPendingWarRequests().add(actor.getId());
            actor.getWarRequestCooldowns().put(target.getId(), now + (30L * 60L * 1000L));
            plugin.storage().save();
            sender.sendMessage("§7[§aGuild§7] §fYou have formally requested war against " + Text.color(target.getName()) + "§f!");

            notifyGuildMaster(target, "§7[§bMagic Era§7] §c" + actor.getName() + " §fhas requested war.");
            notifyGuildMaster(target, "§7Use §a/guild war accept " + actor.getId() + " §7to accept.");
            return true;
        }

        declareWar(actor, target);
        plugin.storage().save();
        return true;
    }

    private boolean handleTruceCommand(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) return true;

        Guild actor = guildOf(player.getUniqueId());
        if (actor == null) {
            sender.sendMessage("§cYou are not in a guild.");
            return true;
        }
        if (actor.getMembers().get(player.getUniqueId()) != GuildRole.MASTER) {
            sender.sendMessage("§cOnly the guild master can use this command.");
            return true;
        }

        if (args.length >= 3 && args[1].equalsIgnoreCase("accept")) {
            Guild requester = resolveGuildTarget(args[2]);
            if (requester == null || requester.getId().equals(actor.getId())) {
                sender.sendMessage("§cInvalid target guild.");
                return true;
            }
            if (!actor.getPendingTruceRequests().remove(requester.getId())) {
                sender.sendMessage("§cNo pending truce request from that guild.");
                return true;
            }
            if (hasCoalitionWarConflict(requester, actor)) {
                sender.sendMessage("§cYou cannot sign this truce while allied guilds remain in active conflict.");
                return true;
            }

            requester.getPendingTruceRequests().remove(actor.getId());
            endWar(requester, actor);
            plugin.storage().save();
            Bukkit.broadcastMessage("§7[§bMagic Era§7] "
                    + Text.color(actor.getName())
                    + " §fand "
                    + Text.color(requester.getName())
                    + " §fhave ended their war with each other.");
            return true;
        }

        if (args.length < 2) {
            sender.sendMessage("§cUsage: /guild truce <player|guild> or /guild truce accept <guild>");
            return true;
        }

        Guild target = resolveGuildTarget(args[1]);
        if (target == null || target.getId().equals(actor.getId())) {
            sender.sendMessage("§cInvalid target guild.");
            return true;
        }
        if (!actor.getEnemies().contains(target.getId())) {
            sender.sendMessage("§cYour guild is not at war with that guild.");
            return true;
        }
        if (hasCoalitionWarConflict(actor, target)) {
            sender.sendMessage("§cYou cannot request a truce while allied guilds remain in active conflict.");
            return true;
        }

        target.getPendingTruceRequests().add(actor.getId());
        plugin.storage().save();
        sender.sendMessage("§7[§aGuild§7] §fYou have requested a truce with " + Text.color(target.getName()) + "§f!");
        notifyGuildMaster(target, "§7[§bMagic Era§7] §e" + actor.getName() + " §fhas requested a truce.");
        notifyGuildMaster(target, "§7Use §a/guild truce accept " + actor.getId() + " §7to accept.");
        return true;
    }

    private void declareWar(Guild actor, Guild target) {
        actor.getEnemies().add(target.getId());
        target.getEnemies().add(actor.getId());
        actor.getAllies().remove(target.getId());
        target.getAllies().remove(actor.getId());
        actor.setInWar(true);
        target.setInWar(true);

        long warSessionId = System.currentTimeMillis();
        actor.setWarSessionId(warSessionId);
        target.setWarSessionId(warSessionId);
        actor.getWarningSentWarSession().clear();
        target.getWarningSentWarSession().clear();
        actor.getWarningLastSent().remove("wearinessCooldown");
        actor.getWarningLastSent().remove("hallCooldown");
        target.getWarningLastSent().remove("wearinessCooldown");
        target.getWarningLastSent().remove("hallCooldown");

        long warEnd = System.currentTimeMillis() + (24L * 60L * 60L * 1000L);
        actor.setWarEndsAtEpochMs(warEnd);
        target.setWarEndsAtEpochMs(warEnd);

        Set<String> sideA = new HashSet<>();
        Set<String> sideB = new HashSet<>();
        sideA.add(actor.getId());
        sideB.add(target.getId());

        callAlliesToWar(actor, target, actor, warEnd, warSessionId, sideA, sideB);
        callAlliesToWar(target, actor, actor, warEnd, warSessionId, sideB, sideA);

        notifyGuildMaster(target, "§7[§bMagic Era§7] §cYour guild is now at war with " + Text.color(actor.getName()) + "§c.");

        Bukkit.broadcastMessage("§7[§bMagic Era§7] "
                + Text.color(actor.getName())
                + " §fhas declared war against "
                + Text.color(target.getName())
                + "§f!");
    }

    private void callAlliesToWar(Guild participant,
                                 Guild opposingAnchor,
                                 Guild declarationLeader,
                                 long warEnd,
                                 long warSessionId,
                                 Set<String> joiningSide,
                                 Set<String> opposingSide) {
        for (String allyId : new HashSet<>(participant.getAllies())) {
            Guild ally = plugin.storage().getGuild(allyId);
            if (ally == null || ally.getId().equals(opposingAnchor.getId())) continue;

            if (hasNonHostileTreatyWithSide(ally, opposingSide)) {
                notifyGuild(ally,
                        "§7[§aGuild§7] §fYou have an alliance with both sides. Your guild is Neutral in the war between "
                                + Text.color(declarationLeader.getName())
                                + "§f and "
                                + Text.color(opposingAnchor.getName())
                                + "§f.");
                notifyGuild(participant,
                        "§7[§aGuild§7] "
                                + Text.color(ally.getName())
                                + " §fdid not join the war because of an alliance with "
                                + Text.color(opposingAnchor.getName())
                                + "§f.");
                continue;
            }

            boolean newJoin = joiningSide.add(ally.getId());
            ally.setInWar(true);
            ally.setWarEndsAtEpochMs(warEnd);
            ally.setWarSessionId(warSessionId);
            ally.getWarningSentWarSession().clear();
            ally.getWarningLastSent().remove("wearinessCooldown");
            ally.getWarningLastSent().remove("hallCooldown");

            for (String enemyId : opposingSide) {
                Guild enemy = plugin.storage().getGuild(enemyId);
                if (enemy == null || enemy.getId().equals(ally.getId())) continue;
                ally.getEnemies().add(enemy.getId());
                enemy.getEnemies().add(ally.getId());
            }

            if (newJoin) {
                announceAllyWarEntry(ally, participant);
            }
        }
    }

    private boolean hasNonHostileTreatyWithSide(Guild guild, Set<String> opposingSide) {
        for (String opposingId : opposingSide) {
            if (guild.getId().equals(opposingId)) return true;
            if (guild.getAllies().contains(opposingId)) return true;
            Guild opposing = plugin.storage().getGuild(opposingId);
            if (opposing != null && opposing.getAllies().contains(guild.getId())) return true;
        }
        return false;
    }

    private void announceAllyWarEntry(Guild entrant, Guild sideLeader) {
        Bukkit.broadcastMessage("§7[§aGuild§7] "
                + Text.color(entrant.getName())
                + " §fhas joined the war on the side of "
                + Text.color(sideLeader.getName())
                + "§f!");
    }

    private void notifyGuild(Guild guild, String message) {
        if (guild == null || message == null || message.isBlank()) return;
        for (UUID memberId : guild.getMembers().keySet()) {
            Player online = Bukkit.getPlayer(memberId);
            if (online != null) {
                online.sendMessage(message);
                continue;
            }
            PlayerData data = plugin.storage().getOrCreatePlayer(memberId);
            data.getPendingGuildMessages().add(message);
        }
    }

    private boolean isOpposingSideConflict(Guild a, Guild b) {
        if (a == null || b == null) return false;
        if (a.getEnemies().contains(b.getId()) || b.getEnemies().contains(a.getId())) return true;
        for (String allyId : a.getAllies()) {
            Guild ally = plugin.storage().getGuild(allyId);
            if (ally != null && (ally.getEnemies().contains(b.getId()) || b.getEnemies().contains(ally.getId()))) {
                return true;
            }
        }
        for (String allyId : b.getAllies()) {
            Guild ally = plugin.storage().getGuild(allyId);
            if (ally != null && (ally.getEnemies().contains(a.getId()) || a.getEnemies().contains(ally.getId()))) {
                return true;
            }
        }
        return false;
    }

    private boolean hasCoalitionWarConflict(Guild a, Guild b) {
        for (String allyId : a.getAllies()) {
            Guild ally = plugin.storage().getGuild(allyId);
            if (ally != null && (ally.getEnemies().contains(b.getId()) || b.getEnemies().contains(ally.getId()))) {
                return true;
            }
        }
        for (String allyId : b.getAllies()) {
            Guild ally = plugin.storage().getGuild(allyId);
            if (ally != null && (ally.getEnemies().contains(a.getId()) || a.getEnemies().contains(ally.getId()))) {
                return true;
            }
        }
        return false;
    }

    private void endWar(Guild a, Guild b) {
        a.getEnemies().remove(b.getId());
        b.getEnemies().remove(a.getId());
        if (a.getEnemies().isEmpty()) {
            a.setInWar(false);
            a.setWarEndsAtEpochMs(null);
            a.setWarSessionId(null);
            a.getWarningSentWarSession().clear();
        }
        if (b.getEnemies().isEmpty()) {
            b.setInWar(false);
            b.setWarEndsAtEpochMs(null);
            b.setWarSessionId(null);
            b.getWarningSentWarSession().clear();
        }
    }

    private boolean canAlly(Guild a, Guild b) {
        if (a.getAlignment() == GuildAlignment.NEUTRAL) return true;
        if (a.getAlignment() == GuildAlignment.DARK) {
            return b.getAlignment() == GuildAlignment.DARK || b.getAlignment() == GuildAlignment.NEUTRAL;
        }
        return b.getAlignment() == GuildAlignment.HONORABLE || b.getAlignment() == GuildAlignment.NEUTRAL;
    }

    private UUID guildMasterId(Guild guild) {
        if (guild == null) return null;
        for (Map.Entry<UUID, GuildRole> e : guild.getMembers().entrySet()) {
            if (e.getValue() == GuildRole.MASTER) {
                return e.getKey();
            }
        }
        return null;
    }

    private void notifyGuildMaster(Guild guild, String message) {
        UUID masterId = guildMasterId(guild);
        if (masterId == null || message == null || message.isBlank()) return;

        Player online = Bukkit.getPlayer(masterId);
        if (online != null) {
            online.sendMessage(message);
            return;
        }

        PlayerData masterData = plugin.storage().getOrCreatePlayer(masterId);
        masterData.getPendingGuildMessages().add(message);
    }

    private String formatDuration(long millis) {
        long totalSeconds = Math.max(1L, millis / 1000L);
        long minutes = totalSeconds / 60L;
        long seconds = totalSeconds % 60L;
        if (minutes <= 0L) return seconds + "s";
        if (seconds == 0L) return minutes + "m";
        return minutes + "m " + seconds + "s";
    }

    private double guildPower(Guild g) {
        double power = 0.0;
        for (UUID id : g.getMembers().keySet()) {
            power += plugin.storage().getOrCreatePlayer(id).getPower();
        }
        return power;
    }

    private static final class ParsedCreate {
        final String guildName;
        final String displayName;

        ParsedCreate(String guildName, String displayName) {
            this.guildName = guildName;
            this.displayName = displayName;
        }
    }

    private String parseGuildNameArg(String[] args, int startIndex) {
        if (args.length <= startIndex) return null;
        String joined = String.join(" ", Arrays.copyOfRange(args, startIndex, args.length)).trim();
        if (joined.isEmpty()) return null;

        if (joined.startsWith("\"")) {
            int secondQuote = joined.indexOf('"', 1);
            if (secondQuote <= 1) return null;
            return joined.substring(1, secondQuote).trim();
        }

        return args[startIndex].trim();
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
        } catch (IllegalArgumentException ignored) {
        }

        Player online = findOnlinePlayerIgnoreCase(input);
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
