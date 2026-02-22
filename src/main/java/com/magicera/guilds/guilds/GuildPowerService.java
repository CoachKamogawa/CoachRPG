package com.magicera.guilds.guilds;

import com.magicera.guilds.MagicEraGuildsPlugin;
import com.magicera.guilds.data.Guild;
import com.magicera.guilds.data.PlayerData;
import com.magicera.guilds.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.*;

public final class GuildPowerService {
    private final MagicEraGuildsPlugin plugin;

    public GuildPowerService(MagicEraGuildsPlugin plugin) {
        this.plugin = plugin;
    }

    public double playerPowerMax() {
        return plugin.territoryConfig().getDouble("playerPowerMax", 10.0);
    }

    public void clampAllPowers() {
        double max = playerPowerMax();
        for (Guild guild : plugin.storage().allGuilds()) {
            for (UUID memberId : guild.getMembers().keySet()) {
                PlayerData pd = plugin.storage().getOrCreatePlayer(memberId);
                pd.setPower(Math.min(max, pd.getPower()));
            }
        }
    }

    public double guildPower(Guild guild) {
        double power = 0.0;
        for (UUID id : guild.getMembers().keySet()) {
            power += plugin.storage().getOrCreatePlayer(id).getPower();
        }
        return power;
    }

    public int maxGuildPower(Guild guild) {
        return guild.getMembers().size() * (int) Math.ceil(playerPowerMax());
    }

    public int allowedChunksForMembers(int memberCount) {
        var sec = plugin.territoryConfig().getConfigurationSection("territoryScaling");
        if (sec == null || sec.getKeys(false).isEmpty()) return 15;

        TreeMap<Integer, Integer> table = new TreeMap<>();
        for (String k : sec.getKeys(false)) {
            try {
                table.put(Integer.parseInt(k), sec.getInt(k));
            } catch (NumberFormatException ignored) {}
        }
        if (table.isEmpty()) return 15;

        int minKey = table.firstKey();
        int maxKey = table.lastKey();
        int effective = Math.max(3, memberCount);

        if (effective <= minKey) return table.get(minKey);
        if (effective >= maxKey) return table.get(maxKey);

        Map.Entry<Integer, Integer> floor = table.floorEntry(effective);
        return floor == null ? table.get(minKey) : floor.getValue();
    }

    public int allowedChunks(Guild guild) {
        return allowedChunksForMembers(guild.getMembers().size());
    }

    public int allowedChunksByPower(Guild guild) {
        double landCostPerChunk = plugin.territoryConfig().getDouble("landCostPerChunk", 2.0);
        if (landCostPerChunk <= 0.0) return Integer.MAX_VALUE;
        return Math.max(0, (int) Math.floor(guildPower(guild) / landCostPerChunk));
    }

    public int maxClaimableChunks(Guild guild) {
        return Math.min(allowedChunks(guild), allowedChunksByPower(guild));
    }

    public int hallVulnerableThreshold(Guild guild) {
        int fixedCount = plugin.territoryConfig().getInt("fixedThresholdMemberCount", 15);
        double pct = plugin.territoryConfig().getDouble("hallVulnerablePercentUnderOrEqual15", 0.15);
        if (guild.getMembers().size() <= fixedCount) {
            return (int) Math.ceil(maxGuildPower(guild) * pct);
        }
        int fixedMax = fixedCount * (int) Math.ceil(playerPowerMax());
        return (int) Math.ceil(fixedMax * pct);
    }

    public int hallAtRiskThreshold(Guild guild) {
        int fixedCount = plugin.territoryConfig().getInt("fixedThresholdMemberCount", 15);
        double pct = plugin.territoryConfig().getDouble("hallAtRiskPercentUnderOrEqual15", 0.20);
        if (guild.getMembers().size() <= fixedCount) {
            return (int) Math.ceil(maxGuildPower(guild) * pct);
        }
        int fixedMax = fixedCount * (int) Math.ceil(playerPowerMax());
        return (int) Math.ceil(fixedMax * pct);
    }

    public boolean isHallProtected(Guild guild) {
        return guildPower(guild) > hallVulnerableThreshold(guild);
    }

    public void refreshUnstableClaims(Guild guild) {
        int excess = Math.max(0, guild.getClaimedChunks().size() - maxClaimableChunks(guild));
        guild.getUnstableClaims().clear();
        if (excess <= 0) return;

        List<String> candidates = new ArrayList<>(guild.getClaimedChunks());

        // claim timestamp sorting (oldest becomes unstable first) with hall-distance preference if hall exists
        Map<String, Long> ts = guild.getClaimTimestamps();
        if (ts == null) ts = Collections.emptyMap();

        String hallWorld = guild.getHallWorld();
        Integer cx = guild.getHallCenterX();
        Integer cz = guild.getHallCenterZ();

        if (guild.hasHall() && hallWorld != null && cx != null && cz != null) {
            Map<String, Long> finalTs = ts;
            candidates.sort(
                    Comparator
                            .comparingDouble((String key) -> -distanceSqFromHall(key, hallWorld, cx, cz))
                            .thenComparingLong(key -> finalTs.getOrDefault(key, 0L))
            );
        } else {
            Map<String, Long> finalTs = ts;
            candidates.sort(Comparator.comparingLong(key -> finalTs.getOrDefault(key, 0L)));
        }

        for (String key : candidates) {
            if (guild.getUnstableClaims().size() >= excess) break;
            guild.getUnstableClaims().add(key);
        }
    }

