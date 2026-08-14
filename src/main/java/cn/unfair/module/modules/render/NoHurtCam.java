package cn.unfair.module.modules.render;

import cn.unfair.module.Module;
import cn.unfair.property.properties.PercentProperty;

public class NoHurtCam extends Module {
    public final PercentProperty multiplier = new PercentProperty("Multiplier", 0);

    public NoHurtCam() {
        super("NoHurtCam", false, true);
    }
}
