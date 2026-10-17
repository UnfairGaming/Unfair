package cn.unfair.module.modules.combat;

import cn.unfair.module.ModuleWithModuleSettings;
import cn.unfair.module.SubModule;
import cn.unfair.module.modules.combat.velocity.*;

public class Velocity extends ModuleWithModuleSettings {

    public Velocity() {
        super("Velocity", false, "Mode",
                new VanillaVelocity(),
                new HypixelVelocity(),
                new ReduceVelocity(),
                new GrimReduceVelocity(),
                new DelayVelocity(),
                new PolarVelocity(),
                new B4Mode()
        );
    }

    public boolean isDelayingVelocity() {
        SubModule subModule = getCurrentSubModule();
        if (subModule instanceof HypixelVelocity hypixel) {
            return hypixel.isDelaying();
        }
        if (subModule instanceof B4Mode b4) {
            return b4.isDelaying();
        }
        return false;
    }

    @Override
    public String[] getSuffix() {
        return new String[]{this.modeProperty.getModeString()};
    }
}
