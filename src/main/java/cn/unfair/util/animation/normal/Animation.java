package cn.unfair.util.animation.normal;

import cn.unfair.util.client.TimerUtil;

public abstract class Animation {

    public TimerUtil timer = new TimerUtil();

    protected int duration;

    protected double endPoint;

    protected Direction direction;

    public Animation(int ms, double endPoint) {
        this.duration = ms;
        this.endPoint = endPoint;
        this.direction = Direction.FORWARDS;
    }

    public Animation(int ms, double endPoint, Direction direction) {
        this.duration = ms;
        this.endPoint = endPoint;
        this.direction = direction;
    }

    public boolean isDone(Direction direction) {
        return isDone() && this.direction.equals(direction);
    }

    public double getLinearOutput() {
        return 1 - ((timer.getElapsedTime() / (double) duration) * endPoint);
    }

    public void reset() {
        timer.reset();
    }

    public boolean isDone() {
        return timer.getPass(duration);
    }

    public void changeDirection() {
        setDirection(direction.opposite());
    }

    public void setDirection(Direction direction) {
        if (this.direction != direction) {
            this.direction = direction;
            timer.setTime(System.currentTimeMillis() - (duration - Math.min(duration, timer.getElapsedTime())));
        }
    }

    protected boolean correctOutput() {
        return false;
    }

    public double getValue() {
        if (direction == Direction.FORWARDS) {
            if (isDone())
                return endPoint;
            return (getEquation(timer.getElapsedTime()) * endPoint);
        } else {
            if (isDone()) return 0;
            if (correctOutput()) {
                double revTime = Math.min(duration, Math.max(0, duration - timer.getElapsedTime()));
                return getEquation(revTime) * endPoint;
            } else return (1 - getEquation(timer.getElapsedTime())) * endPoint;
        }
    }

    public void setValue(double value) {
        if (value >= 0 && value <= 1) {
            this.endPoint = value;
            long elapsedTime = (long) ((1 - value) * duration);
            timer.setTime(System.currentTimeMillis() - (duration - Math.min(duration, elapsedTime)));
        }
    }

    public float getValueFloat() {
        return (float) getValue();
    }

    public int getValueInt() {
        return (int) getValue();
    }

    protected abstract double getEquation(double x);

    public double getEndPoint() {
        return endPoint;
    }

    public void setEndPoint(double endPoint) {
        this.endPoint = endPoint;
    }

    public int getDuration() {
        return duration;
    }

    public Direction getDirection() {
        return direction;
    }
}
