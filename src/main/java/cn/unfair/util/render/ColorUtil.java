package cn.unfair.util.render;

import java.awt.*;

public class ColorUtil {
    public static final Color RED = new Color(255, 0, 0);
    public static final Color GOLD = new Color(255, 165, 0);
    public static final Color YELLOW = new Color(255, 255, 0);
    public static final Color GREEN = new Color(0, 255, 0);

    public static Color applyOpacity(Color color, float opacity) {
        opacity = Math.clamp(opacity, 0.0f, 1.0f);
        return new Color(color.getRed(), color.getGreen(), color.getBlue(), (int) (255 * opacity));
    }

    public static Color rainbow(int seconds, int offset, float saturation, float brightness) {
        float hue = ((System.currentTimeMillis() + offset) % (seconds * 1000)) / (float) (seconds * 1000);
        return new Color(Color.HSBtoRGB(hue, saturation, brightness));
    }

    public static Color fade(Color color, int index, int count) {
        float[] hsb = new float[3];
        Color.RGBtoHSB(color.getRed(), color.getGreen(), color.getBlue(), hsb);
        float brightness = Math.abs((((System.currentTimeMillis() % 2000) / 1000.0f + (index / (float) count) * 2.0f) % 2.0f) - 1.0f);
        brightness = 0.5f + 0.5f * brightness;
        hsb[2] = brightness % 1.0f;
        return new Color(Color.HSBtoRGB(hsb[0], hsb[1], hsb[2]));
    }

    public static Color fromHSB(float hue, float saturation, float brightness) {
        return new Color(Color.HSBtoRGB(hue, saturation, brightness));
    }

    public static Color interpolate(float progress, Color startColor, Color endColor) {
        progress = Math.clamp(progress, 0.0f, 1.0f);
        return new Color((int) ((float) startColor.getRed() + progress * (float) (endColor.getRed() - startColor.getRed())), (int) ((float) startColor.getGreen() + progress * (float) (endColor.getGreen() - startColor.getGreen())), (int) ((float) startColor.getBlue() + progress * (float) (endColor.getBlue() - startColor.getBlue())));
    }

    public static int setAlpha(int rgb, double alpha) {
        return (rgb & 0xFFFFFF) | Math.clamp((int) (alpha * 255.0), 0, 255) << 24;
    }

    public static int getChroma(double step) {
        double divisor = 18000.0 / step;
        double time = System.currentTimeMillis() % divisor / divisor;
        return Color.getHSBColor((float) time, 1.0f, 1.0f).getRGB();
    }

    public static int interpolate(int startColor, int endColor, double percent) {
        Color start = new Color(startColor, true);
        Color end = new Color(endColor, true);
        double inverse = 1.0 - percent;
        int red = (int) (start.getRed() * percent + end.getRed() * inverse);
        int green = (int) (start.getGreen() * percent + end.getGreen() * inverse);
        int blue = (int) (start.getBlue() * percent + end.getBlue() * inverse);
        int alpha = (int) (start.getAlpha() * percent + end.getAlpha() * inverse);
        return new Color(red, green, blue, alpha).getRGB();
    }

    public static Color getHealthBlend(float percent) {
        if (percent >= 0.9f) {
            return GREEN;
        }
        if (percent >= 0.55f) {
            return ColorUtil.interpolate((percent - 0.55f) / 0.35f, YELLOW, GREEN);
        }
        if (percent >= 0.45f) {
            return YELLOW;
        }
        if (percent >= 0.1f) {
            return ColorUtil.interpolate((percent - 0.1f) / 0.35f, RED, YELLOW);
        }
        return RED;
    }

    public static Color darker(Color color, float factor) {
        return ColorUtil.scale(color, factor, color.getAlpha());
    }

    public static Color scale(Color color, float scaleFactor, int alpha) {
        return new Color(Math.clamp((int) ((float) color.getRed() * scaleFactor), 0, 255), Math.clamp((int) ((float) color.getGreen() * scaleFactor), 0, 255), Math.clamp((int) ((float) color.getBlue() * scaleFactor), 0, 255), alpha);
    }
}
