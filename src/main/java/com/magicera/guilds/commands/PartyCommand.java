package com.magicera.guilds.commands;

import com.magicera.guilds.MagicEraGuildsPlugin;
import com.magicera.guilds.data.Guild;
import com.magicera.guilds.data.Party;
import com.magicera.guilds.data.PlayerData;
import com.magicera.guilds.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.text.SimpleDateFormat;
import java.util.*;

public final class PartyCommand implements CommandExecutor {

    private final MagicEraGuildsPlugin plugin;
    private final Map<UUID, String> pendingInviteByPlayer = new HashMap<>();

    public PartyCommand(MagicEraGuildsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(prefix() + "Players only.");
            return true;
        }
        if (args.length == 0) {
            sendHelp(player, label);
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "help", "?" -> sendHelp(player, label);
            case "create" -> createParty(player, args);
            case "invite" -> invite(player, args);
            case "join" -> join(player, args);
            case "promote" -> promote(player, args);
            case "kick" -> kick(player, args);
            case "leave" -> leave(player);
            case "disband" -> disband(player);
            case "info" -> info(player, args);
            case "leaderboard" -> leaderboard(player);
            case "summon" -> summon(player, args);
            default -> {
                player.sendMessage(prefix() + "Unknown subcommand.");
                sendHelp(player, label);
            }
        }
        return true;
    }

    private void createParty(Player player, String[] args) {
        if (plugin.storage().getPartyByMember(player.getUniqueId()) != null) {
            player.sendMessage(prefix() + "You are already in a party.");
            return;
        }
        if (args.length < 2) {
            player.sendMessage(prefix() + "Usage: /party create <Party Name...>");
            return;
        }
        String name = Text.color(String.join(" ", Arrays.copyOfRange(args, 1, args.length)));
        String id = Text.normalizeId(name);
        if (id.isEmpty()) {
            player.sendMessage(prefix() + "Invalid party name.");
            return;
        }
        if (plugin.storage().getPartyById(id) != null) {
            player.sendMessage(prefix() + "A party with that name already exists.");
            return;
        }

        Party party = new Party(id, name, player.getUniqueId(), System.currentTimeMillis());
        plugin.storage().addParty(party);
        player.sendMessage(prefix() + "Created party " + name + "§f.");
    }

    private void invite(Player leader, String[] args) {
        if (args.length < 2) {
            leader.sendMessage(prefix() + "Usage: /party invite <player>");
            return;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            leader.sendMessage(prefix() + "Player is not online.");
            return;
        }
        if (target.getUniqueId().equals(leader.getUniqueId())) {
            leader.sendMessage(prefix() + "You cannot invite yourself.");
            return;
        }

        Party party = plugin.storage().getPartyByMember(leader.getUniqueId());
        if (party == null) {
            String name = leader.getName() + "'s Party";
            String id = Text.normalizeId(name + "-" + leader.getUniqueId().toString().substring(0, 8));
            party = new Party(id, name, leader.getUniqueId(), System.currentTimeMillis());
            plugin.storage().addParty(party);
        }
        if (!party.getLeader().equals(leader.getUniqueId())) {
            leader.sendMessage(prefix() + "Only the party leader can invite.");
            return;
        }
        if (party.size() >= 4) {
            leader.sendMessage(prefix() + "Your party is full (max 4).");
            return;
        }
        if (plugin.storage().getPartyByMember(target.getUniqueId()) != null) {
            leader.sendMessage(prefix() + "That player is already in a party.");
            return;
        }
        if (isAtWarWithAny(target.getUniqueId(), party)) {
            leader.sendMessage(prefix() + "Cannot invite: war restriction with a party member.");
            return;
        }

        pendingInviteByPlayer.put(target.getUniqueId(), leader.getName());
        leader.sendMessage(prefix() + "Invited " + target.getName() + " to the party.");
        target.sendMessage(prefix() + leader.getName() + " invited you. Run: §f/party join " + leader.getName());
    }

    private void join(Player player, String[] args) {
        if (plugin.storage().getPartyByMember(player.getUniqueId()) != null) {
            player.sendMessage(prefix() + "You are already in a party.");
            return;
        }

        String leaderName = args.length >= 2 ? args[1] : pendingInviteByPlayer.get(player.getUniqueId());
        if (leaderName == null || !leaderName.equalsIgnoreCase(pendingInviteByPlayer.get(player.getUniqueId()))) {
            player.sendMessage(prefix() + "No valid invite found. Use /party join <leaderName>.");
            return;
        }

        Player leader = Bukkit.getPlayerExact(leaderName);
        if (leader == null) {
            player.sendMessage(prefix() + "Leader must be online to join.");
            return;
        }

        Party party = plugin.storage().getPartyByMember(leader.getUniqueId());
        if (party == null || !party.getLeader().equals(leader.getUniqueId())) {
            player.sendMessage(prefix() + "That invite is no longer valid.");
            return;
        }
        if (party.size() >= 4) {
            player.sendMessage(prefix() + "That party is full.");
            return;
        }
        if (isAtWarWithAny(player.getUniqueId(), party)) {
            player.sendMessage(prefix() + "You cannot join due to an active war with a party member.");
            return;
        }

        if (!party.addMember(player.getUniqueId())) {
            player.sendMessage(prefix() + "Failed to join party.");
            return;
        }
        plugin.storage().addPartyMember(party.getId(), player.getUniqueId());
        pendingInviteByPlayer.remove(player.getUniqueId());
        broadcastParty(party, player.getName() + " joined the party.");
    }

    private void promote(Player leader, String[] args) {
        Party party = plugin.storage().getPartyByMember(leader.getUniqueId());
        if (party == null) {
            leader.sendMessage(prefix() + "You are not in a party.");
            return;
        }
        if (!party.getLeader().equals(leader.getUniqueId())) {
            leader.sendMessage(prefix() + "Only leader can promote.");
            return;
        }
        if (args.length < 2) {
            leader.sendMessage(prefix() + "Usage: /party promote <player>");
            return;
        }
        OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
        if (!party.isMember(target.getUniqueId())) {
            leader.sendMessage(prefix() + "That player is not in your party.");
            return;
        }
        party.setLeader(target.getUniqueId());
        plugin.storage().markDirty();
        broadcastParty(party, target.getName() + " is now the party leader.");
    }

    private void kick(Player leader, String[] args) {
        Party party = plugin.storage().getPartyByMember(leader.getUniqueId());
        if (party == null) {
            leader.sendMessage(prefix() + "You are not in a party.");
            return;
        }
        if (!party.getLeader().equals(leader.getUniqueId())) {
            leader.sendMessage(prefix() + "Only leader can kick.");
            return;
        }
        if (args.length < 2) {
            leader.sendMessage(prefix() + "Usage: /party kick <player>");
            return;
        }
        OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
        if (target.getUniqueId().equals(leader.getUniqueId())) {
            leader.sendMessage(prefix() + "Use /party disband.");
            return;
        }
        if (!party.removeMember(target.getUniqueId())) {
            leader.sendMessage(prefix() + "That player is not in your party.");
            return;
        }
        plugin.storage().removePartyMember(target.getUniqueId());
        broadcastParty(party, target.getName() + " was kicked from the party.");
    }

    private void leave(Player player) {
        Party party = plugin.storage().getPartyByMember(player.getUniqueId());
        if (party == null) {
            player.sendMessage(prefix() + "You are not in a party.");
            return;
        }
        if (party.getLeader().equals(player.getUniqueId())) {
            player.sendMessage(prefix() + "Party leader cannot leave. Use /party disband or /party promote first.");
            return;
        }
        if (party.removeMember(player.getUniqueId())) {
            plugin.storage().removePartyMember(player.getUniqueId());
            player.sendMessage(prefix() + "You left the party.");
            broadcastParty(party, player.getName() + " left the party.");
        }
    }

    private void disband(Player leader) {
        Party party = plugin.storage().getPartyByMember(leader.getUniqueId());
        if (party == null) {
            leader.sendMessage(prefix() + "You are not in a party.");
            return;
        }
        if (!party.getLeader().equals(leader.getUniqueId())) {
            leader.sendMessage(prefix() + "Only leader can disband.");
            return;
        }
        broadcastParty(party, "Party disbanded.");
        plugin.storage().removeParty(party.getId());
    }

    private void info(Player viewer, String[] args) {
        Party party = null;
        if (args.length >= 2) {
            party = plugin.storage().getPartyById(Text.normalizeId(args[1]));
            if (party == null) {
                OfflinePlayer op = Bukkit.getOfflinePlayer(args[1]);
                party = plugin.storage().getPartyByMember(op.getUniqueId());
            }
        }
        if (party == null) party = plugin.storage().getPartyByMember(viewer.getUniqueId());
        if (party == null) {
            viewer.sendMessage(prefix() + "Party not found.");
            return;
        }

        viewer.sendMessage(prefix() + "§f" + party.getName());
        viewer.sendMessage("§7Created: §f" + new SimpleDateFormat("yyyy-MM-dd HH:mm").format(new Date(party.getCreatedAtEpochMs())));

        int totalKills = 0;
        int totalDeaths = 0;
        for (UUID uuid : party.getMembers()) {
            OfflinePlayer op = Bukkit.getOfflinePlayer(uuid);
            PlayerData pd = plugin.storage().getOrCreatePlayer(uuid);
            totalKills += pd.getPvpKills();
            totalDeaths += pd.getPvpDeaths();
            viewer.sendMessage("§8- §f" + (op.getName() == null ? uuid.toString().substring(0, 8) : op.getName())
                    + (uuid.equals(party.getLeader()) ? " §7(Leader)" : "")
                    + " §7K:§f" + pd.getPvpKills()
                    + " §7D:§f" + pd.getPvpDeaths()
                    + " §7KDA:§f" + formatKda(pd.getPvpKills(), pd.getPvpDeaths()));
        }
        viewer.sendMessage("§7Totals §8» §7K:§f" + totalKills + " §7D:§f" + totalDeaths + " §7KDA:§f" + formatKda(totalKills, totalDeaths));
    }

    private void leaderboard(Player viewer) {
        List<Party> parties = new ArrayList<>();
        for (Party party : plugin.storage().allParties()) {
            if (party.size() >= 2) parties.add(party);
        }
        parties.sort((a, b) -> Double.compare(totalKda(b), totalKda(a)));

        viewer.sendMessage(prefix() + "§fTop Party KDA");
        for (int i = 0; i < Math.min(10, parties.size()); i++) {
            Party p = parties.get(i);
            viewer.sendMessage("§7" + (i + 1) + ". §f" + p.getName() + " §8- §f" + String.format(Locale.US, "%.2f", totalKda(p)) + " §7(" + p.size() + " members)");
        }
    }

    private void summon(Player sender, String[] args) {
        if (!sender.hasPermission("magicera.admin")) {
            sender.sendMessage(prefix() + "No permission.");
            return;
        }
        if (args.length < 2) {
            sender.sendMessage(prefix() + "Usage: /party summon <player>");
            return;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            sender.sendMessage(prefix() + "Player is not online.");
            return;
        }
        Party party = plugin.storage().getPartyByMember(target.getUniqueId());
        if (party == null) {
            sender.sendMessage(prefix() + "Player is not in a party.");
            return;
        }
        Player leader = Bukkit.getPlayer(party.getLeader());
        if (leader == null) {
            sender.sendMessage(prefix() + "Party leader must be online.");
            return;
        }
        for (UUID member : party.getMembers()) {
            Player online = Bukkit.getPlayer(member);
            if (online != null) {
                online.teleport(leader.getLocation());
                online.sendMessage(prefix() + "Summoned to leader location by admin.");
            }
        }
        sender.sendMessage(prefix() + "Summoned party to leader location.");
    }

    private boolean isAtWarWithAny(UUID joiner, Party party) {
        for (UUID member : party.getMembers()) {
            if (arePlayersAtWar(joiner, member)) return true;
        }
        return false;
    }

    private boolean arePlayersAtWar(UUID a, UUID b) {
        if (a.equals(b)) return false;
        PlayerData ap = plugin.storage().getOrCreatePlayer(a);
        PlayerData bp = plugin.storage().getOrCreatePlayer(b);
        if (ap.getGuildId() == null || bp.getGuildId() == null) return false;
        Guild ag = plugin.storage().getGuild(ap.getGuildId());
        Guild bg = plugin.storage().getGuild(bp.getGuildId());
        if (ag == null || bg == null) return false;
        if (!ag.isInWar() || !bg.isInWar()) return false;
        return ag.getEnemies().contains(bg.getId()) && bg.getEnemies().contains(ag.getId());
    }

    private void broadcastParty(Party party, String message) {
        for (UUID member : party.getMembers()) {
            Player online = Bukkit.getPlayer(member);
            if (online != null) {
                online.sendMessage(prefix() + message);
            }
        }
    }

    private String prefix() {
        return Text.color("&7[&dParty&7] ");
    }
    
    private void sendHelp(Player player, String label) {
        String root = "/" + label.toLowerCase(Locale.ROOT);
        player.sendMessage("§8§m----------------------------------------");
        player.sendMessage(prefix() + "§fParty Commands");
        player.sendMessage("§d" + root + " create §7<name> §8- §fCreate a new party.");
        player.sendMessage("§d" + root + " invite §7<player> §8- §fInvite a player.");
        player.sendMessage("§d" + root + " join §7<leader> §8- §fAccept an invite.");
        player.sendMessage("§d" + root + " promote §7<player> §8- §fTransfer party leadership.");
        player.sendMessage("§d" + root + " kick §7<player> §8- §fRemove a member.");
        player.sendMessage("§d" + root + " leave §8- §fLeave your current party.");
        player.sendMessage("§d" + root + " disband §8- §fDisband your party.");
        player.sendMessage("§d" + root + " info §7[party|player] §8- §fShow party details.");
        player.sendMessage("§d" + root + " leaderboard §8- §fShow top party KDA.");
        if (player.hasPermission("magicera.admin")) {
            player.sendMessage("§d" + root + " summon §7<player> §8- §fTeleport party to leader.");
        }
        player.sendMessage("§8§m----------------------------------------");
    }

    private String formatKda(int kills, int deaths) {
        double kda = deaths == 0 ? kills : (kills / (double) deaths);
        return String.format(Locale.US, "%.2f", kda);
    }

    private double totalKda(Party party) {
        int kills = 0;
        int deaths = 0;
        for (UUID member : party.getMembers()) {
            PlayerData pd = plugin.storage().getOrCreatePlayer(member);
            kills += pd.getPvpKills();
            deaths += pd.getPvpDeaths();
        }
        return deaths == 0 ? kills : (kills / (double) deaths);
    }
}
