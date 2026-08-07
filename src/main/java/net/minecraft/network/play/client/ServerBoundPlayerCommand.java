package net.minecraft.network.play.client;

import lombok.Data;
import lombok.EqualsAndHashCode;
import net.minecraft.network.PacketBuffer;
import net.minecraft.network.ViaPacket;
import net.minecraft.network.play.INetHandlerPlayServer;

import java.io.IOException;

@EqualsAndHashCode(callSuper = true)
@Data
public class ServerBoundPlayerCommand extends ViaPacket {
    private int id;
    private Action action;
    private int data;

    public ServerBoundPlayerCommand(int id, Action action) {
        this.id = id;
        this.action = action;
        this.data = 0;
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

    public enum Action {
        PRESS_SHIFT_KEY,
        RELEASE_SHIFT_KEY,
        STOP_SLEEPING,
        START_SPRINTING,
        STOP_SPRINTING,
        START_RIDING_JUMP,
        STOP_RIDING_JUMP,
        OPEN_INVENTORY,
        START_FALL_FLYING;
    }
}
