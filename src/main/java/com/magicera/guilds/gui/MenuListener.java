package com.magicera.guilds.gui;

import com.magicera.guilds.MagicEraGuildsPlugin;
import com.magicera.guilds.data.Guild;
import com.magicera.guilds.data.PlayerData;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class MenuListener implements Listener {

    private final MagicEraGuildsPlugin plugin;

    // Track which player has which guild vault open, so we can save on close
    private final Map<UUID, String> openVaultGuild = new HashMap<>();

    public MenuListener(MagicEraGuildsPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player player)) return;
        String title = e.getView().getTitle();

        if (!title.equals(Menus.TITLE_MAIN)
                && !title.equals(Menus.TITLE_YOUR_GUILD)
                && !title.equals(Menus.TITLE_VAULT)
                && !title.equals(Menus.TITLE_MEMBERS)
                && !title.equals(Menus.TITLE_RELATIONS)) {
            return;
        }

        // cancel clicks in our menus (except vault storage area, handled below)
        e.setCancelled(true);

        PlayerData pd = plugin.storage().getOrCreatePlayer(player.getUniqueId());
        Guild g = pd.getGuildId() == null ? null : plugin.storage().getGuild(pd.getGuildId());

        if (title.equals(Menus.TITLE_MAIN)) {
            Material m = e.getCurrentItem() == null ? null : e.getCurrentItem().getType();
            if (m == null) return;

            if (m == Material.BOOK && g != null) {
                player.openInventory(Menus.yourGuildMenu(g));
            }
            // Guild list + alignment are display-only for now
            return;
        }

        if (title.equals(Menus.TITLE_YOUR_GUILD)) {
            Material m = e.getCurrentItem() == null ? null : e.getCurrentItem().getType();
            if (m == null) return;

            if (m == Material.RED_STAINED_GLASS_PANE) {
                player.openInventory(Menus.mainMenu(plugin, player.getUniqueId()));
                return;
            }

            if (g == null) {
                player.closeInventory();
                return;
            }

            if (m == Material.CHEST) {
                // Bank balance placeholder = 0 for now; Vault hook later
                var inv = Menus.vaultMenu(g, g.getBankBalance());
                // load vault items into slots 9..53
                var items = plugin.vaults().loadVault(g.getId());
                for (int i = 0; i < 45; i++) {
                    inv.setItem(9 + i, items[i]);
                }
                openVaultGuild.put(player.getUniqueId(), g.getId());
                player.openInventory(inv);
                return;
            }

            if (m == Material.PLAYER_HEAD) {
                player.openInventory(Menus.membersMenu(plugin, g));
                return;
            }

            if (m == Material.IRON_SWORD) {
                player.openInventory(Menus.relationsMenu(g));
                return;
            }
            return;
        }

        if (title.equals(Menus.TITLE_MEMBERS) || title.equals(Menus.TITLE_RELATIONS)) {
            Material m = e.getCurrentItem() == null ? null : e.getCurrentItem().getType();
            if (m == Material.RED_STAINED_GLASS_PANE) {
                if (g != null) player.openInventory(Menus.yourGuildMenu(g));
                else player.openInventory(Menus.mainMenu(plugin, player.getUniqueId()));
            }
            return;
        }

        if (title.equals(Menus.TITLE_VAULT)) {
            // Allow interaction ONLY in storage area (slots 9..53)
            int raw = e.getRawSlot();
            if (raw >= 9 && raw < 54) {
                e.setCancelled(false); // allow move/items
                return;
            }

            // Click top bar: go back
            Material m = e.getCurrentItem() == null ? null : e.getCurrentItem().getType();
            if (m == Material.RED_STAINED_GLASS_PANE) {
                if (g != null) player.openInventory(Menus.yourGuildMenu(g));
                else player.openInventory(Menus.mainMenu(plugin, player.getUniqueId()));
            }
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent e) {
        if (!(e.getPlayer() instanceof Player player)) return;
        if (!e.getView().getTitle().equals(Menus.TITLE_VAULT)) return;

        String guildId = openVaultGuild.remove(player.getUniqueId());
        if (guildId == null) return;

        // Save slots 9..53 (45 items)
        var contents45 = new org.bukkit.inventory.ItemStack[45];
        for (int i = 0; i < 45; i++) {
            contents45[i] = e.getInventory().getItem(9 + i);
        }
        plugin.vaults().saveVault(guildId, contents45);
    }
}
