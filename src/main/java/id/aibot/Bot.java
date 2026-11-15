package id.aibot;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/** Satu pemain palsu. */
public class Bot {
    public final String name;
    public final UUID uuid;
    public final int latency;
    public volatile boolean online;

    public Bot(String name, int latency) {
        this.name = name;
        // UUID offline-mode (sama seperti pemain cracked) -> skin default Steve/Alex
        this.uuid = UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes(StandardCharsets.UTF_8));
        this.latency = latency;
    }
}
