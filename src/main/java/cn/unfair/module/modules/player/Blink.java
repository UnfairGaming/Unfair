package cn.unfair.module.modules.player;

import cn.unfair.Unfair;
import cn.unfair.enums.BlinkModules;
import cn.unfair.event.EventTarget;
import cn.unfair.event.types.EventType;
import cn.unfair.event.types.Priority;
import cn.unfair.events.LoadWorldEvent;
import cn.unfair.events.TickEvent;
import cn.unfair.module.Module;
import cn.unfair.property.properties.IntProperty;
import cn.unfair.property.properties.ModeProperty;

public class Blink extends Module {
    public final ModeProperty mode = new ModeProperty("Mode", 0, new String[]{"Default", "Pulse"});
    public final IntProperty ticks = new IntProperty("Ticks", 20, 0, 1200);

    public Blink() {
        super("Blink", false);
    }

    @EventTarget(Priority.LOWEST)
    public void onTick(TickEvent event) {
        if (this.isEnabled() && event.type() == EventType.POST) {
            if (!Unfair.blinkManager.getBlinkingModule().equals(BlinkModules.BLINK)) {
                this.setEnabled(false);
            } else {
                if (this.ticks.getValue() > 0 && Unfair.blinkManager.countMovement() > (long) this.ticks.getValue()) {
                    switch (this.mode.getValue()) {
                        case 0:
                            this.setEnabled(false);
                            break;
                        case 1:
                            Unfair.blinkManager.setBlinkState(false, BlinkModules.BLINK);
                            Unfair.blinkManager.setBlinkState(true, BlinkModules.BLINK);
                    }
                }
            }
        }
    }

    @EventTarget
    public void onWorldLoad(LoadWorldEvent event) {
        this.setEnabled(false);
    }

    @Override
    public void onEnabled() {
        Unfair.blinkManager.setBlinkState(false, Unfair.blinkManager.getBlinkingModule());
        Unfair.blinkManager.setBlinkState(true, BlinkModules.BLINK);
    }

    @Override
    public void onDisabled() {
        Unfair.blinkManager.setBlinkState(false, BlinkModules.BLINK);
    }
}
