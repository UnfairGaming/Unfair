package net.minecraft.network.play.client;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import net.minecraft.network.PacketBuffer;
import net.minecraft.network.ViaPacket;
import net.minecraft.network.play.INetHandlerPlayServer;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;

import java.io.IOException;

@EqualsAndHashCode(callSuper = true)
@Getter
@Data
public class ServerBoundPlayerAction extends ViaPacket {
    private BlockPos pos;
    private EnumFacing facing;
    private C07PacketPlayerDigging.Action action;

    public ServerBoundPlayerAction(C07PacketPlayerDigging.Action action, BlockPos pos, EnumFacing facing) {
        this.pos = pos;
        this.facing = facing;
        this.action = action;
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
