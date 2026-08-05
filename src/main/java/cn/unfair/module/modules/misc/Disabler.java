package cn.unfair.module.modules.misc;

import cn.unfair.event.EventTarget;
import cn.unfair.event.types.EventType;
import cn.unfair.events.PacketEvent;
import cn.unfair.module.Module;
import cn.unfair.property.properties.BooleanProperty;
import cn.unfair.property.properties.ModeProperty;
import cn.unfair.util.ChatUtil;
import cn.unfair.util.PacketUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.item.ItemStack;
import net.minecraft.network.Packet;
import net.minecraft.network.play.client.C0DPacketCloseWindow;
import net.minecraft.network.play.client.C0EPacketClickWindow;
import net.minecraft.network.play.client.C16PacketClientStatus;

import java.util.ArrayList;
import java.util.List;

public class Disabler extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();

    public final ModeProperty mode = new ModeProperty("Mode", 0, new String[]{"Prediction"});
    public final BooleanProperty inventory = new BooleanProperty("Inventory", true, () -> mode.getValue() == 0);

    private final List<Packet<?>> inventoryPackets = new ArrayList<>();

    public Disabler() {
        super("Disabler", false);
    }

    @Override
    public String[] getSuffix() {
        return new String[]{mode.getModeString()};
    }

    @Override
    public void onEnabled() {
        if (mode.getValue() == 0 && inventory.getValue()) {
            ChatUtil.dbg("You can use Vanilla-InvWalk now");
        }
        resetStates();
    }

    @Override
    public void onDisabled() {
        if (mode.getValue() == 0 && inventory.getValue()) {
            if (!inventoryPackets.isEmpty()) {
                for (Packet<?> p : inventoryPackets) {
                    PacketUtil.sendPacketNoEvent(p);
                }
            }
        }
        resetStates();
    }

    private void resetStates() {
        inventoryPackets.clear();
    }

    private boolean checkCompass() {
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.thePlayer.inventory.getStackInSlot(i);
            if (stack != null && stack.getUnlocalizedName().toLowerCase().contains("compass")) {
                return true;
            }
        }
        return false;
    }

    @EventTarget
    public void onPacket(PacketEvent event) {
        if (!this.isEnabled()) return;
        if (mode.getValue() == 0 && inventory.getValue()) {
            if (!checkCompass()) {
                if (event.getType() == EventType.SEND) {
                    handlePredictionInventory(event);
                }
            }
        }
    }

    private void handlePredictionInventory(PacketEvent event) {
        Packet<?> packet = event.getPacket();
        if (packet instanceof C16PacketClientStatus || packet instanceof C0EPacketClickWindow) {
            event.setCancelled(true);
            inventoryPackets.add(packet);
        } else if (packet instanceof C0DPacketCloseWindow) {
            for (Packet<?> p : inventoryPackets) {
                PacketUtil.sendPacketNoEvent(p);
            }
            inventoryPackets.clear();
        }
    }

}
