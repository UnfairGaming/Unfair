package net.minecraft.network.play.client;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import net.minecraft.network.PacketBuffer;
import net.minecraft.network.ViaPacket;
import net.minecraft.network.play.INetHandlerPlayServer;

import java.io.IOException;

@EqualsAndHashCode(callSuper = true)
@Data
@AllArgsConstructor
public class CPacketConfirmTeleport extends ViaPacket {
    private int teleportId;

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
