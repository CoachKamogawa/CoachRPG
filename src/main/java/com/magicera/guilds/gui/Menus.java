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
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class Menus {

    private Menus() {}

    // Titles used to identify menus
    public static final String TITLE_MAIN = "§8Guild Menu";
    public static final String TITLE_YOUR_GUILD = "§8Your Guild";
    public static final String TITLE_VAULT = "§8Guild Vault";
    public static final String TITLE_MEMBERS = "§8Guild Members";
    public static final String TITLE_RELATIONS = "§8Relations";
    public static final String TITLE_ALIGNMENT = "§8Alignment";

    public static Inventory mainMenu(MagicEraGuildsPlugin plugin, UUID viewer) {
        PlayerData pd = plugin.storage().getOrCreatePlayer(viewer);
        Guild g = pd.getGuildId() == null ? null : plugin.storage().getGuild(pd.getGuildId());

        Inventory inv = Bukkit.createInventory(null, 27, TITLE_MAIN);
        fill(inv, Material.BLACK_STAINED_GLASS_PANE, " ");

        if (g != null) {
            inv.setItem(11, item(Material.BOOK, "§bYour Guild", lore(
                    "§7Name: §r" + Text.color(g.getName()),
                    "§7Tag: §7[" + g.getPrefix() + "§7]",
                    "§7Alignment: §f" + g.getAlignment().name(),
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

        // Alignment always accessible
        int score = pd.getAlignmentScore();
        inv.setItem(15, item(Material.NETHER_STAR, "§bAlignment", lore(
                "§7Your Alignment Score: §f" + score,
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
                "§7Alignment: §f" + g.getAlignment().name()
        )));

        return inv;
    }

    // 54 slots: top row UI bar, remaining 45 slots storage (index 9..53)
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
     * Alignment Menu: always available (guild not required)
     * Center head + 5 "pips" per side.
     *
     * Visual note: Minecraft rows are 9 wide, so we represent 5-per-side
     * as 4 on the center row + 1 just below on each side.
     */
    public static Inventory alignmentMenu(MagicEraGuildsPlugin plugin, UUID viewer) {
        PlayerData pd = plugin.storage().getOrCreatePlayer(viewer);
        int score = pd.getAlignmentScore(); // -100..100

        Inventory inv = Bukkit.createInventory(null, 27, TITLE_ALIGNMENT);

        // top bar: go back
        for (int i = 0; i < 9; i++) inv.setItem(i, backPane());
        for (int i = 9; i < 27; i++) inv.setItem(i, filler());

        // center player head
        inv.setItem(13, playerHead(Bukkit.getOfflinePlayer(viewer), score));

        // 5 pips each side (4 in row, 1 below)
        int[] leftSlots = { 12, 11, 10, 9, 21 };   // dark side
        int[] rightSlots = { 14, 15, 16, 17, 23 }; // honorable side

        int redCount = calcNegativePips(score);
        int greenCount = calcPositivePips(score);

        for (int i = 0; i < leftSlots.length; i++) {
            boolean filled = i < redCount;
            inv.setItem(leftSlots[i], item(
                    filled ? Material.RED_STAINED_GLASS_PANE : Material.WHITE_STAINED_GLASS_PANE,
                    filled ? "§cDark" : "§f",
                    null
            ));
        }

        for (int i = 0; i < rightSlots.length; i++) {
            boolean filled = i < greenCount;
            inv.setItem(rightSlots[i], item(
                    filled ? Material.LIME_STAINED_GLASS_PANE : Material.WHITE_STAINED_GLASS_PANE,
                    filled ? "§aHonorable" : "§f",
                    null
            ));
        }

        // info card
        inv.setItem(22, item(Material.PAPER, "§bAlignment Info", lore(
                "§7Score: §f" + score,
                "",
                "§7-100 to -50 = §cDark",
                "§7-49 to 49 = §7Neutral",
                "§750 to 100 = §aHonorable"
        )));

        return inv;
    }

    private static int calcPositivePips(int score) {
        if (score <= 0) return 0;
        if (score >= 100) return 5;
        return (int) Math.ceil(score / 20.0); // 1-20=1 ... 81-100=5
    }

    private static int calcNegativePips(int score) {
        if (score >= 0) return 0;
        if (score <= -100) return 5;
        return (int) Math.ceil(Math.abs(score) / 20.0);
    }

    private static ItemStack playerHead(OfflinePlayer player, int score) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        ItemMeta meta = head.getItemMeta();
        if (meta instanceof SkullMeta sm) {
            sm.setOwningPlayer(player);
            sm.setDisplayName("§bYour Alignment");

            String group = score >= 50 ? "§aHonorable" : (score <= -50 ? "§cDark" : "§7Neutral");

            sm.setLore(lore(
                    "§7Score: §f" + score,
                    "§7Status: " + group
            ));
            head.setItemMeta(sm);
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
