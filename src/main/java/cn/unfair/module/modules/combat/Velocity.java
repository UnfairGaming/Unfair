package cn.unfair.module.modules.combat;

import cn.unfair.module.ModuleWithModuleSettings;
import cn.unfair.module.modules.combat.velocity.*;
import net.minecraft.client.Minecraft;

public class Velocity extends ModuleWithModuleSettings {

    private static final Minecraft mc = Minecraft.getMinecraft();

    public Velocity() {
        super("Velocity", false, "mode",
                new VanillaVelocity(),
                new PredictionVelocity(),
                new ReduceVelocity(),
                new DelayVelocity(),
                new PolarVelocity(),
                new GrimReduceVelocity()
        );
    }

    public static boolean isInLiquidOrWeb() {
        return mc.thePlayer.isInWater() || mc.thePlayer.isInLava() || mc.thePlayer.getIsInWeb();
    }

    @Override
    public String[] getSuffix() {
        return new String[]{this.modeProperty.getModeString()};
    }
}
