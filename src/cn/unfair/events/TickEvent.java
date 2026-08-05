package cn.unfair.events;

import cn.unfair.event.events.Event;
import cn.unfair.event.types.EventType;

public record TickEvent(EventType type) implements Event {
}
