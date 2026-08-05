package cn.unfair.events;

import cn.unfair.event.events.callables.EventCancellable;
import cn.unfair.event.types.EventType;
import net.minecraft.network.Packet;

public class PacketEvent extends EventCancellable {
    private final EventType type;
    private final Packet<?> packet;

    public PacketEvent(EventType type, Packet<?> packet) {
        this.type = type;
        this.packet = packet;
    }

    public EventType getType() {
        return this.type;
    }

    public Packet<?> getPacket() {
        return this.packet;
    }
}
