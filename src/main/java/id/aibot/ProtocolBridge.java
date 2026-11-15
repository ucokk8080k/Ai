package id.aibot;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.wrappers.EnumWrappers.NativeGameMode;
import com.comphenix.protocol.wrappers.EnumWrappers.PlayerInfoAction;
import com.comphenix.protocol.wrappers.PlayerInfoData;
import com.comphenix.protocol.wrappers.WrappedChatComponent;
import com.comphenix.protocol.wrappers.WrappedGameProfile;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

/**
 * Packet tab-list via ProtocolLib.
 * - 1.8  .. 1.19.2 : PLAYER_INFO dengan satu action (ADD_PLAYER / REMOVE_PLAYER)
 * - 1.19.3 .. terbaru: PLAYER_INFO dengan EnumSet action + PLAYER_INFO_REMOVE
 */
public class ProtocolBridge implements TabBridge {

    private final ProtocolManager pm = ProtocolLibrary.getProtocolManager();
    private final boolean modern = detectModern();

    static boolean detectModern() {
        try {
            String v = Bukkit.getBukkitVersion().split("-")[0]; // contoh "1.20.4"
            String[] p = v.split("\\.");
            int minor = Integer.parseInt(p[1]);
            int patch = p.length > 2 ? Integer.parseInt(p[2]) : 0;
            return minor > 19 || (minor == 19 && patch >= 3);
        } catch (Exception e) {
            return true;
        }
    }

    @Override
    public void add(Collection<Bot> bots, Player to) {
        if (bots.isEmpty()) return;
        try {
            List<PlayerInfoData> data = new ArrayList<PlayerInfoData>();
            for (Bot b : bots) data.add(info(b));
            PacketContainer packet = pm.createPacket(PacketType.Play.Server.PLAYER_INFO);
            if (modern) {
                packet.getPlayerInfoActions().write(0, EnumSet.of(
                        PlayerInfoAction.ADD_PLAYER,
                        PlayerInfoAction.UPDATE_LISTED,
                        PlayerInfoAction.UPDATE_LATENCY,
                        PlayerInfoAction.UPDATE_GAME_MODE,
                        PlayerInfoAction.UPDATE_DISPLAY_NAME));
                packet.getPlayerInfoDataLists().write(1, data);
            } else {
                packet.getPlayerInfoAction().write(0, PlayerInfoAction.ADD_PLAYER);
                packet.getPlayerInfoDataLists().write(0, data);
            }
            pm.sendServerPacket(to, packet);
        } catch (Exception e) {
            Bukkit.getLogger().warning("[AIBot] gagal kirim tab-add: " + e.getMessage());
        }
    }

    @Override
    public void remove(Collection<Bot> bots, Player to) {
        if (bots.isEmpty()) return;
        try {
            if (modern) {
                PacketContainer packet = pm.createPacket(PacketType.Play.Server.PLAYER_INFO_REMOVE);
                List<UUID> ids = new ArrayList<UUID>();
                for (Bot b : bots) ids.add(b.uuid);
                packet.getUUIDLists().write(0, ids);
                pm.sendServerPacket(to, packet);
            } else {
                List<PlayerInfoData> data = new ArrayList<PlayerInfoData>();
                for (Bot b : bots) data.add(info(b));
                PacketContainer packet = pm.createPacket(PacketType.Play.Server.PLAYER_INFO);
                packet.getPlayerInfoAction().write(0, PlayerInfoAction.REMOVE_PLAYER);
                packet.getPlayerInfoDataLists().write(0, data);
                pm.sendServerPacket(to, packet);
            }
        } catch (Exception e) {
            Bukkit.getLogger().warning("[AIBot] gagal kirim tab-remove: " + e.getMessage());
        }
    }

    private PlayerInfoData info(Bot b) {
        WrappedGameProfile profile = new WrappedGameProfile(b.uuid, b.name);
        WrappedChatComponent display = WrappedChatComponent.fromText(b.name);
        return new PlayerInfoData(profile, b.latency, NativeGameMode.SURVIVAL, display);
    }
}
