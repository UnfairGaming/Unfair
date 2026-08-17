package cn.unfair.module.modules.combat;

import cn.unfair.Unfair;
import cn.unfair.event.EventTarget;
import cn.unfair.events.LivingUpdateEvent;
import cn.unfair.module.Module;
import cn.unfair.property.properties.BooleanProperty;
import cn.unfair.property.properties.IntProperty;
import cn.unfair.property.properties.ModeProperty;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.EntityLivingBase;

public class Criticals extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();

    public final ModeProperty mode = new ModeProperty("Mode", 0, new String[]{"StopSprint"});
    public final BooleanProperty controlSprintKey = new BooleanProperty("Control Sprint Key", true);
    public final IntProperty hurtTimeMin = new IntProperty("Hurt Time Min", 0, 0, 10);
    public final IntProperty hurtTimeMax = new IntProperty("Hurt Time Max", 2, 0, 10);

    public Criticals() {
        super("Criticals", false);
    }

    @EventTarget
    public void onLivingUpdate(LivingUpdateEvent event) {
        if (!this.isEnabled() || mc.thePlayer == null || this.mode.getValue() != 0) {
            return;
        }

        KillAura killAura = (KillAura) Unfair.moduleManager.modules.get(KillAura.class);
        if (killAura == null || !killAura.isEnabled()) {
            return;
        }

        Velocity velocity = (Velocity) Unfair.moduleManager.modules.get(Velocity.class);
        if (velocity != null && velocity.isEnabled() && velocity.isDelayingVelocity()) {
            return;
        }

        EntityLivingBase target = killAura.getTarget();
        if (target == null) {
            return;
        }

        if (mc.thePlayer.motionY <= -0.08
                && !mc.thePlayer.onGround
                && !mc.thePlayer.isInWater()
                && !mc.thePlayer.isInLava()
                && !mc.thePlayer.isOnLadder()
                && mc.thePlayer.isSprinting()
                && target.hurtTime >= this.hurtTimeMin.getValue()
                && target.hurtTime <= this.hurtTimeMax.getValue()) {

            if (this.controlSprintKey.getValue()) {
                mc.gameSettings.keyBindSprint.pressed = false;
            }
            mc.thePlayer.setSprinting(false);
        }
    }

    @Override
    public void verifyValue(String name) {
        if (this.hurtTimeMin.getName().equals(name)
                && this.hurtTimeMin.getValue() > this.hurtTimeMax.getValue()) {
            this.hurtTimeMax.setValue(this.hurtTimeMin.getValue());
        } else if (this.hurtTimeMax.getName().equals(name)
                && this.hurtTimeMin.getValue() > this.hurtTimeMax.getValue()) {
            this.hurtTimeMin.setValue(this.hurtTimeMax.getValue());
        }
    }

    @Override
    public String[] getSuffix() {
        return new String[]{this.mode.getModeString()};
    }
}
