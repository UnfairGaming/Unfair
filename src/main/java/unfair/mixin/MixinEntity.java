package unfair.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.entity.Entity;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import unfair.Unfair;
import unfair.event.EventManager;
import unfair.events.KnockbackEvent;
import unfair.events.SafeWalkEvent;
import unfair.module.modules.render.FreeLook;

@SideOnly(value = Side.CLIENT)
@Mixin(value = {Entity.class}, priority = 9999)
public abstract class MixinEntity {
    @Shadow
    public World worldObj;
    @Shadow
    public double posX;
    @Shadow
    public double posY;
    @Shadow
    public double posZ;
    @Shadow
    public double motionX;
    @Shadow
    public double motionY;
    @Shadow
    public double motionZ;
    @Shadow
    public float rotationYaw;
    @Shadow
    public float rotationPitch;
    @Shadow
    public float prevRotationYaw;
    @Shadow
    public float prevRotationPitch;
    @Shadow
    public boolean onGround;

    @Shadow
    public boolean isInWater() {
        return false;
    }

    @Inject(method = {"setVelocity"}, at = {@At(value = "HEAD")}, cancellable = true)
    private void setVelocity(double x, double y, double z, CallbackInfo callbackInfo) {
        if (Entity.class.cast(this) instanceof EntityPlayerSP) {
            KnockbackEvent event = new KnockbackEvent(x, y, z);
            EventManager.call(event);
            if (event.isCancelled()) {
                callbackInfo.cancel();
                this.motionX = event.getX();
                this.motionY = event.getY();
                this.motionZ = event.getZ();
            }
        }
    }

    @Inject(method = {"setAngles"}, at = {@At(value = "HEAD")}, cancellable = true)
    private void setAngles(float yaw, float pitch, CallbackInfo callbackInfo) {
        FreeLook freeLook;
        if (Entity.class.cast(this) instanceof EntityPlayerSP && Unfair.rotationManager != null && Unfair.rotationManager.isRotated()) {
            callbackInfo.cancel();
            return;
        }
        if (Entity.class.cast(this) == Minecraft.getMinecraft().thePlayer && (freeLook = FreeLook.INSTANCE) != null && freeLook.isActive()) {
            freeLook.cameraYaw += yaw * 0.15f;
            freeLook.cameraPitch -= pitch * 0.15f;
            if (freeLook.cameraPitch > 90.0f) {
                freeLook.cameraPitch = 90.0f;
            }
            if (freeLook.cameraPitch < -90.0f) {
                freeLook.cameraPitch = -90.0f;
            }
            callbackInfo.cancel();
        }
    }

    @ModifyVariable(method = {"moveEntity"}, ordinal = 0, at = @At(value = "STORE"), name = {"flag"})
    private boolean moveEntity(boolean safeWalkFlag) {
        if (Entity.class.cast(this) instanceof EntityPlayerSP) {
            SafeWalkEvent event = new SafeWalkEvent(safeWalkFlag);
            EventManager.call(event);
            return event.isSafeWalk();
        }
        return safeWalkFlag;
    }
}