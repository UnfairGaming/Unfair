package cn.unfair.util.animations.advanced.impl;

import cn.unfair.util.animations.Animation;

public class DecelerateAnimation extends Animation {

    public DecelerateAnimation(int ms, double endPoint) {
        super(ms, endPoint);
    }

    public DecelerateAnimation(int ms, double endPoint, Direction direction) {
        super(ms, endPoint, direction);
    }

    @Override
    public double getOutput() {
        if (direction == Direction.FORWARDS) {
            if (isDone()) return endPoint;
            return (endPoint * (1 - Math.pow(1 - getLinearOutput(), 2)));
        } else {
            if (isDone()) return 0;
            return (endPoint * Math.pow(1 - getLinearOutput(), 2));
        }
    }
}
