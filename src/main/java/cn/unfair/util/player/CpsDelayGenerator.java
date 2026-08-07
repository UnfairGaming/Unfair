package cn.unfair.util.player;

import java.util.concurrent.ThreadLocalRandom;

public class CpsDelayGenerator {
    private double tempo;
    private int burstTicks;
    private int restTicks;

    private static double clamp(double value, double min, double max) {
        return Math.clamp(value, min, max);
    }

    private static double nextGaussian() {
        return ThreadLocalRandom.current().nextGaussian();
    }

    private static double nextGaussianBetween(double min, double max) {
        double center = (min + max) / 2.0D;
        double deviation = Math.max(0.0001D, (max - min) / 6.0D);
        return clamp(center + nextGaussian() * deviation, min, max);
    }

    private static double nextGaussianChance() {
        return clamp(0.5D + nextGaussian() * 0.15D, 0.0D, 1.0D);
    }

    public long nextDelay(int minCps, int maxCps) {
        int min = Math.min(minCps, maxCps);
        int max = Math.max(minCps, maxCps);
        double center = (min + max) / 2.0D;
        double range = Math.max(0.35D, (max - min) / 2.0D);

        this.updateState();
        this.tempo += nextGaussian() * 0.18D;
        this.tempo *= 0.72D;

        double bias = nextGaussian() * range * 0.42D;
        double micro = nextGaussian() * 0.22D;
        double state = this.getStateOffset(range);
        double cps = center + bias + micro + this.tempo + state;

        double lower = Math.max(1.0D, min - Math.max(0.75D, range * 0.35D));
        double upper = max + Math.max(0.75D, range * 0.45D);
        cps = clamp(cps, lower, upper);

        double delay = 1000.0D / cps + nextGaussian() * 4.0D;
        return Math.max(1L, Math.round(delay));
    }

    public void reset() {
        this.tempo = 0.0D;
        this.burstTicks = 0;
        this.restTicks = 0;
    }

    private void updateState() {
        if (this.restTicks > 0) {
            this.restTicks--;
        } else if (this.burstTicks > 0) {
            this.burstTicks--;
        } else if (nextGaussianChance() > 0.72D) {
            this.burstTicks = (int) Math.round(nextGaussianBetween(2.0D, 5.0D));
        } else if (nextGaussianChance() < 0.28D) {
            this.restTicks = (int) Math.round(nextGaussianBetween(1.0D, 3.0D));
        }
    }

    private double getStateOffset(double range) {
        if (this.burstTicks > 0) {
            return range * nextGaussianBetween(0.25D, 0.6D);
        }
        if (this.restTicks > 0) {
            return -range * nextGaussianBetween(0.3D, 0.75D);
        }
        return 0.0D;
    }
}
