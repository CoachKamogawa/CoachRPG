package com.magicera.guilds.commands;

import com.magicera.guilds.MagicEraGuildsPlugin;
import com.magicera.guilds.data.Guild;
import com.magicera.guilds.data.GuildAlignment;
import com.magicera.guilds.data.GuildRole;
import com.magicera.guilds.data.PlayerData;
import com.magicera.guilds.guilds.InviteManager;
import com.magicera.guilds.util.AlignmentUtil;
import com.magicera.guilds.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.UUID;

public final class GuildCommand implements CommandExecutor {

    private final MagicEraGuildsPlugin plugin;

    public GuildCommand(MagicEraGuildsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

        if (args.length == 0) {
            sender.sendMessage("§7/guild create <name> <displayName>");
            sender.sendMessage("§7/guild invite <player>");
            sender.sendMessage("§7/guild accept");
            sender.sendMessage("§7/guild deny");
            sender.sendMessage("§7/guild disband");
            sender.sendMessage("§7/guild reload");
            return true;
        }

        String sub = args[0].toLowerCase();

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

        if (sub.equals("create")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage("§cPlayers only.");
                return true;
            }
            if (args.length < 3) {
                sender.sendMessage("§cUsage: /guild create <name> <displayName>");
                return true;
            }

            PlayerData pd = plugin.storage().getOrCreatePlayer(player.getUniqueId());
            if (pd.getGuildId() != null) {
                sender.sendMessage("§cYou are already in a guild.");
                return true;
            }

            String rawName = args[1];
            String rawPrefix = args[2];

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

            // Alignment enforcement at invite time:
            GuildAlignment guildAlign = guild.getAlignment();
            GuildAlignment targetAlign = AlignmentUtil.groupFromScore(targetData.getAlignmentScore());

            // If target is neutral, allow invite (they'll snap on accept)
            // If not neutral, must match guild alignment
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

            // ACCEPT: enforce alignment again + apply neutral auto-snap
            GuildAlignment guildAlign = guild.getAlignment();
            GuildAlignment playerAlign = AlignmentUtil.groupFromScore(pd.getAlignmentScore());

            if (playerAlign == GuildAlignment.NEUTRAL && guildAlign != GuildAlignment.NEUTRAL) {
                // Neutral joining non-neutral guild => snap alignment score
                int snapped = AlignmentUtil.snapScoreToGuild(guildAlign);
                pd.setAlignmentScore(snapped);
                playerAlign = AlignmentUtil.groupFromScore(pd.getAlignmentScore());
            }

            if (playerAlign != guildAlign) {
                plugin.inviteManager().clearInvite(player);
                sender.sendMessage("§cYou are out of alignment and cannot join this guild.");
                return true;
            }

            // Join guild
            pd.setGuildId(guild.getId());
            pd.setOutOfAlignmentSinceEpochMs(null);
            guild.setRole(player.getUniqueId(), GuildRole.MEMBER);

            plugin.inviteManager().clearInvite(player);
            plugin.storage().save();

            sender.sendMessage("§aYou joined §r" + guild.getName() + " §7[" + guild.getPrefix() + "§7]");
            Bukkit.broadcastMessage("§7[§bMagicEra§7] §f" + player.getName() + " §7joined §r" + guild.getName());

            // Immediately re-check alignment status (should be fine)
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

            // Clear guildId for every member (FIXED LOOP)
            for (UUID memberId : g.getMembers().keySet()) {
                PlayerData mpd = plugin.storage().getOrCreatePlayer(memberId);
                if (g.getId().equals(mpd.getGuildId())) {
                    mpd.setGuildId(null);
                    mpd.setOutOfAlignmentSinceEpochMs(null);
                }
            }

            // Delete guild
            plugin.storage().deleteGuild(g.getId());
            plugin.storage().save();

            Bukkit.broadcastMessage("§7[§bMagicEra§7] §cGuild disbanded: §r" + g.getName());
            return true;
        }

        sender.sendMessage("§cUnknown subcommand.");
        return true;
    }
}
