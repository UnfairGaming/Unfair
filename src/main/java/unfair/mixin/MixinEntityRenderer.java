package unfair.mixin;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.renderer.EntityRenderer;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.ItemStack;
import net.minecraft.potion.Potion;
import net.minecraft.util.Vec3;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;
import unfair.Unfair;
import unfair.data.Box;
import unfair.event.EventManager;
import unfair.events.PickEvent;
import unfair.events.RaytraceEvent;
import unfair.events.Render3DEvent;
import unfair.module.modules.combat.KillAura;
import unfair.module.modules.player.AntiDebuff;
import unfair.module.modules.player.AutoBlockIn;
import unfair.module.modules.player.GhostHand;
import unfair.module.modules.player.Scaffold;
import unfair.module.modules.render.FreeLook;
import unfair.module.modules.render.NoHurtCam;
import unfair.module.modules.render.ViewClip;

import java.util.List;

@SideOnly(value = Side.CLIENT)
@Mixin(value = {EntityRenderer.class}, priority = 9999)
public abstract class MixinEntityRenderer {
    @Unique
    private Box<Integer> slot = null;
    @Unique
    private Box<ItemStack> using = null;
    @Unique
    private Box<Integer> useCount = null;
    @Shadow
    private Minecraft mc;  // field_78531_r -> mc

    // rendererUpdateCount field removed - doesn't exist in 1.8.9 EntityRenderer

    @Inject(method = {"updateCameraAndRender"}, at = {@At(value = "HEAD")})
    private void updateCameraAndRender(float partialTicks, long nanoTime, CallbackInfo callbackInfo) {
        if (this.mc.thePlayer != null) {  // field_71439_g -> thePlayer
            KillAura killAura;
            int slot;
            Scaffold scaffold = (Scaffold) Unfair.moduleManager.modules.get(Scaffold.class);
            if (scaffold.isEnabled() && scaffold.itemSpoof.getValue() && (slot = scaffold.getSlot()) >= 0) {
                this.slot = new Box<>(this.mc.thePlayer.inventory.currentItem);  // field_71071_by -> inventory, field_70461_c -> currentItem
                this.mc.thePlayer.inventory.currentItem = slot;
            }
            if ((killAura = (KillAura) Unfair.moduleManager.modules.get(KillAura.class)).isEnabled() && killAura.isBlocking()) {
                this.using = new Box<>(((IAccessorEntityPlayer) this.mc.thePlayer).getItemInUse());
                ((IAccessorEntityPlayer) this.mc.thePlayer).setItemInUse(this.mc.thePlayer.inventory.getCurrentItem());  // func_70448_g() -> getCurrentItem()
                this.useCount = new Box<>(((IAccessorEntityPlayer) this.mc.thePlayer).getItemInUseCount());
                ((IAccessorEntityPlayer) this.mc.thePlayer).setItemInUseCount(69000);
            }
        }
    }

    @Inject(method = {"updateCameraAndRender"}, at = {@At(value = "RETURN")})
    private void postUpdateCameraAndRender(float partialTicks, long nanoTime, CallbackInfo callbackInfo) {
        if (this.slot != null) {
            this.mc.thePlayer.inventory.currentItem = this.slot.value;
            this.slot = null;
        }
        if (this.using != null) {
            ((IAccessorEntityPlayer) this.mc.thePlayer).setItemInUse(this.using.value);
            this.using = null;
        }
        if (this.useCount != null) {
            ((IAccessorEntityPlayer) this.mc.thePlayer).setItemInUseCount(this.useCount.value);
            this.useCount = null;
        }
    }

    @Inject(method = {"updateRenderer"}, at = {@At(value = "HEAD")})
    private void updateRenderer(CallbackInfo callbackInfo) {
        int slot;
        AutoBlockIn autoBlockIn;
        int slot2;
        Scaffold scaffold = (Scaffold) Unfair.moduleManager.modules.get(Scaffold.class);
        if (scaffold.isEnabled() && scaffold.itemSpoof.getValue() && (slot2 = scaffold.getSlot()) >= 0) {
            this.slot = new Box<>(this.mc.thePlayer.inventory.currentItem);
            this.mc.thePlayer.inventory.currentItem = slot2;
        }
        if ((autoBlockIn = (AutoBlockIn) Unfair.moduleManager.modules.get(AutoBlockIn.class)).isEnabled() && autoBlockIn.itemSpoof.getValue() && (slot = autoBlockIn.getSlot()) >= 0) {
            this.slot = new Box<>(this.mc.thePlayer.inventory.currentItem);
            this.mc.thePlayer.inventory.currentItem = slot;
        }
    }

