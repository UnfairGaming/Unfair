package cn.unfair.module.modules.movement;

import cn.unfair.Unfair;
import cn.unfair.enums.FloatModules;
import cn.unfair.event.EventTarget;
import cn.unfair.event.types.EventType;
import cn.unfair.event.types.Priority;
import cn.unfair.events.LivingUpdateEvent;
import cn.unfair.events.PacketEvent;
import cn.unfair.events.PlayerUpdateEvent;
import cn.unfair.events.RightClickMouseEvent;
import cn.unfair.module.Module;
import cn.unfair.property.properties.BooleanProperty;
import cn.unfair.property.properties.ModeProperty;
import cn.unfair.property.properties.PercentProperty;
import cn.unfair.util.BlockUtil;
import cn.unfair.util.ItemUtil;
import cn.unfair.util.KeyBindUtil;
import cn.unfair.util.PacketUtil;
import cn.unfair.util.PlayerUtil;
import cn.unfair.util.TeamUtil;
import cn.unfair.util.via.ModernOffhandInteraction;
import com.google.common.base.CaseFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.passive.EntityVillager;
import net.minecraft.item.EnumAction;
import net.minecraft.item.ItemPotion;
import net.minecraft.item.ItemStack;
import net.minecraft.network.Packet;
import net.minecraft.network.play.client.C07PacketPlayerDigging;
import net.minecraft.network.play.client.C08PacketPlayerBlockPlacement;
import net.minecraft.network.play.client.C0DPacketCloseWindow;
import net.minecraft.network.play.client.C0FPacketConfirmTransaction;
import net.minecraft.network.play.client.CPacketSwapItemWithOffHand;
import net.minecraft.network.play.server.S08PacketPlayerPosLook;
import net.minecraft.network.play.server.S12PacketEntityVelocity;
import net.minecraft.network.play.server.S2FPacketSetSlot;
import net.minecraft.util.BlockPos;

import java.util.concurrent.LinkedBlockingQueue;

