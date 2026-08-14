package cn.unfair.util.via;

import com.google.common.collect.Maps;
import com.viaversion.nbt.io.NBTIO;
import com.viaversion.nbt.limiter.TagLimiter;
import com.viaversion.nbt.tag.CompoundTag;
import com.viaversion.viabackwards.protocol.v1_13to1_12_2.Protocol1_13To1_12_2;
import com.viaversion.viarewind.protocol.v1_9to1_8.Protocol1_9To1_8;
import com.viaversion.viabackwards.protocol.v1_11to1_10.Protocol1_11To1_10;
import com.viaversion.viabackwards.protocol.v1_14to1_13_2.Protocol1_14To1_13_2;
import com.viaversion.viabackwards.protocol.v1_15to1_14_4.Protocol1_15To1_14_4;
import com.viaversion.viabackwards.protocol.v1_16to1_15_2.Protocol1_16To1_15_2;
import com.viaversion.viabackwards.protocol.v1_17to1_16_4.Protocol1_17To1_16_4;
import com.viaversion.viabackwards.protocol.v1_19to1_18_2.Protocol1_19To1_18_2;
import com.viaversion.viabackwards.protocol.v1_20to1_19_4.Protocol1_20To1_19_4;
import com.viaversion.viaversion.api.Via;
import com.viaversion.viaversion.api.connection.UserConnection;
import com.viaversion.viaversion.api.data.MappingData;
import com.viaversion.viaversion.api.data.entity.EntityTracker;
import com.viaversion.viaversion.api.minecraft.BlockChangeRecord;
import com.viaversion.viaversion.api.minecraft.BlockPosition;
import com.viaversion.viaversion.api.minecraft.ClientWorld;
import com.viaversion.viaversion.api.minecraft.chunks.ChunkSection;
import com.viaversion.viaversion.api.minecraft.chunks.DataPalette;
import com.viaversion.viaversion.api.minecraft.chunks.PaletteType;
import com.viaversion.viaversion.api.protocol.AbstractProtocol;
import com.viaversion.viaversion.api.protocol.packet.ClientboundPacketType;
import com.viaversion.viaversion.api.protocol.packet.Direction;
import com.viaversion.viaversion.api.protocol.Protocol;
import com.viaversion.viaversion.api.protocol.packet.mapping.PacketMapping;
import com.viaversion.viaversion.api.protocol.packet.mapping.PacketMappings;
import com.viaversion.viaversion.api.protocol.remapper.PacketHandler;
import com.viaversion.viaversion.api.type.Types;
import com.viaversion.viaversion.api.type.types.chunk.ChunkType1_14;
import com.viaversion.viaversion.api.type.types.chunk.ChunkType1_13;
import com.viaversion.viaversion.api.type.types.chunk.ChunkType1_9_1;
import com.viaversion.viaversion.api.type.types.chunk.ChunkType1_9_3;
import com.viaversion.viaversion.api.type.types.chunk.ChunkType1_15;
import com.viaversion.viaversion.api.type.types.chunk.ChunkType1_16;
import com.viaversion.viaversion.api.type.types.chunk.ChunkType1_17;
import com.viaversion.viaversion.api.type.types.chunk.ChunkType1_18;
import com.viaversion.viaversion.protocols.v1_12_2to1_13.data.BlockStates1_13;
import com.viaversion.viaversion.protocols.v1_12_2to1_13.packet.ClientboundPackets1_13;
import com.viaversion.viaversion.protocols.v1_8to1_9.packet.ClientboundPackets1_9;
import com.viaversion.viaversion.protocols.v1_9_1to1_9_3.packet.ClientboundPackets1_9_3;
import com.viaversion.viaversion.protocols.v1_13_2to1_14.packet.ClientboundPackets1_14;
import com.viaversion.viaversion.protocols.v1_14_4to1_15.packet.ClientboundPackets1_15;
import com.viaversion.viaversion.protocols.v1_15_2to1_16.packet.ClientboundPackets1_16;
import com.viaversion.viaversion.protocols.v1_16_4to1_17.packet.ClientboundPackets1_17;
import com.viaversion.viaversion.protocols.v1_18_2to1_19.packet.ClientboundPackets1_19;
import com.viaversion.viaversion.protocols.v1_19_3to1_19_4.packet.ClientboundPackets1_19_4;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import de.florianmichael.vialoadingbase.ViaLoadingBase;
import net.minecraft.block.Block;
import net.minecraft.block.ModernBlock;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.util.BlockPos;
import net.minecraft.world.chunk.Chunk;

import java.lang.reflect.Field;
import java.io.DataInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentMap;
import java.util.logging.Level;

/** Preserves selected modern block states before ViaBackwards replaces them. */
public final class ModernBlockStateTracker {
    private static final ConcurrentMap<Long, ConcurrentMap<BlockPos, ModernState>> CHUNKS = Maps.newConcurrentMap();
    private static final ConcurrentMap<Long, ConcurrentMap<Integer, IBlockState[]>> EXTENDED_SECTIONS = Maps.newConcurrentMap();
    private static final ConcurrentMap<Long, ConcurrentMap<BlockPos, NativeState>> NATIVE_STATES = Maps.newConcurrentMap();
    private static final ConcurrentMap<Long, ConcurrentMap<BlockPos, IBlockState>> PREDICTED_STATES = Maps.newConcurrentMap();
    private static List<ModernBlock> modernBlocks = Collections.emptyList();
    private static List<String> blockStates1_13 = Collections.emptyList();
    private static boolean installationScheduled;
    private static boolean installed;

    private ModernBlockStateTracker() {
    }

