package com.magicera.guilds.guilds;

import com.magicera.guilds.MagicEraGuildsPlugin;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public final class VaultManager {

    private final MagicEraGuildsPlugin plugin;
    private final File file;
    private YamlConfiguration yaml;

    public VaultManager(MagicEraGuildsPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "vaults.yml");
        reload();
    }

    public void reload() {
        if (!plugin.getDataFolder().exists()) {
            //noinspection ResultOfMethodCallIgnored
            plugin.getDataFolder().mkdirs();
        }
        yaml = YamlConfiguration.loadConfiguration(file);
    }

    public ItemStack[] loadVault(String guildId) {
        List<?> raw = yaml.getList("vaults." + guildId, null);
        ItemStack[] contents = new ItemStack[45]; // slots 9..53 in GUI

        if (raw == null) return contents;

        for (int i = 0; i < contents.length && i < raw.size(); i++) {
            Object o = raw.get(i);
            if (o instanceof ItemStack is) {
                contents[i] = is;
            } else {
                contents[i] = new ItemStack(Material.AIR);
            }
        }
        return contents;
    }

    public void saveVault(String guildId, ItemStack[] contents45) {
        List<ItemStack> list = new ArrayList<>(45);
        for (int i = 0; i < 45; i++) {
            ItemStack it = contents45[i];
            list.add(it == null ? new ItemStack(Material.AIR) : it);
        }
        yaml.set("vaults." + guildId, list);
        try {
            yaml.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to save vaults.yml: " + e.getMessage());
        }
    }
}
