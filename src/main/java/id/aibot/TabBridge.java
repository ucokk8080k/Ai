package id.aibot;

import org.bukkit.entity.Player;

import java.util.Collection;

/** Abstraksi pengirim packet tab-list (diimplementasi via ProtocolLib). */
public interface TabBridge {
    void add(Collection<Bot> bots, Player to);

    void remove(Collection<Bot> bots, Player to);
}