    @Inject(method = {"updateRenderer"}, at = {@At(value = "RETURN")})
    private void postUpdateRenderer(CallbackInfo callbackInfo) {
        if (this.slot != null) {
            this.mc.thePlayer.inventory.currentItem = this.slot.value;
            this.slot = null;
        }
    }

    @Inject(method = {"renderWorldPass"}, at = {@At(value = "FIELD", target = "Lnet/minecraft/client/renderer/EntityRenderer;renderHand:Z", shift = At.Shift.BEFORE, opcode = Opcodes.GETFIELD)})
    private void renderWorldPass(int pass, float partialTicks, long finishTimeNano, CallbackInfo callbackInfo) {
        EventManager.call(new Render3DEvent(partialTicks));
    }

    @ModifyConstant(method = {"hurtCameraEffect"}, constant = {@Constant(floatValue = 14.0f, ordinal = 0)})
    private float hurtCameraEffect(float hurtAmount) {
        if (Unfair.moduleManager == null) {
            return hurtAmount;
        }
        NoHurtCam noHurtCam = (NoHurtCam) Unfair.moduleManager.modules.get(NoHurtCam.class);
        return noHurtCam.isEnabled() ? hurtAmount * (float) noHurtCam.multiplier.getValue() / 100.0f : hurtAmount;
    }

    @ModifyConstant(method = {"getMouseOver"}, constant = {@Constant(doubleValue = 3.0, ordinal = 1)})
    private double getMouseOver(double range) {
        PickEvent event = new PickEvent(range);
        EventManager.call(event);
        return event.getRange();
    }

    @ModifyVariable(method = {"getMouseOver"}, at = @At(value = "STORE"), name = {"d0"})
    private double storeMouseOver(double range) {
        RaytraceEvent event = new RaytraceEvent(range);
        EventManager.call(event);
        return event.getRange();
    }

    @Inject(method = {"getMouseOver"}, at = {@At(value = "INVOKE", target = "Ljava/util/List;size()I", ordinal = 0)}, locals = LocalCapture.CAPTURE_FAILSOFT)
    private void onGetMouseOver(float partialTicks, CallbackInfo callbackInfo, Entity entity, double d0, double d1, Vec3 vec3, boolean isSpectator, int i, Vec3 vec31, Vec3 vec32, Vec3 vec33, float f, List<Entity> list, double d2, int j) {
        GhostHand event;
        if (Unfair.moduleManager != null && (event = (GhostHand) Unfair.moduleManager.modules.get(GhostHand.class)).isEnabled()) {
            list.removeIf(event::shouldSkip);
        }
    }

    @Redirect(method = {"orientCamera"}, at = @At(value = "INVOKE", target = "Lnet/minecraft/util/Vec3;distanceTo(Lnet/minecraft/util/Vec3;)D"))
    private double modifyDistanceTo(Vec3 vec3_1, Vec3 vec3_2) {
        if (Unfair.moduleManager == null) {
            return vec3_1.distanceTo(vec3_2);  // func_72438_d -> distanceTo
        }
        return Unfair.moduleManager.modules.get(ViewClip.class).isEnabled() ? 4.0 : vec3_1.distanceTo(vec3_2);
    }

    @Redirect(method = {"setupFog"}, at = @At(value = "INVOKE", target = "Lnet/minecraft/block/Block;getMaterial()Lnet/minecraft/block/material/Material;"))
    private Material getMaterial(Block block) {
        if (Unfair.moduleManager == null) {
            return block.getMaterial();  // func_149688_o -> getMaterial
        }
        return Unfair.moduleManager.modules.get(ViewClip.class).isEnabled() ? Material.air : block.getMaterial();  // field_151579_a -> air
    }

