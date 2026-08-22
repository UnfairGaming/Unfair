package cn.unfair.events;

import cn.unfair.event.events.Event;
import net.minecraft.entity.Entity;

public record StuckInBlockEvent(Entity entity) implements Event {
}
