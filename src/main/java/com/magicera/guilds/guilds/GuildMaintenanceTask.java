package com.magicera.guilds.guilds;

import com.magicera.guilds.MagicEraGuildsPlugin;
import com.magicera.guilds.data.Guild;
import com.magicera.guilds.data.GuildAlignment;
import com.magicera.guilds.data.GuildRole;
import com.magicera.guilds.data.PlayerData;
import com.magicera.guilds.util.AlignmentUtil;
import com.magicera.guilds.util.Text;
import org.bukkit.Bukkit;

import java.util.*;
import java.util.concurrent.TimeUnit;

public final class GuildMaintenanceTask implements Runnable {
    private final MagicEraGuildsPlugin plugin;

    public GuildMaintenanceTask(MagicEraGuildsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void run() {
        long now = System.currentTimeMillis();
        long inactiveMs = TimeUnit.DAYS.toMillis(plugin.getConfig().getLong("guilds.inactivity-autodisbands-days", 30));
        long outOfFavorMs = TimeUnit.HOURS.toMillis(plugin.getConfig().getLong("alignment.out-of-alignment-grace-hours", 48));

        List<String> toDelete = new ArrayList<>();
        List<String> hallTimeoutDelete = new ArrayList<>();

        for (Guild guild : plugin.storage().allGuilds()) {
            UUID masterId = findMaster(guild);
            if (masterId == null) {
                assignNewMaster(guild, "No master set");
                continue;
            }

            boolean allInactive = true;
            for (UUID memberId : guild.getMembers().keySet()) {
                PlayerData pd = plugin.storage().getOrCreatePlayer(memberId);
                if ((now - pd.getLastSeenEpochMs()) < inactiveMs) {
                    allInactive = false;
                    break;
                }
            }
            if (allInactive) {
                toDelete.add(guild.getId());
                continue;
            }

            PlayerData masterPd = plugin.storage().getOrCreatePlayer(masterId);
            if ((now - masterPd.getLastSeenEpochMs()) >= inactiveMs) {
                assignNewMaster(guild, "Guild Master inactive for 30+ days");
            }

            GuildAlignment masterAlign = AlignmentUtil.groupFromScore(masterPd.getAlignmentScore());
            if (masterAlign != guild.getAlignment()) {
                if (guild.getMasterOutOfFavorSinceEpochMs() == null) {
                    guild.setMasterOutOfFavorSinceEpochMs(now);
                    guild.setMasterOutOfFavorWarnEpochMs(null);
                }

                long remaining = Math.max(0L, outOfFavorMs - (now - guild.getMasterOutOfFavorSinceEpochMs()));
                var p = Bukkit.getPlayer(masterId);

                long warnIntervalMs = TimeUnit.MINUTES.toMillis(15);
                Long lastWarn = guild.getMasterOutOfFavorWarnEpochMs();
                boolean shouldWarn = lastWarn == null || (now - lastWarn) >= warnIntervalMs;

                if (p != null && shouldWarn) {
                    String favorWord = coloredFavor(guild.getAlignment());
                    p.sendMessage(Text.color("&7[&aGuild&7] &cYou are out of favor with your guild. You have &e" + formatRemaining(remaining) + " &cto restore your " + favorWord + "&c."));
                    guild.setMasterOutOfFavorWarnEpochMs(now);
                }

                if (remaining == 0L) {
                    assignNewMaster(guild, "Guild Master removed for being out of favor");
                    guild.setMasterOutOfFavorSinceEpochMs(null);
                    guild.setMasterOutOfFavorWarnEpochMs(null);
                }
            } else {
                guild.setMasterOutOfFavorSinceEpochMs(null);
                guild.setMasterOutOfFavorWarnEpochMs(null);
            }

            processImpeachment(guild, now);

            if (guild.isInWar() && guild.getWarEndsAtEpochMs() != null && now >= guild.getWarEndsAtEpochMs()) {
                guild.setInWar(false);
                guild.setWarEndsAtEpochMs(null);
                guild.setWarSessionId(null);
                guild.getWarningSentWarSession().clear();
                guild.addLogEntry("War ended.");
            }

            if (!guild.isInWar()) {
                long hallRequiredHours = Math.max(1L, plugin.territoryConfig().getLong("hallRequiredAfterHours", 48L));
                long guildAgeMs = now - guild.getFoundedAtEpochMs();
                if (!guild.hasHall() && guildAgeMs >= TimeUnit.HOURS.toMillis(hallRequiredHours)) {
                    hallTimeoutDelete.add(guild.getId());
                    continue;
                }

                double regenPerMinute = plugin.territoryConfig().getDouble("playerPowerRegenPerHour", 3.33) / 60.0;
                for (UUID memberId : guild.getMembers().keySet()) {
                    var online = Bukkit.getPlayer(memberId);
                    if (online == null || !online.isOnline()) continue;

                    PlayerData memberPd = plugin.storage().getOrCreatePlayer(memberId);
                    double max = plugin.guildPower().playerPowerMax();
                    memberPd.setPower(Math.min(max, memberPd.getPower() + regenPerMinute));
                }
            }

            plugin.guildPower().clampAllPowers();
            plugin.guildPower().handlePowerThresholds(guild);
        }

        for (String guildId : toDelete) {
            Guild g = plugin.storage().getGuild(guildId);
            if (g == null) continue;
            plugin.guildPower().disbandGuild(g, "&7[&aGuild&7] &cGuild disbanded due to inactivity.");
        }

        for (String guildId : hallTimeoutDelete) {
            Guild g = plugin.storage().getGuild(guildId);
            if (g == null) continue;
            plugin.guildPower().disbandGuild(g, "&7[&aGuild&7] &cYour guild did not claim a Guild Hall in time and has been disbanded.");
        }

        plugin.storage().save();
    }

    private void processImpeachment(Guild guild, long now) {
        Long start = guild.getImpeachmentStartedEpochMs();
        if (start == null) return;
        if ((now - start) < TimeUnit.HOURS.toMillis(24)) return;

        int total = guild.getMembers().size();
        if (total <= 0) {
            guild.setImpeachmentStartedEpochMs(null);
            guild.getImpeachmentVotes().clear();
            guild.setKickLockUntilEpochMs(0L);
            return;
        }

        long removeVotes = guild.getImpeachmentVotes().values().stream().filter(Boolean::booleanValue).count();
        double pct = (removeVotes * 100.0) / total;
        if (pct >= 60.0) {
            assignNewMaster(guild, "Guild Master impeached (" + Math.round(pct) + "%)");
        }

        guild.setImpeachmentStartedEpochMs(null);
        guild.getImpeachmentVotes().clear();
        guild.setKickLockUntilEpochMs(0L);
    }

    private void assignNewMaster(Guild guild, String reason) {
        UUID currentMaster = findMaster(guild);
        List<UUID> officers = guild.getMembers().entrySet().stream()
                .filter(e -> e.getValue() == GuildRole.OFFICER)
                .map(Map.Entry::getKey)
                .toList();

        UUID replacement;
        if (!officers.isEmpty()) {
            replacement = officers.get(new Random().nextInt(officers.size()));
        } else {
            replacement = guild.getMemberJoinedAt().entrySet().stream()
                    .filter(e -> guild.getMembers().containsKey(e.getKey()))
                    .min(Comparator.comparingLong(Map.Entry::getValue))
                    .map(Map.Entry::getKey)
                    .orElse(null);
        }

        if (replacement == null) return;
        if (currentMaster != null && !currentMaster.equals(replacement) && guild.getMembers().containsKey(currentMaster)) {
            guild.setRole(currentMaster, GuildRole.MEMBER);
        }
        guild.setRole(replacement, GuildRole.MASTER);
        guild.addLogEntry("Master change: " + safeName(replacement) + " | " + reason);
        Bukkit.broadcastMessage(Text.color("&7[&bMagic Era&7] &f" + safeName(replacement) + " is now the guild master of " + guild.getName() + "."));
    }

    private UUID findMaster(Guild guild) {
        return guild.getMembers().entrySet().stream()
                .filter(e -> e.getValue() == GuildRole.MASTER)
                .map(Map.Entry::getKey)
                .findFirst().orElse(null);
    }

    private String safeName(UUID id) {
        String name = Bukkit.getOfflinePlayer(id).getName();
        return name == null ? id.toString() : name;
    }

    private String formatRemaining(long ms) {
        long hours = Math.max(0L, TimeUnit.MILLISECONDS.toHours(ms));
        long mins = Math.max(0L, TimeUnit.MILLISECONDS.toMinutes(ms) % 60);
        return hours + "h " + mins + "m";
    }

    private String coloredFavor(GuildAlignment alignment) {
        return switch (alignment) {
            case DARK -> "&cSin";
            case NEUTRAL -> "&fBalance";
            case HONORABLE -> "&aHonor";
        };
    }
}
