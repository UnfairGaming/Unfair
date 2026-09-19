package cn.unfair.module.modules.render;

import cn.unfair.module.Module;
import cn.unfair.property.properties.BooleanProperty;

public class Interface extends Module {
    public final BooleanProperty customButton = new BooleanProperty("CustomButton", false);
    public final BooleanProperty customFont = new BooleanProperty("CustomFont", true, () -> customButton.getValue());

    public Interface() {
        super("Interface", false, true);
    }
}