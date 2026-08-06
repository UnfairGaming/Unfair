package cn.unfair.util.anticheat.check;

import cn.unfair.util.anticheat.*;
import net.minecraft.network.Packet;
import net.minecraft.network.play.server.S0BPacketAnimation;

import java.util.Collection;

public class AutoClickerACheck extends AntiCheatCheck {
    public AutoClickerACheck() { super("AutoClicker A", "Checks attack interval regularity."); }

    @Override
    public void onPacket(AnticheatManager manager, ACPlayerData data, Packet<?> packet) {
        if (!(packet instanceof S0BPacketAnimation animation)
                || animation.getAnimationType() != 0
                || animation.getEntityID() != data.getPlayer().getEntityId()) return;
        long now = System.currentTimeMillis();
        if (data.lastSwingPacketTime > 0L) data.clickIntervals.add(now - data.lastSwingPacketTime);
        data.lastSwingPacketTime = now;
        if (data.clickIntervals.size() >= 50) {
            data.clickDeviationSamples.add((long) standardDeviation(data.clickIntervals));
            data.clickIntervals.clear();
        }
        if (data.clickDeviationSamples.size() >= 3) {
            double std = standardDeviation(data.clickDeviationSamples);
            long length = data.autoClickAStarted == 0L ? Long.MAX_VALUE : now - data.autoClickAStarted;
            if (std < 25.0D && length < 4000L) {
                data.autoClickABuffer += std < 10.0D ? 2.0D : 1.0D;
                if (data.autoClickABuffer > 2.0D) {
                    manager.flag(data, this, "Too low standard deviation", String.valueOf(std));
                }
            } else {
                data.autoClickABuffer = Math.max(0.0D, data.autoClickABuffer - 0.1D) * 0.9D;
            }
            data.autoClickAStarted = now;
            data.clickDeviationSamples.clear();
        }
    }

    private double standardDeviation(Collection<? extends Number> values) {
        double sum = 0.0D;
        for (Number value : values) sum += value.doubleValue();
        double mean = sum / values.size();
        double squared = 0.0D;
        for (Number value : values) squared += Math.pow(value.doubleValue() - mean, 2.0D);
        return Math.sqrt(squared / values.size());
    }
}
