package cn.unfair.util.player;

import java.util.concurrent.ThreadLocalRandom;

public class DelayGenerator {
    private double tempo;
    private double drift;
    private double impulse;
    private double chaos;
    private long phaseUntil;
    private int phase;
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
        double span = Math.max(1.0D, max - min);

        this.updateState();
        this.tempo = this.tempo * 0.68D + nextGaussian() * 0.22D;
        this.drift = this.drift * 0.82D + nextGaussian() * 0.16D;
        this.impulse *= 0.55D;
        this.chaos = this.chaos * 0.73D + nextGaussian() * 0.41D + (ThreadLocalRandom.current().nextDouble() - 0.5D) * 0.35D;

        double bias = this.pickBias(range, span);
        double micro = nextGaussian() * Math.max(0.06D, range * 0.1D);
        double state = this.getStateOffset(range);
        double cps = center + bias + micro + this.tempo + this.drift + this.impulse + this.chaos * 0.12D + state;

        double lower = Math.max(1.0D, min - Math.max(0.75D, range * 0.35D));
        double upper = max + Math.max(0.75D, range * 0.45D);
        cps = clamp(cps, lower, upper);
        if (ThreadLocalRandom.current().nextInt(0, 12) == 0) {
            cps += nextGaussian() * Math.max(0.08D, span * 0.12D);
        }

        double delay = 1000.0D / cps + nextGaussian() * 4.0D;
        return Math.max(1L, Math.round(delay));
    }

    public void reset() {
        this.tempo = 0.0D;
        this.drift = 0.0D;
        this.impulse = 0.0D;
        this.chaos = 0.0D;
        this.phaseUntil = 0L;
        this.phase = 0;
        this.burstTicks = 0;
        this.restTicks = 0;
    }

    private void updateState() {
        long now = System.nanoTime();
        if (now >= this.phaseUntil) {
            this.phase = ThreadLocalRandom.current().nextInt(0, 3);
            this.phaseUntil = now + ThreadLocalRandom.current().nextLong(35_000_000L, 160_000_000L);
        }

        if (this.restTicks > 0) {
            this.restTicks--;
            this.impulse -= Math.min(0.35D, nextGaussianChance() * 0.08D);
        } else if (this.burstTicks > 0) {
            this.burstTicks--;
            this.impulse += Math.min(0.35D, nextGaussianChance() * 0.09D);
        } else {
            double burstChance = 0.18D + (this.phase == 1 ? 0.12D : 0.0D);
            double restChance = 0.16D + (this.phase == 2 ? 0.14D : 0.0D);
            double roll = ThreadLocalRandom.current().nextDouble();
            if (roll > 1.0D - burstChance) {
                this.burstTicks = (int) Math.round(nextGaussianBetween(2.0D, 6.0D));
                this.impulse += nextGaussianBetween(0.12D, 0.42D);
            } else if (roll < restChance) {
                this.restTicks = (int) Math.round(nextGaussianBetween(1.0D, 4.0D));
                this.impulse -= nextGaussianBetween(0.10D, 0.38D);
            }
        }
    }

    private double pickBias(double range, double span) {
        double roll = ThreadLocalRandom.current().nextDouble();
        if (roll < 0.33D) {
            return nextGaussian() * range * (0.22D + ThreadLocalRandom.current().nextDouble() * 0.18D);
        }
        if (roll < 0.72D) {
            return (ThreadLocalRandom.current().nextDouble() - 0.5D) * range * (0.35D + ThreadLocalRandom.current().nextDouble() * 0.2D);
        }
        return (ThreadLocalRandom.current().nextDouble() - 0.5D) * span * (0.18D + ThreadLocalRandom.current().nextDouble() * 0.24D);
    }

    private double getStateOffset(double range) {
        if (this.burstTicks > 0) {
            return range * nextGaussianBetween(0.18D, 0.42D);
        }
        if (this.restTicks > 0) {
            return -range * nextGaussianBetween(0.22D, 0.58D);
        }
        return 0.0D;
    }
}
