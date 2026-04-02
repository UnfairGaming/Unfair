package unfair.module.modules.combat;

import net.minecraft.potion.Potion;
import unfair.event.EventTarget;
import unfair.events.LivingUpdateEvent;
import unfair.events.StrafeEvent;
import unfair.mixin.IAccessorEntity;
import unfair.module.Module;

import static unfair.config.Config.mc;

public class JumpReset extends Module {
    private int hurtTime;
    private boolean jumpFlag = false;

    public JumpReset() {
        super("JumpReset", false);
    }

    private boolean isInLiquidOrWeb() {
        return mc.thePlayer.isInWater() || mc.thePlayer.isInLava() || ((IAccessorEntity) mc.thePlayer).getIsInWeb();
    }

    @EventTarget
    public void onStrafe(StrafeEvent event) {
        if (this.isEnabled()) {
            if (mc.thePlayer.hurtTime == 9 && this.hurtTime != 9 && !mc.gameSettings.keyBindJump.isKeyDown() && mc.thePlayer != null && mc.thePlayer.onGround) {
                this.jumpFlag = true;
            }

            this.hurtTime = mc.thePlayer.hurtTime;
        }

    }

    @EventTarget
    public void onLivingUpdate(LivingUpdateEvent event) {
        if (this.isEnabled() && this.jumpFlag) {
            this.jumpFlag = false;
            if (mc.thePlayer.onGround && mc.thePlayer.isSprinting() && !mc.thePlayer.isPotionActive(Potion.jump) && !this.isInLiquidOrWeb()) {
                mc.thePlayer.movementInput.jump = true;
            }
        }

    }
}
