package com.magicera.guilds.guilds;

import com.magicera.guilds.MagicEraGuildsPlugin;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class InviteManager {

    public static final class Invite {
        public final String guildId;
        public final UUID inviter;
        public final long createdAtMs;

        public Invite(String guildId, UUID inviter, long createdAtMs) {
            this.guildId = guildId;
            this.inviter = inviter;
            this.createdAtMs = createdAtMs;
        }
    }

    private final MagicEraGuildsPlugin plugin;
    private final Map<UUID, Invite> invites = new HashMap<>();

    public InviteManager(MagicEraGuildsPlugin plugin) {
        this.plugin = plugin;
    }

    public void setInvite(UUID invitedPlayer, String guildId, UUID inviter) {
        invites.put(invitedPlayer, new Invite(guildId, inviter, System.currentTimeMillis()));
    }

    public Invite getInvite(UUID invitedPlayer) {
        Invite inv = invites.get(invitedPlayer);
        if (inv == null) return null;

        long expireSeconds = Math.max(10, plugin.getConfig().getLong("invites.expire-seconds", 300));
        long ageMs = System.currentTimeMillis() - inv.createdAtMs;

        if (ageMs > expireSeconds * 1000L) {
            invites.remove(invitedPlayer);
            return null;
        }
        return inv;
    }

    public void clearInvite(UUID invitedPlayer) {
        invites.remove(invitedPlayer);
    }

    public void clearInvite(Player invitedPlayer) {
        if (invitedPlayer != null) invites.remove(invitedPlayer.getUniqueId());
    }

    public void clearAll() {
        invites.clear();
    }
}
