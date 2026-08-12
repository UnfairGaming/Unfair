package cn.unfair.util.via;

import com.google.common.collect.Maps;
import net.minecraft.block.Block;
import net.minecraft.block.BlockCampfire;
import net.minecraft.block.BlockSlab;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.world.World;

import java.util.Map;

public final class CampfireBlockTracker {
    private static final Map<BlockPos, IBlockState> STATES = Maps.newHashMap();

    private CampfireBlockTracker() {
    }

    public static boolean isCampfireItem(ItemStack stack) {
        String model = ViaBackwardsItemModels.getModelName(stack);
        return "campfire".equals(model) || "soul_campfire".equals(model);
    }

    public static boolean place(ItemStack stack, EntityPlayer player, World world, BlockPos hitPos, EnumFacing side) {
        BlockPos placePos = hitPos;
        Block clicked = world.getBlockState(hitPos).getBlock();

        if (!clicked.isReplaceable(world, hitPos)) {
            placePos = hitPos.offset(side);
        }

        String model = ViaBackwardsItemModels.getModelName(stack);
        boolean soul = "soul_campfire".equals(model);
        Block block = soul ? Blocks.soul_campfire : Blocks.campfire;

        if (stack == null || stack.stackSize == 0 || !player.canPlayerEdit(placePos, side, stack)
                || !world.canBlockBePlaced(block, placePos, false, side, null, stack)) {
            return false;
        }

        IBlockState state = block.getDefaultState()
                .withProperty(BlockCampfire.FACING, player.getHorizontalFacing())
                .withProperty(BlockCampfire.LIT, Boolean.TRUE);
        mark(placePos, state);

        if (world.setBlockState(placePos, state, 3)) {
            world.checkLight(placePos);
            world.markBlockRangeForRenderUpdate(placePos.add(-1, -1, -1), placePos.add(1, 1, 1));
            world.playSoundEffect((float) placePos.getX() + 0.5F, (float) placePos.getY() + 0.5F, (float) placePos.getZ() + 0.5F,
                    block.stepSound.getPlaceSound(),
                    (block.stepSound.getVolume() + 1.0F) / 2.0F,
                    block.stepSound.getFrequency() * 0.8F);

            if (!player.capabilities.isCreativeMode) {
                --stack.stackSize;
            }
        }

        return true;
    }

    public static void mark(BlockPos pos, IBlockState state) {
        if (pos != null && state != null) {
            STATES.put(pos, state);
        }
    }

    public static IBlockState remap(BlockPos pos, IBlockState state) {
        if (pos == null || state == null) {
            return state;
        }

        IBlockState campfireState = STATES.get(pos);
        if (campfireState == null) {
            return state;
        }

        Block block = state.getBlock();
        if (block == Blocks.fire) {
            return campfireState.withProperty(BlockCampfire.LIT, Boolean.TRUE);
        }
        if (block instanceof BlockSlab) {
            return campfireState.withProperty(BlockCampfire.LIT, Boolean.FALSE);
        }
        if (block != Blocks.campfire && block != Blocks.soul_campfire) {
            STATES.remove(pos);
        }

        return state;
    }
}
