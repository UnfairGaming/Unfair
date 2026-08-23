package cn.unfair.util.anticheat.impl;

import cn.unfair.util.anticheat.ACPlayerData;
import cn.unfair.util.anticheat.AntiCheatCheck;
import cn.unfair.util.anticheat.AntiCheatManager;
import net.minecraft.network.Packet;

public class MotionACheck extends AntiCheatCheck {
    public MotionACheck() {
        super("Motion A", "Checks excessive horizontal motion.");
    }

    @Override
    public void onPacket(AntiCheatManager manager, ACPlayerData data, Packet<?> packet) {
        if (!isMovementPacket(packet) || data.ticksSinceLastVelocity <= 40 || data.timeSinceLastMovementPacket >= 150L)
            return;
        if (data.packetSpeed > 0.8D) {
            data.motionABuffer += data.packetSpeed - 0.8D;
            if (data.motionABuffer > 1.0D) {
                manager.flag(data, this, "Moving too fast", "Motion: " + data.packetSpeed);
                data.motionABuffer = 0.0D;
            }
        } else {
            data.motionABuffer *= 0.9D;
        }
    }
}
