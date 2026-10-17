package cn.unfair.module.modules.player;

import cn.unfair.Unfair;
import cn.unfair.event.EventTarget;
import cn.unfair.event.types.EventType;
import cn.unfair.event.types.Priority;
import cn.unfair.events.SwapItemEvent;
import cn.unfair.events.UpdateEvent;
import cn.unfair.module.Module;
import cn.unfair.module.modules.world.Scaffold;
import cn.unfair.property.properties.BooleanProperty;
import cn.unfair.property.properties.FloatProperty;
import cn.unfair.util.player.PacketUtil;
import cn.unfair.util.rotation.RayCastUtil;
import cn.unfair.util.world.BlockUtil;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.client.C0APacketAnimation;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.MathHelper;
import net.minecraft.util.MovingObjectPosition;

public final class AutoLadder extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private static final double MIN_FALL_DISTANCE = 1.5D;
    private static final double DOWN_RAYTRACE_DISTANCE = 7.0D;
    private static final long PLACE_COOLDOWN_MS = 1000L;

    public final FloatProperty minimumFallDistance = new FloatProperty("MinimumFallDistance", 5.0F, 0.0F, 20.0F);
    public final BooleanProperty onlyWhileHoldingLadder = new BooleanProperty("OnlyWhileHoldingLadder", false);

    private int previousSlot = -1;
    private int blockSlot = -1;
    private int ladderSlot = -1;
    private BlockPos target;
    private EnumFacing direction;
    private EnumFacing face;
    private boolean ladderNext;
    private long placedAt;
    private boolean aiming;
    private float lastYaw;
    private float lastPitch;

    public AutoLadder() {
        super("AutoLadder", false);
    }

    @EventTarget(Priority.HIGHEST)
    public void onSwap(SwapItemEvent event) {
        if (this.isEnabled() && this.shouldLockInventorySlot()) {
            event.setCancelled(true);
        }
    }

    private boolean shouldLockInventorySlot() {
        return this.previousSlot != -1 || this.ladderNext || this.target != null;
    }

    @EventTarget(Priority.HIGHEST)
    public void onUpdate(UpdateEvent event) {
        if (!this.isEnabled() || event.getType() != EventType.PRE || mc.thePlayer == null || mc.theWorld == null
                || !mc.playerController.gameIsSurvivalOrAdventure()) {
            return;
        }
        if (mc.isGamePaused() || mc.thePlayer.capabilities.isFlying || mc.thePlayer.capabilities.isCreativeMode
                || this.shouldYieldPlacement()) {
            this.reset();
            return;
        }
        if (mc.thePlayer.onGround || mc.thePlayer.isOnLadder() || mc.thePlayer.fallDistance < MIN_FALL_DISTANCE) {
            this.reset();
            return;
        }
        long now = System.currentTimeMillis();
        if (Math.abs(now - this.placedAt) < PLACE_COOLDOWN_MS) {
            this.reset();
            return;
        }
        this.blockSlot = this.findBlockSlot();
        this.ladderSlot = this.findLadderSlot();
        if (this.blockSlot == -1 || this.ladderSlot == -1) {
            this.reset();
            return;
        }
        if (this.onlyWhileHoldingLadder.getValue() && this.target == null
                && mc.thePlayer.inventory.currentItem != this.ladderSlot) {
            this.reset();
            return;
        }
        MovingObjectPosition mop = RayCastUtil.rayTrace(mc.thePlayer.rotationYaw,
                mc.thePlayer.isSprinting() ? 80.0F : 90.0F, DOWN_RAYTRACE_DISTANCE, 1.0F);
        if (mop == null || mop.typeOfHit != MovingObjectPosition.MovingObjectType.BLOCK || mop.sideHit != EnumFacing.UP) {
            this.reset();
            return;
        }
        int slot;
        if (this.ladderNext) {
            this.ladderNext = false;
            slot = this.ladderSlot;
            this.target = this.target.up();
            this.face = this.direction.getOpposite();
            if (!BlockUtil.isReplaceable(this.target.offset(this.face))) {
                this.reset();
                return;
            }
        } else {
            this.previousSlot = mc.thePlayer.inventory.currentItem;
            slot = this.blockSlot;
            BlockPos ground = mop.getBlockPos();
            if (!BlockUtil.isReplaceable(ground.up())) {
                this.reset();
                return;
            }
            EnumFacing facing = mc.thePlayer.getHorizontalFacing();
            EnumFacing[] order = new EnumFacing[]{facing.rotateY(), facing.rotateYCCW(), facing, facing.getOpposite()};
            for (EnumFacing f : order) {
                BlockPos wall = ground.offset(f).up();
                if (BlockUtil.isReplaceable(wall) || !mc.theWorld.getBlockState(wall).getBlock().isFullBlock()) continue;
                slot = this.ladderSlot;
                this.target = wall;
                this.direction = f;
                this.face = this.direction.getOpposite();
                break;
            }
            if (this.target == null) {
                double x = mc.thePlayer.posX;
                double z = mc.thePlayer.posZ;
                double margin = 0.415D;
                double max = margin + 1.0D;
                for (EnumFacing f : order) {
                    BlockPos beside = ground.offset(f);
                    BlockPos above = beside.up();
                    if (x > above.getX() - margin && x < above.getX() + max
                            && z > above.getZ() - margin && z < above.getZ() + max) continue;
                    if (BlockUtil.isReplaceable(beside) || !BlockUtil.isReplaceable(above)) continue;
                    this.target = beside;
                    this.direction = f;
                    this.face = EnumFacing.UP;
                    break;
                }
            }
        }
        if (this.target == null) {
            this.reset();
            return;
        }
        if (mc.thePlayer.fallDistance < this.minFall()) {
            return;
        }
        this.switchSlot(slot);
        this.aim(event, this.target, this.face);
        if (this.place(event, this.target, this.face)) {
            if (slot == this.blockSlot) {
                this.ladderNext = true;
            } else {
                this.placedAt = now;
            }
        }
    }

    private void switchSlot(int slot) {
        if (slot < 0) {
            return;
        }
        if (mc.thePlayer.inventory.currentItem != slot) {
            mc.thePlayer.inventory.currentItem = slot;
            mc.playerController.syncCurrentPlayItem();
        }
    }

    private boolean place(UpdateEvent event, BlockPos target, EnumFacing face) {
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
        float[] rotation = this.toBlockFixed(target, face);
        event.setRotation(rotation[0], rotation[1], 6);
        event.setPervRotation(rotation[0], 6);
    }

    private float[] toBlockFixed(BlockPos pos, EnumFacing facing) {
        if (!this.aiming) {
            this.aiming = true;
            this.lastYaw = mc.thePlayer.rotationYaw;
            this.lastPitch = mc.thePlayer.rotationPitch;
        }
        double x = pos.getX() + 0.45D - mc.thePlayer.posX;
        double y = pos.getY() + 0.45D - (mc.thePlayer.posY + (double) mc.thePlayer.getEyeHeight());
        double z = pos.getZ() + 0.45D - mc.thePlayer.posZ;
        if (facing != null) {
            x += (double) facing.getDirectionVec().getX() * 0.5D;
            y += (double) facing.getDirectionVec().getY() * 0.5D;
            z += (double) facing.getDirectionVec().getZ() * 0.5D;
        }
        double dist = Math.sqrt(x * x + z * z);
        float yaw = (float) (Math.atan2(z, x) * 180.0D / Math.PI) - 90.0F;
        float pitch = (float) (-(Math.atan2(y, dist) * 180.0D / Math.PI));
        yaw = this.lastYaw + MathHelper.wrapAngleTo180_float(yaw - this.lastYaw);
        pitch = MathHelper.clamp_float(this.lastPitch + MathHelper.wrapAngleTo180_float(pitch - this.lastPitch), -90.0F, 90.0F);
        float sensitivity = mc.gameSettings.mouseSensitivity * 0.6F + 0.2F;
        double gcd = (double) (sensitivity * sensitivity * sensitivity) * 1.2D;
        if (gcd <= 0.0D || Double.isNaN(gcd) || Double.isInfinite(gcd)) {
            return new float[]{yaw, pitch};
        }
        float yawStep = (float) ((double) Math.round((double) (yaw - this.lastYaw) / gcd) * gcd);
        float pitchStep = (float) ((double) Math.round((double) (pitch - this.lastPitch) / gcd) * gcd);
        this.lastYaw += yawStep;
        this.lastPitch = MathHelper.clamp_float(this.lastPitch + pitchStep, -90.0F, 90.0F);
        return new float[]{this.lastYaw, this.lastPitch};
    }

    private int findBlockSlot() {
        int most = 0;
        int slot = -1;
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.thePlayer.inventory.getStackInSlot(i);
            if (!this.isScaffoldBlock(stack)) continue;
            int count = stack.stackSize;
            if (count <= most) continue;
            slot = i;
            most = count;
        }
        return slot;
    }

    private boolean isScaffoldBlock(ItemStack stack) {
        if (stack == null || !(stack.getItem() instanceof ItemBlock)) return false;
        Block block = ((ItemBlock) stack.getItem()).getBlock();
        return block != Blocks.ladder && BlockUtil.isSolid(block) && !BlockUtil.isInteractable(block);
    }

    private int findLadderSlot() {
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.thePlayer.inventory.getStackInSlot(i);
            if (stack != null && stack.getItem() instanceof ItemBlock
                    && ((ItemBlock) stack.getItem()).getBlock() == Blocks.ladder) {
                return i;
            }
        }
        return -1;
    }

    private boolean shouldYieldPlacement() {
        if (Unfair.moduleManager == null) return false;
        Scaffold scaffold = (Scaffold) Unfair.moduleManager.getModule(Scaffold.class);
        if (scaffold != null && scaffold.isEnabled()) return true;
        AutoMLG autoMLG = (AutoMLG) Unfair.moduleManager.getModule(AutoMLG.class);
        return autoMLG != null && autoMLG.isEnabled() && this.hasWaterBucket();
    }

    private boolean hasWaterBucket() {
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.thePlayer.inventory.getStackInSlot(i);
            if (stack != null && stack.getItem() == Items.water_bucket) return true;
        }
        return false;
    }

    private double minFall() {
        PotionEffect effect = mc.thePlayer.getActivePotionEffect(Potion.jump);
        return (double) this.minimumFallDistance.getValue() - MIN_FALL_DISTANCE
                + (double) (effect == null ? 0 : effect.getAmplifier() + 1);
    }

    private void reset() {
        if (this.previousSlot != -1) {
            mc.thePlayer.inventory.currentItem = this.previousSlot;
            mc.playerController.syncCurrentPlayItem();
            this.previousSlot = -1;
        }
        this.ladderNext = false;
        this.target = null;
        this.direction = null;
        this.face = null;
        this.placedAt = 0L;
        this.aiming = false;
    }

    @Override
    public void onEnabled() {
        this.reset();
    }

    @Override
    public void onDisabled() {
        this.reset();
    }
}