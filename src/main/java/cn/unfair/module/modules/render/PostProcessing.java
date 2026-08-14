package cn.unfair.module.modules.render;

import cn.unfair.module.Module;
import cn.unfair.property.properties.*;
import cn.unfair.util.ColorUtil;

import java.awt.*;

public class PostProcessing extends Module {
    public final BooleanProperty blur = new BooleanProperty("Blur", true);
    public final IntProperty blurIterations = new IntProperty("Blur Iterations", 2, 1, 10);
    public final IntProperty blurOffset = new IntProperty("Blur Offset", 5, 1, 10);

    public final BooleanProperty bloom = new BooleanProperty("Bloom", true);
    public final ModeProperty bloomColorMode = new ModeProperty(
            "Bloom Color", 3, new String[]{"Rainbow", "Chroma", "Astolfo", "Custom1", "Custom12", "Custom123"}
    );
    public final FloatProperty bloomColorSpeed = new FloatProperty("Bloom Color Speed", 1.0F, 0.5F, 1.5F);
    public final PercentProperty bloomColorSaturation = new PercentProperty("Bloom Saturation", 50);
    public final PercentProperty bloomColorBrightness = new PercentProperty("Bloom Brightness", 100);
    public final ColorProperty bloomCustom1 = new ColorProperty("Bloom Color 1", Color.BLACK.getRGB(), () -> this.bloomColorMode.getValue() == 3 || this.bloomColorMode.getValue() == 4 || this.bloomColorMode.getValue() == 5);
    public final ColorProperty bloomCustom2 = new ColorProperty("Bloom Color 2", Color.BLUE.getRGB(), () -> this.bloomColorMode.getValue() == 4 || this.bloomColorMode.getValue() == 5);
    public final ColorProperty bloomCustom3 = new ColorProperty("Bloom Color 3", Color.WHITE.getRGB(), () -> this.bloomColorMode.getValue() == 5);
    public final IntProperty bloomIterations = new IntProperty("Bloom Iterations", 2, 1, 10);
    public final IntProperty bloomOffset = new IntProperty("Bloom Offset", 2, 1, 10);

    public PostProcessing() {
        super("PostProcessing", false, true);
    }

    private float getBloomColorCycle(long time, long offset) {
        long speed = (long) (3000.0 / Math.pow(Math.clamp(this.bloomColorSpeed.getValue(), 0.5F, 1.5F), 3.0));
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
