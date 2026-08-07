package cn.unfair.util.via;

import com.viaversion.viaversion.api.connection.UserConnection;
import com.viaversion.viaversion.api.protocol.packet.Direction;
import com.viaversion.viaversion.api.protocol.packet.State;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import com.viaversion.viaversion.api.type.Types;
import com.viaversion.viaversion.protocols.v1_20_2to1_20_3.packet.ServerboundPackets1_20_3;
import com.viaversion.viaversion.protocols.v1_20_3to1_20_5.packet.ServerboundPackets1_20_5;
import com.viaversion.viaversion.protocols.v1_21to1_21_2.packet.ServerboundPackets1_21_2;
import de.florianmichael.vialoadingbase.ViaLoadingBase;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageEncoder;

import java.util.List;

/** Replaces ViaBackwards' placeholder sequence in the final 1.20.5 packet. */
@ChannelHandler.Sharable
public final class ModernSequenceEncodeHandler extends MessageToMessageEncoder<ByteBuf> {

    public static final String NAME = "viaforge-modern-sequence";

    private final UserConnection connection;

    public ModernSequenceEncodeHandler(UserConnection connection) {
        this.connection = connection;
    }

    @Override
    protected void encode(ChannelHandlerContext context, ByteBuf buffer, List<Object> output) {
        patchSequence(connection, buffer);
        output.add(buffer.retain());
    }

    public static void patchSequence(UserConnection connection, ByteBuf buffer) {
        if (ViaLoadingBase.getInstance().getTargetVersion() != ProtocolVersion.v1_20_5
                || connection.getProtocolInfo().getState(Direction.SERVERBOUND) != State.PLAY
                || !buffer.isReadable()) {
            return;
        }

        ByteBuf input = buffer.duplicate();
        final int packetId;
        final int sequenceIndex;
        try {
            packetId = Types.VAR_INT.readPrimitive(input);
            if (packetId == ServerboundPackets1_20_5.USE_ITEM.getId()
                    || packetId == ServerboundPackets1_20_3.USE_ITEM.getId()
                    || packetId == ServerboundPackets1_21_2.USE_ITEM.getId()) {
                Types.VAR_INT.readPrimitive(input);
                sequenceIndex = input.readerIndex();
            } else if (packetId == ServerboundPackets1_20_5.USE_ITEM_ON.getId()
                    || packetId == ServerboundPackets1_20_3.USE_ITEM_ON.getId()
                    || packetId == ServerboundPackets1_21_2.USE_ITEM_ON.getId()) {
                Types.VAR_INT.readPrimitive(input);
                input.skipBytes(Long.BYTES);
                Types.VAR_INT.readPrimitive(input);
                input.skipBytes(Float.BYTES * 3);
                input.skipBytes(1);
                sequenceIndex = input.readerIndex();
            } else if (packetId == ServerboundPackets1_20_5.PLAYER_ACTION.getId()
                    || packetId == ServerboundPackets1_20_3.PLAYER_ACTION.getId()
                    || packetId == ServerboundPackets1_21_2.PLAYER_ACTION.getId()) {
                int action = Types.VAR_INT.readPrimitive(input);
                if (action >= 3) {
                    return;
                }
                input.skipBytes(Long.BYTES);
                input.skipBytes(1);
                sequenceIndex = input.readerIndex();
            } else {
                return;
            }

            Types.VAR_INT.readPrimitive(input);
        } catch (IndexOutOfBoundsException ignored) {
            return;
        }

        int sequenceEnd = input.readerIndex();
        byte[] trailingData = new byte[buffer.writerIndex() - sequenceEnd];
        buffer.getBytes(sequenceEnd, trailingData);

        ModernSequenceStorage storage = connection.get(ModernSequenceStorage.class);
        if (storage == null) {
            storage = new ModernSequenceStorage();
            connection.put(storage);
        }

        buffer.writerIndex(sequenceIndex);
        Types.VAR_INT.writePrimitive(buffer, storage.next());
        buffer.writeBytes(trailingData);
    }
}
