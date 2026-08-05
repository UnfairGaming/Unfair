package cn.unfair.util;

import net.minecraft.util.Vec3;

import java.math.BigDecimal;
import java.math.MathContext;
import java.security.SecureRandom;
import java.util.concurrent.ThreadLocalRandom;

public class MathUtil {
    public static final float PI = (float) Math.PI;
    public static final float TO_DEGREES = 180.0F / PI;

    public static double interporate(float partialTicks, double old, double current) {
        return old + (current - old) * partialTicks;
    }

    public static Vec3 interpolateVec3(Vec3 old, Vec3 current, float partialTicks) {
        return new Vec3(
                old.xCoord + (current.xCoord - old.xCoord) * partialTicks,
                old.yCoord + (current.yCoord - old.yCoord) * partialTicks,
                old.zCoord + (current.zCoord - old.zCoord) * partialTicks
        );
    }

    public static double interpolateDouble(double old, double current, float partialTicks) {
        return old + (current - old) * partialTicks;
    }

    public static float normalize(float value, float min, float max) {
        return (value - min) / (max - min);
    }

    public static double roundToHalf(double d) {
        return Math.round(d * 2) / 2.0;
    }

    public static double incValue(double val, double inc) {
        double one = 1.0 / inc;
        return Math.round(val * one) / one;
    }

    public static double roundToDecimalPlace(double value, double inc) {
        double halfOfInc = inc / 2.0;
        double floored = StrictMath.floor(value / inc) * inc;
        if (value >= floored + halfOfInc) {
            return new BigDecimal(StrictMath.ceil(value / inc) * inc, MathContext.DECIMAL64).stripTrailingZeros().doubleValue();
        }
        return new BigDecimal(floored, MathContext.DECIMAL64).stripTrailingZeros().doubleValue();
    }

    public static double interpolate(double old, double now, float partialTicks) {
        return old + (now - old) * partialTicks;
    }

    public static double interpolate(double oldValue, double newValue, double interpolationValue) {
        return (oldValue + (newValue - oldValue) * interpolationValue);
    }

    public static float interpolate(float old, float now, float partialTicks) {
        return old + (now - old) * partialTicks;
    }

    public static Vec3 interpolate(Vec3 end, Vec3 start, float multiple) {
        return new Vec3(
                (float) interpolate(end.xCoord, start.xCoord, multiple),
                (float) interpolate(end.yCoord, start.yCoord, multiple),
                (float) interpolate(end.zCoord, start.zCoord, multiple));
    }


    public static float clampValue(final float value, final float floor, final float cap) {
        if (value < floor) {
            return floor;
        }
        return Math.min(value, cap);
    }

    public static int getRandom(int min, int max) {
        if (min == max) {
            return min;
        } else if (min > max) {
            final int d = min;
            min = max;
            max = d;
        }
        return ThreadLocalRandom.current().nextInt(min, max);
    }

    public static double getRandom(double min, double max) {
        if (min == max) {
            return min;
        } else if (min > max) {
            final double d = min;
            min = max;
            max = d;
        }
        return ThreadLocalRandom.current().nextDouble(min, max);
    }

    public static float nextSecureFloat(final double origin, final double bound) {
        if (origin == bound) {
            return (float) origin;
        }
        final SecureRandom secureRandom = new SecureRandom();
        final float difference = (float) (bound - origin);
        return (float) (origin + secureRandom.nextFloat() * difference);
    }

    public static float calculateGaussianValue(float x, float sigma) {
        double PI = Math.PI;
        double output = 1.0 / Math.sqrt(2.0 * PI * (sigma * sigma));
        return (float) (output * Math.exp(-(x * x) / (2.0 * (sigma * sigma))));
    }

    public static double lerp(double pct, double start, double end) {
        return start + pct * (end - start);
    }

    public static float lerp(float min, float max, float delta) {
        return min + (max - min) * delta;
    }

    public static int nextInt(int min, int max) {
        if (min == max || max - min <= 0D)
            return min;
        return (int) (min + ((max - min) * Math.random()));
    }

    public static double nextDouble(double min, double max) {
        if (min == max || max - min <= 0D)
            return min;
        return min + ((max - min) * Math.random());
    }

    public static float nextFloat(final float startInclusive, final float endInclusive) {
        if (startInclusive == endInclusive || endInclusive - startInclusive <= 0F)
            return startInclusive;
        return (float) (startInclusive + ((endInclusive - startInclusive) * Math.random()));
    }

    public static boolean inBetween(double min, double max, double value) {
        return value >= min && value <= max;
    }

    public static double wrappedDifference(double number1, double number2) {
        return Math.min(Math.abs(number1 - number2), Math.min(Math.abs(number1 - 360) - Math.abs(number2 - 0), Math.abs(number2 - 360) - Math.abs(number1 - 0)));
    }
}