    public static synchronized void install() {
        if (installationScheduled || installed) {
            return;
        }

        Protocol1_13To1_12_2 protocol13 = Via.getManager().getProtocolManager().getProtocol(Protocol1_13To1_12_2.class);
        Protocol1_9To1_8 protocol9 = Via.getManager().getProtocolManager().getProtocol(Protocol1_9To1_8.class);
        Protocol1_11To1_10 protocol11 = Via.getManager().getProtocolManager().getProtocol(Protocol1_11To1_10.class);
        Protocol1_14To1_13_2 protocol14 = Via.getManager().getProtocolManager().getProtocol(Protocol1_14To1_13_2.class);
        Protocol1_15To1_14_4 protocol15 = Via.getManager().getProtocolManager().getProtocol(Protocol1_15To1_14_4.class);
        Protocol1_16To1_15_2 protocol16 = Via.getManager().getProtocolManager().getProtocol(Protocol1_16To1_15_2.class);
        Protocol1_17To1_16_4 protocol17 = Via.getManager().getProtocolManager().getProtocol(Protocol1_17To1_16_4.class);
        Protocol1_19To1_18_2 protocol19 = Via.getManager().getProtocolManager().getProtocol(Protocol1_19To1_18_2.class);
        Protocol1_20To1_19_4 protocol20 = Via.getManager().getProtocolManager().getProtocol(Protocol1_20To1_19_4.class);
        if (protocol13 == null || protocol14 == null) {
            return;
        }
        if (protocol9 != null) {
            Via.getManager().getProtocolManager().getMappingLoaderFuture(Protocol1_9To1_8.class).whenComplete((ignored, throwable) -> finishLayer(throwable, () -> install1_9(protocol9)));
        }
        if (protocol11 != null) {
            Via.getManager().getProtocolManager().getMappingLoaderFuture(Protocol1_11To1_10.class).whenComplete((ignored, throwable) -> finishLayer(throwable, () -> install1_11(protocol11)));
        }

        installationScheduled = true;
        discoverModernBlocks();
        loadBlockStates1_13();
        Via.getManager().getProtocolManager().getMappingLoaderFuture(Protocol1_13To1_12_2.class)
                .whenComplete((ignored, throwable) -> finishLayer(throwable, () -> install1_13(protocol13)));
        Via.getManager().getProtocolManager().getMappingLoaderFuture(Protocol1_14To1_13_2.class)
                .whenComplete((ignored, throwable) -> finishLayer(throwable, () -> install1_14(protocol14)));
        if (protocol15 != null) {
            Via.getManager().getProtocolManager().getMappingLoaderFuture(Protocol1_15To1_14_4.class)
                    .whenComplete((ignored, throwable) -> finishLayer(throwable, () -> install1_15(protocol15)));
        }
        if (protocol16 != null) {
            Via.getManager().getProtocolManager().getMappingLoaderFuture(Protocol1_16To1_15_2.class)
                    .whenComplete((ignored, throwable) -> finishLayer(throwable, () -> install1_16(protocol16)));
        }
        if (protocol17 != null) {
            Via.getManager().getProtocolManager().getMappingLoaderFuture(Protocol1_17To1_16_4.class)
                    .whenComplete((ignored, throwable) -> finishLayer(throwable, () -> install1_17(protocol17)));
        }
        if (protocol19 != null) Via.getManager().getProtocolManager().getMappingLoaderFuture(Protocol1_19To1_18_2.class).whenComplete((ignored, throwable) -> finishLayer(throwable, () -> install1_19(protocol19)));
        if (protocol20 != null) Via.getManager().getProtocolManager().getMappingLoaderFuture(Protocol1_20To1_19_4.class).whenComplete((ignored, throwable) -> finishLayer(throwable, () -> install1_20(protocol20)));
    }

    private static void install1_13(Protocol1_13To1_12_2 protocol) {
        prepend(protocol, ClientboundPackets1_13.LEVEL_CHUNK, wrapper -> {
            ClientWorld clientWorld = wrapper.user().getClientWorld(Protocol1_13To1_12_2.class);
            ChunkType1_13 type = ChunkType1_13.forEnvironment(clientWorld.getEnvironment());
            com.viaversion.viaversion.api.minecraft.chunks.Chunk chunk = wrapper.read(type);
            captureNativeChunk(chunk);
            wrapper.write(type, chunk);
            wrapper.resetReader();
        });
        prepend(protocol, ClientboundPackets1_13.BLOCK_UPDATE, wrapper -> {
            BlockPosition pos = wrapper.read(Types.BLOCK_POSITION1_8);
            int stateId = wrapper.read(Types.VAR_INT);
            captureNative(pos.x(), pos.y(), pos.z(), stateId);
            wrapper.write(Types.BLOCK_POSITION1_8, pos);
            wrapper.write(Types.VAR_INT, stateId);
            wrapper.resetReader();
        });
        prepend(protocol, ClientboundPackets1_13.CHUNK_BLOCKS_UPDATE, wrapper -> {
            int chunkX = wrapper.read(Types.INT);
            int chunkZ = wrapper.read(Types.INT);
            BlockChangeRecord[] records = wrapper.read(Types.BLOCK_CHANGE_ARRAY);
            for (BlockChangeRecord record : records) {
                captureNative((chunkX << 4) + record.getSectionX(), record.getY(),
                        (chunkZ << 4) + record.getSectionZ(), record.getBlockId());
            }
            wrapper.write(Types.INT, chunkX);
            wrapper.write(Types.INT, chunkZ);
            wrapper.write(Types.BLOCK_CHANGE_ARRAY, records);
            wrapper.resetReader();
        });
    }

    private static void install1_9(Protocol1_9To1_8 protocol) {
        prepend(protocol, ClientboundPackets1_9.LEVEL_CHUNK, wrapper -> { ChunkType1_9_1 type = ChunkType1_9_1.forEnvironment(wrapper.user().getClientWorld(Protocol1_9To1_8.class).getEnvironment()); com.viaversion.viaversion.api.minecraft.chunks.Chunk chunk = wrapper.read(type); captureChunk(chunk, 0, ProtocolVersion.v1_9); wrapper.write(type, chunk); wrapper.resetReader(); });
        prepend(protocol, ClientboundPackets1_9.BLOCK_UPDATE, wrapper -> { BlockPosition pos = wrapper.read(Types.BLOCK_POSITION1_8); int id = wrapper.read(Types.VAR_INT); capture(pos.x(), pos.y(), pos.z(), id, ProtocolVersion.v1_9); wrapper.write(Types.BLOCK_POSITION1_8, pos); wrapper.write(Types.VAR_INT, id); wrapper.resetReader(); });
        prepend(protocol, ClientboundPackets1_9.CHUNK_BLOCKS_UPDATE, wrapper -> { int x = wrapper.read(Types.INT), z = wrapper.read(Types.INT); BlockChangeRecord[] records = wrapper.read(Types.BLOCK_CHANGE_ARRAY); for (BlockChangeRecord record : records) capture((x << 4) + record.getSectionX(), record.getY(), (z << 4) + record.getSectionZ(), record.getBlockId(), ProtocolVersion.v1_9); wrapper.write(Types.INT, x); wrapper.write(Types.INT, z); wrapper.write(Types.BLOCK_CHANGE_ARRAY, records); wrapper.resetReader(); });
    }

