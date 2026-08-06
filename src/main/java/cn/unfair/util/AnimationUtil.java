package cn.unfair.util;

public class AnimationUtil {
    public static long start() {
        return System.currentTimeMillis();
    }

    public static long elapsed(long startTime) {
        return startTime <= 0L ? 0L : System.currentTimeMillis() - startTime;
    }

    public static boolean finished(long startTime, float duration) {
        return elapsed(startTime) >= duration;
    }

    public static float progress(long startTime, float duration, float partialTicks, int easing) {
        if (startTime <= 0L) {
            return 1.0F;
        }
        float normalizedDuration = Math.max(1.0F, duration);
        float elapsed = Math.clamp(elapsed(startTime), 0.0F, normalizedDuration);
        float progress = elapsed / normalizedDuration;
        return ease(Math.clamp(progress, 0.0F, 1.0F), easing);
    }

    public static float value(float begin, float end, long startTime, float duration, float partialTicks, int easing) {
        return begin + progress(startTime, duration, partialTicks, easing) * (end - begin);
    }

    public static int value(int begin, int end, long startTime, float duration, float partialTicks, int easing) {
        return Math.round(value((float) begin, (float) end, startTime, duration, partialTicks, easing));
    }

    public static float ease(float t, int type) {
        switch (type) {
            case 1:
                return t < 0.5F ? 4.0F * t * t * t : (t - 1.0F) * (2.0F * t - 2.0F) * (2.0F * t - 2.0F) + 1.0F;
            case 2:
                return (float) (1.0D - Math.pow(1.0F - t, 5.0D));
            case 3:
                return bounce(t);
            case 4:
                return t < 0.5F ? 2.0F * t * t : -1.0F + (4.0F - 2.0F * t) * t;
            default:
                return t;
        }
    }

    private static float bounce(float t) {
        double a = 7.5625D;
        double b = 2.75D;
        if ((double) t < 1.0D / b) {
            return (float) (a * (double) t * (double) t);
        } else if ((double) t < 2.0D / b) {
            t = (float) ((double) t - 1.5D / b);
            return (float) (a * (double) t * (double) t + 0.75D);
        } else if ((double) t < 2.5D / b) {
            t = (float) ((double) t - 2.25D / b);
            return (float) (a * (double) t * (double) t + 0.9375D);
        }
        t = (float) ((double) t - 2.625D / b);
        return (float) (a * (double) t * (double) t + 0.984375D);
    }
}
