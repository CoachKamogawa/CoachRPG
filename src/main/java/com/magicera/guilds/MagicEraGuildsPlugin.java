package com.magicera.guilds;

import com.magicera.guilds.alignment.AlignmentWatcher;
import com.magicera.guilds.alignment.JoinListener;
import com.magicera.guilds.commands.DuelCommand;
import com.magicera.guilds.commands.FavorCommand;
import com.magicera.guilds.commands.GuildCommand;
import com.magicera.guilds.commands.PartyCommand;
import com.magicera.guilds.econ.EconomyHook;
import com.magicera.guilds.gui.MenuListener;
import com.magicera.guilds.guilds.InviteManager;
import com.magicera.guilds.guilds.VaultManager;
import com.magicera.guilds.listeners.GuildChatListener;
import com.magicera.guilds.listeners.PlayerSeenListener;
import com.magicera.guilds.storage.Storage;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.plugin.java.JavaPlugin;

public final class MagicEraGuildsPlugin extends JavaPlugin {

    private static final long WEEK_MS = 7L * 24L * 60L * 60L * 1000L;

    private Storage storage;
    private AlignmentWatcher alignmentWatcher;
    private InviteManager inviteManager;
    private VaultManager vaults;

    private EconomyHook economyHook;
    private long nextGuildTaxEpochMs;

    public Storage storage() { return storage; }
    public AlignmentWatcher alignmentWatcher() { return alignmentWatcher; }
    public InviteManager inviteManager() { return inviteManager; }
    public VaultManager vaults() { return vaults; }
    public EconomyHook economy() { return economyHook; }
    public long nextGuildTaxEpochMs() { return nextGuildTaxEpochMs; }

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

            long now = System.currentTimeMillis();
            nextGuildTaxEpochMs = getConfig().getLong("economy.next-guild-tax-epoch-ms", now + WEEK_MS);

            // Commands
            registerCmd("guild", new GuildCommand(this));
            registerCmd("party", new PartyCommand(this));
            registerCmd("duel", new DuelCommand(this));
            registerCmd("favor", new FavorCommand(this));

            // Listeners
            Bukkit.getPluginManager().registerEvents(new MenuListener(this), this);
            Bukkit.getPluginManager().registerEvents(new PlayerSeenListener(this), this);
            Bukkit.getPluginManager().registerEvents(new GuildChatListener(this), this);

            alignmentWatcher = new AlignmentWatcher(this);
            Bukkit.getPluginManager().registerEvents(new JoinListener(alignmentWatcher), this);

            // Auto-save
            int intervalSeconds = Math.max(30, getConfig().getInt("data.save-interval-seconds", 120));
            Bukkit.getScheduler().runTaskTimerAsynchronously(this, () -> {
                try { storage.save(); } catch (Exception ignored) {}
            }, 20L * intervalSeconds, 20L * intervalSeconds);

            // Weekly guild tax check (runs every minute)
            Bukkit.getScheduler().runTaskTimer(this, () -> {
                long current = System.currentTimeMillis();
                if (current < nextGuildTaxEpochMs) return;
                runGuildTaxCycle(Bukkit.getConsoleSender(), false);
            }, 20L * 60L, 20L * 60L);

            // Alignment warnings
            int warnMinutes = Math.max(1, getConfig().getInt("alignment.warn-interval-minutes", 30));
            Bukkit.getScheduler().runTaskTimer(this, alignmentWatcher,
                    20L * 60L * warnMinutes,
                    20L * 60L * warnMinutes);

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

    public void runGuildTaxCycle(CommandSender initiator, boolean forced) {
        Economy econ = (economy() == null) ? null : economy().econ();
        if (econ == null) {
            initiator.sendMessage("§cEconomy is not available. Cannot run guild tax.");
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
                chargedMembers++;
                totalCollected += taxAmount;
            }
        }

        storage.save();

        nextGuildTaxEpochMs = System.currentTimeMillis() + WEEK_MS;
        getConfig().set("economy.next-guild-tax-epoch-ms", nextGuildTaxEpochMs);
        saveConfig();

        String reason = forced ? "forced" : "scheduled";
        initiator.sendMessage("§aGuild tax cycle (" + reason + ") complete. Members charged: §f"
                + chargedMembers + "§a, failed charges: §f" + failedMembers
                + "§a, total: §f$" + String.format("%.2f", totalCollected));
    }

    @Override
    public void onDisable() {
        if (storage != null) {
            try { storage.save(); } catch (Exception ignored) {}
        }
        getConfig().set("economy.next-guild-tax-epoch-ms", nextGuildTaxEpochMs);
        saveConfig();
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
