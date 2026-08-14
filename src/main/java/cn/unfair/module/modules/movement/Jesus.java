package cn.unfair.module.modules.movement;

import cn.unfair.module.Module;
import cn.unfair.property.properties.BooleanProperty;
import cn.unfair.property.properties.FloatProperty;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

public class Jesus extends Module {
    private static final DecimalFormat df = new DecimalFormat("#.##", new DecimalFormatSymbols(Locale.US));
    public final FloatProperty speed = new FloatProperty("Speed", 2.5F, 0.0F, 3.0F);
    public final BooleanProperty noPush = new BooleanProperty("No Push", true);
    public final BooleanProperty groundOnly = new BooleanProperty("Ground Only", true);

    public Jesus() {
        super("Jesus", false);
    }

    @Override
    public String[] getSuffix() {
        return new String[]{df.format(this.speed.getValue())};
    }
}
