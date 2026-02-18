package com.magicera.guilds.gui;

import com.magicera.guilds.MagicEraGuildsPlugin;
import com.magicera.guilds.data.Guild;
import com.magicera.guilds.data.GuildRole;
import com.magicera.guilds.data.PlayerData;
import com.magicera.guilds.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.time.Duration;
import java.util.*;

public final class Menus {

    private Menus() {}

    // Titles used to identify menus
    public static final String TITLE_MAIN = "§8Guild Menu";
    public static final String TITLE_YOUR_GUILD = "§8Your Guild";
    public static final String TITLE_VAULT = "§8Guild Vault";
    public static final String TITLE_MEMBERS = "§8Guild Members";
    public static final String TITLE_RELATIONS = "§8Relations";

    public static Inventory mainMenu(MagicEraGuildsPlugin plugin, UUID viewer) {
        PlayerData pd = plugin.storage().getOrCreatePlayer(viewer);
        Guild g = pd.getGuildId() == null ? null : plugin.storage().getGuild(pd.getGuildId());

        Inventory inv = Bukkit.createInventory(null, 27, TITLE_MAIN);
        fill(inv, Material.BLACK_STAINED_GLASS_PANE, " ");

        if (g != null) {
            inv.setItem(11, item(Material.BOOK, "§bYour Guild", lore(
                    "§7Name: §r" + g.getName(),
                    "§7Tag: §7[" + g.getPrefix() + "§7]",
                    "§7Alignment: §f" + g.getAlignment().name(),
                    "",
                    "§eClick to open"
            )));
            inv.setItem(15, item(Material.NETHER_STAR, "§bAlignment", lore(
                    "§7Guild Alignment:",
                    "§f" + g.getAlignment().name()
            )));
        } else {
            inv.setItem(11, item(Material.BARRIER, "§bYour Guild", lore(
                    "§cYou are not in a guild.",
                    "§7Use §f/guild create \"Name\" TAG"
            )));
            inv.setItem(15, item(Material.GRAY_DYE, "§bAlignment", lore(
                    "§7Join a guild first."
            )));
        }

        inv.setItem(13, item(Material.MAP, "§bGuilds List", lore(
                "§7Placeholder for now."
        )));

        return inv;
    }

    public static Inventory yourGuildMenu(Guild g) {
        Inventory inv = Bukkit.createInventory(null, 27, TITLE_YOUR_GUILD);

        for (int i = 0; i < 9; i++) inv.setItem(i, backPane());

        for (int i = 9; i < 27; i++) inv.setItem(i, filler());

        inv.setItem(12, item(Material.CHEST, "§bGuild Vault", lore("§7Open the guild vault.")));
        inv.setItem(13, item(Material.PLAYER_HEAD, "§bGuild Members", lore("§7View member list.")));
        inv.setItem(14, item(Material.IRON_SWORD, "§bRelations", lore("§7Allies and enemies.")));

        inv.setItem(22, item(Material.BOOK, "§f" + Text.color(g.getName()), lore(
                "§7Tag: §7[" + g.getPrefix() + "§7]",
                "§7Alignment: §f" + g.getAlignment().name()
        )));

        return inv;
    }

    // 54 slots: top row is UI bar, remaining 45 slots are storage (index 9..53)
    public static Inventory vaultMenu(Guild g, double bankBalance) {
        Inventory inv = Bukkit.createInventory(null, 54, TITLE_VAULT);

        for (int i = 0; i < 9; i++) inv.setItem(i, backPane());

        inv.setItem(4, item(Material.EMERALD, "§aGuild Bank", lore(
                "§7Balance: §f$" + (long) bankBalance
        )));

        for (int i = 9; i < 54; i++) inv.setItem(i, null);
        return inv;
    }

    public static Inventory membersMenu(MagicEraGuildsPlugin plugin, Guild g) {
        Inventory inv = Bukkit.createInventory(null, 54, TITLE_MEMBERS);

        for (int i = 0; i < 9; i++) inv.setItem(i, backPane());
        for (int i = 9; i < 54; i++) inv.setItem(i, filler());

        int slot = 9;
        for (Map.Entry<UUID, GuildRole> e : g.getMembers().entrySet()) {
            if (slot >= 54) break;

            UUID uuid = e.getKey();
            GuildRole role = e.getValue();

            OfflinePlayer off = Bukkit.getOfflinePlayer(uuid);
            PlayerData pd = plugin.storage().getOrCreatePlayer(uuid);

            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            ItemMeta meta = head.getItemMeta();
            if (meta instanceof SkullMeta sm) {
                sm.setOwningPlayer(off);
                sm.setDisplayName("§f" + (off.getName() == null ? uuid.toString() : off.getName()));
                sm.setLore(lore(
                        "§7Rank: §f" + role.name(),
                        "§7Title: §r" + (pd.getGuildTitle().isEmpty() ? "§7None" : Text.color(pd.getGuildTitle())),
                        "§7Last Online: §f" + formatLastOnline(off.isOnline(), pd.getLastSeenEpochMs())
                ));
                head.setItemMeta(sm);
            }

            inv.setItem(slot++, head);
        }

        return inv;
    }

    public static Inventory relationsMenu(Guild g) {
        Inventory inv = Bukkit.createInventory(null, 54, TITLE_RELATIONS);

        for (int i = 0; i < 9; i++) inv.setItem(i, backPane());
        for (int i = 9; i < 54; i++) inv.setItem(i, filler());

        inv.setItem(22, item(Material.PAPER, "§bRelations", lore(
                "§7Placeholder for now.",
                "§7Allies/enemies next increment."
        )));

        return inv;
    }

    private static String formatLastOnline(boolean isOnline, long lastSeenMs) {
        if (isOnline) return "Online";
        long now = System.currentTimeMillis();
        long diff = Math.max(0, now - lastSeenMs);
        Duration d = Duration.ofMillis(diff);
        long days = d.toDays();
        long hours = d.toHours() % 24;
        long mins = d.toMinutes() % 60;
        if (days > 0) return days + "d " + hours + "h ago";
        if (hours > 0) return hours + "h " + mins + "m ago";
        return Math.max(1, mins) + "m ago";
    }

    private static ItemStack filler() {
        return item(Material.BLACK_STAINED_GLASS_PANE, " ", null);
    }

    private static ItemStack backPane() {
        return item(Material.RED_STAINED_GLASS_PANE, "§cGo Back", lore("§7Click to return"));
    }

    private static void fill(Inventory inv, Material mat, String name) {
        for (int i = 0; i < inv.getSize(); i++) inv.setItem(i, item(mat, name, null));
    }

    private static ItemStack item(Material mat, String name, List<String> lore) {
        ItemStack it = new ItemStack(mat);
        ItemMeta meta = it.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            if (lore != null) meta.setLore(lore);
            it.setItemMeta(meta);
        }
        return it;
    }

    private static List<String> lore(String... lines) {
        return Arrays.asList(lines);
    }
}
