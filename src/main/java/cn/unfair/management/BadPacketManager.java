package cn.unfair.management;

import cn.unfair.event.EventTarget;
import cn.unfair.event.types.EventType;
import cn.unfair.events.PacketEvent;
import net.minecraft.network.play.client.*;

public class BadPacketManager {
    private static boolean slot, attack, swing, block, inventory;

    public static boolean bad() {
        return bad(true, true, true, true, true);
    }

    public static boolean bad(final boolean slot, final boolean attack, final boolean swing, final boolean block, final boolean inventory) {
        return (BadPacketManager.slot && slot) ||
                (BadPacketManager.attack && attack) ||
                (BadPacketManager.swing && swing) ||
                (BadPacketManager.block && block) ||
                (BadPacketManager.inventory && inventory);
    }

    @EventTarget
    public void onPacket(PacketEvent event) {
        if (event.getType() == EventType.SEND && !event.isCancelled()) {
            if (event.getPacket() instanceof C02PacketUseEntity useEntity) {
                if (useEntity.getAction() == C02PacketUseEntity.Action.ATTACK) {
                    attack = true;
                }
            } else if (event.getPacket() instanceof C0DPacketCloseWindow || event.getPacket() instanceof C0EPacketClickWindow ||
                    (event.getPacket() instanceof C16PacketClientStatus && ((C16PacketClientStatus) event.getPacket()).getStatus() == C16PacketClientStatus.EnumState.OPEN_INVENTORY_ACHIEVEMENT)) {
                inventory = true;
            } else if (event.getPacket() instanceof C0APacketAnimation) {
                swing = true;
            } else if (event.getPacket() instanceof C08PacketPlayerBlockPlacement || (event.getPacket() instanceof C07PacketPlayerDigging)) {
                block = true;
            } else if (event.getPacket() instanceof C09PacketHeldItemChange) {
                slot = true;
            } else if (event.getPacket() instanceof C03PacketPlayer) {
                resetBadPackets();
            }
        }
    }

    private void resetBadPackets() {
        slot = false;
        swing = false;
        attack = false;
        block = false;
        inventory = false;
    }
}
