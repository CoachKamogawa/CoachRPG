package com.magicera.guilds.data;

import java.util.*;

public final class Guild {

    private final String id;
    private String name;   // colored
    private String prefix; // colored
    private String title;  // colored
    private String description;
    private long foundedAtEpochMs;
    private GuildAlignment alignment;

    private final Map<UUID, GuildRole> members = new HashMap<>();
    private final Map<UUID, Long> memberJoinedAt = new HashMap<>();
    private final List<String> logEntries = new ArrayList<>();

    // Bank + tax + officer withdraw window tracking
    private double bankBalance;
    private int taxPercent; // 0..5
    private double officerWithdrawUsed24h;
    private long officerWithdrawWindowStartMs;

    private Long masterOutOfFavorSinceEpochMs;

    // impeachment
    private Long impeachmentStartedEpochMs;
    private long kickLockUntilEpochMs;
    private final Map<UUID, Boolean> impeachmentVotes = new HashMap<>();

    private String homeWorld;
    private Integer homeX;
    private Integer homeY;
    private Integer homeZ;

    private boolean membersCanClaim;
    private boolean inWar;
    private Long warEndsAtEpochMs;

    private final Set<String> claimedChunks = new HashSet<>();
    private final Set<String> allies = new HashSet<>();
    private final Set<String> enemies = new HashSet<>();

    public Guild(String id, String name, String prefix, GuildAlignment alignment) {
        this.id = id;
        this.name = name;
        this.prefix = prefix;
        this.title = "";
        this.description = "";
        this.foundedAtEpochMs = System.currentTimeMillis();
        this.alignment = alignment;

        this.bankBalance = 0.0;
        this.taxPercent = 0;
        this.officerWithdrawUsed24h = 0.0;
        this.officerWithdrawWindowStartMs = 0L;

        this.masterOutOfFavorSinceEpochMs = null;
        this.impeachmentStartedEpochMs = null;
        this.kickLockUntilEpochMs = 0L;

        this.homeWorld = null;
        this.homeX = null;
        this.homeY = null;
        this.homeZ = null;

        this.membersCanClaim = false;
        this.inWar = false;
        this.warEndsAtEpochMs = null;
    }

    public String getId() { return id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getPrefix() { return prefix; }
    public void setPrefix(String prefix) { this.prefix = prefix; }

    public String getTitle() { return title == null ? "" : title; }
    public void setTitle(String title) { this.title = title; }

    public String getDescription() { return description == null ? "" : description; }
    public void setDescription(String description) { this.description = description == null ? "" : description; }

    public long getFoundedAtEpochMs() { return foundedAtEpochMs; }
    public void setFoundedAtEpochMs(long foundedAtEpochMs) {
        this.foundedAtEpochMs = Math.max(0L, foundedAtEpochMs);
    }

    public GuildAlignment getAlignment() { return alignment; }
    public void setAlignment(GuildAlignment alignment) { this.alignment = alignment; }

    public Map<UUID, GuildRole> getMembers() { return members; }
    public Map<UUID, Long> getMemberJoinedAt() { return memberJoinedAt; }
    public List<String> getLogEntries() { return logEntries; }

    public void setRole(UUID uuid, GuildRole role) {
        if (role == null) role = GuildRole.MEMBER;
        members.put(uuid, role);
        memberJoinedAt.putIfAbsent(uuid, System.currentTimeMillis());
    }

    public void removeMember(UUID uuid) {
        members.remove(uuid);
        memberJoinedAt.remove(uuid);
        impeachmentVotes.remove(uuid);
    }

    public double getBankBalance() { return bankBalance; }
    public void setBankBalance(double bankBalance) {
        this.bankBalance = Math.max(0.0, bankBalance);
    }

    public int getTaxPercent() { return taxPercent; }
    public void setTaxPercent(int taxPercent) {
        this.taxPercent = Math.max(0, Math.min(5, taxPercent));
    }

    public double getOfficerWithdrawUsed24h() { return officerWithdrawUsed24h; }
    public void setOfficerWithdrawUsed24h(double v) {
        this.officerWithdrawUsed24h = Math.max(0.0, v);
    }

    public long getOfficerWithdrawWindowStartMs() { return officerWithdrawWindowStartMs; }
    public void setOfficerWithdrawWindowStartMs(long ms) {
        this.officerWithdrawWindowStartMs = Math.max(0L, ms);
    }

    public Long getMasterOutOfFavorSinceEpochMs() { return masterOutOfFavorSinceEpochMs; }
    public void setMasterOutOfFavorSinceEpochMs(Long ms) {
        this.masterOutOfFavorSinceEpochMs = ms;
    }

    public Long getImpeachmentStartedEpochMs() { return impeachmentStartedEpochMs; }
    public void setImpeachmentStartedEpochMs(Long ms) {
        this.impeachmentStartedEpochMs = ms;
    }

    public long getKickLockUntilEpochMs() { return kickLockUntilEpochMs; }
    public void setKickLockUntilEpochMs(long ms) {
        this.kickLockUntilEpochMs = Math.max(0L, ms);
    }

    public Map<UUID, Boolean> getImpeachmentVotes() { return impeachmentVotes; }

    public String getHomeWorld() { return homeWorld; }
    public Integer getHomeX() { return homeX; }
    public Integer getHomeY() { return homeY; }
    public Integer getHomeZ() { return homeZ; }

    public void setHome(String world, int x, int y, int z) {
        this.homeWorld = world;
        this.homeX = x;
        this.homeY = y;
        this.homeZ = z;
    }

    public boolean hasHome() {
        return homeWorld != null && homeX != null && homeY != null && homeZ != null;
    }

    public boolean isMembersCanClaim() { return membersCanClaim; }
    public void setMembersCanClaim(boolean membersCanClaim) {
        this.membersCanClaim = membersCanClaim;
    }

    public boolean isInWar() { return inWar; }
    public void setInWar(boolean inWar) { this.inWar = inWar; }

    public Long getWarEndsAtEpochMs() { return warEndsAtEpochMs; }
    public void setWarEndsAtEpochMs(Long warEndsAtEpochMs) {
        this.warEndsAtEpochMs = warEndsAtEpochMs;
    }

    public Set<String> getClaimedChunks() { return claimedChunks; }
    public Set<String> getAllies() { return allies; }
    public Set<String> getEnemies() { return enemies; }

    public static String chunkKey(String world, int x, int z) {
        return world + ":" + x + ":" + z;
    }

    public boolean claimsChunk(String world, int x, int z) {
        return claimedChunks.contains(chunkKey(world, x, z));
    }

    public void claimChunk(String world, int x, int z) {
        claimedChunks.add(chunkKey(world, x, z));
    }

    public void addLogEntry(String entry) {
        logEntries.add(0, entry);
        if (logEntries.size() > 250) {
            logEntries.remove(logEntries.size() - 1);
        }
    }
}
