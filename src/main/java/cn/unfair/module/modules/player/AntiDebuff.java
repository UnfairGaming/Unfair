package cn.unfair.module.modules.player;

import cn.unfair.module.Module;
import cn.unfair.property.properties.BooleanProperty;

public class AntiDebuff extends Module {
    public final BooleanProperty blindness = new BooleanProperty("Blindness", true);
    public final BooleanProperty nausea = new BooleanProperty("Nausea", true);

    public AntiDebuff() {
        super("AntiDebuff", false, true);
    }
}
