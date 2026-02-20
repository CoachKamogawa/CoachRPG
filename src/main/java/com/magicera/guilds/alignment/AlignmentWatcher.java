package com.magicera.guilds.alignment;

import com.magicera.guilds.MagicEraGuildsPlugin;
import com.magicera.guilds.data.Guild;
import com.magicera.guilds.data.GuildAlignment;
import com.magicera.guilds.data.PlayerData;
import com.magicera.guilds.util.AlignmentUtil;
import com.magicera.guilds.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

public final class AlignmentWatcher implements Runnable {

    private final MagicEraGuildsPlugin plugin;

    public AlignmentWatcher(MagicEraGuildsPlugin plugin) {
        this.plugin = plugin;
    }

    // Used by JoinListener for delayed execution
    public MagicEraGuildsPlugin getPlugin() {
        return plugin;
    }

    @Override
    public void run() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            checkAndWarn(p, false);
        }
    }

    public void checkAndWarn(Player p, boolean isLogin) {
        PlayerData pd = plugin.storage().getOrCreatePlayer(p.getUniqueId());
        if (pd.getGuildId() == null) {
            pd.setOutOfAlignmentSinceEpochMs(null);
            return;
        }

        Guild g = plugin.storage().getGuild(pd.getGuildId());
        if (g == null) {
            pd.setGuildId(null);
            pd.setOutOfAlignmentSinceEpochMs(null);
            plugin.storage().save();
            return;
        }

        GuildAlignment playerGroup = AlignmentUtil.groupFromScore(pd.getAlignmentScore());
        GuildAlignment guildGroup = g.getAlignment();

        // If alignment matches, clear timer
        if (playerGroup == guildGroup) {
            if (pd.getOutOfAlignmentSinceEpochMs() != null) {
                pd.setOutOfAlignmentSinceEpochMs(null);
                plugin.storage().save();
            }
            return;
        }

        long now = System.currentTimeMillis();
        Long since = pd.getOutOfAlignmentSinceEpochMs();

        if (since == null) {
            pd.setOutOfAlignmentSinceEpochMs(now);
            plugin.storage().save();
            since = now;
        }

        long graceHours = plugin.getConfig().getLong("alignment.out-of-alignment-grace-hours", 48);
        long graceMs = TimeUnit.HOURS.toMillis(graceHours);
        long elapsed = now - since;
        long remaining = Math.max(0L, graceMs - elapsed);

        String timeLeft = formatTime(remaining);

        String key = switch (guildGroup) {
            case HONORABLE -> "honorable";
            case NEUTRAL -> "neutral";
            case DARK -> "dark";
        };

        String restore = switch (guildGroup) {
            case DARK -> "&cSin";
            case NEUTRAL -> "&fBalance";
            case HONORABLE -> "&aHonor";
        };

        String path = "alignment.messages." + key + "." + (isLogin ? "login" : "repeat");
        String msg = plugin.getConfig().getString(path,
                "&7[&aGuild&7] &cYou are out of favor with your guild. You have &e%timeleft% &cto restore your %restore%&c.");

        msg = msg.replace("%timeleft%", timeLeft).replace("%restore%", Text.color(restore));
        p.sendMessage(Text.color(msg));

        // (Kick logic can be added later if remaining == 0)
    }

    private String formatTime(long ms) {
        Duration d = Duration.ofMillis(ms);
        long hours = d.toHours();
        long minutes = d.toMinutes() % 60;
        return hours + "h " + minutes + "m";
    }
}
