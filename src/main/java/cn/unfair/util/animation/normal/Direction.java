package cn.unfair.util.animation.normal;

public enum Direction {
    FORWARDS,
    BACKWARDS;

    public Direction opposite() {
        return this == Direction.FORWARDS ? Direction.BACKWARDS : Direction.FORWARDS;
    }
}
