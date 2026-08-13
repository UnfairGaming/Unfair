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
import com.viaversion.viabackwards.protocol.v1_20_3to1_20_2.Protocol1_20_3To1_20_2;
import com.viaversion.viarewind.protocol.v1_9to1_8.Protocol1_9To1_8;
import com.viaversion.viaversion.api.Via;
import com.viaversion.viaversion.api.connection.UserConnection;
import com.viaversion.viaversion.api.protocol.Protocol;
import com.viaversion.viaversion.api.protocol.packet.PacketWrapper;
import com.viaversion.viaversion.api.protocol.packet.State;
import com.viaversion.viaversion.api.type.Types;
import com.viaversion.viaversion.protocols.base.ServerboundLoginPackets;
import com.viaversion.viaversion.protocols.v1_20_2to1_20_3.packet.ClientboundPackets1_20_3;
import com.viaversion.viaversion.protocols.v1_8to1_9.packet.ClientboundPackets1_9;
import com.viaversion.viaversion.protocols.v1_9_1to1_9_3.packet.ClientboundPackets1_9_3;
import de.florianmichael.vialoadingbase.ViaLoadingBase;
import de.florianmichael.viamcp.gui.AsyncVersionSlider;
import lombok.Getter;
import net.minecraft.client.Minecraft;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.logging.Level;

public class ViaMCP {
    public final static int NATIVE_VERSION = 47;
    public static volatile ViaMCP INSTANCE;
    public UserConnection user;

    @Getter
    public static int sequence;
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

        installProtocolPatches();
    }

    private void installProtocolPatches() {
        afterMappings(Protocol1_20_3To1_20_2.class, () -> {
            Protocol1_20_3To1_20_2 protocol = protocol(Protocol1_20_3To1_20_2.class);
            protocol.registerServerbound(State.LOGIN, ServerboundLoginPackets.LOGIN_ACKNOWLEDGED,
                    wrapper -> this.user = wrapper.user());
            protocol.registerServerbound(State.LOGIN, ServerboundLoginPackets.HELLO, wrapper -> {
                wrapper.cancel();
                PacketWrapper replacement = PacketWrapper.create(ServerboundLoginPackets.HELLO, wrapper.user());
                GameProfile gameProfile = Minecraft.getMinecraft().getSession().getProfile();
                replacement.write(Types.STRING, gameProfile.getName());
                UUID uuid = gameProfile.getId();
                if (uuid != null) {
                    System.out.println("Online Login.");
                    replacement.write(Types.UUID, uuid);
                } else {
                    System.out.println("Offline Login");
                    replacement.write(Types.UUID, UUID.nameUUIDFromBytes(gameProfile.getName().getBytes(StandardCharsets.UTF_8)));
                }
                replacement.sendToServer(Protocol1_20_3To1_20_2.class);
            });
            protocol.appendClientbound(ClientboundPackets1_20_3.BLOCK_CHANGED_ACK, wrapper -> {
                sequence = wrapper.read(Types.VAR_INT);
                if (Minecraft.getMinecraft().theWorld != null && Minecraft.getMinecraft().theWorld.predictionHandler != null) {
                    try (BlockStatePredictionHandler handler = Minecraft.getMinecraft().theWorld.predictionHandler) {
                        handler.endPredictionsUpTo(sequence, Minecraft.getMinecraft().theWorld);
                    }
                }
                wrapper.cancel();
            });
        });

        afterMappings(Protocol1_9To1_8.class, () -> {
            Protocol1_9To1_8 protocol = protocol(Protocol1_9To1_8.class);
            protocol.replaceClientbound(ClientboundPackets1_9.ENTITY_EVENT, wrapper -> {
            });
        });

        afterMappings(Protocol1_11To1_10.class, () -> {
            Protocol1_11To1_10 protocol = protocol(Protocol1_11To1_10.class);
            protocol.replaceClientbound(ClientboundPackets1_9_3.ENTITY_EVENT, wrapper -> {
                wrapper.passthrough(Types.INT);
                byte status = wrapper.passthrough(Types.BYTE);
                if (status == 35) {
                    wrapper.setPacketType(ClientboundPackets1_9_3.ENTITY_EVENT);
                }
            });
        });
    }

    private static <T extends Protocol<?, ?, ?, ?>> T protocol(Class<T> protocolClass) {
        T protocol = Via.getManager().getProtocolManager().getProtocol(protocolClass);
        if (protocol == null) {
            throw new IllegalStateException("Protocol is not registered: " + protocolClass.getName());
        }
        return protocol;
    }

    private static void afterMappings(Class<? extends Protocol<?, ?, ?, ?>> protocolClass, Runnable patch) {
        Via.getManager().getProtocolManager().getMappingLoaderFuture(protocolClass).whenComplete((ignored, throwable) -> {
            if (throwable != null) {
                ViaLoadingBase.LOGGER.log(Level.SEVERE, "Unable to load mappings before patching " + protocolClass.getSimpleName(), throwable);
                return;
            }
            try {
                patch.run();
            } catch (RuntimeException exception) {
                ViaLoadingBase.LOGGER.log(Level.SEVERE, "Unable to patch " + protocolClass.getSimpleName(), exception);
            }
        });
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
