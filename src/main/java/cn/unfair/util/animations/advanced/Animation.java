package cn.unfair.util.animations.advanced;

import cn.unfair.util.TimerUtil;

public abstract class Animation {

    public TimerUtil timerUtil = new TimerUtil();
    protected int duration;
    protected double endPoint;
    protected Direction direction;

    public Animation(int ms, double endPoint) {
        this(ms, endPoint, Direction.FORWARDS);
    }

    public Animation(int ms, double endPoint, Direction direction) {
        this.duration = ms;
        this.endPoint = endPoint;
        this.direction = direction;
    }

    public boolean finished(Direction direction) {
        return isDone() && this.direction.equals(direction);
    }

    public double getLinearOutput() {
        return 1 - ((timerUtil.getElapsedTime() / (double) duration) * endPoint);
    }

    public double getEndPoint() {
        return endPoint;
    }

    public void setEndPoint(double endPoint) {
        this.endPoint = endPoint;
    }

    public void reset() {
        timerUtil.reset();
    }

    public boolean isDone() {
        return timerUtil.hasTimeElapsed(duration);
    }

    public void changeDirection() {
        setDirection(direction.opposite());
    }

    public Direction getDirection() {
        return direction;
    }

    public Animation setDirection(Direction direction) {
        if (this.direction != direction) {
            this.direction = direction;
            timerUtil.setTime(System.currentTimeMillis() - (duration - Math.min(duration, timerUtil.getElapsedTime())));
        }
        return this;
    }

    public void setDuration(int duration) {
        this.duration = duration;
    }

    protected boolean correctOutput() {
        return false;
    }

    public double getOutput() {
        if (this.direction.forwards()) {
            if (this.isDone()) {
                return this.endPoint;
            }
            return this.getEquation((double) this.timerUtil.getElapsedTime() / (double) this.duration) * this.endPoint;
        }
        if (this.isDone()) {
            return 0.0;
        }
        if (this.correctOutput()) {
            double revTime = Math.min(this.duration, Math.max(0L, (long) this.duration - this.timerUtil.getElapsedTime()));
            return this.getEquation(revTime / (double) this.duration) * this.endPoint;
        }
        return (1.0 - this.getEquation((double) this.timerUtil.getElapsedTime() / (double) this.duration)) * this.endPoint;
    }

    protected abstract double getEquation(double x);
}
