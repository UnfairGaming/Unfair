package cn.unfair.module.modules.player;

import cn.unfair.event.EventTarget;
import cn.unfair.event.types.EventType;
import cn.unfair.event.types.Priority;
import cn.unfair.events.SwapItemEvent;
import cn.unfair.events.UpdateEvent;
import cn.unfair.module.Module;
import cn.unfair.util.BlockUtil;
import cn.unfair.util.PacketUtil;
import cn.unfair.util.RayCastUtil;
import cn.unfair.util.RotationUtil;
import cn.unfair.util.player.SimulatedPlayer;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.client.C0APacketAnimation;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.MathHelper;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;

public final class AutoLadder extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();

    private int state;
    private int oldSlot = -1;
    private int blockSlot = -1;
    private int ladderSlot = -1;
    private int activeTicks;
    private BlockPos landing;
    private BlockPos support;
    private BlockPos top;
    private EnumFacing forward;

    public AutoLadder() {
        super("AutoLadder", false);
    }

    @EventTarget(Priority.HIGHEST)
    public void onSwap(SwapItemEvent event) {
        if (this.isEnabled() && this.state != 0) {
            event.setCancelled(true);
        }
    }

    @EventTarget(Priority.HIGHEST)
    public void onUpdate(UpdateEvent event) {
        if (!this.isEnabled() || event.getType() != EventType.PRE || mc.thePlayer == null
                || mc.theWorld == null || !mc.playerController.gameIsSurvivalOrAdventure()) {
            return;
        }

        if (this.state != 0) {
            if (++this.activeTicks > 20 || mc.thePlayer.onGround) {
                this.clear(true);
                return;
            }
        }

        if (this.state == 0) {
            if (mc.thePlayer.onGround || mc.thePlayer.motionY >= -0.08D || mc.thePlayer.fallDistance < 2.0F) {
                return;
            }
            this.forward = EnumFacing.fromAngle(event.getNewYaw());
            this.landing = this.predictLanding();
            this.ladderSlot = this.findLadder();
            this.blockSlot = this.findFullBlock();
            if (this.landing == null || this.ladderSlot < 0) {
                return;
            }
            this.support = this.landing.offset(this.forward);
            this.top = this.support.up();
            if (BlockUtil.isReplaceable(this.top) && this.blockSlot < 0) {
                this.clear(false);
                return;
            }
            this.oldSlot = mc.thePlayer.inventory.currentItem;
            this.state = BlockUtil.isReplaceable(this.top) ? 1 : 3;
        }

        if (this.state == 1) {
            if (!this.reachable(this.support)) {
                BlockPos predicted = this.predictLanding();
                if (predicted != null) {
                    this.landing = predicted;
                    this.support = predicted.offset(this.forward);
                    this.top = this.support.up();
                }
            }
            if (!this.validSupport()) {
                this.clear(true);
                return;
            }
            if (this.switchSlot(this.blockSlot)) {
                return;
            }
            this.aim(event, this.support, EnumFacing.UP);
            if (this.place(event, this.support, EnumFacing.UP)) {
                this.state = 3;
            }
            return;
        }

        if (this.state == 3) {
            this.switchSlot(this.ladderSlot);
            this.state = 4;
            return;
        }

        if (this.state == 4) {
            EnumFacing face = this.forward.getOpposite();
            if (!this.reachable(this.top)) {
                BlockPos predicted = this.predictLanding();
                if (predicted != null) {
                    this.landing = predicted;
                    this.support = predicted.offset(this.forward);
                    this.top = this.support.up();
                }
            }
            if (!this.canPlaceLadder(face)) {
                return;
            }
            this.aim(event, this.top, face);
            if (this.place(event, this.top, face)) {
                this.state = 5;
            }
            return;
        }

        if (this.state == 5) {
            this.state = 6;
            return;
        }

        if (this.state == 6) {
            this.switchSlot(this.oldSlot);
            this.clear(false);
            return;
        }
    }

    @EventTarget(Priority.HIGHEST)
    public void onPostUpdate(UpdateEvent event) {
        if (!this.isEnabled() || event.getType() != EventType.POST || mc.thePlayer == null) {
            return;
        }
        if (this.state == 5) {
            this.state = 6;
        }
    }

    private boolean switchSlot(int slot) {
        if (slot < 0) {
            return false;
        }
        if (mc.thePlayer.inventory.currentItem != slot) {
            mc.thePlayer.inventory.currentItem = slot;
            mc.playerController.syncCurrentPlayItem();
            return true;
        }
        return false;
    }

    private boolean place(UpdateEvent event, BlockPos target, EnumFacing face) {
        if (!this.reachable(target)) {
            return false;
        }
        MovingObjectPosition hit = RayCastUtil.rayTrace(event.getNewYaw(), event.getNewPitch(),
                mc.playerController.getBlockReachDistance(), 1.0F);
        if (hit == null || hit.typeOfHit != MovingObjectPosition.MovingObjectType.BLOCK
                || !target.equals(hit.getBlockPos()) || hit.sideHit != face) {
            return false;
        }
        if (mc.playerController.onPlayerRightClick(mc.thePlayer, mc.theWorld,
                mc.thePlayer.inventory.getCurrentItem(), target, face, hit.hitVec)) {
            PacketUtil.sendPacket(new C0APacketAnimation());
            return true;
        }
        return false;
    }

    private void aim(UpdateEvent event, BlockPos target, EnumFacing face) {
        Vec3 hit = new Vec3(target.getX() + 0.5D, target.getY() + 0.5D, target.getZ() + 0.5D);
        switch (face) {
            case UP: hit = new Vec3(hit.xCoord, target.getY() + 0.99D, hit.zCoord); break;
            case DOWN: hit = new Vec3(hit.xCoord, target.getY() + 0.01D, hit.zCoord); break;
            case NORTH: hit = new Vec3(hit.xCoord, hit.yCoord, target.getZ() + 0.01D); break;
            case SOUTH: hit = new Vec3(hit.xCoord, hit.yCoord, target.getZ() + 0.99D); break;
            case WEST: hit = new Vec3(target.getX() + 0.01D, hit.yCoord, hit.zCoord); break;
            case EAST: hit = new Vec3(target.getX() + 0.99D, hit.yCoord, hit.zCoord); break;
            default: break;
        }
        float[] rotation = RotationUtil.getRotations(hit.xCoord, hit.yCoord, hit.zCoord,
                mc.thePlayer.posX, mc.thePlayer.posY + mc.thePlayer.getEyeHeight(), mc.thePlayer.posZ);
        event.setRotation(rotation[0], rotation[1], 6);
        event.setPervRotation(rotation[0], 6);
    }

    private boolean validSupport() {
        Block block = mc.theWorld.getBlockState(this.support).getBlock();
        return block != Blocks.air && !BlockUtil.isReplaceable(block) && BlockUtil.isSolid(block);
    }

    private boolean canPlaceLadder(EnumFacing face) {
        if (!mc.theWorld.getBlockState(this.top).getBlock().isNormalCube()
                || !BlockUtil.isReplaceable(this.top.offset(face))) {
            return false;
        }
        AxisAlignedBB box = this.ladderBox(this.top.offset(face), face);
        return !box.intersectsWith(mc.thePlayer.getEntityBoundingBox());
    }

    private AxisAlignedBB ladderBox(BlockPos pos, EnumFacing face) {
        double x1 = pos.getX(), y1 = pos.getY(), z1 = pos.getZ();
        double x2 = x1 + 1.0D, y2 = y1 + 1.0D, z2 = z1 + 1.0D, t = 0.125D;
        if (face == EnumFacing.NORTH) z1 = z2 - t;
        if (face == EnumFacing.SOUTH) z2 = z1 + t;
        if (face == EnumFacing.WEST) x1 = x2 - t;
        if (face == EnumFacing.EAST) x2 = x1 + t;
        return new AxisAlignedBB(x1, y1, z1, x2, y2, z2);
    }

    private boolean reachable(BlockPos pos) {
        Vec3 eyes = mc.thePlayer.getPositionEyes(1.0F);
        double x = MathHelper.clamp_double(eyes.xCoord, pos.getX(), pos.getX() + 1.0D);
        double y = MathHelper.clamp_double(eyes.yCoord, pos.getY(), pos.getY() + 1.0D);
        double z = MathHelper.clamp_double(eyes.zCoord, pos.getZ(), pos.getZ() + 1.0D);
        double reach = mc.playerController.getBlockReachDistance();
        return eyes.squareDistanceTo(new Vec3(x, y, z)) <= reach * reach;
    }

    private BlockPos predictLanding() {
        SimulatedPlayer simulated = SimulatedPlayer.fromClientPlayer(mc.thePlayer.movementInput, false, true);
        simulated.rotationYaw = mc.thePlayer.rotationYaw;
        BlockPos lastCandidate = null;
        for (int i = 0; i < 30; i++) {
            simulated.tick();
            Vec3 p = simulated.getPos();
            BlockPos pos = new BlockPos(MathHelper.floor_double(p.xCoord), MathHelper.floor_double(p.yCoord - 0.01D), MathHelper.floor_double(p.zCoord));
            Block block = mc.theWorld.getBlockState(pos).getBlock();
            if (block != Blocks.air && BlockUtil.isSolid(block) && BlockUtil.isReplaceable(pos.up())) {
                lastCandidate = pos;
            }
            if (simulated.onGround) {
                return lastCandidate;
            }
        }
        return lastCandidate;
    }

    private int findLadder() {
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.thePlayer.inventory.getStackInSlot(i);
            if (stack != null && stack.getItem() instanceof ItemBlock && ((ItemBlock) stack.getItem()).getBlock() == Blocks.ladder) return i;
        }
        return -1;
    }

    private int findFullBlock() {
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.thePlayer.inventory.getStackInSlot(i);
            if (stack == null || !(stack.getItem() instanceof ItemBlock)) continue;
            Block block = ((ItemBlock) stack.getItem()).getBlock();
            if (block != Blocks.ladder && block.isFullCube() && BlockUtil.isSolid(block) && !BlockUtil.isInteractable(block)) return i;
        }
        return -1;
    }

    private void clear(boolean restore) {
        if (restore && oldSlot >= 0 && mc.thePlayer != null) {
            mc.thePlayer.inventory.currentItem = oldSlot;
            mc.playerController.syncCurrentPlayItem();
        }
        state = 0;
        activeTicks = 0;
        oldSlot = blockSlot = ladderSlot = -1;
        landing = support = top = null;
        forward = null;
    }

    @Override public void onEnabled() { clear(false); }
    @Override public void onDisabled() { clear(true); }
}
