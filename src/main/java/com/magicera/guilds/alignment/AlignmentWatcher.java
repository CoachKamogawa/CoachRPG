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

        String path = "alignment.messages." + key + "." + (isLogin ? "login" : "repeat");
        String msg = plugin.getConfig().getString(path, "&7[Guild] Out of alignment. Time left: %timeleft%");

        msg = msg.replace("%timeleft%", timeLeft);
        p.sendMessage(Text.color(msg));

        // Next increment: if remaining == 0 => auto-kick or other action.
    }

    private String formatTime(long ms) {
        Duration d = Duration.ofMillis(ms);
        long hours = d.toHours();
        long minutes = d.toMinutes() % 60;
        return hours + "h " + minutes + "m";
    }
}
