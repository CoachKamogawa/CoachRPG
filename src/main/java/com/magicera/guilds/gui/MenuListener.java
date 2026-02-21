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

import java.util.*;

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
                player.openInventory(Menus.yourGuildMenu(plugin, g));
                return;
            }

            if (raw == 13) player.openInventory(Menus.guildListMenu(plugin, 0));
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
            if (raw == 22) {
                player.closeInventory();
                player.performCommand("guild info");
            }
            return;
        }

        if (title.equals(Menus.TITLE_VAULT)) {
            if (raw < 9) {
                event.setCancelled(true);
                if (g != null) player.openInventory(Menus.yourGuildMenu(plugin, g));
            }
            return;
        }

        if (title.startsWith(Menus.TITLE_LOG)) {
            event.setCancelled(true);
            if (g == null) return;

            int page = parsePage(title);

            if (raw >= 1 && raw <= 7) {
                player.openInventory(Menus.yourGuildMenu(plugin, g));
                return;
            }

            if (raw == 0 && page > 0) player.openInventory(Menus.guildLogMenu(g, page - 1));
            if (raw == 8 && (page + 1) * 45 < g.getLogEntries().size()) player.openInventory(Menus.guildLogMenu(g, page + 1));
            return;
        }

        if (title.startsWith(Menus.TITLE_GUILD_LIST)) {
            event.setCancelled(true);
            int page = parsePage(title);

            if (raw >= 1 && raw <= 7) {
                player.openInventory(Menus.mainMenu(plugin, player.getUniqueId()));
                return;
            }

            int minMembers = Math.max(1, plugin.getConfig().getInt("guilds.guild-list-min-members", 3));
            int total = (int) plugin.storage().allGuilds().stream()
                    .filter(g1 -> g1.getMembers().size() >= minMembers)
                    .count();

            if (raw == 0 && page > 0) player.openInventory(Menus.guildListMenu(plugin, page - 1));
            if (raw == 8 && (page + 1) * 45 < total) player.openInventory(Menus.guildListMenu(plugin, page + 1));
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
                if (g != null) player.openInventory(Menus.yourGuildMenu(plugin, g));
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
        ItemStack[] oldStorage = plugin.vaults().loadVault(g.getId());
        ItemStack[] storage = new ItemStack[45];
        for (int i = 0; i < 45; i++) {
            storage[i] = all[i + 9];
        }

        plugin.vaults().saveVault(g.getId(), storage);
        plugin.vaultLogs().appendEntries(g.getId(), VaultLogUtil.diff(oldStorage, storage, player.getName()));
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

    private static final class VaultLogUtil {
        static List<String> diff(ItemStack[] oldStorage, ItemStack[] newStorage, String actor) {
            Map<String, Integer> oldCounts = count(oldStorage);
            Map<String, Integer> newCounts = count(newStorage);
            List<String> out = new ArrayList<>();

            Set<String> keys = new HashSet<>();
            keys.addAll(oldCounts.keySet());
            keys.addAll(newCounts.keySet());

            for (String key : keys) {
                int before = oldCounts.getOrDefault(key, 0);
                int after = newCounts.getOrDefault(key, 0);
                int delta = after - before;
                if (delta > 0) out.add("Vault add: " + actor + " +" + delta + " " + key);
                if (delta < 0) out.add("Vault remove: " + actor + " " + delta + " " + key);
            }
            if (out.isEmpty()) out.add("Vault checked by " + actor + " (no item changes)");
            return out;
        }

        private static Map<String, Integer> count(ItemStack[] items) {
            Map<String, Integer> counts = new HashMap<>();
            if (items == null) return counts;
            for (ItemStack it : items) {
                if (it == null || it.getType().isAir()) continue;
                String name = it.hasItemMeta() && it.getItemMeta() != null && it.getItemMeta().hasDisplayName()
                        ? org.bukkit.ChatColor.stripColor(it.getItemMeta().getDisplayName())
                        : it.getI18NDisplayName();
                counts.merge(name, it.getAmount(), Integer::sum);
            }
            return counts;
        }
    }
}
