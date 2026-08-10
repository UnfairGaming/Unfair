package cn.unfair.util.via;

import net.minecraft.block.Block;
import net.minecraft.block.BlockLadder;
import net.minecraft.block.BlockLiquid;
import net.minecraft.block.BlockStairs;
import net.minecraft.block.BlockTrapDoor;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;

public final class ModernFluidPhysics {

    private ModernFluidPhysics() {
    }

    public static float getFluidHeight(World world, BlockPos position, Material material) {
        IBlockState state = world.getBlockState(position);
        Block block = state.getBlock();
        if (block.getMaterial() != material) {
            return 0.0F;
        }

        if (world.getBlockState(position.up()).getBlock().getMaterial() == material) {
            return 1.0F;
        }

        if (!(block instanceof BlockLiquid)) {
            return 8.0F / 9.0F;
        }

        int level = state.getValue(BlockLiquid.LEVEL);
        return (level & 8) == 8 ? 8.0F / 9.0F : (8 - level) / 9.0F;
    }

    public static float getWaterHeight(World world, BlockPos position) {
        return getFluidHeight(world, position, Material.water);
    }

    public static float getLavaHeight(World world, BlockPos position) {
        return getFluidHeight(world, position, Material.lava);
    }

    public static Vec3 getFlow(World world, BlockPos position, Material material) {
        float fluidHeight = Math.min(getFluidHeight(world, position, material), 8.0F / 9.0F);
        if (fluidHeight == 0.0F) {
            return new Vec3(0.0D, 0.0D, 0.0D);
        }

        double flowX = 0.0D;
        double flowZ = 0.0D;
        EnumFacing[] directions = new EnumFacing[] {EnumFacing.NORTH, EnumFacing.EAST, EnumFacing.SOUTH, EnumFacing.WEST};
        for (EnumFacing direction : directions) {
            BlockPos adjacent = position.offset(direction);
            if (!affectsFlow(world, position, adjacent, material)) {
                continue;
            }

            float adjacentHeight = Math.min(getFluidHeight(world, adjacent, material), 8.0F / 9.0F);
            float flow = 0.0F;
            if (adjacentHeight == 0.0F) {
                if (canFlowDownThrough(world, adjacent, material)) {
                    adjacentHeight = Math.min(getFluidHeight(world, adjacent.down(), material), 8.0F / 9.0F);
                    if (adjacentHeight > 0.0F) {
                        flow = fluidHeight - (adjacentHeight - 0.8888889F);
                    }
                }
            } else {
                flow = fluidHeight - adjacentHeight;
            }

            if (flow != 0.0F) {
                flowX += direction.getDirectionVec().getX() * flow;
                flowZ += direction.getDirectionVec().getZ() * flow;
            }
        }

        Vec3 flow = new Vec3(flowX, 0.0D, flowZ);
        IBlockState state = world.getBlockState(position);
        Block block = state.getBlock();
        if (block.getMaterial() == material && block instanceof BlockLiquid && state.getValue(BlockLiquid.LEVEL) >= 8) {
            for (EnumFacing direction : directions) {
                if (isSolidFace(world, position, direction, material) || isSolidFace(world, position.up(), direction, material)) {
                    flow = normalize(flow).addVector(0.0D, -6.0D, 0.0D);
                    break;
                }
            }
        }

        return normalize(flow);
    }

    private static boolean affectsFlow(World world, BlockPos from, BlockPos to, Material material) {
        return getFluidHeight(world, to, material) == 0.0F || isSameFluid(world, from, to);
    }

    private static boolean isSameFluid(World world, BlockPos first, BlockPos second) {
        Material firstMaterial = world.getBlockState(first).getBlock().getMaterial();
        Material secondMaterial = world.getBlockState(second).getBlock().getMaterial();
        return firstMaterial == secondMaterial && (firstMaterial == Material.water || firstMaterial == Material.lava);
    }

    private static boolean canFlowDownThrough(World world, BlockPos position, Material material) {
        Block block = world.getBlockState(position).getBlock();
        Material blockMaterial = block.getMaterial();
        return blockMaterial != material && blockMaterial != Material.lava && !blockMaterial.blocksMovement();
    }

    private static boolean isSolidFace(World world, BlockPos fluidPosition, EnumFacing direction, Material material) {
        BlockPos position = fluidPosition.offset(direction);
        IBlockState state = world.getBlockState(position);
        Block block = state.getBlock();

        if (block.getMaterial() == material || block.getMaterial() == Material.ice) {
            return false;
        }

        if (block instanceof BlockStairs) {
            return state.getValue(BlockStairs.FACING) == direction;
        }

        if (block instanceof BlockLadder) {
            return state.getValue(BlockLadder.FACING).getOpposite() == direction;
        }

        if (block instanceof BlockTrapDoor) {
            return state.getValue(BlockTrapDoor.OPEN).booleanValue()
                    && state.getValue(BlockTrapDoor.FACING).getOpposite() == direction;
        }

        return block.isBlockSolid(world, position, direction.getOpposite()) && block.isFullBlock();
    }

    private static Vec3 normalize(Vec3 vector) {
        double length = vector.lengthVector();
        return length < 1.0E-4D ? new Vec3(0.0D, 0.0D, 0.0D) : new Vec3(vector.xCoord / length, vector.yCoord / length, vector.zCoord / length);
    }
}
