package cn.unfair.module.modules.player;

import cn.unfair.Unfair;
import cn.unfair.event.EventTarget;
import cn.unfair.event.types.EventType;
import cn.unfair.event.types.Priority;
import cn.unfair.events.LoadWorldEvent;
import cn.unfair.events.UpdateEvent;
import cn.unfair.module.Module;
import cn.unfair.module.modules.combat.KillAura;
import cn.unfair.property.properties.BooleanProperty;
import cn.unfair.property.properties.IntProperty;
import cn.unfair.util.PacketUtil;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.client.C08PacketPlayerBlockPlacement;
import net.minecraft.util.BlockPos;

public class AutoMLG extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();

    private static final int IDLE = 0;
    private static final int SWITCH = 1;   // moved to bucket slot, waiting a tick like a real swap
    private static final int PLACE = 2;    // aim down + send place packet
    private static final int COLLECT = 3;  // after landing: aim down + send collect packet
    private static final int RESTORE = 4;  // switch back to the original slot

    public final IntProperty minFallDistance = new IntProperty("Min Fall Distance", 3, 1, 30);
    public final IntProperty placeDistance = new IntProperty("Place Distance", 3, 1, 8);
    public final IntProperty switchDelay = new IntProperty("Switch Delay", 1, 0, 10);
    public final BooleanProperty collectWater = new BooleanProperty("Collect Water", true);
    public final IntProperty collectDelay = new IntProperty("Collect Delay", 2, 0, 10);
    public final BooleanProperty inventoryCheck = new BooleanProperty("Inventory Check", true);

    private int phase = IDLE;
    private int oldSlot = -1;
    private int bucketSlot = -1;
    private int phaseTicks = 0;
    private boolean pausedOthers = false;

    public AutoMLG() {
        super("AutoMLG", false);
    }

    @Override
    public void onEnabled() {
        this.reset();
    }

    @Override
    public void onDisabled() {
        this.restoreSlot();
        this.releaseOthers();
        this.reset();
    }

    @EventTarget
    public void onLoadWorld(LoadWorldEvent event) {
        this.releaseOthers();
        this.reset();
    }

    private void reset() {
        this.phase = IDLE;
        this.oldSlot = -1;
        this.bucketSlot = -1;
        this.phaseTicks = 0;
    }

    private boolean isInventoryBlocked() {
        return this.inventoryCheck.getValue() && mc.currentScreen instanceof GuiContainer;
    }

    @EventTarget(Priority.HIGHEST)
    public void onUpdate(UpdateEvent event) {
        if (!this.isEnabled() || event.getType() != EventType.PRE || mc.thePlayer == null || mc.theWorld == null) {
            return;
        }
        if (this.isInventoryBlocked()) {
            this.abort();
            return;
        }

        if (this.phase == IDLE) {
            if (this.shouldClutch()) {
                this.beginClutch();
            } else {
                return;
            }
        }

        this.pauseOthers();
        this.handleClutch(event);
    }

    private boolean shouldClutch() {
        if (mc.thePlayer.onGround || mc.thePlayer.motionY >= 0.0D) {
            return false;
        }
        if (mc.thePlayer.capabilities.isFlying || mc.thePlayer.isInWater() || mc.thePlayer.isOnLadder()) {
            return false;
        }
        if (mc.thePlayer.fallDistance < this.minFallDistance.getValue()) {
            return false;
        }
        if (this.findWaterBucket() == -1) {
            return false;
        }
        return this.distanceToGround() <= this.placeDistance.getValue();
    }

    private void beginClutch() {
        this.oldSlot = mc.thePlayer.inventory.currentItem;
        this.bucketSlot = this.findWaterBucket();
        this.phase = SWITCH;
        this.phaseTicks = 0;
    }

    private void handleClutch(UpdateEvent event) {
        switch (this.phase) {
            case SWITCH:
                this.selectBucket();
                if (this.phaseTicks++ >= this.switchDelay.getValue()) {
                    this.phase = PLACE;
                    this.phaseTicks = 0;
                }
                break;
            case PLACE:
                this.selectBucket();
                this.aimDown(event);
                if (this.holdingWaterBucket()) {
                    this.useBucket();
                }
                this.phase = this.collectWater.getValue() ? COLLECT : RESTORE;
                this.phaseTicks = 0;
                break;
            case COLLECT:
                this.selectBucket();
                this.aimDown(event);
                if (this.inWaterOrGround() && this.phaseTicks++ >= this.collectDelay.getValue()) {
                    if (this.holdingEmptyBucket()) {
                        this.useBucket();
                    }
                    this.phase = RESTORE;
                    this.phaseTicks = 0;
                }
                break;
            case RESTORE:
            default:
                this.restoreSlot();
                this.releaseOthers();
                this.reset();
                break;
        }
    }

    private void aimDown(UpdateEvent event) {
        float yaw = mc.thePlayer.rotationYaw;
        float pitch = 90.0F;
        event.setRotation(yaw, pitch, 50);
        event.setPervRotation(yaw, 50);
        BetterRotation.bypassOnce();
    }

    private void useBucket() {
        mc.playerController.syncCurrentPlayItem();
        ItemStack stack = mc.thePlayer.inventory.getCurrentItem();
        PacketUtil.sendPacket(new C08PacketPlayerBlockPlacement(stack));
        mc.thePlayer.swingItem();
    }

    private void selectBucket() {
        if (this.bucketSlot >= 0 && this.bucketSlot < 9) {
            mc.thePlayer.inventory.currentItem = this.bucketSlot;
            mc.playerController.syncCurrentPlayItem();
        }
    }

    private void restoreSlot() {
        if (mc.thePlayer == null || mc.playerController == null) {
            return;
        }
        if (this.oldSlot >= 0 && this.oldSlot < 9) {
            mc.thePlayer.inventory.currentItem = this.oldSlot;
            mc.playerController.syncCurrentPlayItem();
        }
    }

    private boolean holdingWaterBucket() {
        ItemStack stack = mc.thePlayer.inventory.getCurrentItem();
        return stack != null && stack.getItem() == Items.water_bucket;
    }

    private boolean holdingEmptyBucket() {
        ItemStack stack = mc.thePlayer.inventory.getCurrentItem();
        return stack != null && stack.getItem() == Items.bucket;
    }

    private boolean inWaterOrGround() {
        return mc.thePlayer.isInWater() || mc.thePlayer.onGround;
    }

    private int findWaterBucket() {
        for (int slot = 0; slot < 9; slot++) {
            ItemStack stack = mc.thePlayer.inventory.getStackInSlot(slot);
            if (stack != null && stack.getItem() == Items.water_bucket) {
                return slot;
            }
        }
        return -1;
    }

    private double distanceToGround() {
        int startY = (int) Math.floor(mc.thePlayer.getEntityBoundingBox().minY);
        int x = (int) Math.floor(mc.thePlayer.posX);
        int z = (int) Math.floor(mc.thePlayer.posZ);
        for (int y = startY - 1; y >= 0; y--) {
            BlockPos pos = new BlockPos(x, y, z);
            Block block = mc.theWorld.getBlockState(pos).getBlock();
            Material material = block.getMaterial();
            if (material != Material.air && material != Material.water && material != Material.lava
                    && block.getCollisionBoundingBox(mc.theWorld, pos, mc.theWorld.getBlockState(pos)) != null) {
                return mc.thePlayer.getEntityBoundingBox().minY - (y + 1);
            }
        }
        return Double.MAX_VALUE;
    }

    private void pauseOthers() {
        KillAura killAura = (KillAura) Unfair.moduleManager.modules.get(KillAura.class);
        if (killAura != null) {
            killAura.attackDisabled = true;
        }
        Scaffold scaffold = (Scaffold) Unfair.moduleManager.modules.get(Scaffold.class);
        if (scaffold != null) {
            scaffold.setPaused(true);
        }
        this.pausedOthers = true;
    }

    private void releaseOthers() {
        if (!this.pausedOthers) {
            return;
        }
        KillAura killAura = (KillAura) Unfair.moduleManager.modules.get(KillAura.class);
        if (killAura != null) {
            killAura.attackDisabled = false;
        }
        Scaffold scaffold = (Scaffold) Unfair.moduleManager.modules.get(Scaffold.class);
        if (scaffold != null) {
            scaffold.setPaused(false);
        }
        this.pausedOthers = false;
    }

    private void abort() {
        this.restoreSlot();
        this.releaseOthers();
        this.reset();
    }

    @Override
    public String[] getSuffix() {
        return new String[]{this.collectWater.getValue() ? "Collect" : "Place"};
    }
}
