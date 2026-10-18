package cn.unfair.management;

import cn.unfair.Unfair;
import cn.unfair.enums.BlinkModules;
import cn.unfair.event.types.EventType;
import cn.unfair.events.PacketEvent;
import cn.unfair.util.player.PacketUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemSword;
import net.minecraft.network.Packet;
import net.minecraft.network.play.client.C07PacketPlayerDigging;
import net.minecraft.network.play.client.C08PacketPlayerBlockPlacement;
import net.minecraft.network.play.client.C09PacketHeldItemChange;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;

public final class AutoBlockController {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private static final ThreadLocal<Boolean> VANILLA_RELEASE = ThreadLocal.withInitial(() -> false);
    private int abTick;
    private boolean serverBlocking;
    private boolean packetBlocking;
    private boolean ownsUseItem;
    private boolean attackExecuted;
    private boolean ab3AttackPending;
    private boolean ab3ReblockPending;
    private boolean ownsAB34Blink;
    private boolean ab6ReleasePending;
    private boolean ownsAB6Blink;
    private boolean ab1PostBlink;
    private ItemStack blockStack;
    private int abMode;

    public static boolean isVanillaReleaseAllowed() {
        return VANILLA_RELEASE.get();
    }

    public void preAB1(boolean enabled, boolean attackReady, boolean attackCycleReady, Runnable attack, Runnable interactAttack) {
        if (abMode != 0 && abMode != 1) reset();
        abMode = 1;
        attackExecuted = false;
        if (!enabled || !hasSword() || mc.theWorld == null) {
            reset();
            return;
        }
        boolean startBlock = false;
        boolean allowAttack = attackReady;
        switch (abTick) {
            case 0:
                startBlock = !isPlayerBlocking();
                abTick = 1;
                break;
            case 1:
                startBlink();
                releaseBlock();
                allowAttack = false;
                abTick = 2;
                break;
            case 2:
                releaseBlock();
                abTick = 3;
                break;
            case 3:
                ab1PostBlink = true;
                if (attackCycleReady) abTick = 0;
                break;
            default:
                abTick = 0;
                break;
        }

        if (allowAttack && attackReady && attack != null) {
            attack.run();
            attackExecuted = true;
        }

        if (startBlock) {
            if (attackExecuted && interactAttack != null) {
                interactAttack.run();
                serverBlocking = true;
                ownsUseItem = true;
                blockStack = mc.thePlayer == null ? null : mc.thePlayer.getHeldItem();
            } else sendUseItem();
        }
    }

    public void preAB3(boolean enabled, boolean attackReady, Runnable attack) {
        if (abMode != 0 && abMode != 3) reset();
        abMode = 3;
        attackExecuted = false;
        if (!enabled || !hasSword() || mc.theWorld == null) {
            reset();
            return;
        }
        if (ab3ReblockPending) {
            stopAB34Blink();
            if (isAB34BlinkQueued()) return;
            ab3ReblockPending = false;
            if (!packetBlocking && !ownsUseItem) startVanillaBlock();
            return;
        }
        if (ab3AttackPending) {
            if (!attackReady || attack == null) return;
            ab3AttackPending = false;
            attack.run();
            attackExecuted = true;
            ab3ReblockPending = true;
            return;
        }
        if (!packetBlocking && !ownsUseItem) startVanillaBlock();
        if (attackReady && attack != null && (packetBlocking || ownsUseItem)) {
            releaseAB34Block();
            ab3AttackPending = true;
        }
    }

    public void preAB6(boolean enabled, boolean attackReady, boolean blockCycleReady, Runnable attack, Runnable interactAttack) {
        if (abMode != 0 && abMode != 6) reset();
        abMode = 6;
        attackExecuted = false;
        if (!enabled || !hasSword() || mc.theWorld == null) {
            reset();
            return;
        }

        if (ab6ReleasePending) {
            startAB6Blink();
            releaseBlock();
            if (ownsAB6Blink || isBlinkQueueing()) serverBlocking = true;
            ab6ReleasePending = false;
            return;
        }
        if (!blockCycleReady) return;
        if (ownsAB6Blink) stopAB6Blink();
        else if (isBlinkQueueing()) return;

        if (attackReady && attack != null) {
            attack.run();
            if (interactAttack != null) interactAttack.run();
        }
        attackExecuted = true;
        sendABUseItem();
        ab6ReleasePending = true;
    }

