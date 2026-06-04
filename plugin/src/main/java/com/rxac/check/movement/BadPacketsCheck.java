package com.rxac.check.movement;

import com.rxac.RXAC;
import com.rxac.check.Check;
import com.rxac.check.CheckCategory;
import com.rxac.player.PlayerData;

/**
 * BadPackets: rotation values a vanilla client can never send. Pitch is clamped
 * to [-90, 90] and both angles are always finite. Anything else is a malformed
 * or spoofed packet — frequently a sign of a poorly written cheat or an exploit
 * attempt — and is flagged hard immediately.
 */
public final class BadPacketsCheck extends Check {

    public BadPacketsCheck(RXAC plugin) {
        super(plugin, "BadPackets", CheckCategory.MOVEMENT);
    }

    @Override
    public void onMovement(PlayerData data) {
        float yaw = data.yaw;
        float pitch = data.pitch;

        if (!Float.isFinite(yaw) || !Float.isFinite(pitch)) {
            fail(data, 4.0, "non-finite rotation");
            return;
        }
        if (pitch < -90.0f || pitch > 90.0f) {
            fail(data, 4.0, String.format("pitch=%.1f out of [-90,90]", pitch));
        }
    }
}
