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

import com.viaversion.viaversion.api.Via;
import com.viaversion.viaversion.api.connection.UserConnection;
import com.viaversion.viaversion.api.protocol.packet.State;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import com.viaversion.viaversion.api.minecraft.item.Item;
import com.viaversion.viaversion.api.type.Types;
import com.viaversion.viaversion.api.type.types.VarIntType;
import com.viaversion.viaversion.exception.CancelCodecException;
import com.viaversion.viaversion.exception.CancelDecoderException;
import com.viaversion.viaversion.exception.InformativeException;
import com.viaversion.viaversion.protocols.v1_8to1_9.packet.ClientboundPackets1_9;
import cn.unfair.util.via.ModernOffhandStorage;
import de.florianmichael.vialoadingbase.ViaLoadingBase;
import com.viaversion.viaversion.util.PipelineUtil;
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
            if (packetId == ClientboundPackets1_9.CONTAINER_SET_SLOT.getId()) {
                int windowIndex = duplicate.readerIndex();
                byte window = duplicate.readByte();
                short slot = duplicate.readShort();
                if (window == 0 && slot == 45) {
                    input.setByte(windowIndex, ModernOffhandStorage.CLIENT_WINDOW_ID);
                }
                return null;
            }

            if (packetId == ClientboundPackets1_9.CONTAINER_SET_CONTENT.getId()) {
                short window = Types.UNSIGNED_BYTE.read(duplicate);
                Item[] items = Types.ITEM1_8_SHORT_ARRAY.read(duplicate);
                if (window == 0 && items.length == 46) {
                    ByteBuf synthetic = ctx.alloc().buffer();
                    Types.VAR_INT.writePrimitive(synthetic, ClientboundPackets1_9.CONTAINER_SET_SLOT.getId());
                    synthetic.writeByte(ModernOffhandStorage.CLIENT_WINDOW_ID);
                    synthetic.writeShort(45);
                    Types.ITEM1_8.write(synthetic, items[45]);
                    return synthetic;
                }
            }
        } catch (RuntimeException ignored) {
            return null;
        }
        return null;
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
