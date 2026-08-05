package cn.unfair.events;

import cn.unfair.event.events.Event;

public record Render2DEvent(float partialTicks) implements Event {
}
