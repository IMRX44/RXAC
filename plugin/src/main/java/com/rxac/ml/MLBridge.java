package com.rxac.ml;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.rxac.RXAC;
import com.rxac.check.Check;
import com.rxac.player.PlayerData;
import org.bukkit.scheduler.BukkitTask;

import java.net.URI;
import java.util.UUID;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Asynchronous bridge to the Python ML service. It batches feature/violation
 * events off the main thread and ships them over HTTP. It is strictly
 * best-effort and fail-open: if the service is down, gameplay is unaffected.
 */
public final class MLBridge {

    private final RXAC plugin;
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(2))
            .build();

    private final ConcurrentLinkedQueue<JsonObject> queue = new ConcurrentLinkedQueue<>();
    private BukkitTask flushTask;
    private BukkitTask actionTask;
    private volatile boolean unreachableLogged;

    public MLBridge(RXAC plugin) {
        this.plugin = plugin;
    }

    private boolean enabled() {
        return plugin.getConfig().getBoolean("ml.enabled", true);
    }

    public void start() {
        if (!enabled()) return;
        long interval = plugin.getConfig().getLong("ml.flush-interval-ticks", 100);
        // Also stream live feature snapshots for every online player each flush.
        this.flushTask = plugin.getServer().getScheduler().runTaskTimerAsynchronously(
                plugin, this::flush, interval, interval);

        // Poll the panel's moderation queue and execute ban/kick/clear actions.
        long poll = plugin.getConfig().getLong("ml.action-poll-ticks", 40);
        this.actionTask = plugin.getServer().getScheduler().runTaskTimerAsynchronously(
                plugin, this::pollActions, poll, poll);
    }

    public void shutdown() {
        if (flushTask != null) flushTask.cancel();
        if (actionTask != null) actionTask.cancel();
        flush();
    }

    /** Enqueue a violation event for the ML service. */
    public void reportViolation(PlayerData data, Check check, double vl, String debug) {
        if (!enabled()) return;
        JsonObject ev = baseEvent(data);
        ev.addProperty("type", "violation");
        ev.addProperty("check", check.getName());
        ev.addProperty("category", check.getCategory().name());
        ev.addProperty("vl", vl);
        ev.addProperty("debug", debug == null ? "" : debug);
        queue.add(ev);
    }

    private JsonObject baseEvent(PlayerData data) {
        JsonObject o = new JsonObject();
        o.addProperty("uuid", data.getUuid().toString());
        o.addProperty("name", data.getPlayer().getName());
        o.addProperty("protocol", data.getProtocolVersion());
        o.addProperty("ts", System.currentTimeMillis());
        // Behavioral features the model trains on.
        JsonObject f = new JsonObject();
        f.addProperty("hSpeed", data.horizontalSpeed());
        f.addProperty("dY", data.deltaY);
        f.addProperty("yawDelta", data.deltaYaw);
        f.addProperty("pitchDelta", data.deltaPitch);
        f.addProperty("airTicks", data.airTicks);
        f.addProperty("cps", data.cps());
        f.addProperty("totalVl", data.getViolations().totalVl());
        o.add("features", f);
        return o;
    }

    private void flush() {
        if (!enabled() || queue.isEmpty()) return;

        JsonArray batch = new JsonArray();
        JsonObject ev;
        int n = 0;
        while ((ev = queue.poll()) != null && n < 500) {
            batch.add(ev);
            n++;
        }
        if (batch.size() == 0) return;

        JsonObject body = new JsonObject();
        body.add("events", batch);

        String base = plugin.getConfig().getString("ml.endpoint", "http://127.0.0.1:8000");
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(base + "/api/ingest"))
                .timeout(Duration.ofSeconds(3))
                .header("Content-Type", "application/json")
                .header("X-RXAC-Key", plugin.getConfig().getString("ml.api-key", ""))
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                .build();

        http.sendAsync(req, HttpResponse.BodyHandlers.ofString())
                .thenAccept(resp -> {
                    unreachableLogged = false;
                    if (resp.statusCode() == 200) applyVerdicts(resp.body());
                })
                .exceptionally(t -> {
                    if (!unreachableLogged) {
                        boolean failOpen = plugin.getConfig().getBoolean("ml.fail-open", true);
                        plugin.getLogger().warning("ML service unreachable ("
                                + t.getMessage() + "); fail-open=" + failOpen);
                        unreachableLogged = true;
                    }
                    return null;
                });
    }

    /** Pull queued moderation actions from the panel and execute them. */
    private void pollActions() {
        if (!enabled()) return;
        String base = plugin.getConfig().getString("ml.endpoint", "http://127.0.0.1:8000");
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(base + "/api/actions/pending"))
                .timeout(Duration.ofSeconds(3))
                .header("X-RXAC-Key", plugin.getConfig().getString("ml.api-key", ""))
                .GET()
                .build();

        http.sendAsync(req, HttpResponse.BodyHandlers.ofString())
                .thenAccept(resp -> {
                    if (resp.statusCode() == 200) executeActions(resp.body());
                })
                .exceptionally(t -> null);
    }

    private void executeActions(String body) {
        try {
            JsonElement root = JsonParser.parseString(body);
            JsonElement arr = root.getAsJsonObject().get("actions");
            if (arr == null || !arr.isJsonArray()) return;

            for (JsonElement el : arr.getAsJsonArray()) {
                JsonObject a = el.getAsJsonObject();
                final String type = a.has("type") ? a.get("type").getAsString() : "";
                final String reason = a.has("reason") ? a.get("reason").getAsString() : "RXAC";
                final String by = a.has("by") ? a.get("by").getAsString() : "panel";
                final UUID uuid = a.has("uuid") ? UUID.fromString(a.get("uuid").getAsString()) : null;
                final String name = a.has("name") && !a.get("name").isJsonNull()
                        ? a.get("name").getAsString() : null;
                if (uuid == null && name == null) continue;

                plugin.getServer().getScheduler().runTask(plugin,
                        () -> applyAction(type, uuid, name, reason, by));
            }
        } catch (Exception ignored) {
        }
    }

    private void applyAction(String type, UUID uuid, String name, String reason, String by) {
        org.bukkit.entity.Player player = uuid != null
                ? plugin.getServer().getPlayer(uuid)
                : plugin.getServer().getPlayerExact(name);
        String pname = player != null ? player.getName() : name;
        plugin.getLogger().info("[panel] " + by + " -> " + type + " " + pname + " (" + reason + ")");

        switch (type.toLowerCase()) {
            case "ban" -> {
                String cmd = plugin.getConfig().getString("punishments.ban.command",
                                "ban %player% %reason%")
                        .replace("%player%", pname == null ? "" : pname)
                        .replace("%reason%", reason)
                        .replace("%check%", "panel").replace("%vl%", "0");
                if (pname != null) plugin.getServer().dispatchCommand(
                        plugin.getServer().getConsoleSender(), cmd);
            }
            case "kick" -> {
                if (player != null) player.kickPlayer(reason);
            }
            case "clearvl" -> {
                if (player != null) {
                    PlayerData d = plugin.getPlayerDataManager().get(player);
                    if (d != null) d.getViolations().clearAll();
                }
            }
            default -> { /* unknown action */ }
        }
    }

    /**
     * Parse the service's per-player anomaly verdicts and raise the AI check's
     * violation level for anyone scoring above the configured threshold. Runs
     * the actual VL mutation on the main thread.
     */
    private void applyVerdicts(String body) {
        double threshold = plugin.getConfig().getDouble("ml.anomaly-threshold", 0.85);
        double weight = plugin.getConfig().getDouble("ml.vl-weight", 6.0);
        try {
            JsonElement root = JsonParser.parseString(body);
            if (!root.isJsonObject()) return;
            JsonElement verdicts = root.getAsJsonObject().get("verdicts");
            if (verdicts == null || !verdicts.isJsonArray()) return;

            for (JsonElement el : verdicts.getAsJsonArray()) {
                JsonObject v = el.getAsJsonObject();
                double anomaly = v.has("anomaly") ? v.get("anomaly").getAsDouble() : 0;
                if (anomaly < threshold) continue;
                final UUID uuid = UUID.fromString(v.get("uuid").getAsString());
                final double amount = (anomaly - threshold) / Math.max(1e-6, 1 - threshold) * weight;

                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    PlayerData data = plugin.getPlayerDataManager().get(uuid);
                    Check ai = plugin.getCheckManager().getByName("AI");
                    if (data != null && ai != null && ai.isEnabled()) {
                        data.getViolations().fail(ai, amount,
                                String.format("anomaly=%.2f", anomaly));
                    }
                });
            }
        } catch (Exception ignored) {
            // Malformed/empty response: ignore (fail-open).
        }
    }
}
