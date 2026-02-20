package com.magicera.guilds.data;

import java.util.UUID;

public final class PlayerData {
    private final UUID uuid;

    private String guildId;                  // nullable
    private int alignmentScore;              // -100..100
    private Long outOfAlignmentSinceEpochMs; // nullable (epoch ms)

    // NEW:
    private String guildTitle;               // nullable / empty ok
    private long lastSeenEpochMs;            // epoch ms (updated on quit, and on join init)
    private boolean guildChatEnabled;
    private double power;
    private double pendingTaxNoticeAmount;

    public PlayerData(UUID uuid) {
        this.uuid = uuid;
        this.alignmentScore = 0;
        this.outOfAlignmentSinceEpochMs = null;
        this.guildTitle = "";
        this.lastSeenEpochMs = System.currentTimeMillis();
        this.guildChatEnabled = false;
        this.power = 15.0;
        this.pendingTaxNoticeAmount = 0.0;
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

    public boolean isGuildChatEnabled() { return guildChatEnabled; }
    public void setGuildChatEnabled(boolean guildChatEnabled) { this.guildChatEnabled = guildChatEnabled; }

    public double getPower() { return power; }
    public void setPower(double power) { this.power = Math.max(0.0, Math.min(15.0, power)); }

    public double getPendingTaxNoticeAmount() { return pendingTaxNoticeAmount; }
    public void setPendingTaxNoticeAmount(double pendingTaxNoticeAmount) {
        this.pendingTaxNoticeAmount = Math.max(0.0, pendingTaxNoticeAmount);
    }
}
