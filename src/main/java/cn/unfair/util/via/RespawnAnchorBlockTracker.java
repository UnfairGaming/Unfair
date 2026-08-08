package cn.unfair.util.via;

import com.google.common.collect.Sets;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.util.BlockPos;

import java.util.Set;

public final class RespawnAnchorBlockTracker {
    private static final Set<BlockPos> POSITIONS = Sets.newHashSet();

    private RespawnAnchorBlockTracker() {
    }

    public static void mark(BlockPos pos) {
        if (pos != null) {
            POSITIONS.add(pos);
        }
    }

    public static IBlockState remap(BlockPos pos, IBlockState state) {
        if (pos != null && state != null && POSITIONS.contains(pos) && state.getBlock() == Blocks.obsidian) {
            return Blocks.respawn_anchor.getDefaultState();
        }
        if (pos != null && state != null && state.getBlock() != Blocks.respawn_anchor && state.getBlock() != Blocks.obsidian) {
            POSITIONS.remove(pos);
        }
        return state;
    }
}
