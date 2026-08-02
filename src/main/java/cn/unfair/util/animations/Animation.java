package cn.unfair.util.animations;

public abstract class Animation {
    protected int duration;
    protected double endPoint;
    protected Direction direction;
    protected long startTime;
    
    public enum Direction {
        FORWARDS,
        BACKWARDS
    }
    
    public Animation(int ms, double endPoint) {
        this(ms, endPoint, Direction.FORWARDS);
    }
    
    public Animation(int ms, double endPoint, Direction direction) {
        this.duration = ms;
        this.endPoint = endPoint;
        this.direction = direction;
        this.startTime = System.currentTimeMillis();
    }
    
    public boolean finished(Direction direction) {
        return isDone() && this.direction.equals(direction);
    }
    
    public double getLinearOutput() {
        return 1 - ((duration - Math.min(duration, System.currentTimeMillis() - startTime)) / (double) duration);
    }
    
    public void reset() {
        startTime = System.currentTimeMillis();
    }
    
    public boolean isDone() {
        return System.currentTimeMillis() - startTime > duration;
    }
    
    public void changeDirection() {
        setDirection(direction.equals(Direction.FORWARDS) ? Direction.BACKWARDS : Direction.FORWARDS);
    }
    
    public Animation setDirection(Direction direction) {
        if (this.direction != direction) {
            this.direction = direction;
            startTime = System.currentTimeMillis() - (duration - (System.currentTimeMillis() - startTime));
        }
        return this;
    }
    
    public void setDuration(int duration) {
        this.duration = duration;
    }
    
    public void setEndPoint(double endPoint) {
        this.endPoint = endPoint;
    }
    
    public double getEndPoint() {
        return endPoint;
    }
    
    public Direction getDirection() {
        return direction;
    }
    
    protected boolean correctOutput() {
        return false;
    }
    
    public abstract double getOutput();
}
