package com.magicera.guilds.util;

import org.bukkit.ChatColor;

public final class Text {
    private Text() {}

    public static String color(String input) {
        return ChatColor.translateAlternateColorCodes('&', input);
    }

    // Normalize internal id (no colors, lowercase, alnum/_ only)
    public static String normalizeId(String input) {
        String noColors = ChatColor.stripColor(color(input));
        if (noColors == null) return "";
        String lowered = noColors.toLowerCase().trim();
        return lowered.replaceAll("[^a-z0-9_\\-]", "");
    }

    public static String stripColors(String input) {
        return ChatColor.stripColor(color(input));
    }
}
