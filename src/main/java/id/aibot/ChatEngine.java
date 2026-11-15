package id.aibot;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Random;

/** Otak obrolan bot: ngobrol sendiri, menyahut, dan membalas pemain (scripted atau AI). */
public class ChatEngine {

    private final AIBotPlugin plugin;
    private final AiClient ai;
    private final Random rnd = new Random();
    private final LinkedList<String> history = new LinkedList<String>();
    private final LinkedList<Long> replyTimes = new LinkedList<Long>();

    public ChatEngine(AIBotPlugin plugin) {
        this.plugin = plugin;
        this.ai = new AiClient(plugin);
    }

    private FileConfiguration cfg() {
        return plugin.getConfig();
    }

    // ---------------------------------------------------------------- util

    private int randBetween(String path, int defMin, int defMax) {
        int min = cfg().getInt(path + ".min", defMin);
        int max = Math.max(min, cfg().getInt(path + ".max", defMax));
        return min + rnd.nextInt(max - min + 1);
    }

    private String pick(List<String> list, String fallback) {
        if (list == null || list.isEmpty()) return fallback;
        return list.get(rnd.nextInt(list.size()));
    }

    private void remember(String line) {
        synchronized (history) {
            history.add(line);
            int cap = cfg().getInt("ai.history-lines", 10);
            while (history.size() > cap) history.removeFirst();
        }
    }

    /** Kirim chat sebagai bot (harus dipanggil dari main thread). */
    public void say(Bot bot, String message) {
        String format = cfg().getString("chat.format", "<%name%> %message%");
        String out = format.replace("%name%", bot.name).replace("%message%", message);
        Bukkit.broadcastMessage(ChatColor.translateAlternateColorCodes('&', out));
        remember(bot.name + ": " + message);
    }

    // ---------------------------------------------------------------- ambient

    /** Jadwalkan obrolan antar bot secara berulang. */
    public void scheduleAmbient() {
        if (!cfg().getBoolean("chat.ambient.enabled", true)) return;
        long delay = randBetween("chat.ambient.seconds", 25, 90) * 20L;
        Bukkit.getScheduler().runTaskLater(plugin, new Runnable() {
            @Override
            public void run() {
                if (!plugin.isEnabled()) return;
                try {
                    ambient();
                } finally {
                    scheduleAmbient();
                }
            }
        }, delay);
    }

    private void ambient() {
        final List<Bot> on = plugin.bots().onlineBots();
        if (on.isEmpty()) return;
        // sepi kalau tidak ada pemain asli? tetap ngobrol agar terlihat hidup
        final Bot a = on.get(rnd.nextInt(on.size()));
        Bot target = null;
        if (on.size() > 1 && rnd.nextDouble() < cfg().getDouble("chat.ambient.mention-chance", 0.35)) {
            do {
                target = on.get(rnd.nextInt(on.size()));
            } while (target == a);
        }
        final Bot tgt = target;

        if (ai.enabled() && cfg().getBoolean("ai.ambient", false)) {
            final String prompt = buildPrompt(a, "Mulai obrolan baru atau sambung obrolan terakhir"
                    + (tgt != null ? " dan mention @" + tgt.name : "") + ".");
            Bukkit.getScheduler().runTaskAsynchronously(plugin, new Runnable() {
                @Override
                public void run() {
                    String text = askAi(a, prompt);
                    final String finalText = text != null ? text : scriptedAmbient(tgt);
                    Bukkit.getScheduler().runTask(plugin, new Runnable() {
                        @Override
                        public void run() {
                            if (a.online) say(a, finalText);
                            maybeFollowUp(a);
                        }
                    });
                }
            });
            return;
        }
        say(a, scriptedAmbient(tgt));
        maybeFollowUp(a);
    }

    private String scriptedAmbient(Bot target) {
        if (target != null) {
            return pick(cfg().getStringList("chat.mention-lines"), "@%target% halo").replace("%target%", target.name);
        }
        return pick(cfg().getStringList("chat.ambient-lines"), "halo semua");
    }

    private void maybeFollowUp(final Bot speaker) {
        if (rnd.nextDouble() >= cfg().getDouble("chat.ambient.reply-chance", 0.45)) return;
        List<Bot> on = plugin.bots().onlineBots();
        on.remove(speaker);
        if (on.isEmpty()) return;
        final Bot replier = on.get(rnd.nextInt(on.size()));
        long delay = (2 + rnd.nextInt(4)) * 20L;
        Bukkit.getScheduler().runTaskLater(plugin, new Runnable() {
            @Override
            public void run() {
                if (!replier.online) return;
                String line = pick(cfg().getStringList("chat.reply-lines"), "wkwk iya");
                if (rnd.nextBoolean()) line = "@" + speaker.name + " " + line;
                say(replier, line);
            }
        }, delay);
    }

