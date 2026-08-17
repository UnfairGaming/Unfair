package cn.unfair.util.player;

import java.security.SecureRandom;

public final class DelayGenerator {
    private static final SecureRandom RANDOM = new SecureRandom();
    private int peakCooldown;

    public long nextDelay(int minCps, int maxCps) {
        int lowerCps = Math.max(1, Math.min(minCps, maxCps));
        int upperCps = Math.max(lowerCps, Math.max(minCps, maxCps));
        double fastestDelay = 1000.0D / upperCps;
        double slowestDelay = 1000.0D / lowerCps;
        double averageDelay = (fastestDelay + slowestDelay) * 0.5D;
        double deviation = Math.max(1.0D, (slowestDelay - fastestDelay) * 0.22D);
        double delay = Math.clamp(averageDelay + RANDOM.nextGaussian() * deviation, fastestDelay, slowestDelay);

        if (this.peakCooldown > 0) {
            this.peakCooldown--;
        } else if (RANDOM.nextInt(100) < 4) {
            delay = slowestDelay * (1.35D + RANDOM.nextDouble() * 0.75D);
            this.peakCooldown = 7 + RANDOM.nextInt(9);
        }

        return Math.max(1L, Math.round(delay));
    }

    public void reset() {
        this.peakCooldown = 0;
    }
}
