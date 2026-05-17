package com.magicera.guilds.listeners;

import com.magicera.guilds.MagicEraGuildsPlugin;
import com.magicera.guilds.data.Guild;
import com.magicera.guilds.data.PlayerData;
import com.magicera.guilds.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;

public final class GuildChatListener implements Listener {

    private final MagicEraGuildsPlugin plugin;

    public GuildChatListener(MagicEraGuildsPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        PlayerData pd = plugin.storage().getOrCreatePlayer(player.getUniqueId());

        if (!pd.isGuildChatEnabled() || pd.getGuildId() == null) return;

        Guild guild = plugin.storage().getGuild(pd.getGuildId());
        if (guild == null) return;

        event.setCancelled(true);

        String memberTitle = pd.getGuildTitle();
        String titlePart = memberTitle.isEmpty() ? "" : "§8[§r" + Text.color(memberTitle) + "§8]§r ";
        String guildPart = "§7[§aGuild§7] §8[" + guild.getPrefix() + "§8]§r ";
        String msg = guildPart + titlePart + plugin.names().displayName(player) + "§7: §f" + event.getMessage();

        for (Player online : Bukkit.getOnlinePlayers()) {
            PlayerData target = plugin.storage().getOrCreatePlayer(online.getUniqueId());
            if (guild.getId().equals(target.getGuildId())) {
                online.sendMessage(msg);
            }
        }
    }
}
