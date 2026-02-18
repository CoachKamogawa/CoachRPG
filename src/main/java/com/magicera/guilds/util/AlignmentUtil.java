package com.magicera.guilds.util;

import com.magicera.guilds.data.GuildAlignment;

public final class AlignmentUtil {
    private AlignmentUtil() {}

    public static GuildAlignment groupFromScore(int score) {
        if (score >= 50) return GuildAlignment.HONORABLE;
        if (score <= -50) return GuildAlignment.DARK;
        return GuildAlignment.NEUTRAL;
    }

    // If you want to “snap” a neutral player into a guild’s alignment:
    public static int snapScoreToGuild(GuildAlignment g) {
        return switch (g) {
            case HONORABLE -> 50;
            case NEUTRAL -> 0;
            case DARK -> -50;
        };
    }
}
