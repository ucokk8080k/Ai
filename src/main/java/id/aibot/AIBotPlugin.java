package id.aibot;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;

public class AIBotPlugin extends JavaPlugin implements Listener {

    private BotManager bots;
    private ChatEngine chat;
    private TabBridge tab;
    private PingHook ping;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        if (Bukkit.getPluginManager().getPlugin("ProtocolLib") != null) {
            try {
                tab = new ProtocolBridge();
                ping = new PingHook(this);
                ping.register();
            } catch (Throwable t) {
                getLogger().warning("ProtocolLib gagal dipakai (" + t + "). Bot hanya aktif di chat.");
                tab = null;
                ping = null;
            }
        } else {
            getLogger().warning("ProtocolLib tidak ditemukan -> bot tidak tampil di Server List & Tab List. "
                    + "Install ProtocolLib 5.x untuk fitur penuh.");
        }

        bots = new BotManager(this);
        bots.load();
        chat = new ChatEngine(this);
        bots.start();
        chat.scheduleAmbient();

        Bukkit.getPluginManager().registerEvents(this, this);
        getLogger().info("AIBot aktif: " + bots.onlineBots().size() + " bot online dari " + bots.allBots().size());
    }

    @Override
    public void onDisable() {
        if (bots != null) bots.stop();
        if (ping != null) ping.unregister();
        Bukkit.getScheduler().cancelTasks(this);
    }

    public BotManager bots() {
        return bots;
    }

    public TabBridge tab() {
        return tab;
    }

    // ---------------------------------------------------------------- events

    @EventHandler
    public void onJoin(final PlayerJoinEvent e) {
        final Player p = e.getPlayer();
        // tunggu client selesai loading dulu
        Bukkit.getScheduler().runTaskLater(this, new Runnable() {
            @Override
            public void run() {
                if (p.isOnline()) bots.showTo(p);
            }
        }, 20L);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onChat(AsyncPlayerChatEvent e) {
        final String name = e.getPlayer().getName();
        final String msg = e.getMessage();
        Bukkit.getScheduler().runTask(this, new Runnable() {
            @Override
            public void run() {
                chat.onPlayerChat(name, msg);
            }
        });
    }

    // ---------------------------------------------------------------- command

    @Override
    public boolean onCommand(CommandSender s, Command cmd, String label, String[] a) {
        if (a.length == 0) {
            s.sendMessage(ChatColor.YELLOW + "/aibot <list|count|reload|join <nama>|leave <nama>|say <nama> <pesan>>");
            return true;
        }
        String sub = a[0].toLowerCase();
        if (sub.equals("list")) {
            List<String> names = new ArrayList<String>();
            for (Bot b : bots.allBots()) names.add((b.online ? ChatColor.GREEN : ChatColor.GRAY) + b.name);
            s.sendMessage(ChatColor.AQUA + "Bot: " + String.join(ChatColor.WHITE + ", ", names));
        } else if (sub.equals("count")) {
            s.sendMessage(ChatColor.AQUA + "Bot online: " + bots.onlineBots().size() + "/" + bots.allBots().size());
        } else if (sub.equals("reload")) {
            bots.stop();
            Bukkit.getScheduler().cancelTasks(this);
            reloadConfig();
            bots.load();
            for (Player p : Bukkit.getOnlinePlayers()) bots.showTo(p);
            bots.start();
            chat.scheduleAmbient();
            s.sendMessage(ChatColor.GREEN + "AIBot config dimuat ulang.");
        } else if ((sub.equals("join") || sub.equals("leave")) && a.length >= 2) {
            Bot b = bots.get(a[1]);
            if (b == null) {
                s.sendMessage(ChatColor.RED + "Bot tidak ditemukan.");
                return true;
            }
            if (sub.equals("join")) bots.join(b, true);
            else bots.leave(b, true);
        } else if (sub.equals("say") && a.length >= 3) {
            Bot b = bots.get(a[1]);
            if (b == null || !b.online) {
                s.sendMessage(ChatColor.RED + "Bot tidak ditemukan / offline.");
                return true;
            }
            StringBuilder sb = new StringBuilder();
            for (int i = 2; i < a.length; i++) sb.append(i > 2 ? " " : "").append(a[i]);
            chat.say(b, sb.toString());
        } else {
            s.sendMessage(ChatColor.RED + "Perintah tidak dikenal.");
        }
        return true;
    }
}
