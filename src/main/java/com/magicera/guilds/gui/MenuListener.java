package com.magicera.guilds.gui;

import com.magicera.guilds.MagicEraGuildsPlugin;
import com.magicera.guilds.data.Guild;
import com.magicera.guilds.data.PlayerData;
import org.bukkit.Material;
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

        // only handle clicks in the top inventory
        if (raw < 0 || raw >= event.getView().getTopInventory().getSize()) return;

        // ----- ALIGNMENT: always accessible (no guild requirement)
        if (title.equals(Menus.TITLE_ALIGNMENT)) {
            event.setCancelled(true);

            // back bar
            if (raw >= 0 && raw < 9) {
                player.openInventory(Menus.mainMenu(plugin, player.getUniqueId()));
            }
            return;
        }

        // ----- MAIN MENU
        if (title.equals(Menus.TITLE_MAIN)) {
            event.setCancelled(true);

            if (raw == 11) {
                // Your Guild
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
                // Alignment
                player.openInventory(Menus.alignmentMenu(plugin, player.getUniqueId()));
                return;
            }

            // Guild list placeholder (slot 13) - do nothing
            return;
        }

        // For all guild-only menus, we need guild data
        PlayerData pd = plugin.storage().getOrCreatePlayer(player.getUniqueId());
        Guild g = pd.getGuildId() == null ? null : plugin.storage().getGuild(pd.getGuildId());

        // ----- YOUR GUILD MENU
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

        // ----- VAULT
        if (title.equals(Menus.TITLE_VAULT)) {
            event.setCancelled(true);

            if (raw >= 0 && raw < 9) {
                if (g != null) player.openInventory(Menus.yourGuildMenu(g));
                else player.openInventory(Menus.mainMenu(plugin, player.getUniqueId()));
            }
            return;
        }

        // ----- MEMBERS
        if (title.equals(Menus.TITLE_MEMBERS)) {
            event.setCancelled(true);

            if (raw >= 0 && raw < 9) {
                if (g != null) player.openInventory(Menus.yourGuildMenu(g));
                else player.openInventory(Menus.mainMenu(plugin, player.getUniqueId()));
            }
            return;
        }

        // ----- RELATIONS
        if (title.equals(Menus.TITLE_RELATIONS)) {
            event.setCancelled(true);

            if (raw >= 0 && raw < 9) {
                if (g != null) player.openInventory(Menus.yourGuildMenu(g));
                else player.openInventory(Menus.mainMenu(plugin, player.getUniqueId()));
            }
        }
    }
}
