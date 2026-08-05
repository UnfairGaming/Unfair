package cn.unfair.module.modules.movement;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.ai.attributes.AttributeModifier;
import net.minecraft.entity.ai.attributes.IAttributeInstance;
import cn.unfair.event.EventTarget;
import cn.unfair.events.TickEvent;
import cn.unfair.module.Module;
import cn.unfair.property.properties.BooleanProperty;
import cn.unfair.util.KeyBindUtil;

public class Sprint extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    public final BooleanProperty foxFix = new BooleanProperty("fov-fix", true);
    private boolean wasSprinting = false;

    public Sprint() {
        super("Sprint", true, true);
    }

    public boolean shouldApplyFovFix(IAttributeInstance attribute) {
        if (!this.foxFix.getValue() || mc.thePlayer == null) {
            return false;
        } else {
            AttributeModifier attributeModifier = mc.thePlayer.getSprintingSpeedBoostModifier();
            return attribute.getModifier(attributeModifier.getID()) == null && this.wasSprinting;
        }
    }

    public boolean shouldKeepFov(boolean boolean2) {
        return this.foxFix.getValue() && !boolean2 && this.wasSprinting;
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (this.isEnabled()) {
            if (mc.thePlayer == null || mc.theWorld == null) {
                this.wasSprinting = false;
                KeyBindUtil.updateKeyState(mc.gameSettings.keyBindSprint.getKeyCode());
                return;
            }
            switch (event.getType()) {
                case PRE:
                    KeyBindUtil.setKeyBindState(mc.gameSettings.keyBindSprint.getKeyCode(), true);
                    break;
                case POST:
                    this.wasSprinting = mc.thePlayer.isSprinting();
            }
        }
    }

    @Override
    public void onDisabled() {
        this.wasSprinting = false;
        KeyBindUtil.updateKeyState(mc.gameSettings.keyBindSprint.getKeyCode());
    }
}
