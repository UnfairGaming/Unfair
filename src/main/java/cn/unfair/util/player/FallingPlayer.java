package cn.unfair.util.player;

import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.util.Vec3;

/**
 * 1.8.9 port of LiquidBounce/Southside's {@code FallingPlayer}: predicts the player's
 * position over a few ticks using the vanilla air-movement math (drag + gravity + strafe).
 */
public class FallingPlayer {

    private double x;
    private double y;
    private double z;
    private double motionX;
    private double motionY;
    private double motionZ;
    private final float yaw;
    private final float strafe;
    private final float forward;
    private final float jumpMovementFactor;
    private final float eyeHeight;

    public FallingPlayer(EntityPlayerSP player) {
        this.x = player.posX;
        this.y = player.posY;
        this.z = player.posZ;
        this.motionX = player.motionX;
        this.motionY = player.motionY;
        this.motionZ = player.motionZ;
        this.yaw = player.rotationYaw;
        this.strafe = player.movementInput.moveStrafe;
        this.forward = player.movementInput.moveForward;
        this.jumpMovementFactor = player.isSprinting() ? 0.026F : 0.02F;
        this.eyeHeight = player.getEyeHeight();
    }

    public void calculate(int ticks) {
        for (int i = 0; i < ticks; i++) {
            calculateForTick();
        }
    }

    private void calculateForTick() {
        float dragX = 0.91F;
        float dragZ = 0.91F;
        float dragY = 0.98F;
        float acceleration = this.jumpMovementFactor;

        updateVelocity(acceleration, this.strafe, this.forward);

        this.x += this.motionX;
        this.y += this.motionY;
        this.z += this.motionZ;

        this.motionY -= 0.08D;

        this.motionX *= dragX;
        this.motionY *= dragY;
        this.motionZ *= dragZ;
    }

    private void updateVelocity(float speed, float strafe, float forward) {
        float lengthSquared = strafe * strafe + forward * forward;
        if (lengthSquared < 1.0E-7F) {
            return;
        }

        float normalizedStrafe = strafe;
        float normalizedForward = forward;
        if (lengthSquared > 1.0F) {
            float invLength = 1.0F / (float) Math.sqrt(lengthSquared);
            normalizedStrafe *= invLength;
            normalizedForward *= invLength;
        }
        normalizedStrafe *= speed;
        normalizedForward *= speed;

        float sinYaw = (float) Math.sin(this.yaw * Math.PI / 180.0F);
        float cosYaw = (float) Math.cos(this.yaw * Math.PI / 180.0F);
        this.motionX += normalizedStrafe * cosYaw - normalizedForward * sinYaw;
        this.motionZ += normalizedForward * cosYaw + normalizedStrafe * sinYaw;
    }

    public double getX() {
        return this.x;
    }

    public double getY() {
        return this.y;
    }

    public double getZ() {
        return this.z;
    }

    public Vec3 getPos() {
        return new Vec3(this.x, this.y, this.z);
    }

    public Vec3 getEyePos() {
        return new Vec3(this.x, this.y + this.eyeHeight, this.z);
    }
}
