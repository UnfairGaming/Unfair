package cn.unfair.module.modules.player;

import cn.unfair.event.EventTarget;
import cn.unfair.event.types.EventType;
import cn.unfair.events.UpdateEvent;
import cn.unfair.module.Module;
import cn.unfair.property.properties.BooleanProperty;
import cn.unfair.util.BlockUtil;
import cn.unfair.util.PacketUtil;
import cn.unfair.util.RotationUtil;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.client.C0APacketAnimation;
import net.minecraft.util.BlockPos;
import net.minecraft.util.MathHelper;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;

public class AutoMLG extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private static int preTicks = -1;

    public final BooleanProperty cubecraft = new BooleanProperty("cubecraft", true);

    private int waterSlot = -1;
    private int previousSlot = -1;
    private int restoreSlotTicks = 0;
    private BlockPos targetPos;

    public AutoMLG() {
        super("AutoMLG", false);
    }

    public static boolean isPreparingMLG() {
        return preTicks >= 0;
    }

    @EventTarget
    public void onUpdate(UpdateEvent event) {
        if (event.getType() != EventType.PRE || mc.thePlayer == null || mc.theWorld == null
                || !mc.playerController.gameIsSurvivalOrAdventure()) {
            return;
        }

        if (this.restoreSlotTicks > 0 && --this.restoreSlotTicks == 0) {
            this.restorePreviousSlot();
        }

        int slot = this.findWaterBucketSlot();
        if (slot == -1 || mc.thePlayer.onGround || mc.thePlayer.fallDistance <= 1.0F) {
            if (preTicks >= 0) {
                this.resetState(false);
            }
            return;
        }

        if (preTicks >= 0) {
            preTicks++;
            BlockPos updatedTarget = this.findLandingBlock();
            if (updatedTarget == null) {
                this.resetState(false);
                return;
            }
            this.targetPos = updatedTarget;
            this.rotateToTarget(event);
        }

        if (preTicks >= (this.cubecraft.getValue() ? 5 : 3)) {
            if (this.placeWater(event.getNewYaw(), event.getNewPitch())) {
                this.finishPlacement();
            }
            return;
        }

        if (mc.thePlayer.fallDistance <= 3.0F) {
            return;
        }

        BlockPos landingPos = this.findLandingBlock();
        if (landingPos == null) {
            return;
        }

        if (preTicks == -1) {
            this.waterSlot = slot;
            this.targetPos = landingPos;
            if (this.previousSlot == -1) {
                this.previousSlot = mc.thePlayer.inventory.currentItem;
            }
            this.switchToWaterSlot();
            this.rotateToTarget(event);
            preTicks = 0;
        } else if (this.targetPos != null && this.placeWater(event.getNewYaw(), event.getNewPitch())) {
            this.finishPlacement();
        }
    }

    private void rotateToTarget(UpdateEvent event) {
        if (this.targetPos == null) {
            return;
        }
        Vec3 hitVec = new Vec3(
                (double) this.targetPos.getX() + 0.5D,
                (double) this.targetPos.getY() + 1.0D,
                (double) this.targetPos.getZ() + 0.5D
        );
        float[] rotations = RotationUtil.getRotations(
                hitVec.xCoord,
                hitVec.yCoord,
                hitVec.zCoord,
                mc.thePlayer.posX,
                mc.thePlayer.posY + (double) mc.thePlayer.getEyeHeight(),
                mc.thePlayer.posZ
        );
        event.setRotation(rotations[0], rotations[1], 6);
        event.setPervRotation(rotations[0], 6);
    }

    private boolean placeWater(float yaw, float pitch) {
        if (this.targetPos == null || this.waterSlot == -1) {
            return false;
        }
        if (!this.canReachTarget(this.targetPos)) {
            return false;
        }
        MovingObjectPosition mop = RotationUtil.rayTrace(yaw, pitch, mc.playerController.getBlockReachDistance(), 1.0F);
        if (mop == null
                || mop.typeOfHit != MovingObjectPosition.MovingObjectType.BLOCK
                || !mop.getBlockPos().equals(this.targetPos)
                || mop.sideHit != net.minecraft.util.EnumFacing.UP) {
            return false;
        }
        this.switchToWaterSlot();
        boolean placed = mc.playerController.sendUseItem(mc.thePlayer, mc.theWorld, mc.thePlayer.inventory.getCurrentItem());
        PacketUtil.sendPacket(new C0APacketAnimation());
        return placed;
    }

    private void switchToWaterSlot() {
        if (this.waterSlot != -1 && mc.thePlayer.inventory.currentItem != this.waterSlot) {
            mc.thePlayer.inventory.currentItem = this.waterSlot;
            mc.playerController.syncCurrentPlayItem();
        }
    }

    private int findWaterBucketSlot() {
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.thePlayer.inventory.getStackInSlot(i);
            if (stack != null && stack.getItem() == Items.water_bucket) {
                return i;
            }
        }
        return -1;
    }

    private boolean canReachTarget(BlockPos pos) {
        double reach = mc.playerController.getBlockReachDistance();
        double minDistance = Double.MAX_VALUE;
        double[] eyeHeights = new double[]{mc.thePlayer.getEyeHeight(), mc.thePlayer.getEyeHeight() - 0.08D};

        for (double eyeHeight : eyeHeights) {
            Vec3 eyes = new Vec3(mc.thePlayer.posX, mc.thePlayer.posY + eyeHeight, mc.thePlayer.posZ);
            double closestX = MathHelper.clamp_double(eyes.xCoord, pos.getX(), pos.getX() + 1.0D);
            double closestY = MathHelper.clamp_double(eyes.yCoord, pos.getY(), pos.getY() + 1.0D);
            double closestZ = MathHelper.clamp_double(eyes.zCoord, pos.getZ(), pos.getZ() + 1.0D);
            double distance = eyes.squareDistanceTo(new Vec3(closestX, closestY, closestZ));
            minDistance = Math.min(minDistance, distance);
        }

        return minDistance <= reach * reach;
    }

    private BlockPos findLandingBlock() {
        double predictedX = mc.thePlayer.posX + mc.thePlayer.motionX;
        double predictedZ = mc.thePlayer.posZ + mc.thePlayer.motionZ;
        int x = MathHelper.floor_double(predictedX);
        int z = MathHelper.floor_double(predictedZ);
        int startY = MathHelper.floor_double(mc.thePlayer.posY + mc.thePlayer.motionY);

        BlockPos bestPos = null;
        double bestDistance = 0.0D;
        for (int y = startY; y >= Math.max(0, startY - 8); y--) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    BlockPos pos = new BlockPos(x + dx, y, z + dz);
                    Block block = mc.theWorld.getBlockState(pos).getBlock();
                    if (block != Blocks.air
                            && BlockUtil.isSolid(block)
                            && BlockUtil.isReplaceable(pos.up())) {
                        double distance = mc.thePlayer.getDistanceSq(
                                (double) pos.getX() + 0.5D,
                                (double) pos.getY() + 1.0D,
                                (double) pos.getZ() + 0.5D
                        );
                        if (bestPos == null || distance < bestDistance) {
                            bestPos = pos;
                            bestDistance = distance;
                        }
                    }
                }
            }
        }

        return bestPos;
    }

    private void finishPlacement() {
        preTicks = -1;
        this.waterSlot = -1;
        this.targetPos = null;
        this.restoreSlotTicks = 2;
    }

    private void restorePreviousSlot() {
        if (this.previousSlot != -1 && mc.thePlayer != null && mc.thePlayer.inventory.currentItem != this.previousSlot) {
            mc.thePlayer.inventory.currentItem = this.previousSlot;
            mc.playerController.syncCurrentPlayItem();
        }
        this.previousSlot = -1;
    }

    private void resetState(boolean restoreSlot) {
        preTicks = -1;
        this.waterSlot = -1;
        this.targetPos = null;
        this.restoreSlotTicks = 0;
        if (restoreSlot) {
            this.restorePreviousSlot();
        } else {
            this.previousSlot = -1;
        }
    }

    @Override
    public void onEnabled() {
        this.resetState(false);
    }

    @Override
    public void onDisabled() {
        this.resetState(true);
    }
}
