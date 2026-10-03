package com.magicera.guilds.listeners;

import com.magicera.guilds.MagicEraGuildsPlugin;
import com.magicera.guilds.data.Guild;
import com.magicera.guilds.data.PlayerData;
import com.magicera.guilds.util.Text;
import io.papermc.paper.chat.ChatRenderer;
import io.papermc.paper.event.player.ChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

@SuppressWarnings("deprecation")
public final class GuildPublicChatListener implements Listener {

    private final MagicEraGuildsPlugin plugin;

    private final LegacyComponentSerializer legacy =
            LegacyComponentSerializer.legacySection();

    public GuildPublicChatListener(MagicEraGuildsPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(
            priority = EventPriority.HIGHEST,
            ignoreCancelled = true
    )
    public void onChat(ChatEvent event) {
        PlayerData playerData = plugin.storage().getOrCreatePlayer(
                event.getPlayer().getUniqueId()
        );

        if (playerData.getGuildId() == null) {
            return;
        }

        Guild guild = plugin.storage().getGuild(
                playerData.getGuildId()
        );

        if (guild == null) {
            return;
        }

        String tag = guild.getPrefix();

        if (tag == null || tag.isBlank()) {
            return;
        }

        Component guildPrefix = legacy.deserialize(
                "§8[§r" + Text.color(tag) + "§8]§r "
        );

        // Preserve Essentials' nickname, rank, colors, and message format.
        ChatRenderer existingRenderer = event.renderer();

        event.renderer((source, sourceDisplayName, message, viewer) ->
                Component.empty()
                        .append(guildPrefix)
                        .append(existingRenderer.render(
                                source,
                                sourceDisplayName,
                                message,
                                viewer
                        ))
        );
    }
}
