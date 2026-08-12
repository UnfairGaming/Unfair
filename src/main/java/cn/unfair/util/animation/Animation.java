package cn.unfair.util.animation;

public final class Animation {
    private final long duration;
    private long startTime;
    private double startValue;
    private double destinationValue;
    private double value;

    public Animation(long duration) {
        this.duration = duration;
        this.startTime = System.currentTimeMillis();
    }

    public void run(double destinationValue) {
        long now = System.currentTimeMillis();
        if (this.destinationValue != destinationValue) {
            this.destinationValue = destinationValue;
            this.startValue = this.value;
            this.startTime = now;
        }

        double progress = Math.min(1.0D, (double) (now - this.startTime) / (double) this.duration);
        this.value = this.startValue + (this.destinationValue - this.startValue) * progress;
    }

    public void snap(double value) {
        this.startValue = value;
        this.destinationValue = value;
        this.value = value;
        this.startTime = System.currentTimeMillis();
    }

    public double getValue() {
        return this.value;
    }
}