    private double distanceSqFromHall(String key, String hallWorld, int cx, int cz) {
        String[] p = key.split(":");
        if (p.length != 3 || !hallWorld.equals(p[0])) return Double.MAX_VALUE;
        try {
            int x = Integer.parseInt(p[1]);
            int z = Integer.parseInt(p[2]);
            long dx = (long) x - cx;
            long dz = (long) z - cz;
            return (dx * dx) + (dz * dz);
        } catch (NumberFormatException e) {
            return Double.MAX_VALUE;
        }
    }

    public boolean canOverclaimChunk(Guild owner, String key) {
        refreshUnstableClaims(owner);

        // Hall chunk protection should only apply if the guild actually has a hall.
        // Once hall protection drops (vulnerable threshold), hall chunks are explicitly overclaimable.
        if (owner.hasHall() && owner.getHallChunks().contains(key)) {
            return !isHallProtected(owner);
        }
        return owner.getUnstableClaims().contains(key);
    }
@@ -286,70 +287,83 @@
        if (!plugin.territoryConfig().getBoolean("warningTiers." + tier, true)) return;

        long cooldownMs = Math.max(1, plugin.territoryConfig().getLong("warningCooldownMinutes", 15)) * 60_000L;
        String warTierKey = "war:" + channel + ":" + tier;
        boolean tierAlreadySentThisWar = guild.getWarningSentWarSession().contains(warTierKey);

        // Login warnings should respect cooldown per-member for repeated reminders.
        // First-time threshold alerts in a war session should still fire immediately.
        if (loginTarget != null) {
            String perMemberKey = channel + "Cooldown:" + loginTarget.getUniqueId();
            long last = guild.getWarningLastSent().getOrDefault(perMemberKey, 0L);
            boolean allowByCooldown = (now - last) >= cooldownMs;
            if (!allowByCooldown && tierAlreadySentThisWar) return;

            loginTarget.sendMessage(Text.color(message));
            guild.getWarningLastSent().put(perMemberKey, now);
            if (guild.isInWar() && guild.getWarSessionId() != null) {
                guild.getWarningSentWarSession().add(warTierKey);
            }
            return;
        }

        String channelKey = channel + "Cooldown";
        long last = guild.getWarningLastSent().getOrDefault(channelKey, 0L);
        boolean allowByCooldown = (now - last) >= cooldownMs;
        if (!allowByCooldown && tierAlreadySentThisWar) return;

        sendGuildMessage(guild, message);
        guild.getWarningLastSent().put(channelKey, now);
        if (guild.isInWar() && guild.getWarSessionId() != null) {
            guild.getWarningSentWarSession().add(warTierKey);
        }
    }

    private void maybeWarn(Guild guild, String tier, boolean condition, String message, long now) {
        if (!condition) return;
        if (!plugin.territoryConfig().getBoolean("warningTiers." + tier, true)) return;

        long cooldownMs = Math.max(1, plugin.territoryConfig().getLong("warningCooldownMinutes", 15)) * 60_000L;
        long last = guild.getWarningLastSent().getOrDefault(tier, 0L);

        if (guild.isInWar() && guild.getWarSessionId() != null && guild.getWarningSentWarSession().contains(tier)) {
            return;
        }
        if ((now - last) < cooldownMs) return;

        sendGuildMessage(guild, message);
        guild.getWarningLastSent().put(tier, now);

        if (guild.isInWar() && guild.getWarSessionId() != null) {
            guild.getWarningSentWarSession().add(tier);
        }
    }

    private void sendGuildMessage(Guild guild, String msg) {
        String colored = Text.color(msg);
        for (UUID memberId : guild.getMembers().keySet()) {
            Player p = Bukkit.getPlayer(memberId);
            if (p != null && p.isOnline()) {
                p.sendMessage(colored);
            } else {
                PlayerData pd = plugin.storage().getOrCreatePlayer(memberId);
                pd.getPendingGuildMessages().add(colored);
            }
        }
    }

    public void disbandGuild(Guild guild, String personalMessage) {
        String guildId = guild.getId();
        for (UUID memberId : new ArrayList<>(guild.getMembers().keySet())) {
            PlayerData pd = plugin.storage().getOrCreatePlayer(memberId);
            if (guildId.equals(pd.getGuildId())) {
                pd.setGuildId(null);
                pd.setGuildTitle("");
                pd.setOutOfAlignmentSinceEpochMs(null);
            }
            Player online = Bukkit.getPlayer(memberId);
            if (online != null) online.sendMessage(Text.color(personalMessage));
        }
        plugin.storage().deleteGuild(guildId);
        Bukkit.broadcastMessage(Text.color("&7[&aGuild&7] &c" + guild.getName() + " &fhas been disbanded."));
    }
}
