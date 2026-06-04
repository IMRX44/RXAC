package com.rxac.listener;

import org.bukkit.entity.Player;

/**
 * Resolves a player's client protocol version via ViaVersion when present,
 * so checks can adapt thresholds for legacy (1.8) vs modern clients. Falls back
 * to -1 (server-native) if ViaVersion is not installed.
 */
public final class ProtocolResolver {

    private static Boolean viaPresent;

    private ProtocolResolver() {}

    public static int resolve(Player player) {
        if (viaPresent == null) {
            viaPresent = classExists("com.viaversion.viaversion.api.Via");
        }
        if (!viaPresent) return -1;
        try {
            return com.viaversion.viaversion.api.Via.getAPI()
                    .getPlayerVersion(player.getUniqueId());
        } catch (Throwable t) {
            return -1;
        }
    }

    private static boolean classExists(String name) {
        try {
            Class.forName(name);
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }
}