    @Redirect(method = {"updateFogColor"}, at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/EntityLivingBase;isPotionActive(Lnet/minecraft/potion/Potion;)Z"))
    private boolean isPotionActive(EntityLivingBase entityLivingBase, Potion potion) {
        AntiDebuff antiDebuff;
        if (potion == Potion.blindness && Unfair.moduleManager != null && (antiDebuff = (AntiDebuff) Unfair.moduleManager.modules.get(AntiDebuff.class)).isEnabled() && antiDebuff.blindness.getValue()) {  // field_76440_q -> blindness
            return false;
        }
        return ((IAccessorEntityLivingBase) entityLivingBase).getActivePotionsMap().containsKey(potion.id);  // field_76415_H -> id
    }

    @Redirect(method = {"setupFog"}, at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/EntityLivingBase;isPotionActive(Lnet/minecraft/potion/Potion;)Z"))
    private boolean onIsPotionActive(EntityLivingBase entityLivingBase, Potion potion) {
        AntiDebuff antiDebuff;
        if (potion == Potion.blindness && Unfair.moduleManager != null && (antiDebuff = (AntiDebuff) Unfair.moduleManager.modules.get(AntiDebuff.class)).isEnabled() && antiDebuff.blindness.getValue()) {
            return false;
        }
        return ((IAccessorEntityLivingBase) entityLivingBase).getActivePotionsMap().containsKey(potion.id);
    }

    @Redirect(method = {"setupCameraTransform"}, at = @At(value = "INVOKE", target = "Lnet/minecraft/client/entity/EntityPlayerSP;isPotionActive(Lnet/minecraft/potion/Potion;)Z"))
    private boolean isPotionActivePlayer(EntityPlayerSP entityPlayerSP, Potion potion) {
        AntiDebuff antiDebuff;
        if (potion == Potion.confusion && Unfair.moduleManager != null && (antiDebuff = (AntiDebuff) Unfair.moduleManager.modules.get(AntiDebuff.class)).isEnabled() && antiDebuff.nausea.getValue()) {  // field_76431_k -> confusion
            return false;
        }
        return ((IAccessorEntityLivingBase) entityPlayerSP).getActivePotionsMap().containsKey(potion.id);
    }

    @Redirect(method = {"orientCamera"}, at = @At(value = "FIELD", target = "Lnet/minecraft/entity/Entity;rotationYaw:F", opcode = Opcodes.GETFIELD))
    private float getRotationYaw(Entity entity) {
        return FreeLook.INSTANCE != null && FreeLook.INSTANCE.isActive() ? FreeLook.INSTANCE.cameraYaw : entity.rotationYaw;  // field_70177_z -> rotationYaw
    }

    @Redirect(method = {"orientCamera"}, at = @At(value = "FIELD", target = "Lnet/minecraft/entity/Entity;prevRotationYaw:F", opcode = Opcodes.GETFIELD))
    private float getPrevRotationYaw(Entity entity) {
        return FreeLook.INSTANCE != null && FreeLook.INSTANCE.isActive() ? FreeLook.INSTANCE.prevCameraYaw : entity.prevRotationYaw;  // field_70126_B -> prevRotationYaw
    }

    @Redirect(method = {"orientCamera"}, at = @At(value = "FIELD", target = "Lnet/minecraft/entity/Entity;rotationPitch:F", opcode = Opcodes.GETFIELD))
    private float getRotationPitch(Entity entity) {
        return FreeLook.INSTANCE != null && FreeLook.INSTANCE.isActive() ? FreeLook.INSTANCE.cameraPitch : entity.rotationPitch;  // field_70125_A -> rotationPitch
    }

    @Redirect(method = {"orientCamera"}, at = @At(value = "FIELD", target = "Lnet/minecraft/entity/Entity;prevRotationPitch:F", opcode = Opcodes.GETFIELD))
    private float getPrevRotationPitch(Entity entity) {
        return FreeLook.INSTANCE != null && FreeLook.INSTANCE.isActive() ? FreeLook.INSTANCE.prevCameraPitch : entity.prevRotationPitch;  // field_70127_C -> prevRotationPitch
    }
}