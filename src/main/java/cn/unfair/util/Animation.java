package cn.unfair.util;

public class Animation {
    private final float duration;
    private long startTime;
    private float completedValue;

    public Animation(float duration) {
        this.duration = Math.max(1.0F, duration);
        this.completedValue = Float.NaN;
    }

    public void start() {
        this.completedValue = Float.NaN;
        this.startTime = System.currentTimeMillis();
    }

    public boolean isStarted() {
        return this.startTime > 0L;
    }

    public long getStartTime() {
        return this.startTime;
    }

    public long getElapsed() {
        return !this.isStarted() ? 0L : System.currentTimeMillis() - this.startTime;
    }

    public boolean isFinished() {
        return !Float.isNaN(this.completedValue);
    }

    public float getValueFloat(float begin, float end, int type) {
        if (this.isFinished() && this.completedValue == end) {
            return this.completedValue;
        }

        float t = Math.min(1.0F, Math.max(0.0F, this.getElapsed() / this.duration));
        t = this.applyEasing(t, type);

        float value = begin + t * (end - begin);
        if ((end > begin && value > end) || (end < begin && value < end)) {
            value = end;
        }

        if (value == end) {
            this.completedValue = value;
        }

        return value;
    }

    public int getValueInt(int begin, int end, int type) {
        return Math.round(this.getValueFloat(begin, end, type));
    }

    private float applyEasing(float t, int type) {
        switch (type) {
            case 1:
                return t < 0.5F ? 4.0F * t * t * t : (t - 1.0F) * (2.0F * t - 2.0F) * (2.0F * t - 2.0F) + 1.0F;
            case 2:
                return (float) (1.0D - Math.pow(1.0F - t, 5.0D));
            case 3:
                return this.bounce(t);
            case 4:
                return t < 0.5F ? 2.0F * t * t : -1.0F + (4.0F - 2.0F * t) * t;
            default:
                return t;
        }
    }

    private float bounce(float t) {
        float value;
        double a = 7.5625D;
        double b = 2.75D;
        if ((double) t < 1.0D / b) {
            value = (float) (a * (double) t * (double) t);
        } else if ((double) t < 2.0D / b) {
            t = (float) ((double) t - 1.5D / b);
            value = (float) (a * (double) t * (double) t + 0.75D);
        } else if ((double) t < 2.5D / b) {
            t = (float) ((double) t - 2.25D / b);
            value = (float) (a * (double) t * (double) t + 0.9375D);
        } else {
            t = (float) ((double) t - 2.625D / b);
            value = (float) (a * (double) t * (double) t + 0.984375D);
        }
        return value;
    }
}
