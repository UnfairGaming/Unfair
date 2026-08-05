package cn.unfair.events;

import cn.unfair.event.events.Event;
import cn.unfair.event.types.EventType;
import net.minecraft.entity.EntityLivingBase;

public record RenderLivingEvent(EventType type, EntityLivingBase entity) implements Event {
}
