package com.rxac.player;

import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Thread-safe registry of {@link PlayerData}; touched from netty + main thread. */
public final class PlayerDataManager {

    private final Map<UUID, PlayerData> data = new ConcurrentHashMap<>();

    public PlayerData getOrCreate(Player player) {
        return data.computeIfAbsent(player.getUniqueId(), id -> new PlayerData(player));
    }

    public PlayerData get(UUID uuid) {
        return data.get(uuid);
    }

    public PlayerData get(Player player) {
        return data.get(player.getUniqueId());
    }

    public void remove(UUID uuid) {
        data.remove(uuid);
    }

    public void clear() {
        data.clear();
    }

    public Iterable<PlayerData> all() {
        return data.values();
    }
}
