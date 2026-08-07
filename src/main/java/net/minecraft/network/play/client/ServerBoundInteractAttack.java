package net.minecraft.network.play.client;

import lombok.Getter;
import net.minecraft.entity.Entity;
import net.minecraft.network.PacketBuffer;
import net.minecraft.network.ViaPacket;
import net.minecraft.network.play.INetHandlerPlayServer;

import java.io.IOException;

@Getter
public class ServerBoundInteractAttack extends ViaPacket {
    private final int entityId;

    public ServerBoundInteractAttack(Entity entity) {
        this.entityId = entity.getEntityId();
    }

    @Override
    public void readPacketData(PacketBuffer buf) throws IOException {
    }

    @Override
    public void writePacketData(PacketBuffer buf) throws IOException {
    }

    @Override
    public void processPacket(INetHandlerPlayServer handler) {
    }
}
