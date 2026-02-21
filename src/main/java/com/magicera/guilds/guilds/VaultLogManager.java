package com.magicera.guilds.guilds;

import com.magicera.guilds.MagicEraGuildsPlugin;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public final class VaultLogManager {

    private static final DateTimeFormatter DAY_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE;

    private final MagicEraGuildsPlugin plugin;
    private final File rootDir;

    public VaultLogManager(MagicEraGuildsPlugin plugin) {
        this.plugin = plugin;
        this.rootDir = new File(plugin.getDataFolder(), "vault-logs");

        if (!rootDir.exists()) {
            //noinspection ResultOfMethodCallIgnored
            rootDir.mkdirs();
        }
    }

    public void appendEntries(String guildId, List<String> entries) {
        if (entries == null || entries.isEmpty()) return;

        LocalDate today = LocalDate.now();
        File dayFile = getDayFile(guildId, today);
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(dayFile);

        List<String> existing = yaml.getStringList("entries");
        List<String> merged = new ArrayList<>(existing.size() + entries.size());
        merged.addAll(existing);
        merged.addAll(entries);

        yaml.set("guildId", guildId);
        yaml.set("day", DAY_FORMAT.format(today));
        yaml.set("entries", merged);

        try {
            yaml.save(dayFile);
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to save vault logs file '" + dayFile.getName() + "': " + e.getMessage());
        }
    }

    private File getDayFile(String guildId, LocalDate day) {
        File guildDir = new File(rootDir, guildId);
        if (!guildDir.exists()) {
            //noinspection ResultOfMethodCallIgnored
            guildDir.mkdirs();
        }
        return new File(guildDir, DAY_FORMAT.format(day) + ".yml");
    }
}
