package com.magicera.guilds.data;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

public final class Party {
    private final String id;
    private String name;
    private UUID leader;
    private final Set<UUID> members;
    private long createdAtEpochMs;

    public Party(String id, String name, UUID leader, long createdAtEpochMs) {
        this.id = id;
        this.name = name;
        this.leader = leader;
        this.createdAtEpochMs = createdAtEpochMs;
        this.members = new LinkedHashSet<>();
        this.members.add(leader);
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public UUID getLeader() { return leader; }
    public void setLeader(UUID leader) { this.leader = leader; }
    public Set<UUID> getMembers() { return members; }
    public long getCreatedAtEpochMs() { return createdAtEpochMs; }
    public void setCreatedAtEpochMs(long createdAtEpochMs) { this.createdAtEpochMs = createdAtEpochMs; }

    public boolean isMember(UUID uuid) {
        return members.contains(uuid);
    }

    public int size() {
        return members.size();
    }

    public boolean addMember(UUID uuid) {
        if (members.contains(uuid)) return false;
        if (members.size() >= 4) return false;
        return members.add(uuid);
    }

    public boolean removeMember(UUID uuid) {
        return members.remove(uuid);
    }
}
