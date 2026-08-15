package cn.unfair.management;

import cn.unfair.event.EventTarget;
import cn.unfair.event.types.EventType;
import cn.unfair.event.types.Priority;
import cn.unfair.events.LoadWorldEvent;
import cn.unfair.events.PacketEvent;
import net.minecraft.network.Packet;
import net.minecraft.network.play.client.*;

public class BadPacketManager {
    private static final int SLOT = 1;
    private static final int ATTACK = 1 << 1;
    private static final int SWING = 1 << 2;
    private static final int BLOCK = 1 << 3;
    private static final int INVENTORY = 1 << 4;

    private static volatile int sentPackets;

    public static boolean bad() {
        return bad(true, true, true, true, true);
    }

    public static boolean bad(final boolean slot, final boolean attack, final boolean swing, final boolean block, final boolean inventory) {
        int checkedPackets = (slot ? SLOT : 0)
                | (attack ? ATTACK : 0)
                | (swing ? SWING : 0)
                | (block ? BLOCK : 0)
                | (inventory ? INVENTORY : 0);
        return (sentPackets & checkedPackets) != 0;
    }

    @EventTarget(Priority.HIGHEST)
    public void onPacket(PacketEvent event) {
        if (event.getType() != EventType.POST) {
            return;
        }

        Packet<?> packet = event.getPacket();
        if (packet instanceof C03PacketPlayer) {
            resetBadPackets();
        } else if ((packet instanceof C02PacketUseEntity useEntity
                && useEntity.getAction() == C02PacketUseEntity.Action.ATTACK)
                || packet instanceof ServerBoundInteractAttack) {
            mark(ATTACK);
        } else if (packet instanceof C0APacketAnimation || packet instanceof ServerBoundSwing) {
            mark(SWING);
        } else if (packet instanceof C08PacketPlayerBlockPlacement
                || packet instanceof C07PacketPlayerDigging
                || packet instanceof ServerBoundUseItem
                || packet instanceof ServerBoundPlayerAction) {
            mark(BLOCK);
        } else if (packet instanceof C09PacketHeldItemChange) {
            mark(SLOT);
        } else if (packet instanceof C0DPacketCloseWindow
                || packet instanceof C0EPacketClickWindow
                || (packet instanceof C16PacketClientStatus clientStatus
                && clientStatus.getStatus() == C16PacketClientStatus.EnumState.OPEN_INVENTORY_ACHIEVEMENT)) {
            mark(INVENTORY);
        }
    }

    @EventTarget
    public void onLoadWorld(LoadWorldEvent event) {
        resetBadPackets();
    }

    private static void mark(int packetType) {
        sentPackets |= packetType;
    }

    private static void resetBadPackets() {
        sentPackets = 0;
    }
}
