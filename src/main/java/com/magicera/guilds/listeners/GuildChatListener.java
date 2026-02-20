package com.magicera.guilds.listeners;

import com.magicera.guilds.MagicEraGuildsPlugin;
import com.magicera.guilds.data.Guild;
import com.magicera.guilds.data.PlayerData;
import com.magicera.guilds.util.Text;
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
        if (pd.getGuildId() == null) return;

        Guild guild = plugin.storage().getGuild(pd.getGuildId());
        if (guild == null) return;

        String title = guild.getTitle().isEmpty() ? guild.getName() : guild.getTitle();
        String tag = "§8[§r" + Text.color(title) + "§8]§r ";
        event.setFormat(tag + "%1$s§7: §f%2$s");
    }
}
