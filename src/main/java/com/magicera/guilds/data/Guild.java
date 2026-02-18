package com.magicera.guilds.data;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class Guild {
    private final String id;              // internal unique id (normalized name)
    private String name;                  // displayable name (can have colors)
    private String prefix;                // 2-4 chars, color allowed
    private GuildAlignment alignment;     // set at create (later we can prompt)
    private final Map<UUID, GuildRole> members = new HashMap<>();

    public Guild(String id, String name, String prefix, GuildAlignment alignment) {
        this.id = id;
        this.name = name;
        this.prefix = prefix;
        this.alignment = alignment;
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public String getPrefix() { return prefix; }
    public GuildAlignment getAlignment() { return alignment; }
    public Map<UUID, GuildRole> getMembers() { return members; }

    public void setName(String name) { this.name = name; }
    public void setPrefix(String prefix) { this.prefix = prefix; }
    public void setAlignment(GuildAlignment alignment) { this.alignment = alignment; }

    public void setRole(UUID uuid, GuildRole role) {
        members.put(uuid, role);
    }

    public boolean isMember(UUID uuid) {
        return members.containsKey(uuid);
    }
}