public class NoSlow extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    public final ModeProperty swordMode = new ModeProperty("sword-mode", 1, new String[]{"NONE", "VANILLA"});
    public final PercentProperty swordMotion = new PercentProperty("sword-motion", 100, () -> this.swordMode.getValue() != 0);
    public final BooleanProperty swordSprint = new BooleanProperty("sword-sprint", true, () -> this.swordMode.getValue() != 0);
    public final ModeProperty foodMode = new ModeProperty("food-mode", 0, new String[]{"NONE", "VANILLA", "FLOAT", "C0F"});
    public final PercentProperty foodMotion = new PercentProperty("food-motion", 100, () -> this.foodMode.getValue() != 0);
    public final BooleanProperty foodSprint = new BooleanProperty("food-sprint", true, () -> this.foodMode.getValue() != 0);
    public final BooleanProperty c0fDelayKnockback = new BooleanProperty("c0f-delay-knockback", true, () -> this.foodMode.getValue() == 3);
    public final BooleanProperty c0fDelayInteract = new BooleanProperty("c0f-delay-interact", true, () -> this.foodMode.getValue() == 3);
    public final ModeProperty bowMode = new ModeProperty("bow-mode", 0, new String[]{"NONE", "VANILLA", "FLOAT"});
    public final PercentProperty bowMotion = new PercentProperty("bow-motion", 100, () -> this.bowMode.getValue() != 0);
    public final BooleanProperty bowSprint = new BooleanProperty("bow-sprint", true, () -> this.bowMode.getValue() != 0);
    private int lastSlot = -1;

    private enum C0FStep {NONE, CANCEL_C0F, SWAP_HANDS, EATING}

    private C0FStep c0fStep = C0FStep.NONE;
    private int c0fNoUsingItemTicks = 0;
    private final LinkedBlockingQueue<Packet<?>> c0fPackets = new LinkedBlockingQueue<>();
    private final LinkedBlockingQueue<Packet<?>> c0fDelayedVelocity = new LinkedBlockingQueue<>();
    private final LinkedBlockingQueue<Packet<?>> c0fDelayedInteraction = new LinkedBlockingQueue<>();

    public static boolean fakeEating = false;

    public NoSlow() {
        super("NoSlow", false);
    }

    private boolean isFoodC0F() {
        return this.foodMode.getValue() == 3;
    }

    @Override
    public void onDisabled() {
        if (this.c0fStep != C0FStep.NONE) {
            this.releaseC0F();
        }
        this.flushDelayedVelocity();
        this.flushDelayedInteraction();
        fakeEating = false;
        this.c0fNoUsingItemTicks = 0;
    }

    public boolean isSwordActive() {
        return this.swordMode.getValue() != 0 && ItemUtil.isHoldingSword();
    }

    public boolean isFoodActive() {
        return this.foodMode.getValue() != 0 && ItemUtil.isEating();
    }

    public boolean isBowActive() {
        return this.bowMode.getValue() != 0 && ItemUtil.isUsingBow();
    }

    public boolean isFloatMode() {
        return this.foodMode.getValue() == 2 && ItemUtil.isEating()
                || this.bowMode.getValue() == 2 && ItemUtil.isUsingBow();
    }

    public boolean isC0FActive() {
        return this.isFoodC0F() && this.c0fStep != C0FStep.NONE;
    }

    public boolean isAnyActive() {
        return this.isC0FActive()
                || mc.thePlayer.isUsingItem() && (this.isSwordActive() || this.isFoodActive() || this.isBowActive());
    }

    public boolean canSprint() {
        return this.isSwordActive() && this.swordSprint.getValue()
                || this.isFoodActive() && this.foodSprint.getValue()
                || this.isBowActive() && this.bowSprint.getValue()
                || this.isC0FActive() && this.foodSprint.getValue();
    }

    public int getMotionMultiplier() {
        if (ItemUtil.isHoldingSword()) {
            return this.swordMotion.getValue();
        } else if (ItemUtil.isEating()) {
            return this.foodMotion.getValue();
        } else {
            return ItemUtil.isUsingBow() ? this.bowMotion.getValue() : 100;
        }
    }

    @EventTarget
    public void onLivingUpdate(LivingUpdateEvent event) {
        if (this.isEnabled() && this.isAnyActive()) {
            if (this.canSprint() && this.shouldForceSprint()) {
                mc.thePlayer.setSprinting(true);
            } else if (!this.canSprint()) {
                mc.thePlayer.setSprinting(false);
            }
            float multiplier = (float) this.getMotionMultiplier() / 100.0F;
            mc.thePlayer.movementInput.moveForward *= multiplier;
            mc.thePlayer.movementInput.moveStrafe *= multiplier;
        }
    }

    private boolean shouldForceSprint() {
        boolean movingForward = mc.thePlayer.movementInput.moveForward >= 0.8F;
        boolean hasFood = (float) mc.thePlayer.getFoodStats().getFoodLevel() > 6.0F
                || mc.thePlayer.capabilities.allowFlying;
        boolean wantsSprint = mc.gameSettings.keyBindSprint.isKeyDown() || mc.thePlayer.isSprinting();
        return movingForward
                && hasFood
                && wantsSprint
                && !mc.thePlayer.isSneaking()
                && !mc.thePlayer.isPotionActive(net.minecraft.potion.Potion.blindness);
    }

    @EventTarget(Priority.LOW)
    public void onPlayerUpdate(PlayerUpdateEvent event) {
        this.handleC0FTick();
        if (this.isEnabled() && this.isFloatMode()) {
            int item = mc.thePlayer.inventory.currentItem;
            if (this.lastSlot != item && PlayerUtil.isUsingItem()) {
                this.lastSlot = item;
                Unfair.floatManager.setFloatState(true, FloatModules.NO_SLOW);
            }
        } else {
            this.lastSlot = -1;
            Unfair.floatManager.setFloatState(false, FloatModules.NO_SLOW);
        }
    }

    @EventTarget
    public void onRightClick(RightClickMouseEvent event) {
        if (this.isEnabled()) {
            if (mc.objectMouseOver != null) {
                switch (mc.objectMouseOver.typeOfHit) {
                    case BLOCK:
                        BlockPos blockPos = mc.objectMouseOver.getBlockPos();
                        if (BlockUtil.isInteractable(blockPos) && !PlayerUtil.isSneaking()) {
                            return;
                        }
                        break;
                    case ENTITY:
                        Entity entityHit = mc.objectMouseOver.entityHit;
                        if (entityHit instanceof EntityVillager) {
                            return;
                        }
                        if (entityHit instanceof EntityLivingBase && TeamUtil.isShop((EntityLivingBase) entityHit)) {
                            return;
                        }
                }
            }
            if (this.isFloatMode() && !Unfair.floatManager.isPredicted() && mc.thePlayer.onGround) {
                event.setCancelled(true);
                mc.thePlayer.motionY = 0.42F;
            }
        }
    }

    private void handleC0FTick() {
        if (!this.isEnabled() || !this.isFoodC0F()) {
            if (this.c0fStep != C0FStep.NONE) {
                this.releaseC0F();
            }
            this.c0fNoUsingItemTicks = 0;
            return;
        }

        // If the food is sitting in the offhand instead of the mainhand, the whole
        // C0F trick can never start (it relies on eating from the mainhand). Swap it
        // back to the mainhand first, then let vanilla begin the eat next tick.
        if (this.c0fStep == C0FStep.NONE && this.trySwapOffhandFoodToMainHand()) {
            return;
        }

        boolean usingFood = ItemUtil.isEating() && mc.thePlayer.isUsingItem();

        if (this.c0fStep != C0FStep.EATING) {
            KeyBindUtil.setKeyBindState(mc.gameSettings.keyBindUseItem.getKeyCode(), false);
        }

        if (this.c0fStep == C0FStep.NONE && usingFood) {
            this.c0fStep = C0FStep.CANCEL_C0F;
            fakeEating = true;
            this.c0fPackets.clear();

            if (mc.thePlayer.openContainer != mc.thePlayer.inventoryContainer) {
                PacketUtil.sendPacket(new C0DPacketCloseWindow(mc.thePlayer.openContainer.windowId));
            }
        }

        if (this.c0fStep == C0FStep.EATING) {
            if (mc.thePlayer.isUsingItem()) {
                this.c0fNoUsingItemTicks = 0;
            } else {
                this.c0fNoUsingItemTicks++;
                if (this.c0fNoUsingItemTicks >= 10) {
                    this.releaseC0F();
                }
            }
        } else {
            this.c0fNoUsingItemTicks = 0;
        }
    }

    @EventTarget(Priority.LOWEST)
    public void onPacket(PacketEvent event) {
        if (!this.isEnabled() || !this.isFoodC0F()) {
            return;
        }

        Packet<?> packet = event.getPacket();

        if (event.getType() == EventType.RECEIVE
                && this.c0fDelayKnockback.getValue()
                && this.c0fStep != C0FStep.NONE
                && packet instanceof S12PacketEntityVelocity) {
            S12PacketEntityVelocity velocity = (S12PacketEntityVelocity) packet;
            if (mc.thePlayer != null && velocity.getEntityID() == mc.thePlayer.getEntityId()) {
                event.setCancelled(true);
                this.c0fDelayedVelocity.offer(packet);
                return;
            }
        }

        if (event.getType() == EventType.SEND
                && this.c0fDelayInteract.getValue()
                && this.c0fStep != C0FStep.NONE
                && packet instanceof C08PacketPlayerBlockPlacement) {
            // Direction 255 is the "use item in air" sentinel (the eat itself) — leave it.
            // A real block/chest right-click carries a valid face, so hold it back and
            // replay it once the eat is over instead of letting it fire mid-trick.
            if (((C08PacketPlayerBlockPlacement) packet).getPlacedBlockDirection() != 255) {
                event.setCancelled(true);
                this.c0fDelayedInteraction.offer(packet);
                return;
            }
        }

        if (event.getType() == EventType.SEND) {
            if (this.c0fStep != C0FStep.NONE && packet instanceof C0FPacketConfirmTransaction) {
                event.setCancelled(true);
                this.c0fPackets.offer(packet);

                if (this.c0fStep == C0FStep.CANCEL_C0F) {
                    this.c0fStep = C0FStep.SWAP_HANDS;
                    PacketUtil.sendPacket(new CPacketSwapItemWithOffHand());
                }
            }

            if (this.c0fStep == C0FStep.EATING && packet instanceof C07PacketPlayerDigging) {
                if (((C07PacketPlayerDigging) packet).getStatus() == C07PacketPlayerDigging.Action.RELEASE_USE_ITEM) {
                    this.releaseC0F();
                }
            }
        }

        if (event.getType() == EventType.RECEIVE) {
            if (this.c0fStep == C0FStep.SWAP_HANDS && packet instanceof S2FPacketSetSlot) {
                KeyBindUtil.setKeyBindState(mc.gameSettings.keyBindUseItem.getKeyCode(), true);
                this.c0fStep = C0FStep.EATING;
            }

            if (packet instanceof S08PacketPlayerPosLook) {
                this.releaseC0F();
            }
        }
    }

    private void releaseC0F() {
        while (!this.c0fPackets.isEmpty()) {
            Packet<?> packet = this.c0fPackets.poll();
            if (packet != null && mc.getNetHandler() != null) {
                PacketUtil.sendPacketNoEvent(packet);
            }
        }

        if (mc.getNetHandler() != null) {
            PacketUtil.sendPacket(new CPacketSwapItemWithOffHand());
        }

        this.c0fStep = C0FStep.NONE;
        this.c0fNoUsingItemTicks = 0;
        fakeEating = false;

        // Eating is over (whether it finished or the player let go early). Replay everything
        // we held back: the block/chest interactions first, now that the real hand is restored,
        // then the knockback. We never force the eat to complete — releaseC0F is driven by the
        // player actually stopping (RELEASE_USE_ITEM / key up), so a half-eaten food just flushes.
        this.flushDelayedInteraction();
        this.flushDelayedVelocity();
    }

    private void flushDelayedInteraction() {
        while (!this.c0fDelayedInteraction.isEmpty()) {
            Packet<?> packet = this.c0fDelayedInteraction.poll();
            if (packet != null && mc.getNetHandler() != null) {
                PacketUtil.sendPacketNoEvent(packet);
            }
        }
        this.c0fDelayedInteraction.clear();
    }

    private void flushDelayedVelocity() {
        while (!this.c0fDelayedVelocity.isEmpty()) {
            Packet<?> packet = this.c0fDelayedVelocity.poll();
            if (packet != null && mc.getNetHandler() != null) {
                PacketUtil.receivePacketNoEvent(packet);
            }
        }
        this.c0fDelayedVelocity.clear();
    }

    private boolean isFood(ItemStack itemStack) {
        if (itemStack == null || itemStack.stackSize < 1) {
            return false;
        }
        if (ItemPotion.isSplash(itemStack.getItem().getMetadata(itemStack))) {
            return false;
        }
        EnumAction action = itemStack.getItemUseAction();
        return action == EnumAction.EAT || action == EnumAction.DRINK;
    }

    private boolean trySwapOffhandFoodToMainHand() {
        if (mc.thePlayer == null || !ModernOffhandInteraction.isModernTarget()) {
            return false;
        }
        // Only act while the player is actually trying to eat (physical use key held).
        if (!KeyBindUtil.isKeyDown(mc.gameSettings.keyBindUseItem.getKeyCode())) {
            return false;
        }
        // Mainhand already holds food -> normal C0F flow handles it.
        if (this.isFood(mc.thePlayer.getHeldItem())) {
            return false;
        }
        ItemStack offhand = ModernOffhandInteraction.getOffhand(mc.thePlayer);
        if (!this.isFood(offhand)) {
            return false;
        }
        return ModernOffhandInteraction.sendSwapItemWithOffhand(mc.thePlayer);
    }

    @Override
    public String[] getSuffix() {
        return new String[]{CaseFormat.UPPER_UNDERSCORE.to(CaseFormat.UPPER_CAMEL, this.swordMode.getModeString())};
    }
}
