package cn.unfair.management;

import cn.unfair.event.EventTarget;
import cn.unfair.event.types.EventType;
import cn.unfair.events.LoadWorldEvent;
import cn.unfair.events.PacketEvent;
import cn.unfair.event.types.Priority;
import net.minecraft.network.play.client.*;

import java.util.concurrent.atomic.AtomicInteger;

public class BadPacketManager {
    private static final int SLOT = 1;
    private static final int ATTACK = 1 << 1;
    private static final int SWING = 1 << 2;
    private static final int BLOCK = 1 << 3;
    private static final int INVENTORY = 1 << 4;
    private static final AtomicInteger state = new AtomicInteger();

    public static boolean bad() {
        return bad(true, true, true, true, true);
    }

    public static boolean bad(final boolean checkSlot, final boolean checkAttack, final boolean checkSwing,
                              final boolean checkBlock, final boolean checkInventory) {
        int currentState = state.get();
        return (checkSlot && hasFlag(currentState, SLOT))
                || (checkAttack && hasFlag(currentState, ATTACK))
                || (checkSwing && hasFlag(currentState, SWING))
                || (checkBlock && hasFlag(currentState, BLOCK))
                || (checkInventory && hasFlag(currentState, INVENTORY));
    }

    @EventTarget(Priority.LOWEST)
    public void onPacket(PacketEvent event) {
        if (event.getType() != EventType.SEND || event.isCancelled()) return;

        if (event.getPacket() instanceof C03PacketPlayer) {
            resetBadPackets();
            return;
        }

        if (isAttackPacket(event.getPacket())) {
            addFlag(ATTACK);
        } else if (isInventoryPacket(event.getPacket())) {
            addFlag(INVENTORY);
        } else if (isSwingPacket(event.getPacket())) {
            addFlag(SWING);
        } else if (isBlockPacket(event.getPacket())) {
            addFlag(BLOCK);
        } else if (isSlotPacket(event.getPacket())) {
            addFlag(SLOT);
        }
    }

    @EventTarget
    public void onLoadWorld(LoadWorldEvent event) {
        resetBadPackets();
    }

    private static boolean isAttackPacket(Object packet) {
        return packet instanceof ServerBoundInteractAttack
                || (packet instanceof C02PacketUseEntity useEntity
                && useEntity.getAction() == C02PacketUseEntity.Action.ATTACK);
    }

    private static boolean isInventoryPacket(Object packet) {
        return packet instanceof C0DPacketCloseWindow
                || packet instanceof C0EPacketClickWindow
                || (packet instanceof C16PacketClientStatus status
                && status.getStatus() == C16PacketClientStatus.EnumState.OPEN_INVENTORY_ACHIEVEMENT);
    }

    private static boolean isSwingPacket(Object packet) {
        return packet instanceof C0APacketAnimation || packet instanceof ServerBoundSwing;
    }

    private static boolean isBlockPacket(Object packet) {
        return packet instanceof C08PacketPlayerBlockPlacement
                || packet instanceof C07PacketPlayerDigging
                || packet instanceof CPacketPlayerTryUseItem
                || packet instanceof ServerBoundUseItem
                || packet instanceof ServerBoundPlayerAction;
    }

    private static boolean isSlotPacket(Object packet) {
        return packet instanceof C09PacketHeldItemChange || packet instanceof CPacketSwapItemWithOffHand;
    }

    private static boolean hasFlag(int currentState, int flag) {
        return (currentState & flag) != 0;
    }

    private static void addFlag(int flag) {
        state.updateAndGet(currentState -> currentState | flag);
    }

    private void resetBadPackets() {
        state.set(0);
    }
}
