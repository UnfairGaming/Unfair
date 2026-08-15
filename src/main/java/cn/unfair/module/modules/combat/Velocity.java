package cn.unfair.module.modules.combat;

import cn.unfair.module.ModuleWithModuleSettings;
import cn.unfair.module.SubModule;
import cn.unfair.module.modules.combat.velocity.*;
import net.minecraft.client.Minecraft;

public class Velocity extends ModuleWithModuleSettings {

    private static final Minecraft mc = Minecraft.getMinecraft();

    public Velocity() {
        super("Velocity", false, "Mode",
                new VanillaVelocity(),
                new HypixelVelocity(),
                new ReduceVelocity(),
                new DelayVelocity(),
                new PolarVelocity(),
                new GrimReduceVelocity(),
                new ClickVelocity()
        );
    }

    public static boolean isInLiquidOrWeb() {
        return mc.thePlayer.isInWater() || mc.thePlayer.isInLava() || mc.thePlayer.getIsInWeb();
    }

    public boolean isDelayingVelocity() {
        SubModule subModule = getCurrentSubModule();
        if (subModule instanceof GrimReduceVelocity grimReduce) {
            return grimReduce.isSuspending();
        }
        if (subModule instanceof HypixelVelocity hypixel) {
            return hypixel.isDelaying();
        }
        return false;
    }

    @Override
    public String[] getSuffix() {
        return new String[]{this.modeProperty.getModeString()};
    }
}
