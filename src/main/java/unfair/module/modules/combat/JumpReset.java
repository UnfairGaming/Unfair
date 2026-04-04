package unfair.module.modules.combat;

import net.minecraft.potion.Potion;
import unfair.Unfair;
import unfair.event.EventTarget;
import unfair.events.KnockbackEvent;
import unfair.events.LivingUpdateEvent;
import unfair.mixin.IAccessorEntity;
import unfair.module.Module;
import unfair.property.properties.BooleanProperty;
import unfair.util.ChatUtil;
import unfair.util.MoveUtil;

import static unfair.config.Config.mc;

public class JumpReset extends Module {
    private boolean jumpFlag = false;

    public final BooleanProperty dbg = new BooleanProperty("debug", true);

    public JumpReset() {
        super("JumpReset", false);
    }

    private boolean isInLiquidOrWeb() {
        return mc.thePlayer.isInWater() || mc.thePlayer.isInLava() || ((IAccessorEntity) mc.thePlayer).getIsInWeb();
    }

    @EventTarget
    public void onKnockback(KnockbackEvent event) {
        if (this.isEnabled()) {
            if (mc.thePlayer.hurtTime >= 7) {
                this.jumpFlag = true;
            }
        }
    }

    @EventTarget
    public void onLivingUpdate(LivingUpdateEvent event) {
        if (this.isEnabled() && this.jumpFlag) {
            this.jumpFlag = false;
            if (mc.thePlayer.onGround && MoveUtil.isForwardPressed() && !mc.thePlayer.isPotionActive(Potion.jump) && !this.isInLiquidOrWeb()) {
                mc.thePlayer.movementInput.jump = true;
                if(this.dbg.getValue()) ChatUtil.sendFormatted(Unfair.clientName + "jump");
            }
        }

    }
}
