package cn.unfair.util.player;

import cn.unfair.event.types.EventType;
import cn.unfair.events.PacketEvent;
import cn.unfair.events.UpdateEvent;
import cn.unfair.management.RotationState;
import cn.unfair.util.PacketUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.item.ItemBow;
import net.minecraft.item.ItemSoup;
import net.minecraft.item.ItemStack;
import net.minecraft.network.Packet;
import net.minecraft.network.play.client.C03PacketPlayer;
import net.minecraft.network.play.client.C07PacketPlayerDigging;
import net.minecraft.network.play.client.C08PacketPlayerBlockPlacement;
import net.minecraft.network.play.client.C0FPacketConfirmTransaction;
import net.minecraft.network.play.client.CPacketPlayerTryUseItem;
import net.minecraft.network.play.client.CPacketSwapItemWithOffHand;
import net.minecraft.network.play.client.ServerBoundPlayerAction;
import net.minecraft.network.play.client.ServerBoundUseItem;
import net.minecraft.network.play.server.S08PacketPlayerPosLook;
import net.minecraft.util.EnumHand;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

public final class HeypixelStuckController {
    private static final Minecraft mc = Minecraft.getMinecraft();

    private final Queue<C0FPacketConfirmTransaction> packets = new ConcurrentLinkedQueue<>();
    private int stage;
    private Packet<?> actionPacket;
    private float lastYaw;
    private float lastPitch;
    private boolean tryDisable;
    private boolean active;

    public void start() {
        if (active || mc.thePlayer == null) {
            return;
        }
        stage = 0;
        actionPacket = null;
        lastYaw = RotationState.isActived() ? RotationState.getRotationYawHead() : mc.thePlayer.rotationYaw;
        lastPitch = RotationState.isActived() ? RotationState.getRotationPitch() : mc.thePlayer.rotationPitch;
        tryDisable = false;
        active = true;
    }

    public void requestDisable() {
        if (active) {
            tryDisable = true;
        }
    }

    public boolean canDisable() {
        return !active || stage == 3;
    }

    public void forceStop() {
        if (mc.thePlayer == null) {
            reset();
            return;
        }
        forceStop(mc.thePlayer.rotationYaw, mc.thePlayer.rotationPitch);
    }

    public void forceStop(float yaw, float pitch) {
        if (!active) {
            return;
        }
        if (mc.thePlayer != null && mc.getNetHandler() != null) {
            if (stage == 1) {
                sendAction(yaw, pitch);
            }
            PacketUtil.sendPacketNoEvent(new C03PacketPlayer.C04PacketPlayerPosition(
                    mc.thePlayer.posX + 1337.0, mc.thePlayer.posY, mc.thePlayer.posZ + 1337.0, mc.thePlayer.onGround));
            flushPackets();
        } else {
            packets.clear();
        }
        reset();
    }

    public void releaseWithoutPositionSpoof(float yaw, float pitch) {
        if (!active) {
            return;
        }
        if (mc.thePlayer != null && mc.getNetHandler() != null) {
            if (stage == 1) {
                sendAction(yaw, pitch);
            }
            flushPackets();
        } else {
            packets.clear();
        }
        reset();
    }

    public boolean handlePacket(PacketEvent event) {
        if (!active) {
            return false;
        }
        Packet<?> packet = event.getPacket();
        if (event.getType() == EventType.SEND) {
            if (packet instanceof C03PacketPlayer) {
                event.setCancelled(true);
            } else if (packet instanceof C0FPacketConfirmTransaction transaction) {
                packets.offer(transaction);
                event.setCancelled(true);
            } else if (isUseItemPacket(packet) || isPlayerActionPacket(packet)) {
                actionPacket = packet;
                stage = 1;
                event.setCancelled(true);
            }
        } else if (event.getType() == EventType.RECEIVE && packet instanceof S08PacketPlayerPosLook) {
            flushPackets();
            stage = 3;
            actionPacket = null;
            tryDisable = false;
            active = false;
            return true;
        }
        return false;
    }

