package cn.unfair.module.modules.player;

import cn.unfair.events.UpdateEvent;
import cn.unfair.module.Module;
import cn.unfair.property.properties.IntProperty;
import net.minecraft.util.MathHelper;

public class BetterRotation extends Module {
    private static BetterRotation instance;

    private static boolean bypassNextTick;

    public final IntProperty speed = new IntProperty("Speed", 30, 1, 180);

    public BetterRotation() {
        super("BetterRotation", false);
        instance = this;
    }

    public static void bypassOnce() {
        bypassNextTick = true;
    }

    public static void smooth(UpdateEvent event) {
        boolean bypass = bypassNextTick;
        bypassNextTick = false;
        if (bypass || instance == null || !instance.isEnabled() || !event.isRotated()) {
            return;
        }

        float max = instance.speed.getValue();
        if (max >= 180.0F) {
            return;
        }

        float fromYaw = event.getYaw();
        float fromPitch = event.getPitch();
        float targetYaw = event.getNewYaw();
        float targetPitch = event.getNewPitch();

        float deltaYaw = MathHelper.wrapAngleTo180_float(targetYaw - fromYaw);
        float deltaPitch = targetPitch - fromPitch;

        float appliedYaw = MathHelper.clamp_float(deltaYaw, -max, max);
        float appliedPitch = MathHelper.clamp_float(deltaPitch, -max, max);

        float newYaw = fromYaw + appliedYaw;
        float newPitch = MathHelper.clamp_float(fromPitch + appliedPitch, -90.0F, 90.0F);

        event.overwriteRotation(newYaw, newPitch);
    }

    @Override
    public String[] getSuffix() {
        return new String[]{String.format("%d/t", this.speed.getValue())};
    }
}
