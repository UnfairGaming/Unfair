package cn.unfair.module.modules.render;

import cn.unfair.module.Module;
import cn.unfair.property.properties.BooleanProperty;
import cn.unfair.property.properties.ColorProperty;
import cn.unfair.property.properties.ModeProperty;
import cn.unfair.util.render.ColorUtil;

import java.awt.*;

public class Interface extends Module {
    public final BooleanProperty customButton = new BooleanProperty("CustomButton", false);
    public final BooleanProperty customFont = new BooleanProperty("CustomFont", true, () -> customButton.getValue());
    public final ModeProperty buttonColorMode = new ModeProperty("ButtonColor", 0, new String[]{"Hud", "Custom", "Fade"}, () -> customButton.getValue());
    public final ColorProperty buttonColor1 = new ColorProperty("ButtonColor1", Color.WHITE.getRGB(), () -> customButton.getValue() && buttonColorMode.getValue() != 0);
    public final ColorProperty buttonColor2 = new ColorProperty("ButtonColor2", Color.WHITE.getRGB(), () -> customButton.getValue() && buttonColorMode.getValue() == 2);

    public Interface() {
        super("Interface", false, true);
    }

    public Color getButtonColor(long time) {
        return switch (buttonColorMode.getValue()) {
            case 1 -> new Color(buttonColor1.getValue());
            case 2 -> ColorUtil.interpolate(
                    (float) (2.0 * Math.abs(HUD.getColorCycle(time, 0L) - Math.floor(HUD.getColorCycle(time, 0L) + 0.5))),
                    new Color(buttonColor1.getValue()),
                    new Color(buttonColor2.getValue())
            );
            default -> HUD.getColor(time, 0L);
        };
    }
}
