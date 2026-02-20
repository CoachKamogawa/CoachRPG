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
    public static final String TITLE_RELATION_GUILD = "§8Guild: ";
    public static final String TITLE_LOG = "§8Guild Log";
    public static final String TITLE_FAVOR = "§8Favor";
    public static final String TITLE_GUILD_LIST = "§8Guilds List";

    public static Inventory mainMenu(MagicEraGuildsPlugin plugin, UUID viewer) {
        PlayerData pd = plugin.storage().getOrCreatePlayer(viewer);
        Guild g = pd.getGuildId() == null ? null : plugin.storage().getGuild(pd.getGuildId());

        Inventory inv = Bukkit.createInventory(null, 27, TITLE_MAIN);
        fill(inv, Material.BLACK_STAINED_GLASS_PANE, " ");

        if (g != null) {
            inv.setItem(11, item(Material.BOOK, "§bYour Guild", lore(
                    "§7Name: §r" + Text.color(g.getName()),
                    "§7Your Title: §r" + (pd.getGuildTitle().isEmpty() ? "§7None" : Text.color(pd.getGuildTitle())),
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

        int minMembers = Math.max(1, plugin.getConfig().getInt("guilds.guild-list-min-members", 3));
        inv.setItem(13, item(Material.MAP, "§bGuilds List", lore(
                "§7Shows guilds with at least §f" + minMembers + "§7 members.",
                "",
                "§eClick to open"
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

        inv.setItem(11, item(Material.OAK_DOOR, "§bGuild Home", lore("§7Teleport to guild home.")));
        inv.setItem(12, item(Material.CHEST, "§bGuild Vault", lore("§7Open the guild vault.")));
        inv.setItem(13, item(Material.PLAYER_HEAD, "§bGuild Members", lore("§7View member list.")));
        inv.setItem(14, item(Material.IRON_SWORD, "§bRelations", lore("§7Allies and enemies.")));
        inv.setItem(15, item(Material.WRITABLE_BOOK, "§bGuild Log", lore("§7View guild activity.")));

        // Moved Guild Info to slot 22 (and expanded lore)
        inv.setItem(22, item(Material.BOOK, "§bGuild Info", lore(
                "§7View guild details in chat.",
                "",
                "§7Name: §r" + Text.color(g.getName()),
                "§7Tag: §7[" + g.getPrefix() + "§7]",
                "§7Favor: §f" + AlignmentUtil.displayName(g.getAlignment()),
                "§7Type: §f" + AlignmentUtil.guildTypeName(g.getAlignment()),
                "",
                "§eClick to view"
        )));

        return inv;
    }

    // 54 slots: top row is UI bar, remaining 45 slots are storage (index 9..53)
    public static Inventory vaultMenu(MagicEraGuildsPlugin plugin, Guild g, double bankBalance) {
        Inventory inv = Bukkit.createInventory(null, 54, TITLE_VAULT);

        for (int i = 0; i < 9; i++) inv.setItem(i, backPane());

        inv.setItem(4, item(Material.EMERALD, "§aGuild Bank", lore(
                "§7Balance: §f$" + (long) bankBalance
        )));

        ItemStack[] contents = plugin.vaults().loadVault(g.getId());
        for (int i = 9; i < 54; i++) inv.setItem(i, contents[i - 9]);

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

    public static Inventory guildLogMenu(Guild g, int page) {
        Inventory inv = Bukkit.createInventory(null, 54, TITLE_LOG + " §7(" + (page + 1) + ")");
        for (int i = 0; i < 9; i++) inv.setItem(i, backPane());
        for (int i = 9; i < 54; i++) inv.setItem(i, filler());

        int start = page * 45;
        List<String> logs = g.getLogEntries();
        for (int i = 0; i < 45; i++) {
            int idx = start + i;
            if (idx >= logs.size()) break;
            String entry = logs.get(idx);
            Material icon = logMaterial(entry);
            inv.setItem(9 + i, item(icon, "§fLog Entry", lore("§7" + entry)));
        }

        inv.setItem(0, item(Material.LIME_STAINED_GLASS_PANE, "§aPrevious", lore("§7Previous page")));
        inv.setItem(8, item(Material.LIGHT_BLUE_STAINED_GLASS_PANE, "§bNext", lore("§7Next page")));
        for (int i = 1; i <= 7; i++) inv.setItem(i, backPane());
        return inv;
    }

    public static Inventory relationsMenu(MagicEraGuildsPlugin plugin, Guild g) {
        Inventory inv = Bukkit.createInventory(null, 54, TITLE_RELATIONS);

        for (int i = 0; i < 9; i++) inv.setItem(i, backPane());
        for (int i = 9; i < 54; i++) inv.setItem(i, filler());

        int slot = 9;
        for (String allyId : g.getAllies()) {
            Guild ally = plugin.storage().getGuild(allyId);
            if (ally == null || slot >= 54) continue;

            UUID master = ally.getMembers().entrySet().stream()
                    .filter(e -> e.getValue() == GuildRole.MASTER)
                    .map(Map.Entry::getKey)
                    .findFirst()
                    .orElse(null);
            if (master == null) continue;

            OfflinePlayer off = Bukkit.getOfflinePlayer(master);

            ItemStack head = playerHead(master, "§a" + Text.color(ally.getName()), lore(
                    "§7Guild: §r" + Text.color(ally.getName()),
                    "§7Tag: §7[" + ally.getPrefix() + "§7]",
                    "§7Master: §f" + (off.getName() == null ? "Unknown" : off.getName()),
                    "§7Members: §f" + ally.getMembers().size(),
                    "",
                    "§eClick to view members"
            ));

            inv.setItem(slot++, head);
        }

        return inv;
    }

    public static Inventory guildListMenu(MagicEraGuildsPlugin plugin, int page) {
        Inventory inv = Bukkit.createInventory(null, 54, TITLE_GUILD_LIST + " §7(" + (page + 1) + ")");
        for (int i = 0; i < 9; i++) inv.setItem(i, backPane());
        for (int i = 9; i < 54; i++) inv.setItem(i, filler());

        int minMembers = Math.max(1, plugin.getConfig().getInt("guilds.guild-list-min-members", 3));
        List<Guild> guilds = plugin.storage().allGuilds().stream()
                .filter(g -> g.getMembers().size() >= minMembers)
                .sorted(Comparator.comparingInt((Guild g) -> g.getMembers().size()).reversed())
                .toList();

        int start = page * 45;
        for (int i = 0; i < 45; i++) {
            int idx = start + i;
            if (idx >= guilds.size()) break;
            Guild g = guilds.get(idx);
            inv.setItem(9 + i, item(Material.MAP, "§b" + Text.color(g.getName()), lore(
                    "§7Tag: §7[" + g.getPrefix() + "§7]",
                    "§7Members: §f" + g.getMembers().size(),
                    "§7Power: §f" + String.format(Locale.US, "%.2f", guildPower(plugin, g)),
                    "§7Description: §f" + (g.getDescription().isEmpty() ? "None" : Text.stripColors(g.getDescription()))
            )));
        }

        inv.setItem(0, item(Material.LIME_STAINED_GLASS_PANE, "§aPrevious", lore("§7Previous page")));
        inv.setItem(8, item(Material.LIGHT_BLUE_STAINED_GLASS_PANE, "§bNext", lore("§7Next page")));
        for (int i = 1; i <= 7; i++) inv.setItem(i, backPane());
        return inv;
    }

    private static double guildPower(MagicEraGuildsPlugin plugin, Guild g) {
        double total = 0.0;
        for (UUID memberId : g.getMembers().keySet()) {
            total += plugin.storage().getOrCreatePlayer(memberId).getPower();
        }
        return total;
    }

    public static Inventory relationGuildMembersMenu(MagicEraGuildsPlugin plugin, Guild g) {
        Inventory inv = membersMenu(plugin, g);
        Inventory ret = Bukkit.createInventory(null, 54, TITLE_RELATION_GUILD + Text.stripColors(g.getName()));
        ret.setContents(inv.getContents());
        return ret;
    }

    /**
     * Favor menu: 9x5 (45)
     *
     * Row 1: red "Go Back"
     * Row 2: black panes
     * Row 3: favor bar only (4 left panes + head + 4 right panes)
     * Row 4: black panes
     * Row 5: favor-colored panes with center paper "Favor Status"
     */
    public static Inventory favorMenu(MagicEraGuildsPlugin plugin, UUID viewer) {
        PlayerData pd = plugin.storage().getOrCreatePlayer(viewer);

        int score = pd.getAlignmentScore(); // -100..100
        GuildAlignment favor = AlignmentUtil.groupFromScore(score);

        Inventory inv = Bukkit.createInventory(null, 45, TITLE_FAVOR);

        // Row 1: back bar
        for (int i = 0; i < 9; i++) inv.setItem(i, backPane());

        // Row 2: black panes
        for (int i = 9; i < 18; i++) inv.setItem(i, filler());

        // Row 4: black panes
        for (int i = 27; i < 36; i++) inv.setItem(i, filler());

        // Row 5: favor-colored panes + paper center
        Material statusPane = switch (favor) {
            case DARK -> Material.RED_STAINED_GLASS_PANE;
            case HONORABLE -> Material.LIME_STAINED_GLASS_PANE;
            default -> Material.WHITE_STAINED_GLASS_PANE;
        };
        for (int i = 36; i < 45; i++) inv.setItem(i, item(statusPane, " ", null));
        inv.setItem(40, favorStatusPaper(favor)); // center

        // Row 3 layout is: [18][19][20][21][22][23][24][25][26]
        // We'll do: left panes = 18,19,20,21 | head = 22 | right panes = 23,24,25,26
        int[] left = new int[]{18, 19, 20, 21};
        int headSlot = 22;
        int[] right = new int[]{23, 24, 25, 26};

        // default light gray with side labels on hover
        for (int s : left) inv.setItem(s, item(Material.LIGHT_GRAY_STAINED_GLASS_PANE, " ", lore("§c§lSin")));
        for (int s : right) inv.setItem(s, item(Material.LIGHT_GRAY_STAINED_GLASS_PANE, " ", lore("§a§lHonor")));

        // player head hover shows score + status
        inv.setItem(headSlot, playerHead(viewer, "§bYou", lore(
                "§7Favor: §f" + AlignmentUtil.displayName(favor),
                "§7Score: §f" + score
        )));

        // Fill behavior:
        // Sin: all red
        // Honor: all lime
        // Balance: fill proportionally on one side only
        if (favor == GuildAlignment.DARK) {
            for (int s : left) inv.setItem(s, item(Material.RED_STAINED_GLASS_PANE, " ", lore("§c§lSin")));
            for (int s : right) inv.setItem(s, item(Material.RED_STAINED_GLASS_PANE, " ", lore("§a§lHonor")));
        } else if (favor == GuildAlignment.HONORABLE) {
            for (int s : left) inv.setItem(s, item(Material.LIME_STAINED_GLASS_PANE, " ", lore("§c§lSin")));
            for (int s : right) inv.setItem(s, item(Material.LIME_STAINED_GLASS_PANE, " ", lore("§a§lHonor")));
        } else {
            int steps = 4;

            if (score > 0) {
                int fill = (int) Math.ceil((Math.min(100, score) / 100.0) * steps);
                fill = Math.max(0, Math.min(steps, fill));
                for (int i = 0; i < fill; i++) {
                    inv.setItem(right[i], item(Material.LIME_STAINED_GLASS_PANE, " ", lore("§a§lHonor")));
                }
            } else if (score < 0) {
                int abs = Math.abs(score);
                int fill = (int) Math.ceil((Math.min(100, abs) / 100.0) * steps);
                fill = Math.max(0, Math.min(steps, fill));
                int[] leftNearCenterFirst = new int[]{21, 20, 19, 18};
                for (int i = 0; i < fill; i++) {
                    inv.setItem(leftNearCenterFirst[i], item(Material.RED_STAINED_GLASS_PANE, " ", lore("§c§lSin")));
                }
            }
        }

        return inv;
    }

    private static Material logMaterial(String entry) {
        String lower = entry.toLowerCase();
        if (lower.contains("deposit")) return Material.EMERALD;
        if (lower.contains("withdraw")) return Material.GOLD_INGOT;
        if (lower.contains("invite") || lower.contains("join")) return Material.PLAYER_HEAD;
        if (lower.contains("kick") || lower.contains("remove")) return Material.IRON_SWORD;
        if (lower.contains("tax")) return Material.SUNFLOWER;
        if (lower.contains("war")) return Material.NETHERITE_SWORD;
        if (lower.contains("ally")) return Material.LIME_BANNER;
        if (lower.contains("claim")) return Material.GRASS_BLOCK;
        return Material.PAPER;
    }

    private static ItemStack favorStatusPaper(GuildAlignment favor) {
        String favorName = AlignmentUtil.displayName(favor);

        String color;
        String msg;

        if (favor == GuildAlignment.HONORABLE) {
            color = "§a";
            msg = "Your deeds have been recognized by the world.";
        } else if (favor == GuildAlignment.DARK) {
            color = "§c";
            msg = "Your stage is the abyss.";
        } else {
            color = "§f";
            msg = "You choose to walk your own path.";
        }

        return item(Material.PAPER, "§bFavor Status", lore(
                color + favorName,
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
