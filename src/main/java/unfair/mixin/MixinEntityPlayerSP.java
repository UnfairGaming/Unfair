package unfair.mixin;

import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.entity.Entity;
import net.minecraft.potion.Potion;
import net.minecraft.util.BlockPos;
import net.minecraft.util.MathHelper;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import unfair.Unfair;
import unfair.event.EventManager;
import unfair.event.types.EventType;
import unfair.events.LivingUpdateEvent;
import unfair.events.MoveInputEvent;
import unfair.events.PlayerUpdateEvent;
import unfair.events.UpdateEvent;
import unfair.management.RotationState;
import unfair.module.modules.movement.NoSlow;
import unfair.module.modules.player.AntiDebuff;

@SideOnly(Side.CLIENT)
@Mixin({EntityPlayerSP.class})
public abstract class MixinEntityPlayerSP extends MixinEntityPlayer {
    @Shadow
    public float renderArmYaw;
    @Shadow
    public float prevRenderArmYaw;
    @Unique
    private float overrideYaw = Float.NaN;
    @Unique
    private float overridePitch = Float.NaN;
    @Unique
    private float pendingYaw;
    @Unique
    private float pendingPitch;
    @Shadow
    private float lastReportedYaw;
    @Shadow
    private float lastReportedPitch;

    @Inject(
            method = {"onUpdate"},
            at = {@At("HEAD")}
    )
    private void onUpdate(CallbackInfo callbackInfo) {
        if (this.worldObj.isBlockLoaded(new BlockPos(this.posX, 0.0, this.posZ))) {
            UpdateEvent event = new UpdateEvent(EventType.PRE, this.lastReportedYaw, this.lastReportedPitch, this.rotationYaw, this.rotationPitch);
            EventManager.call(event);
            RotationState.applyState(event.isRotated() && !Entity.class.cast(this).isRiding(), event.getNewYaw(), event.getNewPitch(), event.getPreYaw(), event.isRotating());
            if (event.isRotated()) {
                this.pendingYaw = this.rotationYaw;
                this.pendingPitch = this.rotationPitch;
                this.overrideYaw = event.getNewYaw();
                this.overridePitch = event.getNewPitch();
            } else {
                this.pendingYaw = Float.NaN;
                this.pendingPitch = Float.NaN;
                this.overrideYaw = Float.NaN;
                this.overridePitch = Float.NaN;
            }
        }
    }

    @Inject(
            method = {"onUpdate"},
            at = {@At("RETURN")}
    )
    private void postUpdate(CallbackInfo callbackInfo) {
        if (this.worldObj.isBlockLoaded(new BlockPos(this.posX, 0.0, this.posZ))) {
            if (!Float.isNaN(this.pendingYaw) && !Float.isNaN(this.pendingPitch)) {
                this.lastReportedYaw = this.rotationYaw;
                this.lastReportedPitch = this.rotationPitch;
                this.rotationYaw = this.rotationYaw + MathHelper.wrapAngleTo180_float(this.pendingYaw - this.rotationYaw);
                this.rotationPitch = this.pendingPitch;
                this.prevRotationYaw = this.rotationYaw;
                this.prevRotationPitch = this.rotationPitch;
                this.prevRenderArmYaw = this.rotationYaw - (this.renderArmYaw - this.prevRenderArmYaw) * 2.0F;
                this.renderArmYaw = this.rotationYaw;
            }
            EventManager.call(new UpdateEvent(EventType.POST, this.lastReportedYaw, this.lastReportedPitch, this.rotationYaw, this.rotationPitch));
        }
    }

    @Redirect(
            method = {"onUpdate"},
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/entity/EntityPlayerSP;isRiding()Z"
            )
    )
    private boolean onRidding(EntityPlayerSP entityPlayerSP) {
        if (!Float.isNaN(this.overrideYaw) && !Float.isNaN(this.overridePitch)) {
            this.rotationYaw = this.overrideYaw;
            this.rotationPitch = this.overridePitch;
        }
        return entityPlayerSP.isRiding();
    }

    @Inject(
            method = {"onUpdate"},
            at = {@At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/entity/EntityPlayerSP;onUpdateWalkingPlayer()V"
            )}
    )
    private void onMotionUpdate(CallbackInfo callbackInfo) {
        EventManager.call(new PlayerUpdateEvent());
    }

    @Inject(
            method = {"onLivingUpdate"},
            at = {@At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/entity/AbstractClientPlayer;onLivingUpdate()V"
            )}
    )
    private void onLivingUpdate(CallbackInfo callbackInfo) {
        EventManager.call(new LivingUpdateEvent());
    }

    @Inject(
            method = {"onLivingUpdate"},
            at = {@At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/util/MovementInput;updatePlayerMoveState()V",
                    shift = At.Shift.AFTER
            )}
    )
    private void updateMove(CallbackInfo callbackInfo) {
        EventManager.call(new MoveInputEvent());
    }

    @Redirect(
            method = {"onLivingUpdate"},
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/entity/EntityPlayerSP;isUsingItem()Z"
            )
    )
    private boolean isUsing(EntityPlayerSP entityPlayerSP) {
        NoSlow noSlow = (NoSlow) Unfair.moduleManager.modules.get(NoSlow.class);
        return (!noSlow.isEnabled() || !noSlow.isAnyActive()) && entityPlayerSP.isUsingItem();
    }

    @Redirect(
            method = {"onLivingUpdate"},
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/entity/EntityPlayerSP;isPotionActive(Lnet/minecraft/potion/Potion;)Z"
            )
    )
    private boolean checkPotion(EntityPlayerSP entityPlayerSP, Potion potion) {
        if (potion == Potion.confusion && Unfair.moduleManager != null) {
            AntiDebuff antiDebuff = (AntiDebuff) Unfair.moduleManager.modules.get(AntiDebuff.class);
            if (antiDebuff.isEnabled() && antiDebuff.nausea.getValue()) {
                return false;
            }
        }
        return ((IAccessorEntityLivingBase) entityPlayerSP).getActivePotionsMap().containsKey(potion.id);
    }
}
