package cn.unfair.events;

import cn.unfair.event.events.Event;

public record ChatGUIEvent(int mouseX, int mouseY, float partialTicks) implements Event {
}
