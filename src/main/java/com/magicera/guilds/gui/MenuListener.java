package com.magicera.guilds.gui;

import com.magicera.guilds.MagicEraGuildsPlugin;
import com.magicera.guilds.data.Guild;
import com.magicera.guilds.data.PlayerData;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;

public final class MenuListener implements Listener {

    private final MagicEraGuildsPlugin plugin;

    public MenuListener(MagicEraGuildsPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        String title = event.getView().getTitle();
        int raw = event.getRawSlot();

        if (raw < 0 || raw >= event.getView().getTopInventory().getSize()) return;

        // Favor menu always accessible
        if (title.equals(Menus.TITLE_FAVOR)) {
            event.setCancelled(true);

            // Back bar
            if (raw >= 0 && raw < 9) {
                player.openInventory(Menus.mainMenu(plugin, player.getUniqueId()));
            }
            return;
        }

        // Main menu
        if (title.equals(Menus.TITLE_MAIN)) {
            event.setCancelled(true);

            if (raw == 11) {
                PlayerData pd = plugin.storage().getOrCreatePlayer(player.getUniqueId());
                if (pd.getGuildId() == null) {
                    player.sendMessage("§cYou are not in a guild.");
                    return;
                }
                Guild g = plugin.storage().getGuild(pd.getGuildId());
                if (g == null) {
                    pd.setGuildId(null);
                    plugin.storage().save();
                    player.sendMessage("§cYour guild data was missing.");
                    return;
                }
                player.openInventory(Menus.yourGuildMenu(g));
                return;
            }

            if (raw == 15) {
                player.openInventory(Menus.favorMenu(plugin, player.getUniqueId()));
                return;
            }

            return;
        }

        PlayerData pd = plugin.storage().getOrCreatePlayer(player.getUniqueId());
        Guild g = pd.getGuildId() == null ? null : plugin.storage().getGuild(pd.getGuildId());

        // Your guild menu
        if (title.equals(Menus.TITLE_YOUR_GUILD)) {
            event.setCancelled(true);

            if (raw >= 0 && raw < 9) {
                player.openInventory(Menus.mainMenu(plugin, player.getUniqueId()));
                return;
            }

            if (g == null) {
                player.sendMessage("§cYou are not in a guild.");
                player.openInventory(Menus.mainMenu(plugin, player.getUniqueId()));
                return;
            }

            if (raw == 12) {
                player.openInventory(Menus.vaultMenu(g, g.getBankBalance()));
                return;
            }
            if (raw == 13) {
                player.openInventory(Menus.membersMenu(plugin, g));
                return;
            }
            if (raw == 14) {
                player.openInventory(Menus.relationsMenu(g));
                return;
            }
            return;
        }

        // Vault
        if (title.equals(Menus.TITLE_VAULT)) {
            event.setCancelled(true);
            if (raw >= 0 && raw < 9) {
                if (g != null) player.openInventory(Menus.yourGuildMenu(g));
                else player.openInventory(Menus.mainMenu(plugin, player.getUniqueId()));
            }
            return;
        }

        // Members
        if (title.equals(Menus.TITLE_MEMBERS)) {
            event.setCancelled(true);
            if (raw >= 0 && raw < 9) {
                if (g != null) player.openInventory(Menus.yourGuildMenu(g));
                else player.openInventory(Menus.mainMenu(plugin, player.getUniqueId()));
            }
            return;
        }

        // Relations
        if (title.equals(Menus.TITLE_RELATIONS)) {
            event.setCancelled(true);
            if (raw >= 0 && raw < 9) {
                if (g != null) player.openInventory(Menus.yourGuildMenu(g));
                else player.openInventory(Menus.mainMenu(plugin, player.getUniqueId()));
            }
        }
    }
}
