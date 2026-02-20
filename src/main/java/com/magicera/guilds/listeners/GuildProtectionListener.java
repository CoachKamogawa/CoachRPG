package com.magicera.guilds.listeners;

import com.magicera.guilds.MagicEraGuildsPlugin;
import com.magicera.guilds.data.Guild;
import com.magicera.guilds.data.PlayerData;
import com.nisovin.magicspells.events.SpellTargetEvent;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
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
            Material.SHULKER_BOX, Material.ENDER_CHEST
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
            event.getPlayer().sendMessage("§cThis land is claimed.");
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (!canBuild(event.getPlayer(), event.getBlock())) {
            event.setCancelled(true);
            event.getPlayer().sendMessage("§cThis land is claimed.");
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getClickedBlock() == null) return;
        Block b = event.getClickedBlock();
        if (b.getState() instanceof InventoryHolder || CONTAINER_MATERIALS.contains(b.getType())) {
            if (!canBuild(event.getPlayer(), b)) {
                event.setCancelled(true);
                event.getPlayer().sendMessage("§cYou cannot access containers in this claim.");
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPvp(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) return;
        Player attacker = asPlayerDamager(event.getDamager());
        if (attacker == null) return;
        if (isFriendly(attacker, victim)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCombust(EntityCombustByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) return;
        Player attacker = asPlayerDamager(event.getCombuster());
        if (attacker == null) return;
        if (isFriendly(attacker, victim)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSpellTarget(SpellTargetEvent event) {
        if (!(event.getTarget() instanceof Player victim)) return;
        if (!(event.getSpellData().caster() instanceof Player caster)) return;
        if (caster.getUniqueId().equals(victim.getUniqueId())) return;

        if (!isFriendly(caster, victim)) return;

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
        if (owner == null) return;

        String sub = owner.getDescription().isEmpty()
                ? "§7No description set."
                : "§7" + owner.getDescription();

        player.sendTitle("§f[" + owner.getName() + "§f]", sub, 5, 50, 10);
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

    private boolean isFriendly(Player a, Player b) {
        PlayerData ap = plugin.storage().getOrCreatePlayer(a.getUniqueId());
        PlayerData bp = plugin.storage().getOrCreatePlayer(b.getUniqueId());
        if (ap.getGuildId() == null || bp.getGuildId() == null) return false;
        if (ap.getGuildId().equals(bp.getGuildId())) return true;

        Guild ag = plugin.storage().getGuild(ap.getGuildId());
        return ag != null && ag.getAllies().contains(bp.getGuildId());
    }
}
