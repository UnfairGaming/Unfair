package cn.unfair.util.via;

import com.google.common.collect.Sets;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.world.World;

import java.util.Set;

public final class DirtPathBlockTracker {
    private static final Set<BlockPos> POSITIONS = Sets.newHashSet();

    private DirtPathBlockTracker() {
    }

    public static boolean isDirtPathItem(ItemStack stack) {
        String model = ViaBackwardsItemModels.getModelName(stack);
        return "dirt_path".equals(model) || "grass_path".equals(model);
    }

    public static boolean place(ItemStack stack, EntityPlayer player, World world, BlockPos hitPos, EnumFacing side) {
        BlockPos placePos = hitPos;
        Block clicked = world.getBlockState(hitPos).getBlock();

        if (!clicked.isReplaceable(world, hitPos)) {
            placePos = hitPos.offset(side);
        }

        if (stack == null || stack.stackSize == 0 || !player.canPlayerEdit(placePos, side, stack)
                || !world.canBlockBePlaced(Blocks.dirt_path, placePos, false, side, null, stack)) {
            return false;
        }

        mark(placePos);
        if (world.setBlockState(placePos, Blocks.dirt_path.getDefaultState(), 3)) {
            world.checkLight(placePos);
            world.markBlockRangeForRenderUpdate(placePos.add(-1, -1, -1), placePos.add(1, 1, 1));
            world.playSoundEffect((float) placePos.getX() + 0.5F, (float) placePos.getY() + 0.5F, (float) placePos.getZ() + 0.5F,
                    Blocks.dirt_path.stepSound.getPlaceSound(),
                    (Blocks.dirt_path.stepSound.getVolume() + 1.0F) / 2.0F,
                    Blocks.dirt_path.stepSound.getFrequency() * 0.8F);

            if (!player.capabilities.isCreativeMode) {
                --stack.stackSize;
            }
        }

        return true;
    }

    public static void mark(BlockPos pos) {
        if (pos != null) {
            POSITIONS.add(pos);
        }
    }

    public static IBlockState remap(BlockPos pos, IBlockState state) {
        if (pos != null && state != null && POSITIONS.contains(pos) && state.getBlock() == Blocks.grass) {
            return Blocks.dirt_path.getDefaultState();
        }
        if (pos != null && state != null && state.getBlock() != Blocks.dirt_path && state.getBlock() != Blocks.grass) {
            POSITIONS.remove(pos);
        }
        return state;
    }
}
