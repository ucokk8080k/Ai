package id.aibot;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/** Mengelola daftar bot, siapa yang sedang online, serta join/leave acak. */
public class BotManager {

    private final AIBotPlugin plugin;
    private final Random rnd = new Random();
    private final Map<String, Bot> bots = new LinkedHashMap<String, Bot>();
    private boolean running;

    public BotManager(AIBotPlugin plugin) {
        this.plugin = plugin;
    }

    // ---------------------------------------------------------------- lifecycle

    public synchronized void load() {
        bots.clear();
        int pmin = plugin.getConfig().getInt("tab-list.ping.min", 20);
        int pmax = Math.max(pmin, plugin.getConfig().getInt("tab-list.ping.max", 160));
        for (String n : plugin.getConfig().getStringList("bots.names")) {
            n = n.trim();
            if (n.isEmpty() || n.length() > 16) continue;
            bots.put(n.toLowerCase(), new Bot(n, pmin + rnd.nextInt(pmax - pmin + 1)));
        }
        // online awal (tanpa pesan join)
        int min = plugin.getConfig().getInt("bots.min-online", 8);
        int max = Math.max(min, plugin.getConfig().getInt("bots.max-online", 18));
        int want = Math.min(bots.size(), min + rnd.nextInt(max - min + 1));
        List<Bot> all = new ArrayList<Bot>(bots.values());
        Collections.shuffle(all, rnd);
        for (int i = 0; i < want; i++) all.get(i).online = true;
    }

    public void start() {
        running = true;
        scheduleFluctuation();
    }

    public void stop() {
        running = false;
        // hapus bot dari tab semua pemain
        TabBridge tab = plugin.tab();
        if (tab != null) {
            List<Bot> on = onlineBots();
            for (Player p : Bukkit.getOnlinePlayers()) tab.remove(on, p);
        }
    }

    private void scheduleFluctuation() {
        if (!running) return;
        int min = plugin.getConfig().getInt("bots.fluctuate-seconds.min", 60);
        int max = Math.max(min, plugin.getConfig().getInt("bots.fluctuate-seconds.max", 240));
        long delay = (min + rnd.nextInt(max - min + 1)) * 20L;
        Bukkit.getScheduler().runTaskLater(plugin, new Runnable() {
            @Override
            public void run() {
                if (!running || !plugin.isEnabled()) return;
                try {
                    fluctuate();
                } finally {
                    scheduleFluctuation();
                }
            }
        }, delay);
    }

    private void fluctuate() {
        int min = plugin.getConfig().getInt("bots.min-online", 8);
        int max = Math.max(min, plugin.getConfig().getInt("bots.max-online", 18));
        List<Bot> on = onlineBots();
        List<Bot> off = new ArrayList<Bot>();
        synchronized (this) {
            for (Bot b : bots.values()) if (!b.online) off.add(b);
        }
        boolean join;
        if (on.size() <= min) join = true;
        else if (on.size() >= max) join = false;
        else join = rnd.nextBoolean();

        if (join && !off.isEmpty()) join(off.get(rnd.nextInt(off.size())), true);
        else if (!join && !on.isEmpty()) leave(on.get(rnd.nextInt(on.size())), true);
    }

    // ---------------------------------------------------------------- join / leave

    public void join(Bot bot, boolean announce) {
        if (bot.online && announce) return;
        bot.online = true;
        TabBridge tab = plugin.tab();
        if (tab != null) {
            Collection<Bot> one = Collections.singletonList(bot);
            for (Player p : Bukkit.getOnlinePlayers()) tab.add(one, p);
        }
        if (announce && plugin.getConfig().getBoolean("bots.announce-join-leave", true)) {
            broadcast(plugin.getConfig().getString("bots.join-message", "&e%name% joined the game"), bot);
        }
    }

    public void leave(Bot bot, boolean announce) {
        if (!bot.online) return;
        bot.online = false;
        TabBridge tab = plugin.tab();
        if (tab != null) {
            Collection<Bot> one = Collections.singletonList(bot);
            for (Player p : Bukkit.getOnlinePlayers()) tab.remove(one, p);
        }
        if (announce && plugin.getConfig().getBoolean("bots.announce-join-leave", true)) {
            broadcast(plugin.getConfig().getString("bots.leave-message", "&e%name% left the game"), bot);
        }
    }

    private void broadcast(String format, Bot bot) {
        Bukkit.broadcastMessage(ChatColor.translateAlternateColorCodes('&', format.replace("%name%", bot.name)));
    }

    /** Kirim semua bot online ke satu pemain (saat dia join). */
    public void showTo(Player p) {
        TabBridge tab = plugin.tab();
        if (tab != null && plugin.getConfig().getBoolean("tab-list.enabled", true)) tab.add(onlineBots(), p);
    }

    // ---------------------------------------------------------------- query

    public synchronized List<Bot> onlineBots() {
        List<Bot> out = new ArrayList<Bot>();
        for (Bot b : bots.values()) if (b.online) out.add(b);
        return out;
    }

    public synchronized List<Bot> allBots() {
        return new ArrayList<Bot>(bots.values());
    }

    public synchronized Bot get(String name) {
        return bots.get(name.toLowerCase());
    }
}
