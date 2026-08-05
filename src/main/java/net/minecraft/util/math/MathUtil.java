package net.minecraft.util.math;

import lombok.experimental.UtilityClass;
import net.minecraft.util.MathHelper;

@UtilityClass
public class MathUtil {
    public final float PI = (float) Math.PI;
    public final float TO_DEGREES = 180.0F / PI;
    public int floor(double d) {
        return MathHelper.floor_double(d);
    }

    public double lerp(final double d, final double e, final double f) {
        return e + d * (f - e);
    }

    public Double interpolate(double oldValue, double newValue, double interpolationValue) {
        return (oldValue + (newValue - oldValue) * interpolationValue);
    }

    public double wrappedDifference(double number1, double number2) {
        return Math.min(Math.abs(number1 - number2), Math.min(Math.abs(number1 - 360) - Math.abs(number2 - 0), Math.abs(number2 - 360) - Math.abs(number1 - 0)));
    }

    public float calculateGaussianValue(float x, float sigma) {
        double output = 1.0 / Math.sqrt(2.0 * Math.PI * (sigma * sigma));
        return (float) (output * Math.exp(-(x * x) / (2.0 * (sigma * sigma))));
    }
}
