package id.aibot;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.wrappers.WrappedGameProfile;
import com.comphenix.protocol.wrappers.WrappedServerPing;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Menambah bot ke jumlah pemain + tooltip di menu Multiplayer. */
public class PingHook extends PacketAdapter {

    private final AIBotPlugin plugin;

    public PingHook(AIBotPlugin plugin) {
        super(plugin, ListenerPriority.HIGH, PacketType.Status.Server.SERVER_INFO);
        this.plugin = plugin;
    }

    public void register() {
        ProtocolLibrary.getProtocolManager().addPacketListener(this);
    }

    public void unregister() {
        ProtocolLibrary.getProtocolManager().removePacketListener(this);
    }

    @Override
    public void onPacketSending(PacketEvent event) {
        try {
            if (!plugin.getConfig().getBoolean("server-list.enabled", true)) return;
            WrappedServerPing ping = event.getPacket().getServerPings().read(0);

            List<String> names = new ArrayList<String>();
            int real = 0;
            for (Player p : Bukkit.getOnlinePlayers()) {
                names.add(p.getName());
                real++;
            }
            List<Bot> bots = plugin.bots().onlineBots();
            for (Bot b : bots) names.add(b.name);

            int total = real + bots.size();
            int max = plugin.getConfig().getInt("server-list.max-players", 0);
            if (max <= 0) max = Math.max(ping.getPlayersMaximum(), total);
            ping.setPlayersOnline(total);
            ping.setPlayersMaximum(Math.max(max, total));

            if (plugin.getConfig().getBoolean("server-list.show-sample", true)) {
                Collections.shuffle(names);
                List<WrappedGameProfile> sample = new ArrayList<WrappedGameProfile>();
                for (int i = 0; i < Math.min(12, names.size()); i++) {
                    String n = names.get(i);
                    sample.add(new WrappedGameProfile(
                            java.util.UUID.nameUUIDFromBytes(("OfflinePlayer:" + n).getBytes(java.nio.charset.StandardCharsets.UTF_8)), n));
                }
                ping.setPlayers(sample);
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Ping hook error: " + e.getMessage());
        }
    }
}
