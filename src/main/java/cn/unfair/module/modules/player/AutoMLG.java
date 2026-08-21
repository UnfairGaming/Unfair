package cn.unfair.module.modules.player;

import cn.unfair.event.EventTarget;
import cn.unfair.event.types.EventType;
import cn.unfair.event.types.Priority;
import cn.unfair.events.UpdateEvent;
import cn.unfair.events.SwapItemEvent;
import cn.unfair.module.Module;
import cn.unfair.util.BlockUtil;
import cn.unfair.util.PacketUtil;
import cn.unfair.util.RayCastUtil;
import cn.unfair.util.RotationUtil;
import cn.unfair.util.player.SimulatedPlayer;
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
    private static boolean active;

    private int waterSlot = -1;
    private int previousSlot = -1;
    private int restoreSlotTicks = 0;
    private BlockPos targetPos;
    private BlockPos recoveryPos;
    private boolean recoveryPending;

    public AutoMLG() {
        super("AutoMLG", false);
    }

    public static boolean isPreparingMLG() {
        return preTicks >= 0;
    }

    public static boolean isActiveMLG() {
        return active;
    }

    public static boolean shouldLockInventorySlot() {
        return active || preTicks >= 0;
    }

    @EventTarget(Priority.HIGHEST)
    public void onSwap(SwapItemEvent event) {
        if (this.isEnabled() && shouldLockInventorySlot()) {
            event.setCancelled(true);
        }
    }

    @EventTarget(Priority.HIGHEST)
    public void onUpdate(UpdateEvent event) {
        if (!this.isEnabled()) return;

        if (event.getType() != EventType.PRE || mc.thePlayer == null || mc.theWorld == null
                || !mc.playerController.gameIsSurvivalOrAdventure()) {
            return;
        }

        if (this.recoveryPending) {
            this.recoverWater(event);
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
            BlockPos updatedTarget = this.findCurrentLandingBlock();
            if (updatedTarget == null) {
                this.resetState(false);
                return;
            }
            this.targetPos = updatedTarget;
            this.rotateToTarget(event);
        }

        if (preTicks >= 3) {
            if (this.placeWater(event)) {
                this.finishPlacement();
            }
            return;
        }

        if (mc.thePlayer.fallDistance <= 3.0F) {
            return;
        }

        BlockPos landingPos = this.findCurrentLandingBlock();
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
            active = true;
        } else if (this.targetPos != null && this.placeWater(event)) {
            this.finishPlacement();
        }
    }

    private void rotateToTarget(UpdateEvent event) {
        this.rotateToPosition(event, this.targetPos, 1.0D);
    }

    private void rotateToPosition(UpdateEvent event, BlockPos pos, double yOffset) {
        if (pos == null) {
            return;
        }
        Vec3 hitVec = new Vec3(
                (double) pos.getX() + 0.5D,
                (double) pos.getY() + yOffset,
                (double) pos.getZ() + 0.5D
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

    private boolean placeWater(UpdateEvent event) {
        if (this.targetPos == null || this.waterSlot == -1) {
            return false;
        }
        BlockPos currentTarget = this.findCurrentLandingBlock();
        if (currentTarget == null) {
            return false;
        }
        this.targetPos = currentTarget;
        this.rotateToTarget(event);
        if (!this.canReachTarget(this.targetPos)) {
            return false;
        }
        float yaw = event.getNewYaw();
        float pitch = event.getNewPitch();
        MovingObjectPosition mop = RayCastUtil.rayTrace(yaw, pitch, mc.playerController.getBlockReachDistance(), 1.0F);
        if (mop == null
                || mop.typeOfHit != MovingObjectPosition.MovingObjectType.BLOCK
                || !mop.getBlockPos().equals(this.targetPos)
                || mop.sideHit != net.minecraft.util.EnumFacing.UP) {
            return false;
        }
        this.switchToWaterSlot();
        boolean placed = this.useCurrentItemWithRotation(yaw, pitch);
        PacketUtil.sendPacket(new C0APacketAnimation());
        return placed;
    }

    private void recoverWater(UpdateEvent event) {
        if (this.recoveryPos == null) {
            this.finishRecovery();
            return;
        }

        if (!mc.thePlayer.onGround) {
            return;
        }

        int bucketSlot = this.findEmptyBucketSlot();
        if (bucketSlot == -1 || !this.canReachTarget(this.recoveryPos) || !this.isWaterBlock(this.recoveryPos)) {
            this.finishRecovery();
            return;
        }

        this.rotateToPosition(event, this.recoveryPos, 0.5D);

        MovingObjectPosition mop = RayCastUtil.rayTraceWater(
                event.getNewYaw(),
                event.getNewPitch(),
                mc.playerController.getBlockReachDistance(),
                1.0F
        );

        if (mop == null
                || mop.typeOfHit != MovingObjectPosition.MovingObjectType.BLOCK
                || !this.recoveryPos.equals(mop.getBlockPos())
                || !this.isWaterBlock(this.recoveryPos)) {
            this.finishRecovery();
            return;
        }

        this.waterSlot = bucketSlot;
        this.switchToWaterSlot();
        if (this.useCurrentItemWithRotation(event.getNewYaw(), event.getNewPitch())) {
            PacketUtil.sendPacket(new C0APacketAnimation());
        }
        this.finishRecovery();
    }

    private void finishRecovery() {
        this.waterSlot = -1;
        this.recoveryPos = null;
        this.recoveryPending = false;
        this.restoreSlotTicks = 1;
    }

    private boolean useCurrentItemWithRotation(float yaw, float pitch) {
        float oldYaw = mc.thePlayer.rotationYaw;
        float oldPitch = mc.thePlayer.rotationPitch;
        mc.thePlayer.rotationYaw = yaw;
        mc.thePlayer.rotationPitch = pitch;
        try {
            return mc.playerController.sendUseItem(mc.thePlayer, mc.theWorld, mc.thePlayer.inventory.getCurrentItem());
        } finally {
            mc.thePlayer.rotationYaw = oldYaw;
            mc.thePlayer.rotationPitch = oldPitch;
        }
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

    private int findEmptyBucketSlot() {
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.thePlayer.inventory.getStackInSlot(i);
            if (stack != null && stack.getItem() == Items.bucket) {
                return i;
            }
        }
        return -1;
    }

    private boolean isWaterBlock(BlockPos pos) {
        Block block = mc.theWorld.getBlockState(pos).getBlock();
        return block == Blocks.water || block == Blocks.flowing_water;
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

    private BlockPos findCurrentLandingBlock() {
        SimulatedPlayer simulated = SimulatedPlayer.fromClientPlayer(mc.thePlayer.movementInput, false, true);
        simulated.rotationYaw = mc.thePlayer.rotationYaw;
        BlockPos lastCandidate = null;

        for (int tick = 0; tick < 40; tick++) {
            simulated.tick();
            Vec3 position = simulated.getPos();
            int x = MathHelper.floor_double(position.xCoord);
            int z = MathHelper.floor_double(position.zCoord);
            int feetY = MathHelper.floor_double(position.yCoord - 0.01D);
            for (int y = feetY; y >= Math.max(0, feetY - 8); y--) {
                BlockPos pos = new BlockPos(x, y, z);
                Block block = mc.theWorld.getBlockState(pos).getBlock();
                if (block != Blocks.air && BlockUtil.isSolid(block) && BlockUtil.isReplaceable(pos.up())) {
                    lastCandidate = pos;
                    break;
                }
            }
            if (simulated.onGround) {
                return lastCandidate;
            }
        }
        return lastCandidate != null ? lastCandidate : findVerticalLandingBlock();
    }

    private BlockPos findVerticalLandingBlock() {
        int x = MathHelper.floor_double(mc.thePlayer.posX);
        int z = MathHelper.floor_double(mc.thePlayer.posZ);
        int startY = MathHelper.floor_double(mc.thePlayer.posY + mc.thePlayer.motionY);
        for (int y = startY; y >= Math.max(0, startY - 8); y--) {
            BlockPos pos = new BlockPos(x, y, z);
            Block block = mc.theWorld.getBlockState(pos).getBlock();
            if (block != Blocks.air && BlockUtil.isSolid(block) && BlockUtil.isReplaceable(pos.up())) {
                return pos;
            }
        }
        return null;
    }

    private void finishPlacement() {
        preTicks = -1;
        this.waterSlot = -1;
        this.recoveryPos = this.targetPos == null ? null : this.targetPos.up();
        this.recoveryPending = this.recoveryPos != null;
        this.targetPos = null;
        this.restoreSlotTicks = 0;
    }

    private void restorePreviousSlot() {
        if (this.previousSlot != -1 && mc.thePlayer != null && mc.thePlayer.inventory.currentItem != this.previousSlot) {
            mc.thePlayer.inventory.currentItem = this.previousSlot;
            mc.playerController.syncCurrentPlayItem();
        }
        this.previousSlot = -1;
        active = false;
    }

    private void resetState(boolean restoreSlot) {
        preTicks = -1;
        active = false;
        this.waterSlot = -1;
        this.targetPos = null;
        this.recoveryPos = null;
        this.recoveryPending = false;
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
