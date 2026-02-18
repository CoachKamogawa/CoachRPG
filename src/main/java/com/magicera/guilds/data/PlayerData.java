package com.magicera.guilds.data;

import java.util.UUID;

public final class PlayerData {
    private final UUID uuid;

    private String guildId;                 // nullable
    private int alignmentScore;             // -100..100
    private Long outOfAlignmentSinceEpochMs; // nullable (epoch ms)

    // NEW:
    private String guildTitle;              // nullable / empty ok
    private long lastSeenEpochMs;           // epoch ms (updated on quit, and on join init)

    public PlayerData(UUID uuid) {
        this.uuid = uuid;
        this.alignmentScore = 0;
        this.outOfAlignmentSinceEpochMs = null;
        this.guildTitle = "";
        this.lastSeenEpochMs = System.currentTimeMillis();
    }

    public UUID getUuid() { return uuid; }

    public String getGuildId() { return guildId; }
    public void setGuildId(String guildId) { this.guildId = guildId; }

    public int getAlignmentScore() { return alignmentScore; }
    public void setAlignmentScore(int alignmentScore) { this.alignmentScore = alignmentScore; }

    public Long getOutOfAlignmentSinceEpochMs() { return outOfAlignmentSinceEpochMs; }
    public void setOutOfAlignmentSinceEpochMs(Long v) { this.outOfAlignmentSinceEpochMs = v; }

    public String getGuildTitle() { return guildTitle == null ? "" : guildTitle; }
    public void setGuildTitle(String guildTitle) { this.guildTitle = guildTitle; }

    public long getLastSeenEpochMs() { return lastSeenEpochMs; }
    public void setLastSeenEpochMs(long lastSeenEpochMs) { this.lastSeenEpochMs = lastSeenEpochMs; }
}
