package cn.unfair.module.modules.movement;

import cn.unfair.event.EventTarget;
import cn.unfair.event.types.EventType;
import cn.unfair.events.LivingUpdateEvent;
import cn.unfair.events.StuckInBlockEvent;
import cn.unfair.events.TickEvent;
import cn.unfair.module.Module;
import cn.unfair.property.properties.BooleanProperty;
import cn.unfair.property.properties.FloatProperty;
import cn.unfair.property.properties.ModeProperty;
import net.minecraft.client.Minecraft;

public class FastWeb extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();

    public final ModeProperty mode = new ModeProperty("Mode", 0, new String[]{"Heypixel", "LiquidBounce", "Intave14"});
    public final FloatProperty strength = new FloatProperty("Strength", 0.23F, 0.01F, 0.8F, () -> this.mode.getValue() == 1);
    public final BooleanProperty motionYEnabled = new BooleanProperty("MotionY", false, () -> this.mode.getValue() == 1);
    public final FloatProperty motionYStrength = new FloatProperty("MotionYStrength", 0.6F, -2.0F, 2.0F, () -> this.mode.getValue() == 1 && this.motionYEnabled.getValue());
    public final BooleanProperty onlyGround = new BooleanProperty("OnlyOnGround", false, () -> this.mode.getValue() == 1);

    private int playerInWebTick = 0;
    private int ticksInWeb = 0;

    public FastWeb() {
        super("FastWeb", false);
    }

    @EventTarget
    public void onLivingUpdate(LivingUpdateEvent event) {
        if (!this.isEnabled() || mc.thePlayer == null || this.mode.getValue() != 0) {
            return;
        }

        if (this.ticksInWeb > 1) {
            mc.thePlayer.movementInput.jump = false;
        }
    }

    @EventTarget
    public void onStuck(StuckInBlockEvent event) {
        if (!this.isEnabled() || mc.thePlayer == null || event.entity() != mc.thePlayer || this.mode.getValue() != 0) {
            return;
        }

        this.playerInWebTick = mc.thePlayer.ticksExisted;
        this.ticksInWeb++;
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (!this.isEnabled() || mc.thePlayer == null || event.type() != EventType.POST || this.mode.getValue() != 0) {
            return;
        }

        if (this.playerInWebTick < mc.thePlayer.ticksExisted) {
            this.ticksInWeb = 0;
        }
    }

    public boolean shouldBoostWeb() {
        return this.isEnabled() && this.mode.getValue() == 0 && this.ticksInWeb > 5;
    }

    public boolean applyWebMotion() {
        if (!this.isEnabled() || mc.thePlayer == null) {
            return false;
        }

        if (this.mode.getValue() == 1) {
            this.applyLiquidBounce();
            return true;
        }
        return false;
    }

    private void applyLiquidBounce() {
        if (!this.isPlayerMoving()) {
            return;
        }

        if (mc.thePlayer.onGround || !this.onlyGround.getValue()) {
            this.setStrafe(this.strength.getValue().doubleValue());
        }

        if (this.motionYEnabled.getValue()) {
            mc.thePlayer.motionY = this.motionYStrength.getValue().doubleValue();
        }
    }

    private void setStrafe(double speed) {
        double yaw = Math.toRadians(this.getStrafeYaw());
        mc.thePlayer.motionX = -Math.sin(yaw) * speed;
        mc.thePlayer.motionZ = Math.cos(yaw) * speed;
    }

    private boolean isPlayerMoving() {
        return mc.thePlayer.movementInput.moveForward != 0.0F || mc.thePlayer.movementInput.moveStrafe != 0.0F;
    }

    private float getStrafeYaw() {
        float yaw = mc.thePlayer.rotationYaw;
        float forward = mc.thePlayer.movementInput.moveForward;
        float strafe = mc.thePlayer.movementInput.moveStrafe;
        if (forward < 0.0F) {
            yaw += 180.0F;
        }
        if (strafe != 0.0F) {
            float multiplier = forward == 0.0F ? 1.0F : 0.5F * Math.signum(forward);
            yaw += -90.0F * multiplier * Math.signum(strafe);
        }
        return yaw;
    }

    @Override
    public void onDisabled() {
        this.playerInWebTick = 0;
        this.ticksInWeb = 0;
    }

    @Override
    public String[] getSuffix() {
        return new String[]{this.mode.getModeString()};
    }
}
