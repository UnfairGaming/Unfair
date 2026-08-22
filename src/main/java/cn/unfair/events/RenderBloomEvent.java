package cn.unfair.events;

import cn.unfair.event.events.Event;
import cn.unfair.event.types.EventType;

public class RenderBloomEvent implements Event {
    private final EventType type;
    private final float partialTicks;
    private boolean cancelled;

    public RenderBloomEvent(EventType type) {
        this(type, 1.0F);
    }

    public RenderBloomEvent(EventType type, float partialTicks) {
        this.type = type;
        this.partialTicks = partialTicks;
        this.cancelled = false;
    }

    public EventType getType() {
        return this.type;
    }

    public float getPartialTicks() {
        return this.partialTicks;
    }

    public boolean isCancelled() {
        return this.cancelled;
    }

    public void setCancelled(boolean cancelled) {
        this.cancelled = cancelled;
    }
}
