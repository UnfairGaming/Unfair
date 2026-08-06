package cn.unfair.util.anticheat.check;

import cn.unfair.util.anticheat.*;
import net.minecraft.network.Packet;
import net.minecraft.network.play.server.S0BPacketAnimation;

public class InvalidSwingCheck extends AntiCheatCheck {
    public InvalidSwingCheck() { super("Invalid swing", "Checks swinging while using an item."); }

    @Override
    public void onPacket(AnticheatManager manager, ACPlayerData data, Packet<?> packet) {
        if (!(packet instanceof S0BPacketAnimation animation)
                || animation.getAnimationType() != 0
                || data.getPlayer().getEntityId() != animation.getEntityID()) return;
        if (data.getPlayer().getItemInUseDuration() > 2) {
            if (++data.invalidSwingBuffer > 2.0D) {
                manager.flag(data, this, "Swinging on item use", 1.0D);
                data.invalidSwingBuffer = 0.0D;
            }
        } else {
            data.invalidSwingBuffer = Math.max(0.0D, data.invalidSwingBuffer - 0.2D);
        }
    }
}
