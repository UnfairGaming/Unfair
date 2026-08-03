package cn.unfair.mixin;

import cn.unfair.module.modules.combat.velocity.PolarVelocity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.Redirect;
import cn.unfair.Unfair;
import cn.unfair.module.modules.movement.KeepSprint;

@SideOnly(Side.CLIENT)
@Mixin({EntityPlayer.class})
public abstract class MixinEntityPlayer extends MixinEntityLivingBase {
    @ModifyConstant(
            method = {"attackTargetEntityWithCurrentItem"},
            constant = {@Constant(
                    doubleValue = 0.6
            )}
    )
    private double attackTargetEntityWithCurrentItem(double speed) {
        if (Unfair.moduleManager == null) {
            return speed;
        } else {
            KeepSprint keepSprint = (KeepSprint) Unfair.moduleManager.modules.get(KeepSprint.class);
            return keepSprint.isEnabled() && keepSprint.shouldKeepSprint()
                    ? speed + (1.0 - speed) * (1.0 - keepSprint.slowdown.getValue().doubleValue() / 100.0)
                    : speed;
        }
    }

    @Redirect(
            method = "onLivingUpdate",
            at = @At(
                    value = "FIELD",
                    target = "Lnet/minecraft/entity/player/EntityPlayer;motionX:D",
                    ordinal = 0
            )
    )
    private double modifyMotionX(EntityPlayer instance) {
        if (PolarVelocity.mode.getValue() == 0) {
            return instance.motionX * 0.59928D;
        } else {
            return instance.motionX;
        }
    }

    @Redirect(
            method = "onLivingUpdate",
            at = @At(
                    value = "FIELD",
                    target = "Lnet/minecraft/entity/player/EntityPlayer;motionZ:D",
                    ordinal = 0
            )
    )
    private double modifyMotionZ(EntityPlayer instance) {
        if (PolarVelocity.mode.getValue() == 0) {
            return instance.motionZ * 0.59928D;
        } else {
            return instance.motionZ;
        }
    }

    @Redirect(
            method = {"attackTargetEntityWithCurrentItem"},
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/entity/player/EntityPlayer;setSprinting(Z)V"
            )
    )
    private void setSprinnt(EntityPlayer entityPlayer, boolean boolean2) {
        if (Unfair.moduleManager != null) {
            KeepSprint keepSprint = (KeepSprint) Unfair.moduleManager.modules.get(KeepSprint.class);
            if (!keepSprint.isEnabled() || !keepSprint.shouldKeepSprint()) {
                entityPlayer.setSprinting(boolean2);
            }
        }
    }
}
