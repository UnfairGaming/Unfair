package cn.unfair.events;

import cn.unfair.event.events.Event;

public class SwingAnimationEvent implements Event {
    private int animationEnd;

    public SwingAnimationEvent(int animationEnd) {
        this.animationEnd = animationEnd;
    }

    public int getAnimationEnd() {
        return animationEnd;
    }

    public void setAnimationEnd(int animationEnd) {
        this.animationEnd = animationEnd;
    }
}