    public void update(UpdateEvent event) {
        if (!active || event.getType() != EventType.PRE || mc.thePlayer == null) {
            return;
        }
        mc.thePlayer.motionX = 0.0;
        mc.thePlayer.motionZ = 0.0;
        mc.thePlayer.motionY = 0.0;
        if (stage == 1) {
            sendAction(mc.thePlayer.rotationYaw, mc.thePlayer.rotationPitch);
        }
        if (tryDisable) {
            PacketUtil.sendPacketNoEvent(new C03PacketPlayer.C04PacketPlayerPosition(
                    mc.thePlayer.posX + 1337.0, mc.thePlayer.posY, mc.thePlayer.posZ + 1337.0, mc.thePlayer.onGround));
            flushPackets();
            tryDisable = false;
        }
    }

    public void stopMovementInput() {
        if (!active || mc.thePlayer == null) {
            return;
        }
        mc.thePlayer.movementInput.moveForward = 0.0F;
        mc.thePlayer.movementInput.moveStrafe = 0.0F;
        mc.thePlayer.movementInput.jump = false;
        mc.thePlayer.movementInput.sneak = false;
    }

    public boolean isActive() {
        return active;
    }

    public void sendRotation(float yaw, float pitch) {
        if (!active || mc.thePlayer == null || lastYaw == yaw && lastPitch == pitch) {
            return;
        }
        PacketUtil.sendPacketNoEvent(new C03PacketPlayer.C05PacketPlayerLook(yaw, pitch, mc.thePlayer.onGround));
        flushPackets();
        lastYaw = yaw;
        lastPitch = pitch;
    }

    public void reset() {
        stage = 3;
        actionPacket = null;
        tryDisable = false;
        active = false;
    }

    private void sendAction(float yaw, float pitch) {
        stage = 2;
        if (shouldRotate() && (lastYaw != yaw || lastPitch != pitch)) {
            PacketUtil.sendPacketNoEvent(new C03PacketPlayer.C05PacketPlayerLook(yaw, pitch, mc.thePlayer.onGround));
            flushPackets();
            lastYaw = yaw;
            lastPitch = pitch;
        }
        PacketUtil.sendPacketNoEvent(actionPacket);
    }

    private boolean shouldRotate() {
        if (isUseItemPacket(actionPacket)) {
            ItemStack item = getUseItemStack(actionPacket);
            return item == null || !(item.getItem() instanceof ItemSoup) && !(item.getItem() instanceof ItemBow);
        }
        C07PacketPlayerDigging.Action action = getPlayerAction(actionPacket);
        if (action != null) {
            return action == C07PacketPlayerDigging.Action.RELEASE_USE_ITEM
                    && mc.thePlayer.getItemInUse() != null
                    && mc.thePlayer.getItemInUse().getItem() instanceof ItemBow;
        }
        return false;
    }

    private void flushPackets() {
        while (!packets.isEmpty()) {
            PacketUtil.sendPacketNoEvent(packets.poll());
        }
    }

    private boolean isUseItemPacket(Packet<?> packet) {
        return packet instanceof ServerBoundUseItem
                || packet instanceof CPacketPlayerTryUseItem
                || packet instanceof C08PacketPlayerBlockPlacement placement
                && placement.getPlacedBlockDirection() == 255;
    }

    private boolean isPlayerActionPacket(Packet<?> packet) {
        return packet instanceof ServerBoundPlayerAction
                || packet instanceof C07PacketPlayerDigging
                || packet instanceof CPacketSwapItemWithOffHand;
    }

    private ItemStack getUseItemStack(Packet<?> packet) {
        if (packet instanceof ServerBoundUseItem useItem && useItem.getHand() == EnumHand.OFF_HAND) {
            return mc.thePlayer.inventory.viaforge$getOffhand();
        }
        if (packet instanceof CPacketPlayerTryUseItem useItem && useItem.getHand() == EnumHand.OFF_HAND.ordinal()) {
            return mc.thePlayer.inventory.viaforge$getOffhand();
        }
        return mc.thePlayer.getHeldItem();
    }

    private C07PacketPlayerDigging.Action getPlayerAction(Packet<?> packet) {
        if (packet instanceof ServerBoundPlayerAction playerAction) {
            return playerAction.getAction();
        }
        if (packet instanceof C07PacketPlayerDigging digging) {
            return digging.getStatus();
        }
        return null;
    }
}
