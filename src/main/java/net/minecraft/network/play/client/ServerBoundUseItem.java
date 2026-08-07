package net.minecraft.network.play.client;

import lombok.Getter;
import net.minecraft.network.PacketBuffer;
import net.minecraft.network.ViaPacket;
import net.minecraft.network.play.INetHandlerPlayServer;
import net.minecraft.util.EnumHand;

import java.io.IOException;

@Getter
public class ServerBoundUseItem extends ViaPacket {
    EnumHand hand;

    public ServerBoundUseItem(EnumHand hand) {
        this.hand = hand;
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