    public void maintainAB3() {
        if (abMode != 0 && abMode != 3) reset();
        abMode = 3;
        attackExecuted = false;
        if (!hasSword() || mc.theWorld == null) {
            reset();
            return;
        }
        if (ab3ReblockPending) {
            stopAB34Blink();
            if (isAB34BlinkQueued()) return;
            ab3ReblockPending = false;
            if (!packetBlocking && !ownsUseItem) startVanillaBlock();
        }
    }

    public void post() {
        if (!ab1PostBlink) return;
        ab1PostBlink = false;
        stopBlink();
    }

    public void onPacket(PacketEvent event) {
        if (event == null || event.getType() != EventType.SEND) return;
        Packet<?> packet = event.getPacket();
        if (packet instanceof C07PacketPlayerDigging digging
                && digging.getStatus() == C07PacketPlayerDigging.Action.RELEASE_USE_ITEM) {
            packetBlocking = false;
            if (abMode == 3) {
                if (!ownsAB34Blink && !isAB34BlinkQueued()) serverBlocking = false;
                ownsUseItem = false;
                blockStack = null;
            } else if (abMode == 6) {
                if (!ownsAB6Blink && !isBlinkQueueing()) serverBlocking = false;
            } else {
                serverBlocking = false;
            }
        } else if (packet instanceof C09PacketHeldItemChange) {
            packetBlocking = false;
            if (abMode == 3) {
                if (!ownsAB34Blink && !isAB34BlinkQueued()) serverBlocking = false;
                ownsUseItem = false;
                blockStack = null;
            } else if (abMode == 6) {
                if (!ownsAB6Blink && !isBlinkQueueing()) serverBlocking = false;
            } else {
                serverBlocking = false;
            }
        }
    }

    public boolean isServerBlocking() {
        return serverBlocking;
    }

    public boolean isActive() {
        return abTick != 0 || ab1PostBlink || serverBlocking || ownsAB34Blink || abMode == 3
                || ab3AttackPending || ab3ReblockPending
                || abMode == 6 || ab6ReleasePending || ownsAB6Blink
                || isBlinkQueueing();
    }

    public int getTick() {
        if (abMode == 3) {
            if (ab3AttackPending) return 1;
            if (ab3ReblockPending) return 2;
            return packetBlocking || ownsUseItem ? 0 : 3;
        }
        if (abMode == 6) {
            if (ab6ReleasePending) return 1;
            if (isBlinkQueueing()) return 2;
            return serverBlocking ? 0 : 3;
        }
        return abTick;
    }

    public void reset() {
        if (ownsAB34Blink) stopAB34Blink();
        if (ownsAB6Blink) stopAB6Blink();
        if (abMode == 1) stopBlink();
        if (abMode == 3) {
            releaseVanillaBlock(false);
        } else if (ownsUseItem || serverBlocking) {
            sendReleasePacket();
            stopOwnedLocalUse();
        }
        abTick = 0;
        serverBlocking = false;
        packetBlocking = false;
        ownsUseItem = false;
        ab3AttackPending = false;
        ab3ReblockPending = false;
        ownsAB34Blink = false;
        ab6ReleasePending = false;
        ownsAB6Blink = false;
        blockStack = null;
        attackExecuted = false;
        ab1PostBlink = false;
        abMode = 0;
    }

    private void startBlink() {
        if (Unfair.blinkManager != null && !Unfair.blinkManager.isBlinking()) {
            Unfair.blinkManager.setBlinkState(true, BlinkModules.AUTO_BLOCK);
        }
    }

    private void stopBlink() {
        if (Unfair.blinkManager == null || !Unfair.blinkManager.isBlinking()) {
            return;
        }
        BlinkModules module = Unfair.blinkManager.getBlinkingModule();
        if (module != BlinkModules.NONE) {
            Unfair.blinkManager.setBlinkState(false, module);
        }
    }

    private boolean isBlinkQueueing() {
        return Unfair.blinkManager != null && Unfair.blinkManager.isBlinking();
    }

    private void startAB6Blink() {
        if (Unfair.blinkManager == null || Unfair.blinkManager.isBlinking()) return;
        Unfair.blinkManager.setBlinkState(true, BlinkModules.AUTO_BLOCK);
        ownsAB6Blink = true;
    }

    private void stopAB6Blink() {
        if (!ownsAB6Blink) return;
        ownsAB6Blink = false;
        serverBlocking = false;
        stopBlink();
    }

