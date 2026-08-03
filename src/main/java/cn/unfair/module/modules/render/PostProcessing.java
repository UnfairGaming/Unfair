package cn.unfair.module.modules.render;

import cn.unfair.module.Module;
import cn.unfair.property.properties.BooleanProperty;
import cn.unfair.property.properties.ColorProperty;
import cn.unfair.property.properties.FloatProperty;
import cn.unfair.property.properties.IntProperty;
import cn.unfair.property.properties.ModeProperty;
import cn.unfair.property.properties.PercentProperty;
import cn.unfair.util.ColorUtil;

import java.awt.*;

public class PostProcessing extends Module {
    public final BooleanProperty blur = new BooleanProperty("blur", true);
    public final IntProperty blurIterations = new IntProperty("blur-iterations", 3, 1, 10);
    public final IntProperty blurOffset = new IntProperty("blur-offset", 3, 1, 10);

    public final BooleanProperty bloom = new BooleanProperty("bloom", true);
    public final ModeProperty bloomColorMode = new ModeProperty(
            "bloom-color", 3, new String[]{"RAINBOW", "CHROMA", "ASTOLFO", "CUSTOM1", "CUSTOM12", "CUSTOM123"}
    );
    public final FloatProperty bloomColorSpeed = new FloatProperty("bloom-color-speed", 1.0F, 0.5F, 1.5F);
    public final PercentProperty bloomColorSaturation = new PercentProperty("bloom-saturation", 50);
    public final PercentProperty bloomColorBrightness = new PercentProperty("bloom-brightness", 100);
    public final ColorProperty bloomCustom1 = new ColorProperty("bloom-color-1", Color.WHITE.getRGB(), () -> this.bloomColorMode.getValue() == 3 || this.bloomColorMode.getValue() == 4 || this.bloomColorMode.getValue() == 5);
    public final ColorProperty bloomCustom2 = new ColorProperty("bloom-color-2", Color.WHITE.getRGB(), () -> this.bloomColorMode.getValue() == 4 || this.bloomColorMode.getValue() == 5);
    public final ColorProperty bloomCustom3 = new ColorProperty("bloom-color-3", Color.WHITE.getRGB(), () -> this.bloomColorMode.getValue() == 5);
    public final IntProperty bloomIterations = new IntProperty("bloom-iterations", 3, 1, 10);
    public final IntProperty bloomOffset = new IntProperty("bloom-offset", 3, 1, 10);

    public PostProcessing() {
        super("PostProcessing", false, true);
    }

    private float getBloomColorCycle(long time, long offset) {
        long speed = (long) (3000.0 / Math.pow(Math.min(Math.max(0.5F, this.bloomColorSpeed.getValue()), 1.5F), 3.0));
        return 1.0F - (float) (Math.abs(time - offset * 300L) % speed) / (float) speed;
    }

    public Color getBloomColor(long time) {
        return getBloomColor(time, 0L);
    }

    public Color getBloomColor(long time, long offset) {
        Color color = Color.white;
        switch (this.bloomColorMode.getValue()) {
            case 0:
                color = ColorUtil.fromHSB(getBloomColorCycle(time, offset), 1.0F, 1.0F);
                break;
            case 1:
                color = ColorUtil.fromHSB(getBloomColorCycle(time / 3L, 0L), 1.0F, 1.0F);
                break;
            case 2:
                float cycle = getBloomColorCycle(time, offset);
                if (cycle % 1.0F < 0.5F) {
                    cycle = 1.0F - cycle % 1.0F;
                }
                color = ColorUtil.fromHSB(cycle, 1.0F, 1.0F);
                break;
            case 3:
                color = new Color(this.bloomCustom1.getValue());
                break;
            case 4:
                double cycle1 = getBloomColorCycle(time, offset);
                color = ColorUtil.interpolate(
                        (float) (2.0 * Math.abs(cycle1 - Math.floor(cycle1 + 0.5))),
                        new Color(this.bloomCustom1.getValue()),
                        new Color(this.bloomCustom2.getValue())
                );
                break;
            case 5:
                double cycle2 = getBloomColorCycle(time, offset);
                float floor = (float) (2.0 * Math.abs(cycle2 - Math.floor(cycle2 + 0.5)));
                if (floor <= 0.5F) {
                    color = ColorUtil.interpolate(floor * 2.0F, new Color(this.bloomCustom1.getValue()), new Color(this.bloomCustom2.getValue()));
                } else {
                    color = ColorUtil.interpolate((floor - 0.5F) * 2.0F, new Color(this.bloomCustom2.getValue()), new Color(this.bloomCustom3.getValue()));
                }
                break;
        }
        float[] hsb = Color.RGBtoHSB(color.getRed(), color.getGreen(), color.getBlue(), null);
        return Color.getHSBColor(
                hsb[0],
                hsb[1] * (this.bloomColorSaturation.getValue().floatValue() / 100.0F),
                hsb[2] * (this.bloomColorBrightness.getValue().floatValue() / 100.0F)
        );
    }
}
