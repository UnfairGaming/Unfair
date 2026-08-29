package cn.unfair.module.modules.player;

import cn.unfair.event.EventTarget;
import cn.unfair.event.types.EventType;
import cn.unfair.events.KeyEvent;
import cn.unfair.events.TickEvent;
import cn.unfair.module.Module;
import cn.unfair.property.properties.BooleanProperty;
import cn.unfair.util.player.ItemUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.item.ItemStack;

public class ThrowPearl extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();

    public final BooleanProperty swing = new BooleanProperty("Swing", true);
    public final BooleanProperty restore = new BooleanProperty("Restore Slot", true);

    private int stage = 0;
    private int originalSlot = -1;
    private int pearlSlot = -1;

    public ThrowPearl() {
        super("ThrowPearl", false);
    }

    @EventTarget
    public void onKey(KeyEvent event) {
        if (!this.isEnabled() || this.stage != 0 || event.getKey() != -98) {
            return;
        }
        if (mc.theWorld == null || mc.thePlayer == null || mc.thePlayer.isDead || mc.thePlayer.isSpectator()) {
            return;
        }

        int pearl = this.findPearlSlot();
        if (pearl == -1) {
            return;
        }

        this.originalSlot = mc.thePlayer.inventory.currentItem;
        this.pearlSlot = pearl;
        this.stage = 1;
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (!this.isEnabled() || event.type() != EventType.PRE || this.stage == 0) {
            return;
        }
        if (mc.theWorld == null || mc.thePlayer == null || mc.thePlayer.isDead) {
            this.reset();
            return;
        }

        switch (this.stage) {
            case 1 -> {
                if (mc.thePlayer.inventory.currentItem != this.pearlSlot) {
                    mc.thePlayer.inventory.currentItem = this.pearlSlot;
                    mc.playerController.syncCurrentPlayItem();
                }
                this.stage = 2;
            }
            case 2 -> {
                ItemStack held = mc.thePlayer.inventory.getCurrentItem();
                if (held != null && ItemUtil.isEnderPearl(held)
                        && mc.playerController.sendUseItem(mc.thePlayer, mc.theWorld, held)) {
                    if (this.swing.getValue()) {
                        mc.thePlayer.swingItem();
                    }
                }
                if (this.restore.getValue()) {
                    this.stage = 3;
                } else {
                    this.reset();
                }
            }
            case 3 -> {
                if (mc.thePlayer.inventory.currentItem != this.originalSlot) {
                    mc.thePlayer.inventory.currentItem = this.originalSlot;
                    mc.playerController.syncCurrentPlayItem();
                }
                this.reset();
            }
        }
    }

    @Override
    public void onDisabled() {
        this.reset();
    }

    private void reset() {
        this.stage = 0;
        this.originalSlot = -1;
        this.pearlSlot = -1;
    }

    private int findPearlSlot() {
        for (int i = 0; i < 9; ++i) {
            ItemStack stack = mc.thePlayer.inventory.getStackInSlot(i);
            if (ItemUtil.isEnderPearl(stack)) {
                return i;
            }
        }
        return -1;
    }
}
