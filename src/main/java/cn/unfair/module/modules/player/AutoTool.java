package cn.unfair.module.modules.player;

import cn.unfair.event.EventTarget;
import cn.unfair.event.types.EventType;
import cn.unfair.events.TickEvent;
import cn.unfair.module.Module;
import cn.unfair.property.properties.BooleanProperty;
import cn.unfair.property.properties.IntProperty;
import cn.unfair.util.player.ItemUtil;
import cn.unfair.util.client.KeyBindUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.util.MovingObjectPosition.MovingObjectType;

public class AutoTool extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    public final IntProperty switchDelay = new IntProperty("Delay", 0, 0, 5);
    public final BooleanProperty switchBack = new BooleanProperty("Switch Back", true);
    public final BooleanProperty sneakOnly = new BooleanProperty("Sneak Only", true);
    private int currentToolSlot = -1;
    private int previousSlot = -1;
    private int tickDelayCounter = 0;

    public AutoTool() {
        super("AutoTool", false);
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (this.isEnabled() && event.type() == EventType.PRE) {
            if (this.currentToolSlot != -1 && this.currentToolSlot != mc.thePlayer.inventory.currentItem) {
                this.currentToolSlot = -1;
                this.previousSlot = -1;
            }
            if (mc.objectMouseOver != null
                    && mc.objectMouseOver.typeOfHit == MovingObjectType.BLOCK
                    && mc.gameSettings.keyBindAttack.isKeyDown()
                    && !mc.thePlayer.isUsingItem()) {
                if (this.tickDelayCounter >= this.switchDelay.getValue()
                        && (!(Boolean) this.sneakOnly.getValue() || KeyBindUtil.isKeyDown(mc.gameSettings.keyBindSneak.getKeyCode()))) {
                    int slot = ItemUtil.findInventorySlot(
                            mc.thePlayer.inventory.currentItem, mc.theWorld.getBlockState(mc.objectMouseOver.getBlockPos()).getBlock()
                    );
                    if (mc.thePlayer.inventory.currentItem != slot) {
                        if (this.previousSlot == -1) {
                            this.previousSlot = mc.thePlayer.inventory.currentItem;
                        }
                        mc.thePlayer.inventory.currentItem = this.currentToolSlot = slot;
                    }
                }
                this.tickDelayCounter++;
            } else {
                if (this.switchBack.getValue() && this.previousSlot != -1) {
                    mc.thePlayer.inventory.currentItem = this.previousSlot;
                }
                this.currentToolSlot = -1;
                this.previousSlot = -1;
                this.tickDelayCounter = 0;
            }
        }
    }

    @Override
    public void onDisabled() {
        this.currentToolSlot = -1;
        this.previousSlot = -1;
        this.tickDelayCounter = 0;
    }
}
