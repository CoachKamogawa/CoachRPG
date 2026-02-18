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
                String alignStr = s.getString("alignment", "NEUTRAL");

                GuildAlignment alignment;
                try {
                    alignment = GuildAlignment.valueOf(alignStr.toUpperCase());
                } catch (Exception ignored) {
                    alignment = GuildAlignment.NEUTRAL;
                }

                Guild guild = new Guild(guildId, name, prefix, alignment);

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
            guildsYaml.set(base + ".alignment", g.getAlignment().name());

            String memBase = base + ".members";
            guildsYaml.set(memBase, null);
            for (Map.Entry<UUID, GuildRole> e : g.getMembers().entrySet()) {
                guildsYaml.set(memBase + "." + e.getKey(), e.getValue().name());
            }
        }

        // Save players
        for (PlayerData p : playersById.values()) {
            String base = "players." + p.getUuid();
            playersYaml.set(base + ".guildId", p.getGuildId());
            playersYaml.set(base + ".alignmentScore", p.getAlignmentScore());

            Long since = p.getOutOfAlignmentSinceEpochMs();
            playersYaml.set(base + ".outOfAlignmentSinceEpochMs", since == null ? 0L : since);
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

        guildsById.put(id, g);

        PlayerData pd = getOrCreatePlayer(masterUuid);
        pd.setGuildId(id);

        return g;
    }
}
