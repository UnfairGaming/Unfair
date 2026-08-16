package cn.unfair.util;

import java.util.Random;

public class RandomUtil {
    private static final Random theRandom = new Random();

    public static long nextLong(long min, long max) {
        return (long) nextDouble((double) min, (double) (max + 1L));
    }

    public static float nextFloat(float min, float max) {
        float lower = Math.min(min, max);
        float upper = Math.max(min, max);
        return lower == upper ? lower : theRandom.nextFloat() * (upper - lower) + lower;
    }

    public static double nextDouble(double min, double max) {
        return theRandom.nextDouble() * (max - min) + min;
    }

    public static int nextInt(int min, int max) {
        return theRandom.nextInt(max - min) + min;
    }
}
