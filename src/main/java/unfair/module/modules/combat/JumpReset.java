package unfair.module.modules.combat;

import net.minecraft.client.Minecraft;
import net.minecraft.potion.Potion;
import unfair.Unfair;
import unfair.event.EventTarget;
import unfair.events.KnockbackEvent;
import unfair.events.LivingUpdateEvent;
import unfair.mixin.IAccessorEntity;
import unfair.module.Module;
import unfair.property.properties.BooleanProperty;
import unfair.util.ChatUtil;

public class JumpReset extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();

    public BooleanProperty dbg = new BooleanProperty("debug", false);

    private boolean jumpFlag = false;

    public JumpReset() {
        super("JumpReset", false);
    }

    private boolean isInLiquidOrWeb() {
        return mc.thePlayer.isInWater() || mc.thePlayer.isInLava() || ((IAccessorEntity) mc.thePlayer).getIsInWeb();
    }

    @EventTarget
    public void onKnockback(KnockbackEvent event) {
        if (this.isEnabled() || !event.isCancelled()) {
            this.jumpFlag = event.getY() > 0.0;
        }
    }

    @EventTarget
    public void onLivingUpdate(LivingUpdateEvent event) {
        if (this.jumpFlag) {
            this.jumpFlag = false;
            if (mc.thePlayer.onGround && mc.thePlayer.isSprinting() && !mc.thePlayer.isPotionActive(Potion.jump) && !this.isInLiquidOrWeb()) {
                mc.thePlayer.movementInput.jump = true;
                if (dbg.getValue()) {
                    ChatUtil.sendFormatted(Unfair.clientName + "Jump");
                }
            }
        }
    }
}