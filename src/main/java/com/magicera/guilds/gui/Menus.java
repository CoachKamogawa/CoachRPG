package com.magicera.guilds.gui;

import com.magicera.guilds.MagicEraGuildsPlugin;
import com.magicera.guilds.data.Guild;
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
        inv.setItem(15, item(Material.NETHER_STAR, "§bFavor", lore(
                "§7Your Favor:",
                "§f" + AlignmentUtil.displayName(AlignmentUtil.groupFromScore(pd.getAlignmentScore())),
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
     * Favor menu:
     * - 6 panes per side (12 total), split across two rows to fit 9-wide inventories.
     * - Wither skull at far-left end labeled §c§lSin
     * - Nether star at far-right end labeled §a§lHonor
     * - Player head in center
     * - Bottom paper "Favor Status" with colored message
     */
    public static Inventory favorMenu(MagicEraGuildsPlugin plugin, UUID viewer) {
        PlayerData pd = plugin.storage().getOrCreatePlayer(viewer);

        int score = pd.getAlignmentScore(); // -100..100
        var favor = AlignmentUtil.groupFromScore(score); // should map to SIN/BALANCE/HONOR via your util

        Inventory inv = Bukkit.createInventory(null, 45, TITLE_FAVOR);

        // background
        for (int i = 0; i < inv.getSize(); i++) inv.setItem(i, filler());

        // meter rows: row1 (slots 9..17) and row2 (slots 18..26)
        // We use 3 panes per side on row1 + 3 panes per side on row2 = 6 per side total.
        // Row2 also has endpoints + player head.
        //
        // Row1:
        //  9 filler, 10-12 left panes, 13 filler, 14-16 right panes, 17 filler
        //
        // Row2:
        //  18 WITHER_SKULL, 19-21 left panes, 22 PLAYER_HEAD, 23-25 right panes, 26 NETHER_STAR

        // Endpoints
        inv.setItem(18, item(Material.WITHER_SKELETON_SKULL, "§c§lSin", lore("§7The left path")));
        inv.setItem(26, item(Material.NETHER_STAR, "§a§lHonor", lore("§7The right path")));

        // Player head center
        inv.setItem(22, playerHead(viewer, "§fYou", null));

        // Compute fills (6 steps each side)
        int steps = 6;
        int fill = 0;

        // Materials
        Material emptyPane = Material.WHITE_STAINED_GLASS_PANE;
        Material honorPane = Material.LIME_STAINED_GLASS_PANE;
        Material sinPane = Material.RED_STAINED_GLASS_PANE;

        // default: all empty
        Material leftFillMat = sinPane;
        Material rightFillMat = honorPane;

        // Decide what to fill
        boolean allSin = AlignmentUtil.isSin(favor);
        boolean allHonor = AlignmentUtil.isHonor(favor);

        if (allSin) {
            // all panes red
            setMeterPanes(inv, emptyPane, sinPane, steps, steps, true, true);
        } else if (allHonor) {
            // all panes green
            setMeterPanes(inv, emptyPane, honorPane, steps, steps, true, true);
        } else {
            // Balance: fill based on score direction
            if (score > 0) {
                fill = (int) Math.ceil((Math.min(100, score) / 100.0) * steps);
                fill = Math.max(0, Math.min(steps, fill));
                // fill right only
                setMeterPanes(inv, emptyPane, rightFillMat, 0, fill, false, true);
            } else if (score < 0) {
                int abs = Math.abs(score);
                fill = (int) Math.ceil((Math.min(100, abs) / 100.0) * steps);
                fill = Math.max(0, Math.min(steps, fill));
                // fill left only
                setMeterPanes(inv, emptyPane, leftFillMat, fill, 0, true, false);
            } else {
                // zero: all empty
                setMeterPanes(inv, emptyPane, null, 0, 0, true, true);
            }
        }

        // Favor Status paper bottom center (slot 40)
        inv.setItem(40, favorStatusPaper(favor));

        // Optional "go back" (if you want it later) could be slot 36 etc.
        return inv;
    }

    // ---------- Favor menu helpers ----------

    /**
     * Places the empty panes, then overlays filled panes on the left/right.
     * leftFill/rightFill are 0..steps.
     *
     * Layout for panes:
     * Left panes: 10,11,12 and 19,20,21  (3+3 = 6)
     * Right panes: 14,15,16 and 23,24,25 (3+3 = 6)
     */
    private static void setMeterPanes(Inventory inv, Material empty, Material fillMat,
                                      int leftFill, int rightFill,
                                      boolean setLeft, boolean setRight) {

        int[] leftSlots = new int[]{10, 11, 12, 19, 20, 21};
        int[] rightSlots = new int[]{14, 15, 16, 23, 24, 25};

        // base empty
        if (setLeft) for (int s : leftSlots) inv.setItem(s, item(empty, " ", null));
        if (setRight) for (int s : rightSlots) inv.setItem(s, item(empty, " ", null));

        if (fillMat == null) return;

        // Fill from center outward feels better for "meter" readability.
        // Left side: fill nearest-to-center first -> slots [12,11,10,21,20,19] (still 6 total)
        int[] leftOrder = new int[]{12, 11, 10, 21, 20, 19};
        // Right side: fill nearest-to-center first -> [14,15,16,23,24,25]
        int[] rightOrder = new int[]{14, 15, 16, 23, 24, 25};

        for (int i = 0; i < Math.min(leftFill, leftOrder.length); i++) {
            inv.setItem(leftOrder[i], item(fillMat, " ", null));
        }
        for (int i = 0; i < Math.min(rightFill, rightOrder.length); i++) {
            inv.setItem(rightOrder[i], item(fillMat, " ", null));
        }
    }

    private static ItemStack favorStatusPaper(Object favorEnum) {
        // We rely on AlignmentUtil for name mapping
        String favorName = AlignmentUtil.displayName(favorEnum); // "Sin" / "Balance" / "Honor"

        // message + color based on favor
        String msg;
        String color;

        if (AlignmentUtil.isHonor(favorEnum)) {
            color = "§a";
            msg = "Your deeds have been recognized by the world.";
        } else if (AlignmentUtil.isSin(favorEnum)) {
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

    // ---------- existing utility ----------

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
