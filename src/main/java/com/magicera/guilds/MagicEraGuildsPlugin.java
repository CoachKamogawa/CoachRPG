package com.magicera.guilds;

import com.magicera.guilds.alignment.AlignmentWatcher;
import com.magicera.guilds.alignment.JoinListener;
import com.magicera.guilds.commands.DuelCommand;
import com.magicera.guilds.commands.FavorCommand;
import com.magicera.guilds.commands.GuildCommand;
import com.magicera.guilds.commands.PartyCommand;
import com.magicera.guilds.econ.EconomyHook;
import com.magicera.guilds.gui.MenuListener;
import com.magicera.guilds.guilds.GuildMaintenanceTask;
import com.magicera.guilds.guilds.GuildPowerService;
import com.magicera.guilds.guilds.InviteManager;
import com.magicera.guilds.guilds.VaultLogManager;
import com.magicera.guilds.guilds.VaultManager;
import com.magicera.guilds.listeners.GuildChatListener;
import com.magicera.guilds.listeners.GuildProtectionListener;
import com.magicera.guilds.listeners.GuildWarPowerListener;
import com.magicera.guilds.listeners.PlayerSeenListener;
import com.magicera.guilds.listeners.PvpKdaListener;
import com.magicera.guilds.storage.Storage;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public final class MagicEraGuildsPlugin extends JavaPlugin {

    private static final long WEEK_MS = 7L * 24L * 60L * 60L * 1000L;

    private Storage storage;
    private AlignmentWatcher alignmentWatcher;
    private InviteManager inviteManager;
    private VaultManager vaults;
    private VaultLogManager vaultLogs;
    private GuildPowerService guildPower;

    private EconomyHook economyHook;
    private File territoryFile;
    private YamlConfiguration territoryConfig;
    private File runtimeDataFile;
    private YamlConfiguration runtimeDataConfig;
    private long nextGuildTaxEpochMs;

    private BukkitTask autoSaveTask;
    private BukkitTask guildTaxTask;
    private BukkitTask alignmentWarningTask;
    private BukkitTask guildMaintenanceTask;
    private BukkitTask backupCleanupTask;

    public Storage storage() { return storage; }
    public AlignmentWatcher alignmentWatcher() { return alignmentWatcher; }
    public InviteManager inviteManager() { return inviteManager; }
    public VaultManager vaults() { return vaults; }
    public VaultLogManager vaultLogs() { return vaultLogs; }
    public EconomyHook economy() { return economyHook; }
    public GuildPowerService guildPower() { return guildPower; }
    public FileConfiguration territoryConfig() { return territoryConfig == null ? getConfig() : territoryConfig; }
    public long nextGuildTaxEpochMs() { return nextGuildTaxEpochMs; }

    @Override
    public void onEnable() {
        try {
            reloadPluginConfigs();

            storage = new Storage(this);
            storage.load();

            inviteManager = new InviteManager(this);
            vaults = new VaultManager(this);
            vaultLogs = new VaultLogManager(this);

            economyHook = new EconomyHook();
            guildPower = new GuildPowerService(this);
            boolean econOk = economyHook.setup();

            // Commands
            registerCmd("guild", new GuildCommand(this));
            registerCmd("party", new PartyCommand(this));
            registerCmd("duel", new DuelCommand(this));
            registerCmd("favor", new FavorCommand(this));

            // Listeners
            Bukkit.getPluginManager().registerEvents(new MenuListener(this), this);
            Bukkit.getPluginManager().registerEvents(new PlayerSeenListener(this), this);
            Bukkit.getPluginManager().registerEvents(new GuildChatListener(this), this);
            Bukkit.getPluginManager().registerEvents(new GuildProtectionListener(this), this);
            Bukkit.getPluginManager().registerEvents(new GuildWarPowerListener(this), this);
            Bukkit.getPluginManager().registerEvents(new PvpKdaListener(this), this);

            alignmentWatcher = new AlignmentWatcher(this);
            Bukkit.getPluginManager().registerEvents(new JoinListener(alignmentWatcher), this);

            startRecurringTasks();

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

    public void reloadPluginConfigs() {
        saveDefaultConfig();
        reloadConfig();

        if (!new File(getDataFolder(), "territory.yml").exists()) {
            saveResource("territory.yml", false);
        }
        territoryFile = new File(getDataFolder(), "territory.yml");
        territoryConfig = YamlConfiguration.loadConfiguration(territoryFile);
        
        runtimeDataFile = new File(getDataFolder(), "data.yml");
        runtimeDataConfig = YamlConfiguration.loadConfiguration(runtimeDataFile);

        validateRegistrationConfig();

        long now = System.currentTimeMillis();
        nextGuildTaxEpochMs = runtimeDataConfig.getLong("economy.next-guild-tax-epoch-ms", now + WEEK_MS);
    }

    private void validateRegistrationConfig() {
        if (!getConfig().contains("guildRegistration.cost")) {
            getLogger().warning("Missing config key guildRegistration.cost. Defaulting to 0.0 until config.yml is corrected.");
        }
        if (!getConfig().isConfigurationSection("guildRegistration.messages")) {
            getLogger().warning("Missing section guildRegistration.messages. Registration feedback messages may not be shown.");
            return;
        }

        String[] requiredMessageKeys = {
                "registerSuccess",
                "registerInsufficientFunds",
                "registerAlreadyInGuild",
                "unregisterNotice",
                "vaultMissing",
                "playerNotFound"
        };
        for (String key : requiredMessageKeys) {
            if (!getConfig().contains("guildRegistration.messages." + key)) {
                getLogger().warning("Missing config key guildRegistration.messages." + key + ". Falling back to built-in message.");
            }
        }
    }

    public void saveRuntimeData() {
        if (runtimeDataConfig == null) {
            runtimeDataConfig = new YamlConfiguration();
        }
        if (runtimeDataFile == null) {
            runtimeDataFile = new File(getDataFolder(), "data.yml");
        }

        runtimeDataConfig.set("economy.next-guild-tax-epoch-ms", nextGuildTaxEpochMs);
        try {
            runtimeDataConfig.save(runtimeDataFile);
        } catch (IOException e) {
            getLogger().warning("Failed to save data.yml: " + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()));
        }
    }

    public List<String> resetAndReloadPluginConfigs() {
        List<String> deletedFiles = new ArrayList<>();

        Bukkit.getScheduler().cancelTasks(this);
        autoSaveTask = null;
        guildTaxTask = null;
        alignmentWarningTask = null;
        guildMaintenanceTask = null;
        backupCleanupTask = null;

        if (inviteManager != null) inviteManager.clearAll();

        if (!getDataFolder().exists()) {
            //noinspection ResultOfMethodCallIgnored
            getDataFolder().mkdirs();
        }

        File[] files = getDataFolder().listFiles();
        if (files != null) {
            for (File file : files) {
                deleteRecursively(file, deletedFiles);
            }
        }

        reloadPluginConfigs();

        if (storage != null) {
            storage.clearAllData();
            storage.save();
        }

        startRecurringTasks();
        return deletedFiles;
    }

    private void deleteRecursively(File file, List<String> deletedFiles) {
        if (file == null || !file.exists()) return;

        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteRecursively(child, deletedFiles);
                }
            }
        }

        try {
            String relative = getDataFolder().toPath().relativize(file.toPath()).toString().replace('\\', '/');
            if (file.delete()) {
                deletedFiles.add(relative.isBlank() ? file.getName() : relative);
            } else {
                getLogger().warning("Failed to delete plugin data path during thanossnap: " + file.getAbsolutePath());
            }
        } catch (Exception ex) {
            getLogger().warning("Error deleting plugin data path during thanossnap: " + file.getAbsolutePath() + " (" + ex.getMessage() + ")");
        }
    }

    private void startRecurringTasks() {
        int intervalSeconds = Math.max(30, getConfig().getInt("data.save-interval-seconds", 120));

        // Save only when something actually changed, and never lose a save due to lock overlap.
        autoSaveTask = Bukkit.getScheduler().runTaskTimer(this, () -> {
            if (storage == null) return;
            if (!storage.isDirty()) return;

            try {
                storage.save();
            } catch (Exception e) {
                getLogger().warning("Auto-save failed: " + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()));
            }
        }, 20L * intervalSeconds, 20L * intervalSeconds);

        guildTaxTask = Bukkit.getScheduler().runTaskTimer(this, () -> {
            long current = System.currentTimeMillis();
            if (current < nextGuildTaxEpochMs) return;
            runGuildTaxCycle(Bukkit.getConsoleSender(), false);
        }, 20L * 60L, 20L * 60L);

        int warnMinutes = Math.max(1, getConfig().getInt("alignment.warn-interval-minutes", 30));
        alignmentWarningTask = Bukkit.getScheduler().runTaskTimer(this, alignmentWatcher,
                20L * 60L * warnMinutes,
                20L * 60L * warnMinutes);

        guildMaintenanceTask = Bukkit.getScheduler().runTaskTimer(this, new GuildMaintenanceTask(this), 20L * 60L, 20L * 60L);

        // Backup cleanup (keeps .bak from lingering forever if you stop saving for a long time).
        // Default: 7 days (weekly). Runs daily.
        int retentionDays = Math.max(1, getConfig().getInt("data.backup-retention-days", 7));
        long retentionMs = retentionDays * 24L * 60L * 60L * 1000L;

        backupCleanupTask = Bukkit.getScheduler().runTaskTimer(this, () -> {
            File dir = getDataFolder();
            if (dir == null || !dir.exists()) return;

            long now = System.currentTimeMillis();
            File[] files = dir.listFiles();
            if (files == null) return;

            for (File f : files) {
                if (!f.isFile()) continue;

                String name = f.getName().toLowerCase();
                if (name.endsWith(".bak") || name.endsWith(".tmp")) {
                    long age = now - f.lastModified();
                    if (age >= retentionMs) {
                        //noinspection ResultOfMethodCallIgnored
                        f.delete();
                    }
                }
            }
        }, 20L * 60L, 20L * 60L * 60L * 24L);
    }

    public void runGuildTaxCycle(CommandSender initiator, boolean forced) {
        Economy econ = (economy() == null) ? null : economy().econ();
        if (econ == null) {
            initiator.sendMessage("§7[§aGuild§7] §cEconomy is not available. Cannot run guild tax.");
            return;
        }

        int chargedMembers = 0;
        int failedMembers = 0;
        double totalCollected = 0.0;

        for (var guild : storage.allGuilds()) {
            int taxPercent = guild.getTaxPercent();
            if (taxPercent <= 0) continue;

            for (var entry : guild.getMembers().entrySet()) {
                OfflinePlayer member = Bukkit.getOfflinePlayer(entry.getKey());
                double balance = econ.getBalance(member);
                double taxAmount = balance * (taxPercent / 100.0);
                if (taxAmount <= 0.0) continue;

                EconomyResponse withdrawal = econ.withdrawPlayer(member, taxAmount);
                if (!withdrawal.transactionSuccess()) {
                    failedMembers++;
                    continue;
                }

                guild.setBankBalance(guild.getBankBalance() + taxAmount);
                guild.addLogEntry("Tax collected from " + (member.getName() == null ? member.getUniqueId() : member.getName())
                        + " $" + String.format("%.2f", taxAmount));

                var pd = storage.getOrCreatePlayer(entry.getKey());
                if (member.isOnline() && member.getPlayer() != null) {
                    member.getPlayer().sendMessage("§7[§aGuild§7] §eYou have been taxed: §f$" + String.format("%.2f", taxAmount)
                            + " §7(" + taxPercent + "%)");
                } else {
                    pd.setPendingTaxNoticeAmount(pd.getPendingTaxNoticeAmount() + taxAmount);
                }

                chargedMembers++;
                totalCollected += taxAmount;
            }
        }

        storage.markDirty();
        storage.save();

        nextGuildTaxEpochMs = System.currentTimeMillis() + WEEK_MS;
        saveRuntimeData();

        String reason = forced ? "forced" : "scheduled";
        initiator.sendMessage("§7[§aGuild§7] §aGuild tax cycle (" + reason + ") complete. Members charged: §f"
                + chargedMembers + "§a, failed charges: §f" + failedMembers
                + "§a, total: §f$" + String.format("%.2f", totalCollected));
    }

    @Override
    public void onDisable() {
        if (autoSaveTask != null) autoSaveTask.cancel();
        if (guildTaxTask != null) guildTaxTask.cancel();
        if (alignmentWarningTask != null) alignmentWarningTask.cancel();
        if (guildMaintenanceTask != null) guildMaintenanceTask.cancel();
        if (backupCleanupTask != null) backupCleanupTask.cancel();

        if (territoryConfig != null && territoryFile != null) {
            try {
                territoryConfig.save(territoryFile);
            } catch (Exception e) {
                getLogger().warning("Failed to save territory.yml on disable: " + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()));
            }
        }
        if (storage != null) {
            try {
                storage.save();
            } catch (Exception e) {
                getLogger().warning("Failed to save storage on disable: " + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()));
            }
        }
        saveRuntimeData();
        getLogger().info("MagicEraGuilds disabled.");
    }

    private void registerCmd(String name, CommandExecutor executor) {
        PluginCommand cmd = getCommand(name);
        if (cmd == null) {
            getLogger().warning("Command '/" + name + "' is missing from plugin.yml (not registered).");
            return;
        }

        cmd.setExecutor(executor);

        if (executor instanceof TabCompleter completer) {
            cmd.setTabCompleter(completer);
        }
    }
}
