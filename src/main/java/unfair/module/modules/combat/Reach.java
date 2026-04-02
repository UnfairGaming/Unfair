package unfair.module.modules.combat;

import unfair.event.EventTarget;
import unfair.event.types.EventType;
import unfair.events.PickEvent;
import unfair.events.RaytraceEvent;
import unfair.events.TickEvent;
import unfair.module.Module;
import unfair.property.properties.FloatProperty;
import unfair.property.properties.PercentProperty;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;
import java.util.Random;

public class Reach extends Module {
    private static final DecimalFormat df = new DecimalFormat("0.0#", new DecimalFormatSymbols(Locale.US));
    public final FloatProperty range = new FloatProperty("range", 3.1F, 3.0F, 6.0F);
    public final PercentProperty chance = new PercentProperty("chance", 100);
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
        if (this.isEnabled() && event.getType() == EventType.PRE) {
            this.expanding = this.theRandom.nextDouble() <= (double) this.chance.getValue() / 100.0;
        }
    }

    @Override
    public String[] getSuffix() {
        return new String[]{df.format(this.range.getValue())};
    }
}
