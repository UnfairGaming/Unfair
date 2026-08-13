package cn.unfair.util.via;

import com.google.common.collect.Maps;
import com.viaversion.viabackwards.protocol.v1_14to1_13_2.Protocol1_14To1_13_2;
import com.viaversion.viabackwards.protocol.v1_15to1_14_4.Protocol1_15To1_14_4;
import com.viaversion.viabackwards.protocol.v1_17to1_16_4.Protocol1_17To1_16_4;
import com.viaversion.viaversion.api.Via;
import com.viaversion.viaversion.api.data.entity.EntityTracker;
import com.viaversion.viaversion.api.minecraft.BlockChangeRecord;
import com.viaversion.viaversion.api.minecraft.BlockPosition;
import com.viaversion.viaversion.api.minecraft.chunks.ChunkSection;
import com.viaversion.viaversion.api.minecraft.chunks.DataPalette;
import com.viaversion.viaversion.api.minecraft.chunks.PaletteType;
import com.viaversion.viaversion.api.protocol.AbstractProtocol;
import com.viaversion.viaversion.api.protocol.packet.ClientboundPacketType;
import com.viaversion.viaversion.api.protocol.Protocol;
import com.viaversion.viaversion.api.protocol.packet.mapping.PacketMapping;
import com.viaversion.viaversion.api.protocol.packet.mapping.PacketMappings;
import com.viaversion.viaversion.api.protocol.remapper.PacketHandler;
import com.viaversion.viaversion.api.type.Types;
import com.viaversion.viaversion.api.type.types.chunk.ChunkType1_14;
import com.viaversion.viaversion.api.type.types.chunk.ChunkType1_15;
import com.viaversion.viaversion.api.type.types.chunk.ChunkType1_17;
import com.viaversion.viaversion.protocols.v1_13_2to1_14.packet.ClientboundPackets1_14;
import com.viaversion.viaversion.protocols.v1_14_4to1_15.packet.ClientboundPackets1_15;
import com.viaversion.viaversion.protocols.v1_16_4to1_17.packet.ClientboundPackets1_17;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import de.florianmichael.vialoadingbase.ViaLoadingBase;
import net.minecraft.block.Block;
import net.minecraft.block.ModernBlock;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.BlockPos;
import net.minecraft.world.chunk.Chunk;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentMap;
import java.util.logging.Level;

/** Preserves selected modern block states before ViaBackwards replaces them. */
public final class ModernBlockStateTracker {
    private static final ConcurrentMap<Long, ConcurrentMap<BlockPos, ModernState>> CHUNKS = Maps.newConcurrentMap();
    private static List<ModernBlock> modernBlocks = Collections.emptyList();
    private static boolean installationScheduled;
    private static boolean installed;

    private ModernBlockStateTracker() {
    }

