package cn.unfair.util.anticheat.check;

import cn.unfair.util.anticheat.*;
import net.minecraft.network.Packet;

public class NoSlowBCheck extends AntiCheatCheck {
    public NoSlowBCheck() { super("NoSlow B", "Checks movement speed after item use."); }

    @Override
    public void onPacket(AnticheatManager manager, ACPlayerData data, Packet<?> packet) {
        if (!isMovementPacket(packet)) return;
        boolean using = data.getPlayer().getItemInUseDuration() > 5;
        if (!using && data.lastUsingItemForPacket && data.getPlayer().hurtTime == 0 && data.packetSpeed > 0.2D) {
            data.noSlowBBuffer += 2.5D;
            if (data.noSlowBBuffer > 5.0D) {
                manager.flag(data, this, "Moving too fast on item release", "Max: 0.2 Current: " + data.packetSpeed);
                data.noSlowBBuffer = 0.0D;
            }
        } else if (data.packetSpeed <= 0.2D) {
            data.noSlowBBuffer = Math.max(0.0D, data.noSlowBBuffer - 0.25D);
        }
        data.lastUsingItemForPacket = using;
    }
}
