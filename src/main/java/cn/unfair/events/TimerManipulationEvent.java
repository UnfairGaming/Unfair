package cn.unfair.events;

import cn.unfair.event.events.Event;

public class TimerManipulationEvent implements Event {
    private long time;

    public TimerManipulationEvent(long time) {
        this.time = time;
    }

    public long getTime() {
        return this.time;
    }

    public void setTime(long time) {
        this.time = time;
    }
}
