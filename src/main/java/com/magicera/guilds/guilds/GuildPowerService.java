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
        int sizeCap = Math.max(9, allowedChunksForMembers(guild.getMembers().size()));
        int baseline = 15;
        double landCostPerChunk = plugin.territoryConfig().getDouble("landCostPerChunk", 2.0);
        int powerChunks = landCostPerChunk <= 0.0
                ? Integer.MAX_VALUE
                : (int) Math.floor(guildPower(guild) / landCostPerChunk);

        int effectiveByPower = Math.max(9, Math.min(sizeCap, baseline + powerChunks));
        return Math.min(sizeCap, effectiveByPower);
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
        int excess = Math.max(0, guild.getClaimedChunks().size() - allowedChunks(guild));
        guild.getUnstableClaims().clear();
        if (excess <= 0) return;

        List<String> candidates = new ArrayList<>();
        for (String key : guild.getClaimedChunks()) {
            if (!guild.isHallChunk(key)) {
                candidates.add(key);
            }
        }
        if (candidates.isEmpty()) return;

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
                            .thenComparing(key -> key)
            );
        } else {
            Map<String, Long> finalTs = ts;
            candidates.sort(
                    Comparator
                            .comparingLong((String key) -> finalTs.getOrDefault(key, 0L))
                            .thenComparing(key -> key)
            );
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

    public boolean canOverclaimChunk(Guild attacker, Guild owner, String key) {
        refreshUnstableClaims(owner);

        boolean atWar = attacker != null
                && owner != null
                && attacker.getEnemies().contains(owner.getId())
                && owner.getEnemies().contains(attacker.getId())
                && attacker.isInWar()
                && owner.isInWar();

        if (!atWar) {
            return false;
        }

        if (owner.isHallChunk(key)) {
            return !isHallProtected(owner);
        }
        return owner.getUnstableClaims().contains(key);
    }

    public void handlePowerThresholds(Guild guild) {
        evaluateGuildWarnings(guild, System.currentTimeMillis(), null);
    }

    public void handlePowerThresholdsForLoginPlayer(Player player) {
        if (player == null) return;

        PlayerData pd = plugin.storage().getOrCreatePlayer(player.getUniqueId());
        String guildId = pd.getGuildId();
        if (guildId == null || guildId.isBlank()) return;

        Guild guild = plugin.storage().getGuild(guildId);
        if (guild == null || !guild.isInWar()) return;

        evaluateGuildWarnings(guild, System.currentTimeMillis(), player);
    }

    private void evaluateGuildWarnings(Guild guild, long now, Player loginTarget) {
        refreshUnstableClaims(guild);

        int claims = guild.getClaimedChunks().size();
        double power = guildPower(guild);
        int max = Math.max(1, maxGuildPower(guild));

        if (power <= 0.0) {
            disbandGuild(guild, "&7[&aGuild&7] &cYour guild has collapsed at &e0 &cpower and has been disbanded.");
            return;
        }

        // Only disband for 0 land if they had actually established a hall (i.e., they progressed into territory gameplay).
        if (claims <= 0) {
            if (guild.hasHall()) {
                disbandGuild(guild, "&7[&aGuild&7] &cYour guild has lost all territory and has been disbanded.");
            }
            return;
        }

        int lowClaimsThreshold = plugin.territoryConfig().getInt("lowClaimsThreshold", 3);
        maybeWarn(
                guild,
                "lowClaims",
                claims <= lowClaimsThreshold,
                "&7[&aGuild&7] &cYour guild controls only &e" + claims + " &cremaining claims. Losing all territory will result in disband.",
                now
        );

        if (!guild.isInWar()) return;

        int atRisk = hallAtRiskThreshold(guild);
        int vulnerable = hallVulnerableThreshold(guild);

        // Hall: only send the current most severe tier.
        String hallTier = currentHallTier(power, atRisk, vulnerable);

        // Weariness: only send the current most severe tier.
        String wearinessTier = currentWearinessTier(power, max);
        if ("collapse10".equals(wearinessTier)) {
            maybeWarnChannel(
                    guild,
                    "weariness",
                    "collapse10",
                    "&7[&aGuild&7] &cYour guild is below &e10% &cpower. You are on the verge of collapse. If power reaches &e0&c, the guild will disband.",
                    now,
                    loginTarget
            );
        } else if ("critical25".equals(wearinessTier)) {
            maybeWarnChannel(
                    guild,
                    "weariness",
                    "critical25",
                    "&7[&aGuild&7] &cYour guild is below &e25% &cpower. Continued losses will begin threatening your territory.",
                    now,
                    loginTarget
            );
        } else if ("warWeariness50".equals(wearinessTier)) {
            maybeWarnChannel(
                    guild,
                    "weariness",
                    "warWeariness50",
                    "&7[&aGuild&7] &cYour guild has fallen below &e50% &cpower, and casualties are increasing. Consider seeking a truce.",
                    now,
                    loginTarget
            );
        }

        if ("hallVulnerable".equals(hallTier)) {
            maybeWarnChannel(
                    guild,
                    "hall",
                    "hallVulnerable",
                    "&7[&aGuild&7] &cYour guild is at threat of collapse. The Guild Hall is no longer secure and can now be overclaimed.",
                    now,
                    loginTarget
            );
        } else if ("hallAtRisk".equals(hallTier)) {
            maybeWarnChannel(
                    guild,
                    "hall",
                    "hallAtRisk",
                    "&7[&aGuild&7] &cYour Guild Hall is at risk. You are close to losing protection.",
                    now,
                    loginTarget
            );
        }
    }

    private String currentWearinessTier(double power, int maxPower) {
        if (power <= maxPower * 0.10D) return "collapse10";
        if (power <= maxPower * 0.25D) return "critical25";
        if (power <= maxPower * 0.50D) return "warWeariness50";
        return null;
    }

    private String currentHallTier(double power, int atRiskThreshold, int vulnerableThreshold) {
        if (power <= vulnerableThreshold) return "hallVulnerable";
        if (power <= atRiskThreshold) return "hallAtRisk";
        return null;
    }

    private void maybeWarnChannel(
            Guild guild,
            String channel,
            String tier,
            String message,
            long now,
            Player loginTarget
    ) {
        if (!plugin.territoryConfig().getBoolean("warningTiers." + tier, true)) return;

        long cooldownMs = Math.max(1, plugin.territoryConfig().getLong("warningCooldownMinutes", 15)) * 60_000L;

        String tierKey = channel + "TierCode";
        String channelKey = channel + "Cooldown";
        long currentTierCode = tier.hashCode();
        long lastTierCode = guild.getWarningLastSent().getOrDefault(tierKey, Long.MIN_VALUE);

        if (lastTierCode != currentTierCode) {
            if (loginTarget != null) {
                loginTarget.sendMessage(Text.color(message));
            } else {
                sendGuildMessage(guild, message);
            }
            guild.getWarningLastSent().put(channelKey, now);
            guild.getWarningLastSent().put(tierKey, currentTierCode);
            return;
        }

        long last = guild.getWarningLastSent().getOrDefault(channelKey, 0L);
        if ((now - last) < cooldownMs) return;

        if (loginTarget != null) {
            loginTarget.sendMessage(Text.color(message));
        } else {
            sendGuildMessage(guild, message);
        }
        guild.getWarningLastSent().put(channelKey, now);
        guild.getWarningLastSent().put(tierKey, currentTierCode);
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
