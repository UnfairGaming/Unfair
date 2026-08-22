package cn.unfair.util.anticheat.impl;

import cn.unfair.util.anticheat.ACPlayerData;
import cn.unfair.util.anticheat.AntiCheatCheck;
import cn.unfair.util.anticheat.AnticheatManager;
import net.minecraft.network.Packet;

public class NoSlowACheck extends AntiCheatCheck {
    public NoSlowACheck() {
        super("NoSlow A", "Checks sprinting while using an item.");
    }

    @Override
    public void onPacket(AnticheatManager manager, ACPlayerData data, Packet<?> packet) {
        if (!isMovementPacket(packet)) return;
        if (data.getPlayer().isUsingItem() && data.getPlayer().isSprinting()) {
            if (++data.noSlowABuffer > 5.0D) {
                manager.flag(data, this, "Sprinting while using an item", 1.0D);
                data.noSlowABuffer = 0.0D;
            }
        } else {
            data.noSlowABuffer = Math.max(0.0D, data.noSlowABuffer - 1.0D);
        }
    }
}
