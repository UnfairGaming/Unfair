package cn.unfair.module.modules.combat;

import cn.unfair.module.ModuleWithModuleSettings;
import cn.unfair.module.modules.combat.velocity.Prediction;
import cn.unfair.module.modules.combat.velocity.Vanilla;

public class Velocity extends ModuleWithModuleSettings {
    public Velocity() {
        super("Velocity", false, "mode", new Vanilla(), new Prediction());
    }

    @Override
    public String[] getSuffix() {
        return new String[]{this.modeProperty.getModeString()};
    }
}