    // ---------------------------------------------------------------- balas pemain asli

    /** Dipanggil (main thread) saat pemain asli mengirim chat. */
    public void onPlayerChat(final String player, final String message) {
        remember(player + ": " + message);
        if (!cfg().getBoolean("chat.respond.enabled", true)) return;
        List<Bot> on = plugin.bots().onlineBots();
        if (on.isEmpty()) return;

        // apakah ada bot yang di-mention?
        Bot mentioned = null;
        String lower = message.toLowerCase();
        for (Bot b : on) {
            if (lower.contains(b.name.toLowerCase())) {
                mentioned = b;
                break;
            }
        }
        boolean force = mentioned != null && cfg().getBoolean("chat.respond.always-when-mentioned", true);
        if (!force && rnd.nextDouble() >= cfg().getDouble("chat.respond.chance", 0.85)) return;

        // batas balasan per menit
        long now = System.currentTimeMillis();
        while (!replyTimes.isEmpty() && now - replyTimes.getFirst() > 60000L) replyTimes.removeFirst();
        if (!force && replyTimes.size() >= cfg().getInt("chat.respond.max-per-minute", 6)) return;
        replyTimes.add(now);

        final Bot responder = mentioned != null ? mentioned : on.get(rnd.nextInt(on.size()));
        final long delay = randBetween("chat.respond.delay-seconds", 1, 4) * 20L;

        if (ai.enabled()) {
            final String prompt = buildPrompt(responder, "Balas pesan terakhir dari " + player + ".");
            Bukkit.getScheduler().runTaskAsynchronously(plugin, new Runnable() {
                @Override
                public void run() {
                    String text = askAi(responder, prompt);
                    final String reply = text != null ? text : scriptedReply(player, message);
                    sendLater(responder, reply, delay);
                }
            });
        } else {
            sendLater(responder, scriptedReply(player, message), delay);
        }
    }

    private void sendLater(final Bot bot, final String text, final long delayTicks) {
        Bukkit.getScheduler().runTaskLater(plugin, new Runnable() {
            @Override
            public void run() {
                if (bot.online) say(bot, text);
            }
        }, delayTicks);
    }

    @SuppressWarnings("unchecked")
    private String scriptedReply(String player, String message) {
        String lower = " " + message.toLowerCase().replaceAll("[^a-z0-9@ ]", " ") + " ";
        List<Map<?, ?>> rules = cfg().getMapList("chat.keyword-replies");
        List<String> candidates = null;
        for (Map<?, ?> rule : rules) {
            Object m = rule.get("match");
            if (!(m instanceof List)) continue;
            for (Object kw : (List<Object>) m) {
                if (lower.contains(" " + String.valueOf(kw).toLowerCase() + " ")
                        || (String.valueOf(kw).length() > 3 && lower.contains(String.valueOf(kw).toLowerCase()))) {
                    Object r = rule.get("replies");
                    if (r instanceof List) {
                        candidates = new ArrayList<String>();
                        for (Object o : (List<Object>) r) candidates.add(String.valueOf(o));
                    }
                    break;
                }
            }
            if (candidates != null) break;
        }
        if (candidates == null) candidates = cfg().getStringList("chat.fallback-replies");
        return pick(candidates, "iya bro").replace("%player%", player);
    }

    // ---------------------------------------------------------------- AI

    private String buildPrompt(Bot bot, String instruction) {
        StringBuilder sb = new StringBuilder("Riwayat chat server (terbaru di bawah):\n");
        synchronized (history) {
            for (String h : history) sb.append(h).append('\n');
        }
        sb.append('\n').append(instruction).append(" Tulis hanya isi chat-nya saja.");
        return sb.toString();
    }

    /** Panggil dari thread async. Mengembalikan null jika gagal. */
    private String askAi(Bot bot, String prompt) {
        try {
            String system = cfg().getString("ai.system-prompt", "").replace("%bot%", bot.name);
            return clean(ai.ask(system, prompt), bot.name);
        } catch (Exception e) {
            plugin.getLogger().warning("AI gagal, pakai balasan scripted: " + e.getMessage());
            return null;
        }
    }

    private String clean(String s, String botName) {
        if (s == null) return null;
        s = s.replace("\n", " ").replace("\r", " ").trim();
        s = ChatColor.stripColor(s);
        if (s.startsWith("<" + botName + ">")) s = s.substring(botName.length() + 2).trim();
        if (s.toLowerCase().startsWith(botName.toLowerCase() + ":")) s = s.substring(botName.length() + 1).trim();
        if (s.length() > 1 && s.startsWith("\"") && s.endsWith("\"")) s = s.substring(1, s.length() - 1);
        int max = cfg().getInt("ai.max-length", 100);
        if (s.length() > max) s = s.substring(0, max).trim();
        return s.isEmpty() ? null : s;
    }
}
