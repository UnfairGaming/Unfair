package cn.unfair.module.modules.player;

import cn.unfair.module.Module;
import cn.unfair.property.properties.BooleanProperty;

public class AntiDebuff extends Module {
    public final BooleanProperty blindness = new BooleanProperty("blindness", true);
    public final BooleanProperty nausea = new BooleanProperty("nausea", true);

    public AntiDebuff() {
        super("AntiDebuff", false);
    }
}