    private void startAB34Blink() {
        if (Unfair.blinkManager == null || Unfair.blinkManager.isBlinking()) return;
        Unfair.blinkManager.setBlinkState(true, BlinkModules.AUTO_BLOCK);
        ownsAB34Blink = true;
    }

    private void stopAB34Blink() {
        if (!ownsAB34Blink) return;
        ownsAB34Blink = false;
        serverBlocking = false;
        stopBlink();
    }

    private boolean isAB34BlinkQueued() {
        return isBlinkQueueing();
    }

    private void releaseAB34Block() {
        startAB34Blink();
        releaseVanillaBlock(false);
        if (isBlinkQueueing()) {
            serverBlocking = true;
        }
    }

    private void releaseBlock() {
        if (!ownsUseItem && !isPlayerBlocking()) return;
        sendReleasePacket();
        stopOwnedLocalUse();
        serverBlocking = false;
        packetBlocking = false;
        ownsUseItem = false;
        blockStack = null;
    }

    private void sendUseItem() {
        if (mc.thePlayer == null || mc.theWorld == null) return;
        ItemStack held = mc.thePlayer.getHeldItem();
        if (held == null || !(held.getItem() instanceof ItemSword)) return;
        PacketUtil.sendPacket(new C08PacketPlayerBlockPlacement(held));
        mc.thePlayer.setItemInUse(held, held.getMaxItemUseDuration());
        serverBlocking = true;
        ownsUseItem = true;
        blockStack = held;
    }

    private void sendABUseItem() {
        if (mc.thePlayer == null || mc.theWorld == null) return;
        ItemStack held = mc.thePlayer.getHeldItem();
        if (held == null || !(held.getItem() instanceof ItemSword)) return;
        if (isBlinkQueueing()) {
            PacketUtil.sendPacketNoEvent(new C08PacketPlayerBlockPlacement(held));
            mc.thePlayer.setItemInUse(held, held.getMaxItemUseDuration());
        } else {
            PacketUtil.sendPacket(new C08PacketPlayerBlockPlacement(held));
            mc.thePlayer.setItemInUse(held, held.getMaxItemUseDuration());
        }
        serverBlocking = true;
        ownsUseItem = true;
        blockStack = held;
    }

    private void startVanillaBlock() {
        if (mc.thePlayer == null || mc.theWorld == null || mc.playerController == null || packetBlocking || !hasSword()) return;
        ItemStack held = mc.thePlayer.getHeldItem();
        if (held == null) return;
        if (isBlinkQueueing()) {
            PacketUtil.sendPacketNoEvent(new C08PacketPlayerBlockPlacement(held));
            mc.thePlayer.setItemInUse(held, held.getMaxItemUseDuration());
        } else {
            mc.playerController.sendUseItem(mc.thePlayer, mc.theWorld, held);
        }
        packetBlocking = true;
        serverBlocking = true;
        ownsUseItem = true;
        blockStack = held;
    }

    private void releaseVanillaBlock(boolean force) {
        if (mc.thePlayer == null) return;
        boolean owned = ownsUseItem || packetBlocking || force && mc.thePlayer.isUsingItem();
        if (mc.playerController != null && owned) {
            VANILLA_RELEASE.set(true);
            try {
                mc.playerController.onStoppedUsingItem(mc.thePlayer);
            } finally {
                VANILLA_RELEASE.set(false);
            }
            stopOwnedLocalUse();
        }
        packetBlocking = false;
        serverBlocking = false;
        ownsUseItem = false;
        blockStack = null;
    }

    private boolean isPlayerBlocking() {
        return mc.thePlayer != null && mc.thePlayer.isUsingItem() && hasSword();
    }

    private boolean hasSword() {
        return mc.thePlayer != null
                && mc.thePlayer.getHeldItem() != null
                && mc.thePlayer.getHeldItem().getItem() instanceof ItemSword;
    }

    private void sendReleasePacket() {
        PacketUtil.sendPacket(new C07PacketPlayerDigging(
                C07PacketPlayerDigging.Action.RELEASE_USE_ITEM, BlockPos.ORIGIN, EnumFacing.DOWN));
    }

    private void stopOwnedLocalUse() {
        if (mc.thePlayer == null || !mc.thePlayer.isUsingItem()) return;
        ItemStack inUse = mc.thePlayer.getItemInUse();
        if (blockStack == null || inUse == blockStack || inUse != null && inUse.getItem() instanceof ItemSword) {
            mc.thePlayer.stopUsingItem();
        }
    }
}
