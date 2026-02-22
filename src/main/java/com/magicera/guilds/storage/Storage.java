package com.magicera.guilds.storage;

import com.magicera.guilds.data.Guild;
import com.magicera.guilds.data.GuildAlignment;
import com.magicera.guilds.data.GuildRole;
import com.magicera.guilds.data.PlayerData;
import com.magicera.guilds.util.Text;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.*;

public final class Storage {

    private final JavaPlugin plugin;

    private File guildsFile;
    private File playersFile;
    private YamlConfiguration guildsYaml;
    private YamlConfiguration playersYaml;

    private final Map<String, Guild> guildsById = new HashMap<>();
    private final Map<UUID, PlayerData> playersById = new HashMap<>();

    public Storage(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        if (!plugin.getDataFolder().exists()) {
            //noinspection ResultOfMethodCallIgnored
            plugin.getDataFolder().mkdirs();
        }

        guildsFile = new File(plugin.getDataFolder(), "guilds.yml");
        playersFile = new File(plugin.getDataFolder(), "players.yml");

        guildsYaml = YamlConfiguration.loadConfiguration(guildsFile);
        playersYaml = YamlConfiguration.loadConfiguration(playersFile);

        guildsById.clear();
        playersById.clear();

        // Load guilds
        ConfigurationSection gSec = guildsYaml.getConfigurationSection("guilds");
        if (gSec != null) {
            for (String guildId : gSec.getKeys(false)) {
                ConfigurationSection s = gSec.getConfigurationSection(guildId);
                if (s == null) continue;

                String name = s.getString("name", guildId);
                String prefix = s.getString("prefix", guildId.substring(0, Math.min(4, guildId.length())));
                String title = s.getString("title", "");
                String alignStr = s.getString("alignment", "NEUTRAL");

                GuildAlignment alignment;
                try {
                    alignment = GuildAlignment.valueOf(alignStr.toUpperCase());
                } catch (Exception ignored) {
                    alignment = GuildAlignment.NEUTRAL;
                }

                Guild guild = new Guild(guildId, name, prefix, alignment);
                guild.setTitle(title);

                // description + founded timestamp
                guild.setDescription(s.getString("description", ""));
                guild.setFoundedAtEpochMs(s.getLong("foundedAtEpochMs", System.currentTimeMillis()));

                // Bank + tax + officer withdraw window tracking
                guild.setBankBalance(s.getDouble("bankBalance", 0.0));
                guild.setTaxPercent(s.getInt("taxPercent", 0));
                guild.setOfficerWithdrawUsed24h(s.getDouble("officerWithdrawUsed24h", 0.0));
                guild.setOfficerWithdrawWindowStartMs(s.getLong("officerWithdrawWindowStartMs", 0L));

                // Favor / impeachment / log fields
                long masterSince = s.getLong("masterOutOfFavorSinceEpochMs", 0L);
                guild.setMasterOutOfFavorSinceEpochMs(masterSince == 0L ? null : masterSince);

                long masterWarn = s.getLong("masterOutOfFavorWarnEpochMs", 0L);
                guild.setMasterOutOfFavorWarnEpochMs(masterWarn == 0L ? null : masterWarn);

                long impSince = s.getLong("impeachmentStartedEpochMs", 0L);
                guild.setImpeachmentStartedEpochMs(impSince == 0L ? null : impSince);

                guild.setKickLockUntilEpochMs(s.getLong("kickLockUntilEpochMs", 0L));

                String homeWorld = s.getString("home.world");
                if (homeWorld != null && s.contains("home.x") && s.contains("home.y") && s.contains("home.z")) {
                    guild.setHome(homeWorld, s.getInt("home.x"), s.getInt("home.y"), s.getInt("home.z"));
                }

                guild.setMembersCanClaim(s.getBoolean("membersCanClaim", false));
                guild.setFriendlyFireEnabled(s.getBoolean("friendlyFireEnabled", false));
                guild.setAllyFireEnabled(s.getBoolean("allyFireEnabled", false));

                guild.setInWar(s.getBoolean("inWar", false));
                long warEnds = s.getLong("warEndsAtEpochMs", 0L);
                guild.setWarEndsAtEpochMs(warEnds == 0L ? null : warEnds);

                long warSessionId = s.getLong("warSessionId", 0L);
                guild.setWarSessionId(warSessionId == 0L ? null : warSessionId);

                guild.getClaimedChunks().addAll(s.getStringList("claimedChunks"));

                ConfigurationSection claimTs = s.getConfigurationSection("claimTimestamps");
                if (claimTs != null) {
                    for (String key : claimTs.getKeys(false)) {
                        String decoded = key.replace("%2E", ".");
                        guild.getClaimTimestamps().put(decoded, claimTs.getLong(key, 0L));
                    }
                }

                guild.getUnstableClaims().addAll(s.getStringList("unstableClaims"));
                guild.getOverclaimedChunks().addAll(s.getStringList("overclaimedChunks"));

                ConfigurationSection overclaimWarSessionIds = s.getConfigurationSection("overclaimWarSessionIds");
                if (overclaimWarSessionIds != null) {
                    for (String key : overclaimWarSessionIds.getKeys(false)) {
                        String decoded = key.replace("%2E", ".");
                        guild.getOverclaimWarSessionIds().put(decoded, overclaimWarSessionIds.getLong(key, 0L));
                    }
                }

                ConfigurationSection overclaimTimes = s.getConfigurationSection("overclaimTimes");
                if (overclaimTimes != null) {
                    for (String key : overclaimTimes.getKeys(false)) {
                        String decoded = key.replace("%2E", ".");
                        guild.getOverclaimTimes().put(decoded, overclaimTimes.getLong(key, 0L));
                    }
                }

                ConfigurationSection overclaimedFromGuildIds = s.getConfigurationSection("overclaimedFromGuildIds");
                if (overclaimedFromGuildIds != null) {
                    for (String key : overclaimedFromGuildIds.getKeys(false)) {
                        String decoded = key.replace("%2E", ".");
                        guild.getOverclaimedFromGuildIds().put(decoded, overclaimedFromGuildIds.getString(key, ""));
                    }
                }

                String hallWorld = s.getString("hall.world");
                guild.setHasHall(s.getBoolean("hall.hasHall", false));
                long hallLastMoved = s.getLong("hall.lastMovedAtEpochMs", 0L);
                guild.setHallLastMovedAtEpochMs(hallLastMoved == 0L ? null : hallLastMoved);

                if (hallWorld != null && s.contains("hall.centerX") && s.contains("hall.centerZ")) {
                    guild.setHall(
                            hallWorld,
                            s.getInt("hall.centerX"),
                            s.getInt("hall.centerZ"),
                            s.getStringList("hall.chunks")
                    );
                }

                guild.getAllies().addAll(s.getStringList("allies"));
                guild.getEnemies().addAll(s.getStringList("enemies"));
                guild.getPendingAllyRequests().addAll(s.getStringList("pendingAllyRequests"));
                guild.getPendingWarRequests().addAll(s.getStringList("pendingWarRequests"));

                // pending truce requests
                guild.getPendingTruceRequests().addAll(s.getStringList("pendingTruceRequests"));

                // ally/war request cooldowns
                ConfigurationSection allyCooldowns = s.getConfigurationSection("allyRequestCooldowns");
                if (allyCooldowns != null) {
                    for (String guildIdKey : allyCooldowns.getKeys(false)) {
                        guild.getAllyRequestCooldowns().put(guildIdKey, allyCooldowns.getLong(guildIdKey, 0L));
                    }
                }
                ConfigurationSection warCooldowns = s.getConfigurationSection("warRequestCooldowns");
                if (warCooldowns != null) {
                    for (String guildIdKey : warCooldowns.getKeys(false)) {
                        guild.getWarRequestCooldowns().put(guildIdKey, warCooldowns.getLong(guildIdKey, 0L));
                    }
                }

                ConfigurationSection warningLastSent = s.getConfigurationSection("warningLastSent");
                if (warningLastSent != null) {
                    for (String tier : warningLastSent.getKeys(false)) {
                        guild.getWarningLastSent().put(tier, warningLastSent.getLong(tier, 0L));
                    }
                }
                guild.getWarningSentWarSession().addAll(s.getStringList("warningSentWarSession"));

                List<String> logs = s.getStringList("logEntries");
                if (logs != null && !logs.isEmpty()) {
                    guild.getLogEntries().addAll(logs);
                }

                ConfigurationSection mem = s.getConfigurationSection("members");
                if (mem != null) {
                    for (String uuidStr : mem.getKeys(false)) {
                        try {
                            UUID uuid = UUID.fromString(uuidStr);
                            String roleStr = mem.getString(uuidStr, "MEMBER");
                            GuildRole role;
                            try {
                                role = GuildRole.valueOf(roleStr.toUpperCase());
                            } catch (Exception ignored2) {
                                role = GuildRole.MEMBER;
                            }
                            guild.setRole(uuid, role);

                            long joinedAt = s.getLong("memberJoinedAt." + uuidStr, 0L);
                            if (joinedAt > 0L) {
                                guild.getMemberJoinedAt().put(uuid, joinedAt);
                            }

                            if (s.contains("impeachmentVotes." + uuidStr)) {
                                guild.getImpeachmentVotes().put(uuid, s.getBoolean("impeachmentVotes." + uuidStr));
                            }
                        } catch (IllegalArgumentException ignored3) {
                        }
                    }
                }

                guildsById.put(guildId, guild);
            }
        }

        // Load players
        ConfigurationSection pSec = playersYaml.getConfigurationSection("players");
        if (pSec != null) {
            for (String uuidStr : pSec.getKeys(false)) {
                try {
                    UUID uuid = UUID.fromString(uuidStr);
                    PlayerData pd = new PlayerData(uuid);

                    pd.setGuildId(pSec.getString(uuidStr + ".guildId", null));
                    pd.setAlignmentScore(pSec.getInt(uuidStr + ".alignmentScore", 0));

                    long since = pSec.getLong(uuidStr + ".outOfAlignmentSinceEpochMs", 0L);
                    pd.setOutOfAlignmentSinceEpochMs(since == 0L ? null : since);

                    long lastWarn = pSec.getLong(uuidStr + ".lastOutOfAlignmentWarnEpochMs", 0L);
                    pd.setLastOutOfAlignmentWarnEpochMs(lastWarn == 0L ? null : lastWarn);

                    pd.setGuildTitle(pSec.getString(uuidStr + ".guildTitle", ""));
                    pd.setLastSeenEpochMs(pSec.getLong(uuidStr + ".lastSeenEpochMs", System.currentTimeMillis()));
                    pd.setGuildChatEnabled(pSec.getBoolean(uuidStr + ".guildChatEnabled", false));
                    pd.setPower(pSec.getDouble(uuidStr + ".power", 10.0));

                    // offline tax notice accumulator
                    pd.setPendingTaxNoticeAmount(pSec.getDouble(uuidStr + ".pendingTaxNoticeAmount", 0.0));

                    // pending guild messages
                    pd.getPendingGuildMessages().addAll(pSec.getStringList(uuidStr + ".pendingGuildMessages"));

                    playersById.put(uuid, pd);
                } catch (IllegalArgumentException ignored) {
                }
            }
        }
    }

