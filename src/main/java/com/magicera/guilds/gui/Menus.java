package com.magicera.guilds.gui;

import com.magicera.guilds.MagicEraGuildsPlugin;
import com.magicera.guilds.data.Guild;
import com.magicera.guilds.data.GuildAlignment;
import com.magicera.guilds.data.GuildRole;
import com.magicera.guilds.data.PlayerData;
import com.magicera.guilds.util.AlignmentUtil;
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
    public static final String TITLE_FAVOR = "§8Favor";

    public static Inventory mainMenu(MagicEraGuildsPlugin plugin, UUID viewer) {
        PlayerData pd = plugin.storage().getOrCreatePlayer(viewer);
        Guild g = pd.getGuildId() == null ? null : plugin.storage().getGuild(pd.getGuildId());

        Inventory inv = Bukkit.createInventory(null, 27, TITLE_MAIN);
        fill(inv, Material.BLACK_STAINED_GLASS_PANE, " ");

        if (g != null) {
            inv.setItem(11, item(Material.BOOK, "§bYour Guild", lore(
                    "§7Name: §r" + Text.color(g.getName()),
                    "§7Tag: §7[" + g.getPrefix() + "§7]",
                    "§7Favor: §f" + AlignmentUtil.displayName(g.getAlignment()),
                    "§7Type: §f" + AlignmentUtil.guildTypeName(g.getAlignment()),
                    "",
                    "§eClick to open"
            )));
        } else {
            inv.setItem(11, item(Material.BARRIER, "§bYour Guild", lore(
                    "§cYou are not in a guild.",
                    "§7Use §f/guild create \"Name\" TAG"
            )));
        }

        inv.setItem(13, item(Material.MAP, "§bGuilds List", lore(
                "§7Placeholder for now."
        )));

        // Favor entry (always accessible)
        GuildAlignment favor = AlignmentUtil.groupFromScore(pd.getAlignmentScore());
        inv.setItem(15, item(Material.NETHER_STAR, "§bFavor", lore(
                "§7Your Favor:",
                "§f" + AlignmentUtil.displayName(favor),
                "",
                "§eClick to open"
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
                "§7Favor: §f" + AlignmentUtil.displayName(g.getAlignment()),
                "§7Type: §f" + AlignmentUtil.guildTypeName(g.getAlignment())
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

    /**
     * Favor menu: 9x6 (expanded)
     *
     * Row 1: red "Go Back" panes
     * Row 2: black panes
     * Row 3-4: favor bar (6 meters per side across two rows)
     * Row 5: black panes
     * Row 6: bottom bar (pane color depends on favor) + center paper "Favor Status"
     */
    public static Inventory favorMenu(MagicEraGuildsPlugin plugin, UUID viewer) {
        PlayerData pd = plugin.storage().getOrCreatePlayer(viewer);

        int score = pd.getAlignmentScore(); // -100..100
        GuildAlignment favor = AlignmentUtil.groupFromScore(score);

        Inventory inv = Bukkit.createInventory(null, 54, TITLE_FAVOR);

        // Row 1: back bar
        for (int i = 0; i < 9; i++) inv.setItem(i, backPane());

        // Row 2: black panes
        for (int i = 9; i < 18; i++) inv.setItem(i, filler());

        // Row 5: black panes
        for (int i = 36; i < 45; i++) inv.setItem(i, filler());

        // Row 6: favor-colored panes + paper center
        Material bottom = favorBottomPaneMaterial(favor);
        for (int i = 45; i < 54; i++) inv.setItem(i, item(bottom, " ", null));
        inv.setItem(49, favorStatusPaper(favor)); // center (row 6 col 5)

        // --- Favor bar (rows 3-4) ---
        // Row 3 slots 18..26
        // [18] skull | [19][20][21] left meters | [22] player head | [23][24][25] right meters | [26] star
        inv.setItem(18, item(Material.WITHER_SKELETON_SKULL, "§c§lSin", lore("§7The left path")));
        inv.setItem(22, playerHead(viewer, "§fYou", null));
        inv.setItem(26, item(Material.NETHER_STAR, "§a§lHonor", lore("§7The right path")));

        // Row 4 slots 27..35
        // We'll put remaining meters here:
        // [27] filler | [28][29][30] left meters | [31] filler | [32][33][34] right meters | [35] filler
        inv.setItem(27, filler());
        inv.setItem(31, filler());
        inv.setItem(35, filler());

        // Meter slot lists (6 per side)
        // Left meters: 19,20,21, 30,29,28 (so it feels like it continues downward/outward cleanly)
        // Right meters: 23,24,25, 32,33,34
        int[] leftMeters = new int[]{19, 20, 21, 30, 29, 28};
        int[] rightMeters = new int[]{23, 24, 25, 32, 33, 34};

        // Default: WHITE (unfilled)
        for (int s : leftMeters) inv.setItem(s, item(Material.WHITE_STAINED_GLASS_PANE, " ", null));
        for (int s : rightMeters) inv.setItem(s, item(Material.WHITE_STAINED_GLASS_PANE, " ", null));

        // Fill behavior:
        // Sin: all meters red
        // Honor: all meters lime
        // Balance: fill one side based on score sign, proportionally (1-6)
        if (favor == GuildAlignment.DARK) {
            for (int s : leftMeters) inv.setItem(s, item(Material.RED_STAINED_GLASS_PANE, " ", null));
            for (int s : rightMeters) inv.setItem(s, item(Material.RED_STAINED_GLASS_PANE, " ", null));
        } else if (favor == GuildAlignment.HONORABLE) {
            for (int s : leftMeters) inv.setItem(s, item(Material.LIME_STAINED_GLASS_PANE, " ", null));
            for (int s : rightMeters) inv.setItem(s, item(Material.LIME_STAINED_GLASS_PANE, " ", null));
        } else {
            int steps = 6;

            if (score > 0) {
                int fill = (int) Math.ceil((Math.min(100, score) / 100.0) * steps);
                fill = Math.max(0, Math.min(steps, fill));

                // Fill from near-center outward in our rightMeters order
                for (int i = 0; i < fill; i++) {
                    inv.setItem(rightMeters[i], item(Material.LIME_STAINED_GLASS_PANE, " ", null));
                }
            } else if (score < 0) {
                int abs = Math.abs(score);
                int fill = (int) Math.ceil((Math.min(100, abs) / 100.0) * steps);
                fill = Math.max(0, Math.min(steps, fill));

                // Fill from near-center outward in our leftMeters order
                for (int i = 0; i < fill; i++) {
                    inv.setItem(leftMeters[i], item(Material.RED_STAINED_GLASS_PANE, " ", null));
                }
            }
        }

        return inv;
    }

    private static Material favorBottomPaneMaterial(GuildAlignment favor) {
        // Requested:
        // white = balance
        // red = sin
        // light green = honor
        if (favor == GuildAlignment.HONORABLE) return Material.LIME_STAINED_GLASS_PANE;
        if (favor == GuildAlignment.DARK) return Material.RED_STAINED_GLASS_PANE;
        return Material.WHITE_STAINED_GLASS_PANE;
    }

    private static ItemStack favorStatusPaper(GuildAlignment favor) {
        String favorName = AlignmentUtil.displayName(favor);

        String color;
        String msg;

        // Messages requested:
        // Balance = "You choose to walk your own path."
        // Honor = "Your deeds have been recognized by the world."
        // Sin = "Your stage is the abyss."
        if (favor == GuildAlignment.HONORABLE) {
            color = "§a";
            msg = "Your deeds have been recognized by the world.";
        } else if (favor == GuildAlignment.DARK) {
            color = "§c";
            msg = "Your stage is the abyss.";
        } else {
            color = "§7";
            msg = "You choose to walk your own path.";
        }

        return item(Material.PAPER, "§fFavor Status", lore(
                "§7Current: " + color + favorName,
                "",
                color + msg
        ));
    }

    private static ItemStack playerHead(UUID uuid, String name, List<String> lore) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        ItemMeta meta = head.getItemMeta();
        if (meta instanceof SkullMeta sm) {
            sm.setOwningPlayer(Bukkit.getOfflinePlayer(uuid));
            sm.setDisplayName(name);
            if (lore != null) sm.setLore(lore);
            head.setItemMeta(sm);
        } else if (meta != null) {
            meta.setDisplayName(name);
            if (lore != null) meta.setLore(lore);
            head.setItemMeta(meta);
        }
        return head;
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
