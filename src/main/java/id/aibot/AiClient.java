package id.aibot;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.bukkit.configuration.file.FileConfiguration;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/** Klien HTTP sederhana (Java 8) untuk Anthropic & OpenAI-compatible. Panggil dari thread async. */
public class AiClient {

    private final AIBotPlugin plugin;

    public AiClient(AIBotPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean enabled() {
        FileConfiguration c = plugin.getConfig();
        return c.getBoolean("ai.enabled", false) && !apiKey().isEmpty();
    }

    private String apiKey() {
        String k = plugin.getConfig().getString("ai.api-key", "");
        if (k == null || k.trim().isEmpty()) {
            String env = System.getenv("AIBOT_API_KEY");
            k = env == null ? "" : env;
        }
        return k.trim();
    }

    /** @return teks balasan, sudah dibersihkan */
    public String ask(String system, String userContent) throws IOException {
        FileConfiguration c = plugin.getConfig();
        String provider = c.getString("ai.provider", "anthropic").toLowerCase();
        String model = c.getString("ai.model", "claude-haiku-5-5");
        int maxTokens = c.getInt("ai.max-tokens", 80);
        boolean anthropic = provider.equals("anthropic");

        String endpoint = c.getString("ai.endpoint", "");
        if (endpoint == null || endpoint.trim().isEmpty()) {
            endpoint = anthropic ? "https://api.anthropic.com/v1/messages"
                    : "https://api.openai.com/v1/chat/completions";
        }

        JsonObject body = new JsonObject();
        body.addProperty("model", model);
        body.addProperty("max_tokens", maxTokens);
        JsonArray messages = new JsonArray();
        if (anthropic) {
            body.addProperty("system", system);
        } else {
            JsonObject sys = new JsonObject();
            sys.addProperty("role", "system");
            sys.addProperty("content", system);
            messages.add(sys);
        }
        JsonObject user = new JsonObject();
        user.addProperty("role", "user");
        user.addProperty("content", userContent);
        messages.add(user);
        body.add("messages", messages);

        HttpURLConnection con = (HttpURLConnection) new URL(endpoint).openConnection();
        int timeout = c.getInt("ai.timeout-seconds", 15) * 1000;
        con.setConnectTimeout(timeout);
        con.setReadTimeout(timeout);
        con.setRequestMethod("POST");
        con.setDoOutput(true);
        con.setRequestProperty("Content-Type", "application/json");
        if (anthropic) {
            con.setRequestProperty("x-api-key", apiKey());
            con.setRequestProperty("anthropic-version", "2023-06-01");
        } else {
            con.setRequestProperty("Authorization", "Bearer " + apiKey());
        }
        OutputStream os = con.getOutputStream();
        os.write(body.toString().getBytes(StandardCharsets.UTF_8));
        os.close();

        int code = con.getResponseCode();
        InputStream in = code >= 400 ? con.getErrorStream() : con.getInputStream();
        String resp = read(in);
        if (code >= 400) throw new IOException("HTTP " + code + ": " + resp);

        JsonObject json = new JsonParser().parse(resp).getAsJsonObject();
        String text;
        if (anthropic) {
            JsonElement first = json.getAsJsonArray("content").get(0);
            text = first.getAsJsonObject().get("text").getAsString();
        } else {
            text = json.getAsJsonArray("choices").get(0).getAsJsonObject()
                    .getAsJsonObject("message").get("content").getAsString();
        }
        return text;
    }

    private static String read(InputStream in) throws IOException {
        if (in == null) return "";
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int n;
        while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        in.close();
        return new String(out.toByteArray(), StandardCharsets.UTF_8);
    }
}