    public void save() {
        if (guildsYaml == null) guildsYaml = new YamlConfiguration();
        if (playersYaml == null) playersYaml = new YamlConfiguration();

        guildsYaml.set("guilds", null);
        playersYaml.set("players", null);

        // Save guilds
        for (Guild g : guildsById.values()) {
            String base = "guilds." + g.getId();
            guildsYaml.set(base + ".name", g.getName());
            guildsYaml.set(base + ".prefix", g.getPrefix());
            guildsYaml.set(base + ".title", g.getTitle());

            // description + founded timestamp
            guildsYaml.set(base + ".description", g.getDescription());
            guildsYaml.set(base + ".foundedAtEpochMs", g.getFoundedAtEpochMs());

            guildsYaml.set(base + ".alignment", g.getAlignment().name());

            // Bank + tax + officer withdraw window tracking
            guildsYaml.set(base + ".bankBalance", g.getBankBalance());
            guildsYaml.set(base + ".taxPercent", g.getTaxPercent());
            guildsYaml.set(base + ".officerWithdrawUsed24h", g.getOfficerWithdrawUsed24h());
            guildsYaml.set(base + ".officerWithdrawWindowStartMs", g.getOfficerWithdrawWindowStartMs());

            // Favor / impeachment / log fields
            guildsYaml.set(base + ".masterOutOfFavorSinceEpochMs",
                    g.getMasterOutOfFavorSinceEpochMs() == null ? 0L : g.getMasterOutOfFavorSinceEpochMs());

            guildsYaml.set(base + ".masterOutOfFavorWarnEpochMs",
                    g.getMasterOutOfFavorWarnEpochMs() == null ? 0L : g.getMasterOutOfFavorWarnEpochMs());

            guildsYaml.set(base + ".impeachmentStartedEpochMs",
                    g.getImpeachmentStartedEpochMs() == null ? 0L : g.getImpeachmentStartedEpochMs());
            guildsYaml.set(base + ".kickLockUntilEpochMs", g.getKickLockUntilEpochMs());
            guildsYaml.set(base + ".logEntries", g.getLogEntries());

            guildsYaml.set(base + ".home.world", g.getHomeWorld());
            guildsYaml.set(base + ".home.x", g.getHomeX());
            guildsYaml.set(base + ".home.y", g.getHomeY());
            guildsYaml.set(base + ".home.z", g.getHomeZ());

            guildsYaml.set(base + ".membersCanClaim", g.isMembersCanClaim());
            guildsYaml.set(base + ".friendlyFireEnabled", g.isFriendlyFireEnabled());
            guildsYaml.set(base + ".allyFireEnabled", g.isAllyFireEnabled());

            guildsYaml.set(base + ".inWar", g.isInWar());
            guildsYaml.set(base + ".warEndsAtEpochMs", g.getWarEndsAtEpochMs() == null ? 0L : g.getWarEndsAtEpochMs());
            guildsYaml.set(base + ".warSessionId", g.getWarSessionId() == null ? 0L : g.getWarSessionId());

            guildsYaml.set(base + ".claimedChunks", new ArrayList<>(g.getClaimedChunks()));

            String claimTsBase = base + ".claimTimestamps";
            guildsYaml.set(claimTsBase, null);
            for (Map.Entry<String, Long> e : g.getClaimTimestamps().entrySet()) {
                guildsYaml.set(claimTsBase + "." + e.getKey().replace(".", "%2E"), e.getValue());
            }

            guildsYaml.set(base + ".unstableClaims", new ArrayList<>(g.getUnstableClaims()));
            guildsYaml.set(base + ".overclaimedChunks", new ArrayList<>(g.getOverclaimedChunks()));

            String overclaimWarSessionIdsBase = base + ".overclaimWarSessionIds";
            guildsYaml.set(overclaimWarSessionIdsBase, null);
            for (Map.Entry<String, Long> e : g.getOverclaimWarSessionIds().entrySet()) {
                guildsYaml.set(overclaimWarSessionIdsBase + "." + e.getKey().replace(".", "%2E"), e.getValue());
            }

            String overclaimTimesBase = base + ".overclaimTimes";
            guildsYaml.set(overclaimTimesBase, null);
            for (Map.Entry<String, Long> e : g.getOverclaimTimes().entrySet()) {
                guildsYaml.set(overclaimTimesBase + "." + e.getKey().replace(".", "%2E"), e.getValue());
            }

            String overclaimedFromGuildIdsBase = base + ".overclaimedFromGuildIds";
            guildsYaml.set(overclaimedFromGuildIdsBase, null);
            for (Map.Entry<String, String> e : g.getOverclaimedFromGuildIds().entrySet()) {
                guildsYaml.set(overclaimedFromGuildIdsBase + "." + e.getKey().replace(".", "%2E"), e.getValue());
            }

            guildsYaml.set(base + ".hall.world", g.getHallWorld());
            guildsYaml.set(base + ".hall.centerX", g.getHallCenterX());
            guildsYaml.set(base + ".hall.centerZ", g.getHallCenterZ());
            guildsYaml.set(base + ".hall.hasHall", g.hasHall());
            guildsYaml.set(base + ".hall.lastMovedAtEpochMs", g.getHallLastMovedAtEpochMs() == null ? 0L : g.getHallLastMovedAtEpochMs());
            guildsYaml.set(base + ".hall.chunks", new ArrayList<>(g.getHallChunks()));

            guildsYaml.set(base + ".allies", new ArrayList<>(g.getAllies()));
            guildsYaml.set(base + ".enemies", new ArrayList<>(g.getEnemies()));
            guildsYaml.set(base + ".pendingAllyRequests", new ArrayList<>(g.getPendingAllyRequests()));
            guildsYaml.set(base + ".pendingWarRequests", new ArrayList<>(g.getPendingWarRequests()));

            // pending truce requests
            guildsYaml.set(base + ".pendingTruceRequests", new ArrayList<>(g.getPendingTruceRequests()));

            // ally/war request cooldowns
            String allyCooldownBase = base + ".allyRequestCooldowns";
            guildsYaml.set(allyCooldownBase, null);
            for (Map.Entry<String, Long> e : g.getAllyRequestCooldowns().entrySet()) {
                guildsYaml.set(allyCooldownBase + "." + e.getKey(), e.getValue());
            }

            String warCooldownBase = base + ".warRequestCooldowns";
            guildsYaml.set(warCooldownBase, null);
            for (Map.Entry<String, Long> e : g.getWarRequestCooldowns().entrySet()) {
                guildsYaml.set(warCooldownBase + "." + e.getKey(), e.getValue());
            }

            String warningLastSentBase = base + ".warningLastSent";
            guildsYaml.set(warningLastSentBase, null);
            for (Map.Entry<String, Long> e : g.getWarningLastSent().entrySet()) {
                guildsYaml.set(warningLastSentBase + "." + e.getKey(), e.getValue());
            }
            guildsYaml.set(base + ".warningSentWarSession", new ArrayList<>(g.getWarningSentWarSession()));

            String memBase = base + ".members";
            guildsYaml.set(memBase, null);
            for (Map.Entry<UUID, GuildRole> e : g.getMembers().entrySet()) {
                guildsYaml.set(memBase + "." + e.getKey(), e.getValue().name());
            }

            String joinedBase = base + ".memberJoinedAt";
            guildsYaml.set(joinedBase, null);
            for (Map.Entry<UUID, Long> e : g.getMemberJoinedAt().entrySet()) {
                guildsYaml.set(joinedBase + "." + e.getKey(), e.getValue());
            }

            String votesBase = base + ".impeachmentVotes";
            guildsYaml.set(votesBase, null);
            for (Map.Entry<UUID, Boolean> e : g.getImpeachmentVotes().entrySet()) {
                guildsYaml.set(votesBase + "." + e.getKey(), e.getValue());
            }
        }

        // Save players
        for (PlayerData p : playersById.values()) {
            String base = "players." + p.getUuid();
            playersYaml.set(base + ".guildId", p.getGuildId());
            playersYaml.set(base + ".alignmentScore", p.getAlignmentScore());

            Long since = p.getOutOfAlignmentSinceEpochMs();
            playersYaml.set(base + ".outOfAlignmentSinceEpochMs", since == null ? 0L : since);

            Long lastWarn = p.getLastOutOfAlignmentWarnEpochMs();
            playersYaml.set(base + ".lastOutOfAlignmentWarnEpochMs", lastWarn == null ? 0L : lastWarn);

            playersYaml.set(base + ".guildTitle", p.getGuildTitle());
            playersYaml.set(base + ".lastSeenEpochMs", p.getLastSeenEpochMs());
            playersYaml.set(base + ".guildChatEnabled", p.isGuildChatEnabled());
            playersYaml.set(base + ".power", p.getPower());

            // offline tax notice accumulator
            playersYaml.set(base + ".pendingTaxNoticeAmount", p.getPendingTaxNoticeAmount());

            // pending guild messages
            playersYaml.set(base + ".pendingGuildMessages", new ArrayList<>(p.getPendingGuildMessages()));
        }

        try {
            guildsYaml.save(guildsFile);
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to save guilds.yml: " + e.getMessage());
        }

        try {
            playersYaml.save(playersFile);
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to save players.yml: " + e.getMessage());
        }
    }

