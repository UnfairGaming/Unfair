package cn.unfair.module.modules.combat;

import cn.unfair.module.ModuleWithModuleSettings;
import cn.unfair.module.SubModule;
import cn.unfair.module.modules.combat.velocity.*;
import cn.unfair.util.player.PlayerUtil;

public class Velocity extends ModuleWithModuleSettings {

    public Velocity() {
        super("Velocity", false, "Mode",
                new VanillaVelocity(),
                new HypixelVelocity(),
                new ReduceVelocity(),
                new GrimReduceVelocity(),
                new DelayVelocity(),
                new PolarVelocity(),
                new LegitVelocity()
        );
    }

    public static boolean isInLiquidOrWeb() {
        return PlayerUtil.isInLiquidOrWeb();
    }

    public boolean isDelayingVelocity() {
        SubModule subModule = getCurrentSubModule();
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