    private static void install1_11(Protocol1_11To1_10 protocol) {
        prepend(protocol, ClientboundPackets1_9_3.LEVEL_CHUNK, wrapper -> { ChunkType1_9_3 type = ChunkType1_9_3.forEnvironment(wrapper.user().getClientWorld(Protocol1_11To1_10.class).getEnvironment()); com.viaversion.viaversion.api.minecraft.chunks.Chunk chunk = wrapper.read(type); captureChunk(chunk, 0, ProtocolVersion.v1_11); wrapper.write(type, chunk); wrapper.resetReader(); });
        prepend(protocol, ClientboundPackets1_9_3.BLOCK_UPDATE, wrapper -> { BlockPosition pos = wrapper.read(Types.BLOCK_POSITION1_8); int id = wrapper.read(Types.VAR_INT); capture(pos.x(), pos.y(), pos.z(), id, ProtocolVersion.v1_11); wrapper.write(Types.BLOCK_POSITION1_8, pos); wrapper.write(Types.VAR_INT, id); wrapper.resetReader(); });
        prepend(protocol, ClientboundPackets1_9_3.CHUNK_BLOCKS_UPDATE, wrapper -> { int x = wrapper.read(Types.INT), z = wrapper.read(Types.INT); BlockChangeRecord[] records = wrapper.read(Types.BLOCK_CHANGE_ARRAY); for (BlockChangeRecord record : records) capture((x << 4) + record.getSectionX(), record.getY(), (z << 4) + record.getSectionZ(), record.getBlockId(), ProtocolVersion.v1_11); wrapper.write(Types.INT, x); wrapper.write(Types.INT, z); wrapper.write(Types.BLOCK_CHANGE_ARRAY, records); wrapper.resetReader(); });
    }

    private static synchronized void finishLayer(Throwable throwable, Runnable installer) {
        if (throwable != null) {
            ViaLoadingBase.LOGGER.log(Level.SEVERE, "Unable to load mappings before installing modern block tracking", throwable);
            return;
        }
        try {
            installer.run();
            installed = true;
        } catch (RuntimeException exception) {
            ViaLoadingBase.LOGGER.log(Level.SEVERE, "Unable to install modern block state tracking", exception);
        }
    }

    private static void install1_14(Protocol1_14To1_13_2 protocol) {
        prepend(protocol, ClientboundPackets1_14.LEVEL_CHUNK, wrapper -> {
            com.viaversion.viaversion.api.minecraft.chunks.Chunk chunk = wrapper.read(ChunkType1_14.TYPE);
            captureChunk(chunk, 0, ProtocolVersion.v1_14);
            wrapper.write(ChunkType1_14.TYPE, chunk);
            wrapper.resetReader();
        });
        prepend(protocol, ClientboundPackets1_14.BLOCK_UPDATE, wrapper -> {
            BlockPosition pos = wrapper.read(Types.BLOCK_POSITION1_14);
            int stateId = wrapper.read(Types.VAR_INT);
            capture(pos.x(), pos.y(), pos.z(), stateId, ProtocolVersion.v1_14);
            wrapper.write(Types.BLOCK_POSITION1_14, pos);
            wrapper.write(Types.VAR_INT, stateId);
            wrapper.resetReader();
        });
        prepend(protocol, ClientboundPackets1_14.CHUNK_BLOCKS_UPDATE, wrapper -> {
            int chunkX = wrapper.read(Types.INT);
            int chunkZ = wrapper.read(Types.INT);
            BlockChangeRecord[] records = wrapper.read(Types.BLOCK_CHANGE_ARRAY);
            for (BlockChangeRecord record : records) {
                capture((chunkX << 4) + record.getSectionX(), record.getY(),
                        (chunkZ << 4) + record.getSectionZ(), record.getBlockId(), ProtocolVersion.v1_14);
            }
            wrapper.write(Types.INT, chunkX);
            wrapper.write(Types.INT, chunkZ);
            wrapper.write(Types.BLOCK_CHANGE_ARRAY, records);
            wrapper.resetReader();
        });
    }

    private static void install1_15(Protocol1_15To1_14_4 protocol) {
        prepend(protocol, ClientboundPackets1_15.LEVEL_CHUNK, wrapper -> {
            com.viaversion.viaversion.api.minecraft.chunks.Chunk chunk = wrapper.read(ChunkType1_15.TYPE);
            captureChunk(chunk, 0, ProtocolVersion.v1_15);
            wrapper.write(ChunkType1_15.TYPE, chunk);
            wrapper.resetReader();
        });
        prepend(protocol, ClientboundPackets1_15.BLOCK_UPDATE, wrapper -> {
            BlockPosition pos = wrapper.read(Types.BLOCK_POSITION1_14);
            int stateId = wrapper.read(Types.VAR_INT);
            capture(pos.x(), pos.y(), pos.z(), stateId, ProtocolVersion.v1_15);
            wrapper.write(Types.BLOCK_POSITION1_14, pos);
            wrapper.write(Types.VAR_INT, stateId);
            wrapper.resetReader();
        });
        prepend(protocol, ClientboundPackets1_15.CHUNK_BLOCKS_UPDATE, wrapper -> {
            int chunkX = wrapper.read(Types.INT);
            int chunkZ = wrapper.read(Types.INT);
            BlockChangeRecord[] records = wrapper.read(Types.BLOCK_CHANGE_ARRAY);
            for (BlockChangeRecord record : records) {
                capture((chunkX << 4) + record.getSectionX(), record.getY(),
                        (chunkZ << 4) + record.getSectionZ(), record.getBlockId(), ProtocolVersion.v1_15);
            }
            wrapper.write(Types.INT, chunkX);
            wrapper.write(Types.INT, chunkZ);
            wrapper.write(Types.BLOCK_CHANGE_ARRAY, records);
            wrapper.resetReader();
        });
    }