    public PlayerData getOrCreatePlayer(UUID uuid) {
        return playersById.computeIfAbsent(uuid, PlayerData::new);
    }

    public Guild getGuild(String guildId) {
        return guildsById.get(guildId);
    }

    public Collection<Guild> allGuilds() {
        return Collections.unmodifiableCollection(guildsById.values());
    }

    public boolean guildExists(String guildId) {
        return guildsById.containsKey(guildId);
    }

    public boolean prefixInUse(String prefixColored) {
        String stripped = Text.stripColors(prefixColored);
        for (Guild g : guildsById.values()) {
            if (Text.stripColors(g.getPrefix()).equalsIgnoreCase(stripped)) {
                return true;
            }
        }
        return false;
    }

    public Guild createGuild(String rawName, String rawPrefix, UUID masterUuid) {
        String id = Text.normalizeId(rawName);
        String name = Text.color(rawName);
        String prefix = Text.color(rawPrefix);

        Guild g = new Guild(id, name, prefix, GuildAlignment.NEUTRAL);
        g.setRole(masterUuid, GuildRole.MASTER);

        g.setDescription("");
        g.setFoundedAtEpochMs(System.currentTimeMillis());

        guildsById.put(id, g);

        PlayerData pd = getOrCreatePlayer(masterUuid);
        pd.setGuildId(id);

        return g;
    }

    public void deleteGuild(String guildId) {
        guildsById.remove(guildId);
    }
}
