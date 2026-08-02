package cn.unfair.module.modules.misc;

import cn.unfair.module.Module;
import cn.unfair.property.properties.BooleanProperty;

public class AntiBot extends Module {
    public final BooleanProperty render = new BooleanProperty("render", true);

    public AntiBot() {
        super("AntiBot", false, true);
    }
}
