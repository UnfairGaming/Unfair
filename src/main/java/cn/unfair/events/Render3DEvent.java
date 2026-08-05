package cn.unfair.events;

import cn.unfair.event.events.Event;

public record Render3DEvent(float partialTicks) implements Event {
}
