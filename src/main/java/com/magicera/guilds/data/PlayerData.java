package com.magicera.guilds.data;

import java.util.UUID;

public final class PlayerData {
    private final UUID uuid;
    private String guildId;     // nullable
    private int alignmentScore; // -100..100 (we’ll hook rules later)

    public PlayerData(UUID uuid) {
        this.uuid = uuid;
        this.alignmentScore = 0;
    }

    public UUID getUuid() { return uuid; }
    public String getGuildId() { return guildId; }
    public int getAlignmentScore() { return alignmentScore; }

    public void setGuildId(String guildId) { this.guildId = guildId; }
    public void setAlignmentScore(int alignmentScore) { this.alignmentScore = alignmentScore; }
}
