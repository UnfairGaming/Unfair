package cn.unfair.events;

import cn.unfair.event.events.Event;
import net.minecraft.entity.Entity;

public class StuckInBlockEvent implements Event {
    private final Entity entity;

    public StuckInBlockEvent(Entity entity) {
        this.entity = entity;
    }

    public Entity getEntity() {
        return this.entity;
    }
}
