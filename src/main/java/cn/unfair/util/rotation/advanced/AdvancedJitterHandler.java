package cn.unfair.util.rotation.advanced;

import cn.unfair.event.EventTarget;
import cn.unfair.event.types.EventType;
import cn.unfair.events.PacketEvent;
import cn.unfair.util.client.RandomUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.network.play.client.C0APacketAnimation;

import java.util.ArrayList;

public class AdvancedJitterHandler {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private static final ArrayList<Long> clicks = new ArrayList<>();
    private static int lastTicksExisted;
    private static float tremorYaw;
    private static float tremorPitch;
    private static float targetYaw;
    private static float targetPitch;
    private static float clickImpulseYaw;
    private static float clickImpulsePitch;
    private static float jitterYaw;
    private static float jitterPitch;

    public static float[] calculateJitter(float strength, boolean interpolate) {
        long time = System.currentTimeMillis();
        clicks.removeIf(click -> click + 1000L < time);

        if (clicks.isEmpty()) {
            return new float[]{jitterYaw, jitterPitch};
        }

        boolean lastTickClicked = false;
        for (Long clickTime : clicks) {
            if (clickTime + (long) 16.67 > time) {
                lastTickClicked = true;
                break;
            }
        }

        if (lastTickClicked) {
            clickImpulseYaw += RandomUtil.nextFloat(-strength, strength);
            clickImpulsePitch += RandomUtil.nextFloat(-strength, strength);
        }

        float cpsFactor = Math.min(clicks.size() / 12.0F, 1.5F);
        float yawJitter = tremorYaw * 0.6F * cpsFactor + clickImpulseYaw;
        float pitchJitter = tremorPitch * 0.6F * cpsFactor + clickImpulsePitch;

        if (interpolate) {
            jitterYaw = AdvancedRotationMath.interpolate(jitterYaw, yawJitter, 0.25F);
            jitterPitch = AdvancedRotationMath.interpolate(jitterPitch, pitchJitter, 0.25F);
        } else {
            jitterYaw = yawJitter;
            jitterPitch = pitchJitter;
        }

        if (mc.thePlayer.ticksExisted != lastTicksExisted) {
            if (Math.random() < 0.08) {
                targetYaw = RandomUtil.nextFloat(-0.15F, 0.15F);
                targetPitch = RandomUtil.nextFloat(-0.15F, 0.15F);
            }

            tremorYaw += (targetYaw - tremorYaw) * 0.12F;
            tremorPitch += (targetPitch - tremorPitch) * 0.12F;
            clickImpulseYaw *= 0.75F;
            clickImpulsePitch *= 0.75F;
            lastTicksExisted = mc.thePlayer.ticksExisted;
        }

        return new float[]{jitterYaw, jitterPitch};
    }

    @EventTarget
    public void onPacket(PacketEvent event) {
        if (event.getType() == EventType.SEND && event.getPacket() instanceof C0APacketAnimation) {
            clicks.add(System.currentTimeMillis());
        }
    }
}
