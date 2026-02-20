package com.magicera.guilds.util;

import com.magicera.guilds.data.GuildAlignment;

public final class AlignmentUtil {

    private AlignmentUtil() {}

    // Score scale:
    // -100..-50 = Sin
    // -49..49 = Balance
    // 50..100 = Honor
    public static GuildAlignment groupFromScore(int score) {
        if (score >= 50) return GuildAlignment.HONORABLE;
        if (score <= -50) return GuildAlignment.DARK;
        return GuildAlignment.NEUTRAL;
    }

    // Snap neutral players to a guild alignment when joining
    public static int snapScoreToGuild(GuildAlignment guildAlign) {
        return switch (guildAlign) {
            case HONORABLE -> 50;
            case DARK -> -50;
            case NEUTRAL -> 0;
        };
    }

    // Player-facing names
    public static String displayName(GuildAlignment a) {
        return switch (a) {
            case HONORABLE -> "Honor";
            case NEUTRAL -> "Balance";
            case DARK -> "Sin";
        };
    }

    // Guild type names (your requested mapping)
    public static String guildTypeName(GuildAlignment a) {
        return switch (a) {
            case HONORABLE -> "Honorable Guild";
            case NEUTRAL -> "Independent Guild";
            case DARK -> "Dark Guild";
        };
    }
}
