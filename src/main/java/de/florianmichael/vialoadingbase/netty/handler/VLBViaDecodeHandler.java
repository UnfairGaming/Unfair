/*
 * This file is part of ViaLoadingBase - https://github.com/FlorianMichael/ViaLoadingBase
 * Copyright (C) 2020-2024 FlorianMichael/EnZaXD <florian.michael07@gmail.com> and contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package de.florianmichael.vialoadingbase.netty.handler;

import cn.unfair.util.via.ModernOffhandStorage;
import com.viaversion.viaversion.api.Via;
import com.viaversion.viaversion.api.connection.UserConnection;
import com.viaversion.viaversion.api.minecraft.item.Item;
import com.viaversion.viaversion.api.protocol.packet.State;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import com.viaversion.viaversion.api.type.Type;
import com.viaversion.viaversion.api.type.Types;
import com.viaversion.viaversion.api.type.types.VarIntType;
import com.viaversion.viaversion.exception.CancelCodecException;
import com.viaversion.viaversion.exception.CancelDecoderException;
import com.viaversion.viaversion.exception.InformativeException;
import com.viaversion.viaversion.protocols.v1_8to1_9.packet.ClientboundPackets1_9;
import com.viaversion.viaversion.util.PipelineUtil;
import de.florianmichael.vialoadingbase.ViaLoadingBase;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageDecoder;

import java.util.List;

@ChannelHandler.Sharable
public class VLBViaDecodeHandler extends MessageToMessageDecoder<ByteBuf> {
    private final UserConnection user;
    public static int stateId;

    public VLBViaDecodeHandler(UserConnection user) {
        this.user = user;
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf bytebuf, List<Object> out) {
        if (!user.shouldTransformPacket()) {
            out.add(bytebuf.retain());
            return;
        }

        ByteBuf transformedBuf = ctx.alloc().buffer().writeBytes(bytebuf);
        ByteBuf byteBuf3 = transformedBuf.copy();
        ByteBuf syntheticOffhandSlot = null;
        try {
            syntheticOffhandSlot = captureModernOffhandSlot(ctx, transformedBuf);
            user.transformIncoming(transformedBuf, CancelDecoderException::generate);
            if (syntheticOffhandSlot != null) {
                user.transformIncoming(syntheticOffhandSlot, CancelDecoderException::generate);
                out.add(syntheticOffhandSlot.retain());
            }
            int n = new VarIntType().readPrimitive(byteBuf3);
            if (n == 20 || n == 22) {
                short s = byteBuf3.readUnsignedByte();
                stateId = new VarIntType().readPrimitive(byteBuf3);
            }
            out.add(transformedBuf.retain());
        } finally {
            transformedBuf.release();
            byteBuf3.release();
            if (syntheticOffhandSlot != null) {
                syntheticOffhandSlot.release();
            }
        }
    }

    private ByteBuf captureModernOffhandSlot(ChannelHandlerContext ctx, ByteBuf input) {
        if (ViaLoadingBase.getInstance().getTargetVersion().olderThan(ProtocolVersion.v1_9)
                || user.getProtocolInfo().getServerState() != State.PLAY
                || !input.isReadable()) {
            return null;
        }

        ByteBuf duplicate = input.duplicate();
        try {
            int packetId = Types.VAR_INT.readPrimitive(duplicate);
            ProtocolVersion version = ViaLoadingBase.getInstance().getTargetVersion();
            if (packetId == getSetSlotPacketId(version)) {
                int windowIndex = duplicate.readerIndex();
                int window = version.newerThanOrEqualTo(ProtocolVersion.v1_21_2)
                        ? Types.VAR_INT.readPrimitive(duplicate)
                        : duplicate.readByte();
                int stateId = 0;
                if (version.newerThanOrEqualTo(ProtocolVersion.v1_17)) {
                    stateId = Types.VAR_INT.readPrimitive(duplicate);
                }
                short slot = duplicate.readShort();
                if (window == 0 && slot == 45) {
                    if (version.newerThanOrEqualTo(ProtocolVersion.v1_21_2)) {
                        Item item = getItemType(version).read(duplicate);
                        input.setByte(windowIndex, 127);
                        ByteBuf synthetic = ctx.alloc().buffer();
                        Types.VAR_INT.writePrimitive(synthetic, getSetSlotPacketId(version));
                        writeContainerWindowId(synthetic, version, ModernOffhandStorage.CLIENT_WINDOW_ID);
                        Types.VAR_INT.writePrimitive(synthetic, stateId);
                        synthetic.writeShort(45);
                        getItemType(version).write(synthetic, item);
                        return synthetic;
                    }
                    input.setByte(windowIndex, ModernOffhandStorage.CLIENT_WINDOW_ID);
                }
                return null;
            }

            if (packetId == getSetContentPacketId(version)) {
                int window = readContainerWindowId(duplicate, version);
                int stateId = 0;
                if (version.newerThanOrEqualTo(ProtocolVersion.v1_17)) {
                    stateId = Types.VAR_INT.readPrimitive(duplicate);
                }
                Type<Item[]> itemArrayType = getItemArrayType(version);
                Item[] items = itemArrayType.read(duplicate);
                if (window == 0 && items.length == 46) {
                    ByteBuf synthetic = ctx.alloc().buffer();
                    Types.VAR_INT.writePrimitive(synthetic, getSetSlotPacketId(version));
                    writeContainerWindowId(synthetic, version, ModernOffhandStorage.CLIENT_WINDOW_ID);
                    if (version.newerThanOrEqualTo(ProtocolVersion.v1_17)) {
                        Types.VAR_INT.writePrimitive(synthetic, stateId);
                    }
                    synthetic.writeShort(45);
                    getItemType(version).write(synthetic, items[45]);
                    return synthetic;
                }
            }
        } catch (RuntimeException ignored) {
            return null;
        }
        return null;
    }

    private static int getSetContentPacketId(ProtocolVersion version) {
        if (version.newerThanOrEqualTo(ProtocolVersion.v1_21_4)) {
            return 18;
        }
        if (version.newerThanOrEqualTo(ProtocolVersion.v1_18_2)) {
            return 19;
        }
        if (version.newerThanOrEqualTo(ProtocolVersion.v1_17)) {
            return 20;
        }
        if (version.newerThanOrEqualTo(ProtocolVersion.v1_16)) {
            return 20;
        }
        if (version.newerThanOrEqualTo(ProtocolVersion.v1_15)) {
            return 21;
        }
        if (version.newerThanOrEqualTo(ProtocolVersion.v1_14)) {
            return 20;
        }
        if (version.newerThanOrEqualTo(ProtocolVersion.v1_13)) {
            return 21;
        }
        return ClientboundPackets1_9.CONTAINER_SET_CONTENT.getId();
    }

    private static int getSetSlotPacketId(ProtocolVersion version) {
        if (version.newerThanOrEqualTo(ProtocolVersion.v1_21_4)) {
            return 20;
        }
        if (version.newerThanOrEqualTo(ProtocolVersion.v1_18_2)) {
            return 21;
        }
        if (version.newerThanOrEqualTo(ProtocolVersion.v1_17)) {
            return 22;
        }
        if (version.newerThanOrEqualTo(ProtocolVersion.v1_16)) {
            return 22;
        }
        if (version.newerThanOrEqualTo(ProtocolVersion.v1_15)) {
            return 23;
        }
        if (version.newerThanOrEqualTo(ProtocolVersion.v1_14)) {
            return 22;
        }
        if (version.newerThanOrEqualTo(ProtocolVersion.v1_13)) {
            return 23;
        }
        return ClientboundPackets1_9.CONTAINER_SET_SLOT.getId();
    }

    private static int readContainerWindowId(ByteBuf input, ProtocolVersion version) {
        if (version.newerThanOrEqualTo(ProtocolVersion.v1_21_2)) {
            return Types.VAR_INT.readPrimitive(input);
        }
        return Types.UNSIGNED_BYTE.read(input);
    }

    private static void writeContainerWindowId(ByteBuf output, ProtocolVersion version, int windowId) {
        if (version.newerThanOrEqualTo(ProtocolVersion.v1_21_2)) {
            Types.VAR_INT.writePrimitive(output, windowId);
        } else {
            output.writeByte(windowId);
        }
    }

    @SuppressWarnings("unchecked")
    private static Type<Item[]> getItemArrayType(ProtocolVersion version) {
        if (version.newerThanOrEqualTo(ProtocolVersion.v1_20_2)) {
            return Types.ITEM1_20_2_ARRAY;
        }
        if (version.newerThanOrEqualTo(ProtocolVersion.v1_13_2)) {
            return Types.ITEM1_13_2_ARRAY;
        }
        if (version.newerThanOrEqualTo(ProtocolVersion.v1_13)) {
            return Types.ITEM1_13_ARRAY;
        }
        return Types.ITEM1_8_SHORT_ARRAY;
    }

    @SuppressWarnings("unchecked")
    private static Type<Item> getItemType(ProtocolVersion version) {
        if (version.newerThanOrEqualTo(ProtocolVersion.v1_20_2)) {
            return Types.ITEM1_20_2;
        }
        if (version.newerThanOrEqualTo(ProtocolVersion.v1_13_2)) {
            return Types.ITEM1_13_2;
        }
        if (version.newerThanOrEqualTo(ProtocolVersion.v1_13)) {
            return Types.ITEM1_13;
        }
        return Types.ITEM1_8;
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        if (PipelineUtil.containsCause(cause, CancelCodecException.class)) return;

        if ((PipelineUtil.containsCause(cause, InformativeException.class)
                && user.getProtocolInfo().getServerState() != State.HANDSHAKE)
                || Via.getManager().debugHandler().enabled()) {
            cause.printStackTrace();
        }
    }
}
