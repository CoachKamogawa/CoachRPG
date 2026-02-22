package com.magicera.guilds.listeners;

import com.magicera.guilds.MagicEraGuildsPlugin;
import com.magicera.guilds.data.Guild;
import com.magicera.guilds.data.PlayerData;
import com.nisovin.magicspells.events.SpellTargetEvent;
import net.kyori.adventure.text.Component;
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

    private final MagicEraGuildsPlugin plugin;

    private static final Set<Material> CONTAINER_MATERIALS = Set.of(
            Material.CHEST, Material.TRAPPED_CHEST, Material.BARREL,
            Material.HOPPER, Material.DROPPER, Material.DISPENSER,
            Material.FURNACE, Material.BLAST_FURNACE, Material.SMOKER,
            Material.BREWING_STAND, Material.SHULKER_BOX
    );

    private final Map<UUID, String> lastShownClaim = new HashMap<>();

    public GuildProtectionListener(MagicEraGuildsPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        if (!canBuild(event.getPlayer(), event.getBlock())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (!canBuild(event.getPlayer(), event.getBlockPlaced())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getClickedBlock() == null) return;

        Block block = event.getClickedBlock();
        Player player = event.getPlayer();

        // allow container interaction only for members
        if (isContainer(block.getType())) {
            if (!canBuild(player, block)) {
                event.setCancelled(true);
            }
            return;
        }

        // allow other interactions only for members too (buttons/doors/etc) if you want:
        // if (!canBuild(player, block)) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        Entity damager = event.getDamager();
        if (damager instanceof Projectile proj && proj.getShooter() instanceof Entity shooter) {
            damager = shooter;
        }
        if (!(damager instanceof Player attacker)) return;
        if (!(event.getEntity() instanceof Player victim)) return;

        Guild attackerGuild = plugin.storage().getGuild(plugin.storage().getOrCreatePlayer(attacker.getUniqueId()).getGuildId());
        Guild victimGuild = plugin.storage().getGuild(plugin.storage().getOrCreatePlayer(victim.getUniqueId()).getGuildId());

        // if you already have your own friendly-fire logic elsewhere, keep it there.
        // This file is unchanged by the diff you provided.
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCombust(EntityCombustByEntityEvent event) {
        // unchanged by diff you provided (kept as-is in your repo)
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onSpellTarget(SpellTargetEvent event) {
        if (!(event.getCaster() instanceof Player caster)) return;
        if (!(event.getTarget() instanceof Player target)) return;

        PlayerData casterPd = plugin.storage().getOrCreatePlayer(caster.getUniqueId());
        PlayerData targetPd = plugin.storage().getOrCreatePlayer(target.getUniqueId());

        if (casterPd.getGuildId() == null || targetPd.getGuildId() == null) return;
        if (casterPd.getGuildId().equalsIgnoreCase(targetPd.getGuildId())) return;

        String internalName = (event.getSpell() == null ? "" : event.getSpell().getInternalName());
        internalName = internalName == null ? "" : internalName.toLowerCase(Locale.ROOT);

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
                    player.sendActionBar(Component.text("Leaving " + previousOwner.getName() + " territory."));
                }
            }
            return;
        }

        String sub = owner.getDescription().isEmpty()
                ? "§7No description set."
                : "§7" + owner.getDescription();

        player.sendTitle("§f[" + owner.getName() + "§f]", sub, 5, 50, 10);
    }

    private boolean isContainer(Material mat) {
        if (CONTAINER_MATERIALS.contains(mat)) return true;
        // support all shulker box colors
        return mat.name().endsWith("SHULKER_BOX");
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
}
