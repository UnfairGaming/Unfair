package cn.unfair.util.via;

import com.google.common.collect.Maps;
import com.viaversion.viabackwards.protocol.v1_14to1_13_2.Protocol1_14To1_13_2;
import com.viaversion.viaversion.api.Via;
import com.viaversion.viaversion.api.minecraft.BlockChangeRecord;
import com.viaversion.viaversion.api.minecraft.BlockPosition;
import com.viaversion.viaversion.api.minecraft.chunks.ChunkSection;
import com.viaversion.viaversion.api.minecraft.chunks.DataPalette;
import com.viaversion.viaversion.api.minecraft.chunks.PaletteType;
import com.viaversion.viaversion.api.protocol.AbstractProtocol;
import com.viaversion.viaversion.api.protocol.packet.ClientboundPacketType;
import com.viaversion.viaversion.api.protocol.packet.mapping.PacketMapping;
import com.viaversion.viaversion.api.protocol.packet.mapping.PacketMappings;
import com.viaversion.viaversion.api.protocol.remapper.PacketHandler;
import com.viaversion.viaversion.api.type.Types;
import com.viaversion.viaversion.api.type.types.chunk.ChunkType1_14;
import com.viaversion.viaversion.protocols.v1_12_2to1_13.packet.ClientboundPackets1_13;
import com.viaversion.viaversion.protocols.v1_13_2to1_14.packet.ClientboundPackets1_14;
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

/** Preserves selected modern block states before ViaBackwards replaces them. */
public final class ModernBlockStateTracker {
    private static final ConcurrentMap<Long, ConcurrentMap<BlockPos, ModernState>> CHUNKS = Maps.newConcurrentMap();
    private static List<ModernBlock> modernBlocks = Collections.emptyList();
    private static boolean installed;

    private ModernBlockStateTracker() {
    }

    public static synchronized void install() {
        if (installed) {
            return;
        }

        Protocol1_14To1_13_2 protocol = Via.getManager().getProtocolManager().getProtocol(Protocol1_14To1_13_2.class);
        if (protocol == null) {
            return;
        }

        discoverModernBlocks();

        prepend(protocol, ClientboundPackets1_14.LEVEL_CHUNK, ClientboundPackets1_13.LEVEL_CHUNK, wrapper -> {
            com.viaversion.viaversion.api.minecraft.chunks.Chunk chunk = wrapper.read(ChunkType1_14.TYPE);
            captureChunk(chunk);
            wrapper.write(ChunkType1_14.TYPE, chunk);
            wrapper.resetReader();
        });
        prepend(protocol, ClientboundPackets1_14.BLOCK_UPDATE, ClientboundPackets1_13.BLOCK_UPDATE, wrapper -> {
            BlockPosition pos = wrapper.read(Types.BLOCK_POSITION1_14);
            int stateId = wrapper.read(Types.VAR_INT);
            capture(pos.x(), pos.y(), pos.z(), stateId);
            wrapper.write(Types.BLOCK_POSITION1_14, pos);
            wrapper.write(Types.VAR_INT, stateId);
            wrapper.resetReader();
        });
        prepend(protocol, ClientboundPackets1_14.CHUNK_BLOCKS_UPDATE, ClientboundPackets1_13.CHUNK_BLOCKS_UPDATE, wrapper -> {
            int chunkX = wrapper.read(Types.INT);
            int chunkZ = wrapper.read(Types.INT);
            BlockChangeRecord[] records = wrapper.read(Types.BLOCK_CHANGE_ARRAY);
            for (BlockChangeRecord record : records) {
                capture((chunkX << 4) + record.getSectionX(), record.getY(),
                        (chunkZ << 4) + record.getSectionZ(), record.getBlockId());
            }
            wrapper.write(Types.INT, chunkX);
            wrapper.write(Types.INT, chunkZ);
            wrapper.write(Types.BLOCK_CHANGE_ARRAY, records);
            wrapper.resetReader();
        });
        installed = true;
    }

    public static void clear() {
        CHUNKS.clear();
    }

    public static void clearChunk(int chunkX, int chunkZ) {
        CHUNKS.remove(chunkKey(chunkX, chunkZ));
    }

    private static void captureChunk(com.viaversion.viaversion.api.minecraft.chunks.Chunk chunk) {
        long key = chunkKey(chunk.getX(), chunk.getZ());
        if (chunk.isFullChunk()) {
            CHUNKS.remove(key);
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
                        capture((chunk.getX() << 4) + x, (sectionY << 4) + y,
                                (chunk.getZ() << 4) + z, palette.idAt(x, y, z));
                    }
                }
            }
        }
    }

    private static void capture(int x, int y, int z, int stateId) {
        BlockPos pos = new BlockPos(x, y, z);
        long key = chunkKey(x >> 4, z >> 4);
        ModernState modernState = decode(stateId);
        if (modernState != null) {
            CHUNKS.computeIfAbsent(key, ignored -> Maps.newConcurrentMap()).put(pos, modernState);
            return;
        }

        ConcurrentMap<BlockPos, ModernState> states = CHUNKS.get(key);
        if (states != null) {
            states.remove(pos);
            if (states.isEmpty()) {
                CHUNKS.remove(key, states);
            }
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

    private static ModernState decode(int stateId) {
        for (ModernBlock block : modernBlocks) {
            if (block.handlesViaStateId(stateId)) {
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
    private static void prepend(Protocol1_14To1_13_2 protocol, ClientboundPackets1_14 source,
                                ClientboundPackets1_13 target, PacketHandler capture) {
        try {
            Field field = AbstractProtocol.class.getDeclaredField("clientboundMappings");
            field.setAccessible(true);
            PacketMappings mappings = (PacketMappings) field.get(protocol);
            PacketMapping mapping = mappings.mappedPacket(source.state(), source.getId());
            PacketHandler original = mapping != null ? mapping.handler() : null;
            PacketHandler handler = original != null ? capture.then(original) : capture;
            protocol.registerClientbound(source, target, handler, true);
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

        private void onApplied(BlockPos pos, IBlockState state) {
            block.onModernStateApplied(pos, state);
        }
    }
}
