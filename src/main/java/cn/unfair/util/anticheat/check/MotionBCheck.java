package cn.unfair.util.anticheat.check;

import cn.unfair.util.anticheat.*;
import net.minecraft.network.Packet;

public class MotionBCheck extends AntiCheatCheck {
    public MotionBCheck() { super("Motion B", "Checks invalid gravity."); }

    @Override
    public void onPacket(AnticheatManager manager, ACPlayerData data, Packet<?> packet) {
        if (!isMovementPacket(packet) || data.ticksSinceLastVelocity <= 40 || data.timeSinceLastMovementPacket >= 150L) return;
        if (!data.packetOnGround && data.deltaY < 0.0D && data.lastDeltaY < 0.0D) {
            double expected = (data.lastDeltaY - 0.08D) * 0.98D;
            if (Math.abs(data.deltaY - expected) > 0.1D) {
                if (++data.motionBBuffer > 5.0D) {
                    manager.flag(data, this, "Invalid gravity", "Expected " + expected + ", Received " + data.deltaY);
                    data.motionBBuffer = 0.0D;
                }
            } else {
                data.motionBBuffer = Math.max(0.0D, data.motionBBuffer - 0.8D);
            }
        }
    }
}
