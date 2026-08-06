package cn.unfair.util.player;

import cn.unfair.util.RandomUtil;

public class ApsDelayGenerator {
    private double tempo;
    private int burstTicks;
    private int restTicks;

    private static double clamp(double value, double min, double max) {
        return Math.clamp(max, min, value);
    }

    public long nextDelay(int minAps, int maxAps) {
        int min = Math.min(minAps, maxAps);
        int max = Math.max(minAps, maxAps);
        double center = (min + max) / 2.0D;
        double range = Math.max(0.35D, (max - min) / 2.0D);

        this.updateState();
        this.tempo += RandomUtil.nextDouble(-0.18D, 0.18D);
        this.tempo *= 0.72D;

        double bias = (RandomUtil.nextDouble(0.0D, 1.0D) - RandomUtil.nextDouble(0.0D, 1.0D)) * range;
        double micro = RandomUtil.nextDouble(-0.45D, 0.45D);
        double state = this.getStateOffset(range);
        double aps = center + bias + micro + this.tempo + state;

        double lower = Math.max(1.0D, min - Math.max(0.75D, range * 0.35D));
        double upper = max + Math.max(0.75D, range * 0.45D);
        aps = clamp(aps, lower, upper);

        double delay = 1000.0D / aps + RandomUtil.nextDouble(-7.0D, 7.0D);
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
        } else if (RandomUtil.nextDouble(0.0D, 1.0D) < 0.14D) {
            this.burstTicks = RandomUtil.nextInt(2, 5);
        } else if (RandomUtil.nextDouble(0.0D, 1.0D) < 0.08D) {
            this.restTicks = RandomUtil.nextInt(1, 3);
        }
    }

    private double getStateOffset(double range) {
        if (this.burstTicks > 0) {
            return range * RandomUtil.nextDouble(0.25D, 0.6D);
        }
        if (this.restTicks > 0) {
            return -range * RandomUtil.nextDouble(0.3D, 0.75D);
        }
        return 0.0D;
    }
}
