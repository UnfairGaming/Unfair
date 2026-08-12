/*
 * This file is part of ViaMCP - https://github.com/FlorianMichael/ViaMCP
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

package de.florianmichael.viamcp;

import cn.unfair.util.via.BlockStatePredictionHandler;
import cn.unfair.util.via.ModernBlockStateTracker;
import com.mojang.authlib.GameProfile;
import com.viaversion.viabackwards.protocol.v1_11to1_10.Protocol1_11To1_10;
import com.viaversion.viabackwards.protocol.v1_17to1_16_4.Protocol1_17To1_16_4;
import com.viaversion.viabackwards.protocol.v1_20_3to1_20_2.Protocol1_20_3To1_20_2;
import com.viaversion.viarewind.protocol.v1_9to1_8.Protocol1_9To1_8;
import com.viaversion.viaversion.api.Via;
import com.viaversion.viaversion.api.connection.UserConnection;
import com.viaversion.viaversion.api.protocol.packet.PacketWrapper;
import com.viaversion.viaversion.api.protocol.packet.State;
import com.viaversion.viaversion.api.type.Types;
import com.viaversion.viaversion.protocols.base.ServerboundLoginPackets;
import com.viaversion.viaversion.protocols.v1_16_1to1_16_2.packet.ClientboundPackets1_16_2;
import com.viaversion.viaversion.protocols.v1_16_1to1_16_2.packet.ServerboundPackets1_16_2;
import com.viaversion.viaversion.protocols.v1_16_4to1_17.packet.ClientboundPackets1_17;
import com.viaversion.viaversion.protocols.v1_16_4to1_17.packet.ServerboundPackets1_17;
import com.viaversion.viaversion.protocols.v1_20_2to1_20_3.packet.ClientboundPackets1_20_3;
import com.viaversion.viaversion.protocols.v1_8to1_9.packet.ClientboundPackets1_8;
import com.viaversion.viaversion.protocols.v1_8to1_9.packet.ClientboundPackets1_9;
import com.viaversion.viaversion.protocols.v1_9_1to1_9_3.packet.ClientboundPackets1_9_3;
import de.florianmichael.vialoadingbase.ViaLoadingBase;
import de.florianmichael.viamcp.gui.AsyncVersionSlider;
import lombok.Getter;
import net.minecraft.client.Minecraft;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

public class ViaMCP {
    public final static int NATIVE_VERSION = 47;
    public static volatile ViaMCP INSTANCE;
    public UserConnection user;

    @Getter
    public static int sequence;
    public int TransactionCount = 0;

    public static synchronized ViaMCP create() {
        if (INSTANCE == null) {
            INSTANCE = new ViaMCP();
        }

        return INSTANCE;
    }

    private AsyncVersionSlider asyncVersionSlider;

    public ViaMCP() {
        ViaLoadingBase.ViaLoadingBaseBuilder.create().runDirectory(new File("ViaMCP")).nativeVersion(NATIVE_VERSION).onProtocolReload(protocolVersion -> {
            if (getAsyncVersionSlider() != null) {
                getAsyncVersionSlider().setVersion(protocolVersion.getVersion());
            }
        }).build();

        ModernBlockStateTracker.install();

        //1.17 Logging ViaFix
        Protocol1_20_3To1_20_2 protocol1_20_3To1_20_2 = Via.getManager().getProtocolManager().getProtocol(Protocol1_20_3To1_20_2.class);
        protocol1_20_3To1_20_2.registerServerbound(State.LOGIN, ServerboundLoginPackets.LOGIN_ACKNOWLEDGED, packetWrapper -> {
            this.user = packetWrapper.user();
        });
        protocol1_20_3To1_20_2.registerServerbound(State.LOGIN, ServerboundLoginPackets.HELLO, packetWrapper -> {
            packetWrapper.cancel();
            PacketWrapper packetWrapper2 = PacketWrapper.create(ServerboundLoginPackets.HELLO, packetWrapper.user());
            Minecraft mc = Minecraft.getMinecraft();
            GameProfile gameProfile = mc.getSession().getProfile();
            packetWrapper2.write(Types.STRING, gameProfile.getName());
            UUID uUID = gameProfile.getId();
            if (uUID != null) {
                System.out.println("Online Login.");
                packetWrapper2.write(Types.UUID, uUID);
            } else {
                System.out.println("Offline Login");
                packetWrapper2.write(Types.UUID, UUID.nameUUIDFromBytes(gameProfile.getName().getBytes(StandardCharsets.UTF_8)));
            }
            packetWrapper2.sendToServer(Protocol1_20_3To1_20_2.class);
        // 1.20.3+ BLOCK_CHANGED_ACK -> sequence number sync
        protocol1_20_3To1_20_2.registerClientbound(
                State.PLAY,
                ClientboundPackets1_20_3.BLOCK_CHANGED_ACK,
                wrapper -> {
                    sequence = wrapper.read(Types.VAR_INT);
                    if (Minecraft.getMinecraft().theWorld != null && Minecraft.getMinecraft().theWorld.predictionHandler != null) {
                        try (BlockStatePredictionHandler h = Minecraft.getMinecraft().theWorld.predictionHandler) {
                            h.endPredictionsUpTo(sequence, Minecraft.getMinecraft().theWorld);
                        }
                    }
                    wrapper.cancel();
                }
        );
        });

        // Add this line if you implement the transaction fixes into the game code
        fixTransactions();

        Protocol1_9To1_8 protocol1_9To1_8 = Via.getManager().getProtocolManager().getProtocol(Protocol1_9To1_8.class);
        protocol1_9To1_8.registerClientbound(ClientboundPackets1_9.PLAYER_POSITION, ClientboundPackets1_8.PLAYER_POSITION, packetWrapper -> {}, true);
        protocol1_9To1_8.registerClientbound(ClientboundPackets1_9.ENTITY_EVENT, ClientboundPackets1_8.ENTITY_EVENT, packetWrapper -> {

        }, true);

        Protocol1_11To1_10 protocol1_11To1_10 = Via.getManager().getProtocolManager().getProtocol(Protocol1_11To1_10.class);
        protocol1_11To1_10.registerClientbound(
                ClientboundPackets1_9_3.ENTITY_EVENT,
                ClientboundPackets1_9_3.ENTITY_EVENT,
                wrapper -> {
                    int entityId = wrapper.passthrough(Types.INT);
                    byte status = wrapper.passthrough(Types.BYTE);

                    if (status == 35) {
                        wrapper.setPacketType(ClientboundPackets1_9_3.ENTITY_EVENT);
                    }
                },
                true
        );
    }

    private void fixTransactions() {
        // We handle the differences between those versions in the net code, so we can make the Via handlers pass through
        final Protocol1_17To1_16_4 protocol = Via.getManager().getProtocolManager().getProtocol(Protocol1_17To1_16_4.class);
        protocol.registerClientbound(ClientboundPackets1_17.PING, ClientboundPackets1_16_2.CONTAINER_ACK, wrapper -> {
            TransactionCount++;
        }, true);
        protocol.registerServerbound(ServerboundPackets1_16_2.CONTAINER_ACK, ServerboundPackets1_17.PONG, wrapper -> {
        }, true);
    }

    public void initAsyncSlider() {
        this.initAsyncSlider(5, 5, 110, 20);
    }

    public void initAsyncSlider(int x, int y, int width, int height) {
        asyncVersionSlider = new AsyncVersionSlider(-1, x, y, Math.max(width, 110), height);
    }

    public AsyncVersionSlider getAsyncVersionSlider() {
        return asyncVersionSlider;
    }

    public synchronized AsyncVersionSlider getOrCreateAsyncVersionSlider() {
        if (asyncVersionSlider == null) {
            initAsyncSlider();
        }

        return asyncVersionSlider;
    }
}
