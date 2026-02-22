package com.magicera.guilds.listeners;

import com.magicera.guilds.MagicEraGuildsPlugin;
import com.magicera.guilds.data.Guild;
import com.magicera.guilds.data.PlayerData;
import com.magicera.guilds.util.Text;
import com.nisovin.magicspells.events.SpellTargetEvent;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityCombustByEntityEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.InventoryHolder;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class GuildProtectionListener implements Listener {

    private static final Set<Material> CONTAINER_MATERIALS = Set.of(
            Material.CHEST, Material.TRAPPED_CHEST, Material.BARREL,
            Material.FURNACE, Material.BLAST_FURNACE, Material.SMOKER,
            Material.HOPPER, Material.DISPENSER, Material.DROPPER,
            Material.SHULKER_BOX, Material.ENDER_CHEST,
            Material.BREWING_STAND, Material.CRAFTER
    );

    private final MagicEraGuildsPlugin plugin;

    // Tracks the last claim shown per player (by guild id); avoids spamming titles.
    private final Map<UUID, String> lastShownClaim = new HashMap<>();

    public GuildProtectionListener(MagicEraGuildsPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        if (!canBuild(event.getPlayer(), event.getBlock())) {
            event.setCancelled(true);
            event.getPlayer().sendMessage("§7[§aGuild§7] §cThis land is claimed.");
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (!canBuild(event.getPlayer(), event.getBlock())) {
            event.setCancelled(true);
            event.getPlayer().sendMessage("§7[§aGuild§7] §cThis land is claimed.");
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getClickedBlock() == null) return;
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_BLOCK && action != Action.PHYSICAL) return;

        Block block = event.getClickedBlock();
        Player player = event.getPlayer();
        Guild owner = ownerOf(block);
        if (owner == null) return;

        PlayerData pd = plugin.storage().getOrCreatePlayer(player.getUniqueId());
        String playerGuildId = pd.getGuildId();
        boolean sameGuild = owner.getId().equals(playerGuildId);
        Guild playerGuild = playerGuildId == null ? null : plugin.storage().getGuild(playerGuildId);
        boolean enemyAtWar = playerGuild != null
                && playerGuild.getEnemies().contains(owner.getId())
                && owner.getEnemies().contains(playerGuild.getId());

        if (isContainer(block)) {
            if (!sameGuild) {
                event.setCancelled(true);
                player.sendMessage("§7[§aGuild§7] §cYou cannot access containers in this claim.");
            }
            return;
        }

        if (!sameGuild && !enemyAtWar && !canBuild(player, block)) {
            event.setCancelled(true);
            player.sendMessage("§7[§aGuild§7] §cThis land is claimed.");
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPvp(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) return;
        Player attacker = asPlayerDamager(event.getDamager());
        if (attacker == null) return;

        if (shouldBlockWarNeutralInteraction(attacker, victim) || shouldBlockFriendlyFire(attacker, victim)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCombust(EntityCombustByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) return;
        Player attacker = asPlayerDamager(event.getCombuster());
        if (attacker == null) return;

        if (shouldBlockWarNeutralInteraction(attacker, victim) || shouldBlockFriendlyFire(attacker, victim)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSpellTarget(SpellTargetEvent event) {
        if (!(event.getTarget() instanceof Player victim)) return;
        if (!(event.getSpellData().caster() instanceof Player caster)) return;
        if (caster.getUniqueId().equals(victim.getUniqueId())) return;

        if (shouldBlockWarNeutralInteraction(caster, victim)) {
            event.setCancelled(true);
            event.setCastCancelled(true);
            return;
        }

        // Only block harmful spells when the relationship + toggles say so.
        if (!shouldBlockFriendlyFire(caster, victim)) return;

        String internalName = event.getSpell().getInternalName().toLowerCase();
        boolean positive = internalName.contains("heal") || internalName.contains("buff") || internalName.contains("regen");
        if (!positive) {
            event.setCancelled(true);
            event.setCastCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (event.getTo() == null) return;
        if (event.getFrom().getChunk().equals(event.getTo().getChunk())) return;

        Player player = event.getPlayer();

        Guild owner = ownerOf(event.getTo().getBlock());
        String ownerId = owner == null ? "" : owner.getId();

        String prev = lastShownClaim.getOrDefault(player.getUniqueId(), "");
        if (prev.equals(ownerId)) return;

        lastShownClaim.put(player.getUniqueId(), ownerId);
        if (owner == null) {
            if (!prev.isEmpty()) {
                Guild previousOwner = plugin.storage().getGuild(prev);
                if (previousOwner != null) {
                    showLeavingTerritoryTitle(player, previousOwner.getName());
                }
            }
            return;
        }

        String sub = owner.getDescription().isEmpty()
                ? "§7No description set."
                : "§7" + owner.getDescription();

        player.sendTitle("§f[" + owner.getName() + "§f]", sub, 5, 50, 10);
    }

    private void showLeavingTerritoryTitle(Player player, String guildName) {
        String safeGuildName = truncateGuildNameForLeavingTitle(guildName);
        player.sendTitle("§fLeaving " + safeGuildName + " territory.", "", 5, 35, 10);
    }

    private String truncateGuildNameForLeavingTitle(String guildName) {
        String plain = guildName == null ? "Guild" : Text.stripColors(guildName);
        int maxGuildChars = 24;
        if (plain.length() <= maxGuildChars) return plain;
        return plain.substring(0, Math.max(1, maxGuildChars - 3)) + "...";
    }

    private boolean isContainer(Block block) {
        return block.getState() instanceof InventoryHolder || CONTAINER_MATERIALS.contains(block.getType());
    }

    private boolean canBuild(Player player, Block block) {
        Guild owner = ownerOf(block);
        if (owner == null) return true;
        PlayerData pd = plugin.storage().getOrCreatePlayer(player.getUniqueId());
        return owner.getId().equals(pd.getGuildId());
    }

    private Guild ownerOf(Block block) {
        String key = Guild.chunkKey(block.getWorld().getName(), block.getChunk().getX(), block.getChunk().getZ());
        for (Guild g : plugin.storage().allGuilds()) {
            if (g.getClaimedChunks().contains(key)) return g;
        }
        return null;
    }

    private Player asPlayerDamager(Entity entity) {
        if (entity instanceof Player p) return p;
        if (entity instanceof Projectile proj && proj.getShooter() instanceof Player p) return p;
        return null;
    }

    private boolean shouldBlockWarNeutralInteraction(Player a, Player b) {
        PlayerData ap = plugin.storage().getOrCreatePlayer(a.getUniqueId());
        PlayerData bp = plugin.storage().getOrCreatePlayer(b.getUniqueId());
        if (ap.getGuildId() == null || bp.getGuildId() == null) return false;

        Guild ag = plugin.storage().getGuild(ap.getGuildId());
        Guild bg = plugin.storage().getGuild(bp.getGuildId());
        if (ag == null || bg == null || ag.getId().equals(bg.getId())) return false;

        return isNeutralObserverAgainst(ag, bg) || isNeutralObserverAgainst(bg, ag);
    }

    private boolean isNeutralObserverAgainst(Guild observer, Guild belligerent) {
        if (observer.isInWar() || !belligerent.isInWar()) return false;
        if (!areAllied(observer, belligerent)) return false;

        for (String enemyId : belligerent.getEnemies()) {
            Guild enemy = plugin.storage().getGuild(enemyId);
            if (enemy != null && areAllied(observer, enemy)) {
                return true;
            }
        }
        return false;
    }

    private boolean areAllied(Guild a, Guild b) {
        return a.getAllies().contains(b.getId()) || b.getAllies().contains(a.getId());
    }

    private boolean shouldBlockFriendlyFire(Player a, Player b) {
        PlayerData ap = plugin.storage().getOrCreatePlayer(a.getUniqueId());
        PlayerData bp = plugin.storage().getOrCreatePlayer(b.getUniqueId());
        if (ap.getGuildId() == null || bp.getGuildId() == null) return false;

        Guild ag = plugin.storage().getGuild(ap.getGuildId());
        Guild bg = plugin.storage().getGuild(bp.getGuildId());
        if (ag == null || bg == null) return false;

        // Same guild: blocked unless that guild explicitly enables friendly fire.
        if (ag.getId().equals(bg.getId())) {
            return !ag.isFriendlyFireEnabled();
        }

        // Allies: only considered "friendly" if either side has the other listed.
        boolean allied = ag.getAllies().contains(bg.getId()) || bg.getAllies().contains(ag.getId());
        if (!allied) return false;

        // Both guilds must opt in for allied PvP and harmful spells.
        return !(ag.isAllyFireEnabled() && bg.isAllyFireEnabled());
    }

    private boolean isFriendly(Player a, Player b) {
        PlayerData ap = plugin.storage().getOrCreatePlayer(a.getUniqueId());
        PlayerData bp = plugin.storage().getOrCreatePlayer(b.getUniqueId());
        if (ap.getGuildId() == null || bp.getGuildId() == null) return false;
        if (ap.getGuildId().equals(bp.getGuildId())) return true;

        Guild ag = plugin.storage().getGuild(ap.getGuildId());
        Guild bg = plugin.storage().getGuild(bp.getGuildId());
        return ag != null && bg != null && (ag.getAllies().contains(bg.getId()) || bg.getAllies().contains(ag.getId()));
    }
}
