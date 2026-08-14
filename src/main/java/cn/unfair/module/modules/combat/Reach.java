package cn.unfair.module.modules.combat;

import cn.unfair.event.EventTarget;
import cn.unfair.event.types.EventType;
import cn.unfair.events.PickEvent;
import cn.unfair.events.RaytraceEvent;
import cn.unfair.events.TickEvent;
import cn.unfair.module.Module;
import cn.unfair.property.properties.FloatProperty;
import cn.unfair.property.properties.PercentProperty;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;
import java.util.Random;

public class Reach extends Module {
    private static final DecimalFormat df = new DecimalFormat("0.0#", new DecimalFormatSymbols(Locale.US));
    public final FloatProperty range = new FloatProperty("Range", 3.1F, 3.0F, 6.0F);
    public final PercentProperty chance = new PercentProperty("Chance", 100);
    private final Random theRandom = new Random();
    private boolean expanding = true;

    public Reach() {
        super("Reach", false);
    }

    @EventTarget
    public void onPick(PickEvent event) {
        if (this.isEnabled() && this.expanding) {
            event.setRange(this.range.getValue().doubleValue());
        }
    }

    @EventTarget
    public void onRaytrace(RaytraceEvent event) {
        if (this.isEnabled() && this.expanding) {
            event.setRange(Math.max(event.getRange(), this.range.getValue().doubleValue() + 0.5));
        }
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (this.isEnabled() && event.type() == EventType.PRE) {
            this.expanding = this.theRandom.nextDouble() <= (double) this.chance.getValue() / 100.0;
        }
    }

    @Override
    public String[] getSuffix() {
        return new String[]{df.format(this.range.getValue())};
    }
}
