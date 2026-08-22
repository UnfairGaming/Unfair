package cn.unfair.util.anticheat.check;

import cn.unfair.util.anticheat.ACPlayerData;
import cn.unfair.util.anticheat.AntiCheatCheck;
import cn.unfair.util.anticheat.AnticheatManager;
import net.minecraft.network.Packet;
import net.minecraft.potion.Potion;

public class MotionBCheck extends AntiCheatCheck {
    public MotionBCheck() {
        super("Motion B", "Checks invalid acceleration.");
    }

    @Override
    public void onPacket(AnticheatManager manager, ACPlayerData data, Packet<?> packet) {
        if (!isMovementPacket(packet) || data.ticksSinceLastVelocity <= 40 || data.timeSinceLastMovementPacket >= 150L)
            return;
        double acceleration = data.packetSpeed - data.lastPacketSpeed;
        if (acceleration < 10.0D) {
            double multiplier = 1.0D;
            if (data.getPlayer().isPotionActive(Potion.moveSpeed)) {
                multiplier = data.getPlayer().getActivePotionEffect(Potion.moveSpeed).getAmplifier() + 1.0D;
            }
            double limit = 0.32D * (multiplier + 1.0D);
            if (acceleration > limit) {
                if (++data.motionCBuffer > 3.0D) {
                    manager.flag(data, this, "Invalid acceleration", "Motion delta: " + acceleration + ", max: " + limit);
                    data.motionCBuffer = 0.0D;
                }
            } else if (acceleration > 0.0D) {
                data.motionCBuffer = Math.max(0.0D, data.motionCBuffer - 0.8D);
            }
        }
    }
}
