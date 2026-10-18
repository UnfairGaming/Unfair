package cn.unfair.module.modules.movement;

import cn.unfair.event.EventTarget;
import cn.unfair.events.LivingUpdateEvent;
import cn.unfair.events.MoveInputEvent;
import cn.unfair.events.StrafeEvent;
import cn.unfair.module.Module;
import cn.unfair.property.properties.FloatProperty;
import cn.unfair.property.properties.ModeProperty;
import net.minecraft.client.Minecraft;

public class Stuck extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();

    public final ModeProperty mode = new ModeProperty("Mode", 0, new String[]{"Pulse", "Normal"});
    public final FloatProperty pulseDelay = new FloatProperty("PulseDelay", 20.0F, 0.0F, 200.0F);

    private int pulseTimer;
    private boolean passMovement;

    public Stuck() {
        super("Stuck", false, false);
    }

    @EventTarget
    public void onMoveInput(MoveInputEvent event) {
        if (!this.isEnabled() || mc.thePlayer == null) {
            return;
        }
        mc.thePlayer.movementInput.moveForward = 0.0F;
        mc.thePlayer.movementInput.moveStrafe = 0.0F;
        mc.thePlayer.movementInput.jump = false;
        mc.thePlayer.movementInput.sneak = false;
    }

    @EventTarget
    public void onLivingUpdate(LivingUpdateEvent event) {
        if (!this.isEnabled() || mc.thePlayer == null) {
            return;
        }
        this.passMovement = false;
        mc.thePlayer.setSprinting(false);
        if (this.pulseTimer > 0) {
            this.pulseTimer -= 50;
        }
        if (mc.thePlayer.hurtTime != 0) {
            this.passMovement = true;
        } else if (this.mode.getValue() == 0 && this.pulseTimer <= 0) {
            this.pulseTimer = this.pulseTimer + (int) (this.pulseDelay.getValue() * 50.0F);
            this.passMovement = true;
        }
        if (!this.passMovement) {
            mc.thePlayer.motionX = 0.0;
            mc.thePlayer.motionY = 0.0;
            mc.thePlayer.motionZ = 0.0;
        }
    }

    @EventTarget
    public void onStrafe(StrafeEvent event) {
        if (this.isEnabled() && !this.passMovement) {
            event.setForward(0.0F);
            event.setStrafe(0.0F);
        }
    }

    @Override
    public void onDisabled() {
        this.passMovement = false;
        this.pulseTimer = 0;
        if (mc.thePlayer != null) {
            mc.thePlayer.setSprinting(false);
        }
    }

    @Override
    public String[] getSuffix() {
        return new String[]{this.mode.getModeString()};
    }
}