    private static void install1_17(Protocol1_17To1_16_4 protocol) {
        prepend(protocol, ClientboundPackets1_17.LEVEL_CHUNK, wrapper -> {
            EntityTracker tracker = wrapper.user().getEntityTracker(Protocol1_17To1_16_4.class);
            ChunkType1_17 type = new ChunkType1_17(tracker.currentWorldSectionHeight());
            com.viaversion.viaversion.api.minecraft.chunks.Chunk chunk = wrapper.read(type);
            ModernWorldHeight.configure(tracker.currentMinY(), tracker.currentWorldSectionHeight());
            captureExtendedChunk(chunk, tracker.currentMinY() >> 4, wrapper.user());
            captureChunk(chunk, tracker.currentMinY() >> 4, ProtocolVersion.v1_17);
            wrapper.write(type, chunk);
            wrapper.resetReader();
        });
        prepend(protocol, ClientboundPackets1_17.BLOCK_UPDATE, wrapper -> {
            BlockPosition pos = wrapper.read(Types.BLOCK_POSITION1_14);
            int stateId = wrapper.read(Types.VAR_INT);
            captureExtended(pos.x(), pos.y(), pos.z(), stateId, wrapper.user());
            capture(pos.x(), pos.y(), pos.z(), stateId, ProtocolVersion.v1_17);
            wrapper.write(Types.BLOCK_POSITION1_14, pos);
            wrapper.write(Types.VAR_INT, stateId);
            wrapper.resetReader();
        });
        prepend(protocol, ClientboundPackets1_17.SECTION_BLOCKS_UPDATE, wrapper -> {
            long section = wrapper.read(Types.LONG);
            boolean suppressLightUpdates = wrapper.read(Types.BOOLEAN);
            BlockChangeRecord[] records = wrapper.read(Types.VAR_LONG_BLOCK_CHANGE_ARRAY);
            int sectionX = (int) (section >> 42);
            int sectionY = (int) (section << 44 >> 44);
            int sectionZ = (int) (section << 22 >> 42);
            for (BlockChangeRecord record : records) {
                captureExtended((sectionX << 4) + record.getSectionX(), (sectionY << 4) + record.getSectionY(),
                        (sectionZ << 4) + record.getSectionZ(), record.getBlockId(), wrapper.user());
                capture((sectionX << 4) + record.getSectionX(), (sectionY << 4) + record.getSectionY(),
                        (sectionZ << 4) + record.getSectionZ(), record.getBlockId(), ProtocolVersion.v1_17);
            }
            wrapper.write(Types.LONG, section);
            wrapper.write(Types.BOOLEAN, suppressLightUpdates);
            wrapper.write(Types.VAR_LONG_BLOCK_CHANGE_ARRAY, records);
            wrapper.resetReader();
        });
    }

    private static void install1_16(Protocol1_16To1_15_2 protocol) {
        prepend(protocol, ClientboundPackets1_16.LEVEL_CHUNK, wrapper -> {
            com.viaversion.viaversion.api.minecraft.chunks.Chunk chunk = wrapper.read(ChunkType1_16.TYPE);
            captureChunk(chunk, 0, ProtocolVersion.v1_16);
            wrapper.write(ChunkType1_16.TYPE, chunk);
            wrapper.resetReader();
        });
        prepend(protocol, ClientboundPackets1_16.BLOCK_UPDATE, wrapper -> {
            BlockPosition pos = wrapper.read(Types.BLOCK_POSITION1_14);
            int stateId = wrapper.read(Types.VAR_INT);
            capture(pos.x(), pos.y(), pos.z(), stateId, ProtocolVersion.v1_16);
            wrapper.write(Types.BLOCK_POSITION1_14, pos);
            wrapper.write(Types.VAR_INT, stateId);
            wrapper.resetReader();
        });
        prepend(protocol, ClientboundPackets1_16.CHUNK_BLOCKS_UPDATE, wrapper -> {
            int chunkX = wrapper.read(Types.INT);
            int chunkZ = wrapper.read(Types.INT);
            BlockChangeRecord[] records = wrapper.read(Types.BLOCK_CHANGE_ARRAY);
            for (BlockChangeRecord record : records) {
                capture((chunkX << 4) + record.getSectionX(), record.getY(),
                        (chunkZ << 4) + record.getSectionZ(), record.getBlockId(), ProtocolVersion.v1_16);
            }
            wrapper.write(Types.INT, chunkX);
            wrapper.write(Types.INT, chunkZ);
            wrapper.write(Types.BLOCK_CHANGE_ARRAY, records);
            wrapper.resetReader();
        });
    }

