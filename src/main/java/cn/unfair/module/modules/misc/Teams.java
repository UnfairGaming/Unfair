package cn.unfair.module.modules.misc;

import cn.unfair.module.Module;
import cn.unfair.property.properties.BooleanProperty;

public class Teams extends Module {
    public final BooleanProperty render = new BooleanProperty("Render", true);

    public Teams() {
        super("Teams", false, true);
    }
}
