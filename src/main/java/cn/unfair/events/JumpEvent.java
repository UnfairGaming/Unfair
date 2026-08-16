package cn.unfair.events;

import cn.unfair.event.events.Event;

/**
 * Fired when the local player is about to jump.
 */
public class JumpEvent implements Event {
    private boolean cancelled = false;

    public boolean isCancelled() {
        return this.cancelled;
    }

    public void setCancelled(boolean cancelled) {
        this.cancelled = cancelled;
    }
}
