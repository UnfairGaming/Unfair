package cn.unfair.module.modules.combat;

import cn.unfair.Unfair;
import cn.unfair.event.EventTarget;
import cn.unfair.events.KnockbackEvent;
import cn.unfair.events.LivingUpdateEvent;
import cn.unfair.module.Module;
import cn.unfair.property.properties.BooleanProperty;
import cn.unfair.util.ChatUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.potion.Potion;

public class JumpReset extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();

    public BooleanProperty dbg = new BooleanProperty("debug", false);

    private boolean jumpFlag = false;

    public JumpReset() {
        super("JumpReset", false);
    }

    private boolean isInLiquidOrWeb() {
        return mc.thePlayer.isInWater() || mc.thePlayer.isInLava() || mc.thePlayer.getIsInWeb();
    }

    @EventTarget
    public void onKnockback(KnockbackEvent event) {
        if (this.isEnabled() && !event.isCancelled()) {
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