    private static ChunkType1_18 chunkType1_18(EntityTracker tracker) { return new ChunkType1_18(tracker.currentWorldSectionHeight(), tracker.currentMinY(), tracker.biomesSent()); }
    private static void install1_19(Protocol1_19To1_18_2 protocol) {
        prepend(protocol, ClientboundPackets1_19.LEVEL_CHUNK_WITH_LIGHT, wrapper -> { EntityTracker t=wrapper.user().getEntityTracker(Protocol1_19To1_18_2.class); ChunkType1_18 type=chunkType1_18(t); com.viaversion.viaversion.api.minecraft.chunks.Chunk c=wrapper.read(type); captureChunk(c,t.currentMinY()>>4,ProtocolVersion.v1_19); wrapper.write(type,c); wrapper.resetReader(); });
        prepend(protocol, ClientboundPackets1_19.BLOCK_UPDATE, wrapper -> captureModernBlockUpdate(wrapper, ProtocolVersion.v1_19));
        prepend(protocol, ClientboundPackets1_19.SECTION_BLOCKS_UPDATE,
                wrapper -> captureModernSectionUpdate(wrapper, ProtocolVersion.v1_19, true));
    }
    private static void install1_20(Protocol1_20To1_19_4 protocol) {
        prepend(protocol, ClientboundPackets1_19_4.LEVEL_CHUNK_WITH_LIGHT, wrapper -> { EntityTracker t=wrapper.user().getEntityTracker(Protocol1_20To1_19_4.class); ChunkType1_18 type=chunkType1_18(t); com.viaversion.viaversion.api.minecraft.chunks.Chunk c=wrapper.read(type); captureChunk(c,t.currentMinY()>>4,ProtocolVersion.v1_20); wrapper.write(type,c); wrapper.resetReader(); });
        prepend(protocol, ClientboundPackets1_19_4.BLOCK_UPDATE, wrapper -> captureModernBlockUpdate(wrapper, ProtocolVersion.v1_20));
        prepend(protocol, ClientboundPackets1_19_4.SECTION_BLOCKS_UPDATE,
                wrapper -> captureModernSectionUpdate(wrapper, ProtocolVersion.v1_20, false));
    }
    private static void captureModernBlockUpdate(com.viaversion.viaversion.api.protocol.packet.PacketWrapper wrapper, ProtocolVersion version) { try { BlockPosition p=wrapper.read(Types.BLOCK_POSITION1_14);int id=wrapper.read(Types.VAR_INT);capture(p.x(),p.y(),p.z(),id,version);wrapper.write(Types.BLOCK_POSITION1_14,p);wrapper.write(Types.VAR_INT,id);wrapper.resetReader(); } catch (Exception e) { throw new IllegalStateException(e); } }
    private static void captureModernSectionUpdate(
            com.viaversion.viaversion.api.protocol.packet.PacketWrapper wrapper,
            ProtocolVersion version,
            boolean hasSuppressLightUpdates) {
        try {
            long section = wrapper.read(Types.LONG);
            Boolean suppressLightUpdates = hasSuppressLightUpdates ? wrapper.read(Types.BOOLEAN) : null;
            BlockChangeRecord[] records = wrapper.read(Types.VAR_LONG_BLOCK_CHANGE_ARRAY);
            int sectionX = (int) (section >> 42);
            int sectionY = (int) (section << 44 >> 44);
            int sectionZ = (int) (section << 22 >> 42);
            for (BlockChangeRecord record : records) {
                capture((sectionX << 4) + record.getSectionX(),
                        (sectionY << 4) + record.getSectionY(),
                        (sectionZ << 4) + record.getSectionZ(), record.getBlockId(), version);
            }
            wrapper.write(Types.LONG, section);
            if (hasSuppressLightUpdates) {
                wrapper.write(Types.BOOLEAN, suppressLightUpdates);
            }
            wrapper.write(Types.VAR_LONG_BLOCK_CHANGE_ARRAY, records);
            wrapper.resetReader();
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    public static void clear() {
        CHUNKS.clear();
        EXTENDED_SECTIONS.clear();
        NATIVE_STATES.clear();
        PREDICTED_STATES.clear();
        CampfireBlockTracker.clear();
        ModernWorldHeight.reset();
    }

    public static void clearChunk(int chunkX, int chunkZ) {
        CHUNKS.remove(chunkKey(chunkX, chunkZ));
        EXTENDED_SECTIONS.remove(chunkKey(chunkX, chunkZ));
        NATIVE_STATES.remove(chunkKey(chunkX, chunkZ));
        PREDICTED_STATES.remove(chunkKey(chunkX, chunkZ));
        CampfireBlockTracker.clearChunk(chunkX, chunkZ);
    }

    private static void loadBlockStates1_13() {
        try (InputStream input = ClassLoader.getSystemResourceAsStream("assets/viaversion/data/blockstates-1.13.nbt")) {
            if (input == null) {
                throw new IllegalStateException("Missing ViaVersion 1.13 block-state registry");
            }
            CompoundTag registry = NBTIO.readTag(new DataInputStream(input), TagLimiter.noop(), true, CompoundTag.class);
            List<String> states = new ArrayList<>();
            BlockStates1_13.forEach(registry, (state, id) -> {
                while (states.size() <= id) {
                    states.add(null);
                }
                states.set(id, state);
            });
            blockStates1_13 = Collections.unmodifiableList(states);
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to load ViaVersion 1.13 block states", exception);
        }
    }

    private static void captureNativeChunk(com.viaversion.viaversion.api.minecraft.chunks.Chunk chunk) {
        long key = chunkKey(chunk.getX(), chunk.getZ());
        if (chunk.isFullChunk()) {
            for (int sectionY = 0; sectionY < 16; sectionY++) {
                clearNativeStatesInSection(key, sectionY);
            }
        }

        ChunkSection[] sections = chunk.getSections();
        for (int sectionY = 0; sectionY < sections.length; sectionY++) {
            ChunkSection section = sections[sectionY];
            if (section == null) {
                continue;
            }
            DataPalette palette = section.palette(PaletteType.BLOCKS);
            if (!chunk.isFullChunk()) {
                clearNativeStatesInSection(key, sectionY);
            }

            Map<Integer, NativeState> matchingStates = new HashMap<>();
            for (int paletteIndex = 0; paletteIndex < palette.size(); paletteIndex++) {
                int stateId = palette.idByIndex(paletteIndex);
                NativeState state = decodeNative(stateId);
                if (state != null) {
                    matchingStates.put(stateId, state);
                }
            }
            if (matchingStates.isEmpty()) {
                continue;
            }

            int capturedSectionY = sectionY;
            palette.forEachMatchingCoordinate(matchingStates::containsKey, coordinate -> {
                int x = (chunk.getX() << 4) + ChunkSection.xFromIndex(coordinate);
                int y = (capturedSectionY << 4) + ChunkSection.yFromIndex(coordinate);
                int z = (chunk.getZ() << 4) + ChunkSection.zFromIndex(coordinate);
                NativeState state = matchingStates.get(palette.idAt(coordinate));
                NATIVE_STATES.computeIfAbsent(key, ignored -> Maps.newConcurrentMap())
                        .put(new BlockPos(x, y, z), state);
            });
        }
    }

    private static void clearNativeStatesInSection(long key, int sectionY) {
        ConcurrentMap<BlockPos, NativeState> states = NATIVE_STATES.get(key);
        if (states == null) {
            return;
        }
        int minY = sectionY << 4;
        int maxY = minY + 15;
        for (Map.Entry<BlockPos, NativeState> entry : states.entrySet()) {
            int y = entry.getKey().getY();
            if (y >= minY && y <= maxY) {
                states.remove(entry.getKey(), entry.getValue());
            }
        }
        if (states.isEmpty()) {
            NATIVE_STATES.remove(key, states);
        }
    }

    private static void captureNative(int x, int y, int z, int stateId) {
        BlockPos pos = new BlockPos(x, y, z);
        long key = chunkKey(x >> 4, z >> 4);
        NativeState state = decodeNative(stateId);
        if (state != null) {
            NATIVE_STATES.computeIfAbsent(key, ignored -> Maps.newConcurrentMap()).put(pos, state);
            return;
        }

        ConcurrentMap<BlockPos, NativeState> states = NATIVE_STATES.get(key);
        if (states != null) {
            states.remove(pos);
            if (states.isEmpty()) {
                NATIVE_STATES.remove(key, states);
            }
        }
    }

    private static NativeState decodeNative(int stateId) {
        if (stateId < 0 || stateId >= blockStates1_13.size()) {
            return null;
        }
        String encoded = blockStates1_13.get(stateId);
        if (encoded == null) {
            return null;
        }

        int propertiesStart = encoded.indexOf('[');
        String identifier = propertiesStart >= 0 ? encoded.substring(0, propertiesStart) : encoded;
        Map<String, String> properties = new HashMap<>();
        if (propertiesStart >= 0 && encoded.endsWith("]")) {
            String body = encoded.substring(propertiesStart + 1, encoded.length() - 1);
            for (String entry : body.split(",")) {
                int separator = entry.indexOf('=');
                if (separator > 0) {
                    properties.put(entry.substring(0, separator), entry.substring(separator + 1));
                }
            }
        }

        if (!isRelevantNativeState(identifier, properties)) {
            return null;
        }
        return new NativeState(identifier, Collections.unmodifiableMap(properties));
    }

    private static boolean isRelevantNativeState(String identifier, Map<String, String> properties) {
        return identifier.endsWith("_stairs")
                || identifier.endsWith("_chest")
                || identifier.endsWith("_wall")
                || identifier.endsWith("_fence")
                || identifier.endsWith("_fence_gate")
                || identifier.endsWith("_door")
                || identifier.endsWith("_trapdoor")
                || identifier.endsWith("_pane")
                || identifier.equals("minecraft:iron_bars")
                || "true".equals(properties.get("waterlogged"));
    }

    public static String getNativeProperty(BlockPos pos, String property) {
        NativeState state = getNativeState(pos);
        return state != null ? state.properties.get(property) : null;
    }

    public static String getNativeIdentifier(BlockPos pos) {
        NativeState state = getNativeState(pos);
        return state != null ? state.identifier : null;
    }

    private static NativeState getNativeState(BlockPos pos) {
        if (pos == null) {
            return null;
        }
        Map<BlockPos, NativeState> states = NATIVE_STATES.get(chunkKey(pos.getX() >> 4, pos.getZ() >> 4));
        return states != null ? states.get(pos) : null;
    }

    private static void captureChunk(com.viaversion.viaversion.api.minecraft.chunks.Chunk chunk, int minSectionY, ProtocolVersion sourceVersion) {
        long key = chunkKey(chunk.getX(), chunk.getZ());
        if (chunk.isFullChunk()) {
            clearSourceStates(key, sourceVersion);
            PREDICTED_STATES.remove(key);
        }

        ChunkSection[] sections = chunk.getSections();
        for (int sectionY = 0; sectionY < sections.length; sectionY++) {
            ChunkSection section = sections[sectionY];
            if (section == null) {
                continue;
            }
            DataPalette palette = section.palette(PaletteType.BLOCKS);
            int absoluteSectionY = sectionY + minSectionY;
            if (!chunk.isFullChunk()) {
                clearSourceStatesInSection(key, sourceVersion, absoluteSectionY);
                clearPredictedStatesInSection(key, absoluteSectionY);
            }

            Map<Integer, ModernState> matchingStates = new HashMap<>();
            for (int paletteIndex = 0; paletteIndex < palette.size(); paletteIndex++) {
                int stateId = palette.idByIndex(paletteIndex);
                ModernState state = decode(stateId, sourceVersion);
                if (state != null) {
                    matchingStates.put(stateId, state);
                }
            }
            if (matchingStates.isEmpty()) {
                continue;
            }

            palette.forEachMatchingCoordinate(matchingStates::containsKey, coordinate -> {
                int x = (chunk.getX() << 4) + ChunkSection.xFromIndex(coordinate);
                int y = (absoluteSectionY << 4) + ChunkSection.yFromIndex(coordinate);
                int z = (chunk.getZ() << 4) + ChunkSection.zFromIndex(coordinate);
                ModernState state = matchingStates.get(palette.idAt(coordinate));
                CHUNKS.computeIfAbsent(key, ignored -> Maps.newConcurrentMap())
                        .put(new BlockPos(x, y, z), state);
            });
        }
    }

    private static void captureExtendedChunk(com.viaversion.viaversion.api.minecraft.chunks.Chunk chunk,
                                             int minSectionY, UserConnection connection) {
        long key = chunkKey(chunk.getX(), chunk.getZ());
        if (chunk.isFullChunk()) {
            EXTENDED_SECTIONS.remove(key);
        }

        ChunkSection[] sections = chunk.getSections();
        for (int sectionIndex = 0; sectionIndex < sections.length; sectionIndex++) {
            int sectionY = minSectionY + sectionIndex;
            if (sectionY >= 0 && sectionY < 16) {
                continue;
            }
            clearNativeStatesInSection(key, sectionY);
            ChunkSection section = sections[sectionIndex];
            if (section == null) {
                continue;
            }

            DataPalette palette = section.palette(PaletteType.BLOCKS);
            Map<Integer, IBlockState> mappedStates = new HashMap<>();
            Map<Integer, NativeState> nativeStates = new HashMap<>();
            for (int paletteIndex = 0; paletteIndex < palette.size(); paletteIndex++) {
                int stateId = palette.idByIndex(paletteIndex);
                mappedStates.put(stateId, mapToLegacyState(connection, stateId));
                NativeState nativeState = mapToNativeState(connection, stateId);
                if (nativeState != null) {
                    nativeStates.put(stateId, nativeState);
                }
            }

            IBlockState[] states = new IBlockState[4096];
            palette.forEachMatchingCoordinate(stateId -> true, coordinate -> {
                int stateId = palette.idAt(coordinate);
                IBlockState state = mappedStates.get(stateId);
                if (state != null && state.getBlock() != net.minecraft.init.Blocks.air) {
                    states[coordinate] = state;
                }
                NativeState nativeState = nativeStates.get(stateId);
                if (nativeState != null) {
                    int x = (chunk.getX() << 4) + ChunkSection.xFromIndex(coordinate);
                    int y = (sectionY << 4) + ChunkSection.yFromIndex(coordinate);
                    int z = (chunk.getZ() << 4) + ChunkSection.zFromIndex(coordinate);
                    NATIVE_STATES.computeIfAbsent(key, ignored -> Maps.newConcurrentMap())
                            .put(new BlockPos(x, y, z), nativeState);
                }
            });
            EXTENDED_SECTIONS.computeIfAbsent(key, ignored -> Maps.newConcurrentMap()).put(sectionY, states);
        }
    }

    private static void captureExtended(int x, int y, int z, int stateId, UserConnection connection) {
        if (y >= 0 && y < 256) {
            return;
        }
        BlockPos pos = new BlockPos(x, y, z);
        long key = chunkKey(x >> 4, z >> 4);
        ModernState modernState = decode(stateId, ProtocolVersion.v1_17);
        IBlockState state = modernState != null ? modernState.toBlockState() : mapToLegacyState(connection, stateId);
        updateNativeState(pos, mapToNativeState(connection, stateId));
        IBlockState[] states = EXTENDED_SECTIONS.computeIfAbsent(key, ignored -> Maps.newConcurrentMap())
                .computeIfAbsent(y >> 4, ignored -> new IBlockState[4096]);
        states[ChunkSection.index(x & 15, y & 15, z & 15)] =
                state != null && state.getBlock() != net.minecraft.init.Blocks.air ? state : null;
        scheduleExtendedUpdate(pos, state, modernState);
    }

    private static void scheduleExtendedUpdate(BlockPos pos, IBlockState state, ModernState modernState) {
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft == null) {
            return;
        }
        minecraft.addScheduledTask(() -> {
            if (minecraft.theWorld == null || !ModernWorldHeight.isValidY(pos.getY())
                    || !minecraft.theWorld.isBlockLoaded(pos)) {
                return;
            }
            Chunk chunk = minecraft.theWorld.getChunkFromBlockCoords(pos);
            chunk.setBlockState(pos, state != null ? state : net.minecraft.init.Blocks.air.getDefaultState());
            if (modernState != null && state != null) {
                modernState.onApplied(pos, state);
            }
            chunk.refreshHeightMap();
            minecraft.theWorld.markBlockForUpdate(pos);
        });
    }

    @SuppressWarnings("rawtypes")
    private static IBlockState mapToLegacyState(UserConnection connection, int stateId) {
        int mappedId = stateId;
        try {
            List<Protocol> protocols = connection.getProtocolInfo().getPipeline().pipes(
                    Protocol1_17To1_16_4.class, false, Direction.CLIENTBOUND);
            for (Protocol protocol : protocols) {
                MappingData mappingData = protocol.getMappingData();
                if (mappingData != null && mappingData.getBlockStateMappings() != null) {
                    mappedId = mappingData.getNewBlockStateId(mappedId);
                }
                if (protocol instanceof Protocol1_9To1_8 protocol1_9To1_8) {
                    mappedId = protocol1_9To1_8.getItemRewriter().handleBlockId(mappedId);
                }
            }
        } catch (RuntimeException exception) {
            return net.minecraft.init.Blocks.air.getDefaultState();
        }
        int blockId = mappedId >> 4;
        int metadata = mappedId & 15;
        IBlockState state = Block.getStateById(blockId | metadata << 12);
        return state != null ? state : net.minecraft.init.Blocks.air.getDefaultState();
    }

    private static NativeState mapToNativeState(UserConnection connection, int stateId) {
        int mappedId = stateId;
        try {
            List<Protocol> protocols = connection.getProtocolInfo().getPipeline().pipes(
                    Protocol1_17To1_16_4.class, false, Direction.CLIENTBOUND);
            for (Protocol protocol : protocols) {
                if (protocol instanceof Protocol1_13To1_12_2) {
                    break;
                }
                MappingData mappingData = protocol.getMappingData();
                if (mappingData != null && mappingData.getBlockStateMappings() != null) {
                    mappedId = mappingData.getNewBlockStateId(mappedId);
                }
            }
            return decodeNative(mappedId);
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private static void updateNativeState(BlockPos pos, NativeState state) {
        long key = chunkKey(pos.getX() >> 4, pos.getZ() >> 4);
        if (state != null) {
            NATIVE_STATES.computeIfAbsent(key, ignored -> Maps.newConcurrentMap()).put(pos, state);
            return;
        }
        ConcurrentMap<BlockPos, NativeState> states = NATIVE_STATES.get(key);
        if (states != null) {
            states.remove(pos);
            if (states.isEmpty()) {
                NATIVE_STATES.remove(key, states);
            }
        }
    }

    private static void clearSourceStatesInSection(long key, ProtocolVersion sourceVersion, int sectionY) {
        ConcurrentMap<BlockPos, ModernState> states = CHUNKS.get(key);
        if (states == null) {
            return;
        }
        int minY = sectionY << 4;
        int maxY = minY + 15;
        for (Map.Entry<BlockPos, ModernState> entry : states.entrySet()) {
            int y = entry.getKey().getY();
            if (y >= minY && y <= maxY && entry.getValue().belongsTo(sourceVersion)) {
                states.remove(entry.getKey(), entry.getValue());
            }
        }
        if (states.isEmpty()) {
            CHUNKS.remove(key, states);
        }
    }

    private static void capture(int x, int y, int z, int stateId, ProtocolVersion sourceVersion) {
        BlockPos pos = new BlockPos(x, y, z);
        long key = chunkKey(x >> 4, z >> 4);
        ModernState modernState = decode(stateId, sourceVersion);
        if (modernState != null) {
            removeState(PREDICTED_STATES, key, pos);
            CHUNKS.computeIfAbsent(key, ignored -> Maps.newConcurrentMap()).put(pos, modernState);
            return;
        }

        // Any server update resolves the local placement prediction. Keep a
        // captured state through lower ViaBackwards fallback layers, but clear
        // it when the update reaches the protocol layer that owns that state.
        removeState(PREDICTED_STATES, key, pos);
        ConcurrentMap<BlockPos, ModernState> states = CHUNKS.get(key);
        if (states != null) {
            ModernState existing = states.get(pos);
            if (existing != null && existing.belongsTo(sourceVersion)) {
                states.remove(pos, existing);
            }
            if (states.isEmpty()) {
                CHUNKS.remove(key, states);
            }
        }
    }

    private static void clearSourceStates(long key, ProtocolVersion sourceVersion) {
        ConcurrentMap<BlockPos, ModernState> states = CHUNKS.get(key);
        if (states == null) {
            return;
        }

        for (Map.Entry<BlockPos, ModernState> entry : states.entrySet()) {
            if (entry.getValue().belongsTo(sourceVersion)) {
                states.remove(entry.getKey(), entry.getValue());
            }
        }
        if (states.isEmpty()) {
            CHUNKS.remove(key, states);
        }
    }

    public static void applyChunk(Chunk chunk) {
        Map<Integer, IBlockState[]> extendedSections = EXTENDED_SECTIONS.get(chunkKey(chunk.xPosition, chunk.zPosition));
        boolean extendedChanged = extendedSections != null;
        if (extendedSections != null) {
            for (Map.Entry<Integer, IBlockState[]> entry : extendedSections.entrySet()) {
                chunk.setExtendedSection(entry.getKey(), entry.getValue());
            }
        }

        Map<BlockPos, ModernState> states = CHUNKS.get(chunkKey(chunk.xPosition, chunk.zPosition));
        if (states == null) {
            if (extendedChanged) {
                chunk.refreshHeightMap();
            }
            return;
        }

        for (Map.Entry<BlockPos, ModernState> entry : states.entrySet()) {
            int y = entry.getKey().getY();
            if (!chunk.isValidY(y)) {
                continue;
            }
            IBlockState state = entry.getValue().toBlockState();
            chunk.setBlockState(entry.getKey(), state);
            entry.getValue().onApplied(entry.getKey(), state);
        }
        if (extendedChanged) {
            chunk.refreshHeightMap();
        }
    }

    public static IBlockState remap(BlockPos pos, IBlockState fallback) {
        if (pos == null) {
            return fallback;
        }
        Map<BlockPos, IBlockState> predictions = PREDICTED_STATES.get(chunkKey(pos.getX() >> 4, pos.getZ() >> 4));
        IBlockState predicted = predictions != null ? predictions.get(pos) : null;
        if (predicted != null) {
            return predicted;
        }
        Map<BlockPos, ModernState> states = CHUNKS.get(chunkKey(pos.getX() >> 4, pos.getZ() >> 4));
        ModernState state = states != null ? states.get(pos) : null;
        if (state == null) {
            return fallback;
        }
        IBlockState remapped = state.toBlockState();
        state.onApplied(pos, remapped);
        return remapped;
    }

    public static void predict(BlockPos pos, IBlockState state) {
        if (pos == null || state == null || !(state.getBlock() instanceof ModernBlock)) {
            return;
        }
        long key = chunkKey(pos.getX() >> 4, pos.getZ() >> 4);
        ConcurrentMap<BlockPos, ModernState> modernStates = CHUNKS.get(key);
        if (modernStates != null && modernStates.containsKey(pos)) {
            return;
        }
        PREDICTED_STATES.computeIfAbsent(key, ignored -> Maps.newConcurrentMap()).put(pos, state);
    }

    public static void remove(BlockPos pos) {
        if (pos == null) {
            return;
        }
        long key = chunkKey(pos.getX() >> 4, pos.getZ() >> 4);
        removeState(PREDICTED_STATES, key, pos);
        removeState(CHUNKS, key, pos);
        removeState(NATIVE_STATES, key, pos);
    }

    private static void clearPredictedStatesInSection(long key, int sectionY) {
        ConcurrentMap<BlockPos, IBlockState> states = PREDICTED_STATES.get(key);
        if (states == null) {
            return;
        }
        int minY = sectionY << 4;
        int maxY = minY + 15;
        for (Map.Entry<BlockPos, IBlockState> entry : states.entrySet()) {
            int y = entry.getKey().getY();
            if (y >= minY && y <= maxY) {
                states.remove(entry.getKey(), entry.getValue());
            }
        }
        if (states.isEmpty()) {
            PREDICTED_STATES.remove(key, states);
        }
    }

    private static <T> void removeState(ConcurrentMap<Long, ConcurrentMap<BlockPos, T>> chunks, long key, BlockPos pos) {
        ConcurrentMap<BlockPos, T> states = chunks.get(key);
        if (states == null) {
            return;
        }
        states.remove(pos);
        if (states.isEmpty()) {
            chunks.remove(key, states);
        }
    }

    private static ModernState decode(int stateId, ProtocolVersion sourceVersion) {
        for (ModernBlock block : modernBlocks) {
            if (block.handlesViaState(sourceVersion, stateId)) {
                return new ModernState(block, stateId, sourceVersion);
            }
        }
        return null;
    }

    private static void discoverModernBlocks() {
        List<ModernBlock> discovered = new ArrayList<>();
        for (Block block : Block.blockRegistry) {
            if (block instanceof ModernBlock) {
                ModernBlock modernBlock = (ModernBlock) block;
                if (modernBlock.getViaStateIdMin() <= modernBlock.getViaStateIdMax()) {
                    discovered.add(modernBlock);
                }
            }
        }
        modernBlocks = Collections.unmodifiableList(discovered);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void prepend(Protocol<?, ?, ?, ?> protocol, ClientboundPacketType source, PacketHandler capture) {
        try {
            Field field = AbstractProtocol.class.getDeclaredField("clientboundMappings");
            field.setAccessible(true);
            PacketMappings mappings = (PacketMappings) field.get(protocol);
            PacketMapping mapping = mappings.mappedPacket(source.state(), source.getId());
            if (mapping == null) {
                throw new IllegalStateException("No clientbound mapping for " + source);
            }
            PacketHandler original = mapping.handler();
            mapping.setHandler(original != null ? capture.then(original) : capture);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Unable to install modern block state capture for " + source, exception);
        }
    }

    private static long chunkKey(int chunkX, int chunkZ) {
        return (long) chunkX & 0xFFFFFFFFL | ((long) chunkZ & 0xFFFFFFFFL) << 32;
    }

    private static final class ModernState {
        private final ModernBlock block;
        private final int stateId;
        private final ProtocolVersion protocol;

        private ModernState(ModernBlock block, int stateId, ProtocolVersion protocol) {
            this.block = block;
            this.stateId = stateId;
            this.protocol = protocol;
        }

        private IBlockState toBlockState() {
            return block.getStateFromViaState(protocol, stateId);
        }

        private boolean belongsTo(ProtocolVersion sourceVersion) {
            return protocol.equals(sourceVersion);
        }

        private void onApplied(BlockPos pos, IBlockState state) {
            block.onModernStateApplied(pos, state);
        }
    }

    private static final class NativeState {
        private final String identifier;
        private final Map<String, String> properties;

        private NativeState(String identifier, Map<String, String> properties) {
            this.identifier = identifier;
            this.properties = properties;
        }
    }
}
