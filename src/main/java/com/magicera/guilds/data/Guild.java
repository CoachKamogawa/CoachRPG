package com.magicera.guilds.data;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class Guild {

    private final String id;
    private String name;   // colored
    private String prefix; // colored
    private String title;  // colored
    private GuildAlignment alignment;

    private final Map<UUID, GuildRole> members = new HashMap<>();

    // Bank + tax + officer withdraw window tracking
    private double bankBalance;
    private int taxPercent; // 0..9
    private double officerWithdrawUsed24h;
    private long officerWithdrawWindowStartMs;

    public Guild(String id, String name, String prefix, GuildAlignment alignment) {
        this.id = id;
        this.name = name;
        this.prefix = prefix;
        this.title = "";
        this.alignment = alignment;

        this.bankBalance = 0.0;
        this.taxPercent = 0;
        this.officerWithdrawUsed24h = 0.0;
        this.officerWithdrawWindowStartMs = 0L;
    }

    public String getId() { return id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getPrefix() { return prefix; }
    public void setPrefix(String prefix) { this.prefix = prefix; }

    public String getTitle() { return title == null ? "" : title; }
    public void setTitle(String title) { this.title = title; }

    public GuildAlignment getAlignment() { return alignment; }
    public void setAlignment(GuildAlignment alignment) { this.alignment = alignment; }

    public Map<UUID, GuildRole> getMembers() { return members; }

    public void setRole(UUID uuid, GuildRole role) {
        if (role == null) role = GuildRole.MEMBER;
        members.put(uuid, role);
    }

    public double getBankBalance() { return bankBalance; }
    public void setBankBalance(double bankBalance) { this.bankBalance = Math.max(0.0, bankBalance); }

    public int getTaxPercent() { return taxPercent; }
    public void setTaxPercent(int taxPercent) { this.taxPercent = Math.max(0, Math.min(9, taxPercent)); }

    public double getOfficerWithdrawUsed24h() { return officerWithdrawUsed24h; }
    public void setOfficerWithdrawUsed24h(double v) { this.officerWithdrawUsed24h = Math.max(0.0, v); }

    public long getOfficerWithdrawWindowStartMs() { return officerWithdrawWindowStartMs; }
    public void setOfficerWithdrawWindowStartMs(long ms) { this.officerWithdrawWindowStartMs = Math.max(0L, ms); }
}
