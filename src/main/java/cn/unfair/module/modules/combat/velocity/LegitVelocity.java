package cn.unfair.module.modules.combat.velocity;

import cn.unfair.event.EventTarget;
import cn.unfair.events.KnockbackEvent;
import cn.unfair.events.LivingUpdateEvent;
import cn.unfair.module.SubModule;
import cn.unfair.property.properties.PercentProperty;
import cn.unfair.util.client.RandomUtil;
import cn.unfair.util.player.PlayerUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.potion.Potion;

public class LegitVelocity extends SubModule {
    private static final Minecraft mc = Minecraft.getMinecraft();

    public final PercentProperty chance = new PercentProperty("Chance", 100);
    private boolean jumpFlag = false;

    public LegitVelocity() {
        super("Legit");
    }

    @EventTarget
    public void onKnockback(KnockbackEvent event) {
        if (this.isEnabled() && !event.isCancelled()) {
            this.jumpFlag = event.getY() > 0.0
                    && RandomUtil.nextFloat(0.0F, 100.0F) < chance.getValue();
        }
    }

    @EventTarget
    public void onLivingUpdate(LivingUpdateEvent event) {
        if (this.jumpFlag) {
            this.jumpFlag = false;
            if (mc.thePlayer.onGround && mc.thePlayer.isSprinting() && !mc.thePlayer.isPotionActive(Potion.jump) && !PlayerUtil.isInLiquidOrWeb()) {
                mc.thePlayer.movementInput.jump = true;
            }
        }
    }
}
