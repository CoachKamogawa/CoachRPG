package com.magicera.guilds.util;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.UUID;

public final class NicknameService {

    private static final String COACH_RENAME_PLUGIN = "CoachRename";

    private Object nicknameManager;
    private Method getDisplayNameMethod;
    private Method getPlainNameMethod;
    private Method findByNicknameOrUsernameMethod;

    public void hook() {
        this.nicknameManager = null;
        this.getDisplayNameMethod = null;
        this.getPlainNameMethod = null;
        this.findByNicknameOrUsernameMethod = null;

        Plugin plugin = Bukkit.getPluginManager().getPlugin(COACH_RENAME_PLUGIN);
        if (plugin == null || !plugin.isEnabled()) {
            return;
        }

        try {
            Object manager = resolveNicknameManager(plugin);
            if (manager == null) {
                return;
            }

            Class<?> managerClass = manager.getClass();
            this.getDisplayNameMethod = managerClass.getMethod("getDisplayName", UUID.class, String.class);
            this.getPlainNameMethod = managerClass.getMethod("getPlainName", UUID.class, String.class);
            this.findByNicknameOrUsernameMethod = managerClass.getMethod("findByNicknameOrUsername", String.class);
            this.nicknameManager = manager;
        } catch (ReflectiveOperationException | SecurityException ignored) {
            this.nicknameManager = null;
            this.getDisplayNameMethod = null;
            this.getPlainNameMethod = null;
            this.findByNicknameOrUsernameMethod = null;
        }
    }

    public boolean hooked() {
        return nicknameManager != null && getDisplayNameMethod != null && getPlainNameMethod != null;
    }

    public String displayName(Player player) {
        if (player == null) return "Unknown";
        return displayName(player.getUniqueId(), player.getName());
    }

    public String displayName(UUID uuid) {
        if (uuid == null) return "Unknown";

        Player online = Bukkit.getPlayer(uuid);
        if (online != null) return displayName(online);

        OfflinePlayer offline = Bukkit.getOfflinePlayer(uuid);
        String fallback = offline.getName() == null || offline.getName().isBlank()
                ? uuid.toString()
                : offline.getName();

        return displayName(uuid, fallback);
    }

    public String plainName(Player player) {
        if (player == null) return "Unknown";
        return plainName(player.getUniqueId(), player.getName());
    }

    public String plainName(UUID uuid) {
        if (uuid == null) return "Unknown";

        Player online = Bukkit.getPlayer(uuid);
        if (online != null) return plainName(online);

        OfflinePlayer offline = Bukkit.getOfflinePlayer(uuid);
        String fallback = offline.getName() == null || offline.getName().isBlank()
                ? uuid.toString()
                : offline.getName();

        return plainName(uuid, fallback);
    }

    public UUID findPlayerId(String input) {
        if (input == null || input.isBlank()) return null;

        if (hooked() && findByNicknameOrUsernameMethod != null) {
            try {
                Object result = findByNicknameOrUsernameMethod.invoke(nicknameManager, input);
                if (result instanceof UUID uuid) return uuid;
            } catch (ReflectiveOperationException ignored) {
            }
        }

        Player exact = Bukkit.getPlayerExact(input);
        if (exact != null) return exact.getUniqueId();

        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online.getName().equalsIgnoreCase(input)) {
                return online.getUniqueId();
            }
        }

        OfflinePlayer offline = Bukkit.getOfflinePlayer(input);
        if (offline.getName() != null || offline.hasPlayedBefore()) {
            return offline.getUniqueId();
        }

        return null;
    }

    private String displayName(UUID uuid, String realUsername) {
        if (!hooked()) return realUsername;

        try {
            Object result = getDisplayNameMethod.invoke(nicknameManager, uuid, realUsername);
            return result instanceof String value && !value.isBlank() ? value : realUsername;
        } catch (ReflectiveOperationException ignored) {
            return realUsername;
        }
    }

    private String plainName(UUID uuid, String realUsername) {
        if (!hooked()) return realUsername;

        try {
            Object result = getPlainNameMethod.invoke(nicknameManager, uuid, realUsername);
            return result instanceof String value && !value.isBlank() ? value : realUsername;
        } catch (ReflectiveOperationException ignored) {
            return realUsername;
        }
    }

    private Object resolveNicknameManager(Plugin plugin) throws ReflectiveOperationException {
        try {
            Method getter = plugin.getClass().getMethod("nicknameManager");
            return getter.invoke(plugin);
        } catch (NoSuchMethodException ignored) {
            Field field = plugin.getClass().getDeclaredField("nicknameManager");
            field.setAccessible(true);
            return field.get(plugin);
        }
    }
}
