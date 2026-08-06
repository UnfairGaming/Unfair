package cn.unfair.util.anticheat.check;

import cn.unfair.util.anticheat.*;
import net.minecraft.network.Packet;
import net.minecraft.network.play.server.S0BPacketAnimation;

public class AutoClickerBCheck extends AntiCheatCheck {
    public AutoClickerBCheck() { super("AutoClicker B", "Checks excessive attack frequency."); }

    @Override
    public void onPacket(AnticheatManager manager, ACPlayerData data, Packet<?> packet) {
        if (!(packet instanceof S0BPacketAnimation animation)
                || animation.getAnimationType() != 0
                || animation.getEntityID() != data.getPlayer().getEntityId()) return;
        long now = System.currentTimeMillis();
        if (data.autoClickBCount == 0) data.autoClickBStarted = now;
        data.autoClickBCount++;
        if (now - data.autoClickBStarted >= 1000L) {
            if (data.autoClickBCount > 24) manager.flag(data, this, "Clicking too fast", data.autoClickBCount + "cps");
            data.autoClickBStarted = now;
            data.autoClickBCount = 0;
        }
    }
}
