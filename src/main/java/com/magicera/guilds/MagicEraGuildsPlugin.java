package com.magicera.guilds;

import com.magicera.guilds.alignment.AlignmentWatcher;
import com.magicera.guilds.alignment.JoinListener;
import com.magicera.guilds.commands.AlignmentCommand;
import com.magicera.guilds.commands.DuelCommand;
import com.magicera.guilds.commands.GuildCommand;
import com.magicera.guilds.commands.PartyCommand;
import com.magicera.guilds.econ.EconomyHook;
import com.magicera.guilds.gui.MenuListener;
import com.magicera.guilds.guilds.InviteManager;
import com.magicera.guilds.guilds.VaultManager;
import com.magicera.guilds.listeners.PlayerSeenListener;
import com.magicera.guilds.storage.Storage;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

public final class MagicEraGuildsPlugin extends JavaPlugin {

    private Storage storage;
    private AlignmentWatcher alignmentWatcher;
    private InviteManager inviteManager;
    private VaultManager vaults;

    private EconomyHook economyHook;

    public Storage storage() { return storage; }
    public AlignmentWatcher alignmentWatcher() { return alignmentWatcher; }
    public InviteManager inviteManager() { return inviteManager; }
    public VaultManager vaults() { return vaults; }
    public EconomyHook economy() { return economyHook; }

    @Override
    public void onEnable() {
        try {
            saveDefaultConfig();

            storage = new Storage(this);
            storage.load();

            inviteManager = new InviteManager(this);
            vaults = new VaultManager(this);

            economyHook = new EconomyHook();
            boolean econOk = economyHook.setup();

            // -------------------------
            // Commands
            // -------------------------
            if (getCommand("guild") != null) getCommand("guild").setExecutor(new GuildCommand(this));
            if (getCommand("party") != null) getCommand("party").setExecutor(new PartyCommand(this));
            if (getCommand("duel") != null) getCommand("duel").setExecutor(new DuelCommand(this));
            if (getCommand("alignment") != null) getCommand("alignment").setExecutor(new AlignmentCommand(this));

            // -------------------------
            // Listeners
            // -------------------------

            // GUI listener (handles /guild menu and /align menus)
            Bukkit.getPluginManager().registerEvents(new MenuListener(this), this);

            // Player last-seen tracking
            Bukkit.getPluginManager().registerEvents(new PlayerSeenListener(this), this);

            // Alignment join check
            alignmentWatcher = new AlignmentWatcher(this);
            Bukkit.getPluginManager().registerEvents(new JoinListener(alignmentWatcher), this);

            // -------------------------
            // Auto-save
            // -------------------------
            int intervalSeconds = Math.max(30, getConfig().getInt("data.save-interval-seconds", 120));
            Bukkit.getScheduler().runTaskTimerAsynchronously(this, () -> {
                try { storage.save(); } catch (Exception ignored) {}
            }, 20L * intervalSeconds, 20L * intervalSeconds);

            // -------------------------
            // Alignment warning task
            // -------------------------
            int warnMinutes = Math.max(1, getConfig().getInt("alignment.warn-interval-minutes", 30));
            Bukkit.getScheduler().runTaskTimer(this, alignmentWatcher,
                    20L * 60L * warnMinutes,
                    20L * 60L * warnMinutes);

            // -------------------------
            // Startup log
            // -------------------------
            getLogger().info("====================================");
            getLogger().info("MagicEraGuilds loaded successfully");
            getLogger().info("Author: Coach Kamogawa");
            getLogger().info("Guilds loaded: " + storage.allGuilds().size());
            getLogger().info("Economy: " + (econOk ? "Vault hooked" : "DISABLED (Vault/Economy missing)"));
            getLogger().info("====================================");

            if (!econOk) {
                getLogger().warning("Vault economy not found. Guild bank features will not work until Vault + EssentialsX Economy are installed.");
            }

        } catch (Exception e) {
            getLogger().severe("MagicEraGuilds FAILED to load: " +
                    (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()));
            Bukkit.getPluginManager().disablePlugin(this);
        }
    }

    @Override
    public void onDisable() {
        if (storage != null) {
            try { storage.save(); } catch (Exception ignored) {}
        }
        getLogger().info("MagicEraGuilds disabled.");
    }
}
