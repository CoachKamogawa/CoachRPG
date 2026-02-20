package com.magicera.guilds.gui;

import com.magicera.guilds.MagicEraGuildsPlugin;
import com.magicera.guilds.data.Guild;
import com.magicera.guilds.data.PlayerData;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.ItemStack;

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

        if (title.equals(Menus.TITLE_FAVOR)) {
            event.setCancelled(true);
            if (raw >= 0 && raw < 9) player.openInventory(Menus.mainMenu(plugin, player.getUniqueId()));
            return;
        }

        if (title.equals(Menus.TITLE_MAIN)) {
            event.setCancelled(true);

            if (raw == 11) {
                PlayerData pd = plugin.storage().getOrCreatePlayer(player.getUniqueId());
                if (pd.getGuildId() == null) {
                    player.sendMessage("§cYou are not in a guild.");
                    return;
                }
                Guild g = plugin.storage().getGuild(pd.getGuildId());
                if (g == null) return;
                player.openInventory(Menus.yourGuildMenu(g));
                return;
            }

            if (raw == 15) player.openInventory(Menus.favorMenu(plugin, player.getUniqueId()));
            return;
        }

        PlayerData pd = plugin.storage().getOrCreatePlayer(player.getUniqueId());
        Guild g = pd.getGuildId() == null ? null : plugin.storage().getGuild(pd.getGuildId());

        if (title.equals(Menus.TITLE_YOUR_GUILD)) {
            event.setCancelled(true);

            if (raw >= 0 && raw < 9) {
                player.openInventory(Menus.mainMenu(plugin, player.getUniqueId()));
                return;
            }

            if (g == null) return;

            if (raw == 11) { player.closeInventory(); player.performCommand("guild home"); }
            if (raw == 12) player.openInventory(Menus.vaultMenu(plugin, g, g.getBankBalance()));
            if (raw == 13) player.openInventory(Menus.membersMenu(plugin, g));
            if (raw == 14) player.openInventory(Menus.relationsMenu(plugin, g));
            if (raw == 15) player.openInventory(Menus.guildLogMenu(g, 0));
            return;
        }

        if (title.equals(Menus.TITLE_VAULT)) {
            if (raw < 9) {
                event.setCancelled(true);
                if (g != null) player.openInventory(Menus.yourGuildMenu(g));
            }
            return;
        }

        if (title.startsWith(Menus.TITLE_LOG)) {
            event.setCancelled(true);
            if (g == null) return;

            int page = parsePage(title);

            if (raw < 9 && raw == 0) {
                player.openInventory(Menus.yourGuildMenu(g));
                return;
            }

            if (raw == 7 && page > 0) player.openInventory(Menus.guildLogMenu(g, page - 1));
            if (raw == 8 && (page + 1) * 45 < g.getLogEntries().size()) player.openInventory(Menus.guildLogMenu(g, page + 1));
            return;
        }

        if (title.startsWith(Menus.TITLE_RELATION_GUILD)) {
            event.setCancelled(true);
            if (raw >= 0 && raw < 9 && g != null) {
                player.openInventory(Menus.relationsMenu(plugin, g));
            }
            return;
        }

        if (title.equals(Menus.TITLE_MEMBERS) || title.equals(Menus.TITLE_RELATIONS)) {
            event.setCancelled(true);

            if (raw >= 0 && raw < 9) {
                if (g != null) player.openInventory(Menus.yourGuildMenu(g));
                else player.openInventory(Menus.mainMenu(plugin, player.getUniqueId()));
                return;
            }

            if (title.equals(Menus.TITLE_RELATIONS) && raw >= 9 && g != null) {
                ItemStack clicked = event.getCurrentItem();
                if (clicked != null && clicked.getType().name().endsWith("PLAYER_HEAD")) {
                    String stripped = org.bukkit.ChatColor.stripColor(
                            clicked.getItemMeta() == null ? "" : clicked.getItemMeta().getDisplayName()
                    );

                    for (String allyId : g.getAllies()) {
                        Guild ally = plugin.storage().getGuild(allyId);
                        if (ally == null) continue;

                        String allyName = org.bukkit.ChatColor.stripColor(
                                com.magicera.guilds.util.Text.color(ally.getName())
                        );

                        if (allyName != null && allyName.equalsIgnoreCase(stripped)) {
                            player.openInventory(Menus.relationGuildMembersMenu(plugin, ally));
                            break;
                        }
                    }
                }
            }
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        if (!event.getView().getTitle().equals(Menus.TITLE_VAULT)) return;

        PlayerData pd = plugin.storage().getOrCreatePlayer(player.getUniqueId());
        if (pd.getGuildId() == null) return;
        Guild g = plugin.storage().getGuild(pd.getGuildId());
        if (g == null) return;

        ItemStack[] all = event.getInventory().getContents();
        ItemStack[] storage = new ItemStack[45];
        for (int i = 0; i < 45; i++) {
            storage[i] = all[i + 9];
        }

        plugin.vaults().saveVault(g.getId(), storage);
        g.addLogEntry("Vault updated by " + player.getName());
        plugin.storage().save();
    }

    private int parsePage(String title) {
        int i = title.indexOf('(');
        int j = title.indexOf(')');
        if (i < 0 || j < 0 || j <= i) return 0;
        try {
            return Math.max(0, Integer.parseInt(title.substring(i + 1, j).trim()) - 1);
        } catch (Exception ignored) {
            return 0;
        }
    }
}
