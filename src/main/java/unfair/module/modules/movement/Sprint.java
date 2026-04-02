package unfair.module.modules.movement;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.ai.attributes.AttributeModifier;
import net.minecraft.entity.ai.attributes.IAttributeInstance;
import unfair.event.EventTarget;
import unfair.events.TickEvent;
import unfair.mixin.IAccessorEntityLivingBase;
import unfair.module.Module;
import unfair.property.properties.BooleanProperty;
import unfair.util.KeyBindUtil;

public class Sprint extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    public final BooleanProperty foxFix = new BooleanProperty("fov-fix", true);
    private boolean wasSprinting = false;

    public Sprint() {
        super("Sprint", true, true);
    }

    public boolean shouldApplyFovFix(IAttributeInstance attribute) {
        if (!this.foxFix.getValue()) {
            return false;
        } else {
            AttributeModifier attributeModifier = ((IAccessorEntityLivingBase) mc.thePlayer).getSprintingSpeedBoostModifier();
            return attribute.getModifier(attributeModifier.getID()) == null && this.wasSprinting;
        }
    }

    public boolean shouldKeepFov(boolean boolean2) {
        return this.foxFix.getValue() && !boolean2 && this.wasSprinting;
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (this.isEnabled()) {
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
