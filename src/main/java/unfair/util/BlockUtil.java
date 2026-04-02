package unfair.util;

import net.minecraft.block.*;
import net.minecraft.block.material.Material;
import net.minecraft.client.Minecraft;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;

public class BlockUtil {
    private static final Minecraft mc = Minecraft.getMinecraft();

    public static boolean isReplaceable(BlockPos blockPos) {
        return BlockUtil.isReplaceable(BlockUtil.mc.theWorld.getBlockState(blockPos).getBlock());
    }

    public static boolean isReplaceable(Block block) {
        if (!block.getMaterial().isReplaceable()) return false;
        if (!(block instanceof BlockSnow)) return true;
        return !(block.getBlockBoundsMaxY() > 0.125);
    }

    public static boolean isInteractable(BlockPos blockPos) {
        return BlockUtil.isInteractable(BlockUtil.mc.theWorld.getBlockState(blockPos).getBlock());
    }

    public static boolean isInteractable(Block block) {
        if (block instanceof BlockContainer) return true;
        if (block instanceof BlockWorkbench) return true;
        if (block instanceof BlockAnvil) return true;
        if (block instanceof BlockBed) return true;
        if (block instanceof BlockDoor) {
            if (block.getMaterial() != Material.iron) return true;
        }
        if (block instanceof BlockTrapDoor) return true;
        if (block instanceof BlockFenceGate) return true;
        if (block instanceof BlockFence) return true;
        if (block instanceof BlockButton) return true;
        if (block instanceof BlockLever) return true;
        return block instanceof BlockJukebox;
    }

    public static boolean isSolid(Block block) {
        if (block instanceof BlockStairs) return false;
        if (block instanceof BlockSlab) return false;
        if (block instanceof BlockEndPortalFrame) return false;
        if (block instanceof BlockEndPortal) return false;
        if (block instanceof BlockVine) return false;
        if (block instanceof BlockPumpkin) return false;
        if (block instanceof BlockCactus) return false;
        if (block instanceof BlockBush) return false;
        if (block instanceof BlockFalling) return false;
        if (block instanceof BlockWeb) return false;
        if (block instanceof BlockPane) return false;
        if (block instanceof BlockCarpet) return false;
        if (block instanceof BlockSnow) return false;
        if (block instanceof BlockFence) return false;
        if (block instanceof BlockFenceGate) return false;
        if (block instanceof BlockWall) return false;
        if (block instanceof BlockLadder) return false;
        if (block instanceof BlockTorch) return false;
        if (block instanceof BlockRedstoneWire) return false;
        if (block instanceof BlockRedstoneDiode) return false;
        if (block instanceof BlockBasePressurePlate) return false;
        if (block instanceof BlockTripWire) return false;
        if (block instanceof BlockTripWireHook) return false;
        if (block instanceof BlockRailBase) return false;
        if (block instanceof BlockSlime) return false;
        return !(block instanceof BlockTNT);
    }

    public static Vec3 getHitVec(BlockPos blockPos, EnumFacing enumFacing, float yaw, float pitch) {
        MovingObjectPosition movingObjectPosition = RotationUtil.rayTrace(yaw, pitch, BlockUtil.mc.playerController.getBlockReachDistance(), 1.0f);
        if (movingObjectPosition != null) {
            if (movingObjectPosition.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK) {
                if (movingObjectPosition.getBlockPos().equals(blockPos)) {
                    if (movingObjectPosition.sideHit == enumFacing) {
                        return movingObjectPosition.hitVec;
                    }
                }
            }
        }
        return BlockUtil.getClickVec(blockPos, enumFacing);
    }

    public static Vec3 getClickVec(BlockPos blockPos, EnumFacing enumFacing) {
        Block block = BlockUtil.mc.theWorld.getBlockState(blockPos).getBlock();
        Vec3 vec3 = new Vec3((double) blockPos.getX() + Math.min(Math.max(RandomUtil.nextDouble(0.0, 1.0), block.getBlockBoundsMinX()), block.getBlockBoundsMaxX()), (double) blockPos.getY() + Math.min(Math.max(RandomUtil.nextDouble(0.0, 1.0), block.getBlockBoundsMinY()), block.getBlockBoundsMaxY()), (double) blockPos.getZ() + Math.min(Math.max(RandomUtil.nextDouble(0.0, 1.0), block.getBlockBoundsMinZ()), block.getBlockBoundsMaxZ()));
        switch (enumFacing) {
            default: {
                return new Vec3(vec3.xCoord, (double) blockPos.getY() + block.getBlockBoundsMinY(), vec3.zCoord);
            }
            case UP: {
                return new Vec3(vec3.xCoord, (double) blockPos.getY() + block.getBlockBoundsMaxY(), vec3.zCoord);
            }
            case NORTH: {
                return new Vec3(vec3.xCoord, vec3.yCoord, (double) blockPos.getZ() + block.getBlockBoundsMinZ());
            }
            case EAST: {
                return new Vec3((double) blockPos.getX() + block.getBlockBoundsMaxX(), vec3.yCoord, vec3.zCoord);
            }
            case SOUTH: {
                return new Vec3(vec3.xCoord, vec3.yCoord, (double) blockPos.getZ() + block.getBlockBoundsMaxZ());
            }
            case WEST:
        }
        return new Vec3((double) blockPos.getX() + block.getBlockBoundsMinX(), vec3.yCoord, vec3.zCoord);
    }
}
