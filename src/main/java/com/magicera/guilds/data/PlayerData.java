package com.magicera.guilds.data;

import java.util.UUID;

public final class PlayerData {
    private final UUID uuid;

    private String guildId;                 // nullable
    private int alignmentScore;             // -100..100
    private Long outOfAlignmentSinceEpochMs; // nullable, real-time epoch ms

    public PlayerData(UUID uuid) {
        this.uuid = uuid;
        this.alignmentScore = 0;
        this.outOfAlignmentSinceEpochMs = null;
    }

    public UUID getUuid() { return uuid; }

    public String getGuildId() { return guildId; }
    public void setGuildId(String guildId) { this.guildId = guildId; }

    public int getAlignmentScore() { return alignmentScore; }
    public void setAlignmentScore(int alignmentScore) { this.alignmentScore = alignmentScore; }

    public Long getOutOfAlignmentSinceEpochMs() { return outOfAlignmentSinceEpochMs; }
    public void setOutOfAlignmentSinceEpochMs(Long v) { this.outOfAlignmentSinceEpochMs = v; }
}