    public static synchronized void install() {
        if (installationScheduled || installed) {
            return;
        }

        Protocol1_14To1_13_2 protocol14 = Via.getManager().getProtocolManager().getProtocol(Protocol1_14To1_13_2.class);
        Protocol1_15To1_14_4 protocol15 = Via.getManager().getProtocolManager().getProtocol(Protocol1_15To1_14_4.class);
        Protocol1_17To1_16_4 protocol17 = Via.getManager().getProtocolManager().getProtocol(Protocol1_17To1_16_4.class);
        if (protocol14 == null) {
            return;
        }

        installationScheduled = true;
        discoverModernBlocks();
        Via.getManager().getProtocolManager().getMappingLoaderFuture(Protocol1_14To1_13_2.class)
                .whenComplete((ignored, throwable) -> finishLayer(throwable, () -> install1_14(protocol14)));
        if (protocol15 != null) {
            Via.getManager().getProtocolManager().getMappingLoaderFuture(Protocol1_15To1_14_4.class)
                    .whenComplete((ignored, throwable) -> finishLayer(throwable, () -> install1_15(protocol15)));
        }
        if (protocol17 != null) {
            Via.getManager().getProtocolManager().getMappingLoaderFuture(Protocol1_17To1_16_4.class)
                    .whenComplete((ignored, throwable) -> finishLayer(throwable, () -> install1_17(protocol17)));
        }
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
            captureChunk(chunk, tracker.currentMinY() >> 4, ProtocolVersion.v1_17);
            wrapper.write(type, chunk);
            wrapper.resetReader();
        });
        prepend(protocol, ClientboundPackets1_17.BLOCK_UPDATE, wrapper -> {
            BlockPosition pos = wrapper.read(Types.BLOCK_POSITION1_14);
            int stateId = wrapper.read(Types.VAR_INT);
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
                capture((sectionX << 4) + record.getSectionX(), (sectionY << 4) + record.getSectionY(),
                        (sectionZ << 4) + record.getSectionZ(), record.getBlockId(), ProtocolVersion.v1_17);
            }
            wrapper.write(Types.LONG, section);
            wrapper.write(Types.BOOLEAN, suppressLightUpdates);
            wrapper.write(Types.VAR_LONG_BLOCK_CHANGE_ARRAY, records);
            wrapper.resetReader();
        });
    }

    public static void clear() {
        CHUNKS.clear();
        CampfireBlockTracker.clear();
    }

    public static void clearChunk(int chunkX, int chunkZ) {
        CHUNKS.remove(chunkKey(chunkX, chunkZ));
        CampfireBlockTracker.clearChunk(chunkX, chunkZ);
    }

    private static void captureChunk(com.viaversion.viaversion.api.minecraft.chunks.Chunk chunk, int minSectionY, ProtocolVersion sourceVersion) {
        long key = chunkKey(chunk.getX(), chunk.getZ());
        if (chunk.isFullChunk()) {
            clearSourceStates(key, sourceVersion);
        }

        ChunkSection[] sections = chunk.getSections();
        for (int sectionY = 0; sectionY < sections.length; sectionY++) {
            ChunkSection section = sections[sectionY];
            if (section == null) {
                continue;
            }
            DataPalette palette = section.palette(PaletteType.BLOCKS);
            for (int y = 0; y < 16; y++) {
                for (int z = 0; z < 16; z++) {
                    for (int x = 0; x < 16; x++) {
                        capture((chunk.getX() << 4) + x, ((sectionY + minSectionY) << 4) + y,
                                (chunk.getZ() << 4) + z, palette.idAt(x, y, z), sourceVersion);
                    }
                }
            }
        }
    }

    private static void capture(int x, int y, int z, int stateId, ProtocolVersion sourceVersion) {
        BlockPos pos = new BlockPos(x, y, z);
        long key = chunkKey(x >> 4, z >> 4);
        ModernState modernState = decode(stateId, sourceVersion);
        if (modernState != null) {
            CHUNKS.computeIfAbsent(key, ignored -> Maps.newConcurrentMap()).put(pos, modernState);
            return;
        }

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
        Map<BlockPos, ModernState> states = CHUNKS.get(chunkKey(chunk.xPosition, chunk.zPosition));
        if (states == null) {
            return;
        }

        for (Map.Entry<BlockPos, ModernState> entry : states.entrySet()) {
            IBlockState state = entry.getValue().toBlockState();
            chunk.setBlockState(entry.getKey(), state);
            entry.getValue().onApplied(entry.getKey(), state);
        }
    }

    public static IBlockState remap(BlockPos pos, IBlockState fallback) {
        if (pos == null) {
            return fallback;
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

    private static ModernState decode(int stateId, ProtocolVersion sourceVersion) {
        for (ModernBlock block : modernBlocks) {
            if (block.getViaStateProtocol().equals(sourceVersion) && block.handlesViaStateId(stateId)) {
                return new ModernState(block, stateId);
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

        private ModernState(ModernBlock block, int stateId) {
            this.block = block;
            this.stateId = stateId;
        }

        private IBlockState toBlockState() {
            return block.getStateFromViaStateId(stateId);
        }

        private boolean belongsTo(ProtocolVersion sourceVersion) {
            return block.getViaStateProtocol().equals(sourceVersion);
        }

        private void onApplied(BlockPos pos, IBlockState state) {
            block.onModernStateApplied(pos, state);
        }
    }
}
