package cn.unfair.module.modules.combat;

import cn.unfair.Unfair;
import cn.unfair.event.EventTarget;
import cn.unfair.event.types.EventType;
import cn.unfair.events.LeftClickMouseEvent;
import cn.unfair.events.UpdateEvent;
import cn.unfair.mixin.IAccessorPlayerControllerMP;
import cn.unfair.module.Module;
import cn.unfair.module.modules.render.HUD;
import cn.unfair.property.properties.BooleanProperty;
import cn.unfair.property.properties.FloatProperty;
import cn.unfair.property.properties.IntProperty;
import cn.unfair.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemFishingRod;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.client.C08PacketPlayerBlockPlacement;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MathHelper;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;

import java.awt.*;

public class AutoRod extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();

    public final FloatProperty minRange = new FloatProperty("min-range", 3.2F, 1.0F, 8.0F);
    public final FloatProperty maxRange = new FloatProperty("max-range", 4.5F, 1.0F, 8.0F);
    public final IntProperty useDelayOnSwitch = new IntProperty("use-delay-on-switch", 1, 0, 10);
    public final IntProperty maxWaitDelay = new IntProperty("max-wait-delay", 2, 0, 20);
    public final IntProperty maxRecastDelay = new IntProperty("max-recast-delay", 1, 0, 20);
    public final IntProperty switchBackDelay = new IntProperty("switchback-delay", 3, 0, 10);
    public final IntProperty fov = new IntProperty("fov", 90, 0, 360);
    public final BooleanProperty rotate = new BooleanProperty("rotate", true);
    public final FloatProperty basePrediction = new FloatProperty("base-prediction", 2.0F, 0.0F, 8.0F, this.rotate::getValue);
    public final BooleanProperty onlyOnKillAura = new BooleanProperty("only-on-kill-aura", false);
    public final BooleanProperty overrideAuraRots = new BooleanProperty("override-kill-aura-rots", true);

    private final TimerUtil recastTimer = new TimerUtil();
    private final TimerUtil delayTimer = new TimerUtil();
    private final TimerUtil switchTimer = new TimerUtil();
    private final TimerUtil useTimer = new TimerUtil();
    private boolean usingRod;
    private int oldSlot = -1;
    private EntityLivingBase currentTarget;
    private boolean resetSpoofing = true;
    private double predictionSize;
    private boolean rotating;
    private int rodState;
    private int rodSlot = -1;

    public AutoRod() {
        super("AutoRod", false);
    }

    @Override
    public void onEnabled() {
        this.oldSlot = mc.thePlayer != null ? mc.thePlayer.inventory.currentItem : -1;
        this.currentTarget = null;
        this.usingRod = false;
        this.resetSpoofing = true;
        this.rotating = false;
        this.rodState = 0;
        this.rodSlot = -1;
        this.recastTimer.reset();
        this.delayTimer.reset();
        this.switchTimer.reset();
        this.useTimer.reset();
    }

    @Override
    public void onDisabled() {
        this.restoreSlot();
        this.currentTarget = null;
        this.usingRod = false;
        this.resetSpoofing = true;
        this.rotating = false;
        this.rodState = 0;
        this.rodSlot = -1;
    }

    @EventTarget
    public void onUpdate(UpdateEvent event) {
        if (!this.isEnabled() || event.getType() != EventType.PRE || mc.thePlayer == null || mc.theWorld == null) {
            return;
        }

        if (BadPacketUtil.bad()) return;

        BackTrack backTrack = (BackTrack) Unfair.moduleManager.modules.get(BackTrack.class);
        if (backTrack.isEnabled() && backTrack.isBackTracking) {
            return;
        }

        AutoProjectiles autoProjectiles = ((AutoProjectiles) Unfair.moduleManager.modules.get(AutoProjectiles.class));
        if ((autoProjectiles.isEnabled() && autoProjectiles.hasProjectile())) {
            return;
        }

        this.currentTarget = this.getTarget(this.maxRange.getValue());
        if (this.currentTarget == null
                || !mc.thePlayer.canEntityBeSeen(this.currentTarget)
                || mc.thePlayer.isUsingItem()
                || (this.onlyOnKillAura.getValue() && !this.isKillAuraEnabled())) {
            this.reset();
            return;
        }

        this.predictionSize = this.basePrediction.getValue()
                + Math.min(Math.max((this.getDistanceToEntityBox(this.currentTarget) - this.minRange.getValue()) * 3.0D, 0.0D), 8.0D);

        Vec3 playerEyes = mc.thePlayer.getPositionEyes(1.0F).add(this.getFlatMoveDelta(mc.thePlayer, this.predictionSize));
        Vec3 targetEyes = this.currentTarget.getPositionEyes(1.0F).add(this.getFlatMoveDelta(this.currentTarget, this.predictionSize));
        double predictedDistance = this.getCustomDistanceToEntityBox(playerEyes, targetEyes, this.currentTarget);

        this.updateRotations(event);
        if (this.rodState != 0) {
            this.handleRodState(event);
            return;
        }

        if (predictedDistance >= this.minRange.getValue()
                && predictedDistance <= this.maxRange.getValue()
                && this.getRotationDifference(this.currentTarget) <= this.fov.getValue()) {
            if (!this.usingRod) {
                if (this.delayTimer.hasTimeElapsed(this.maxWaitDelay.getValue() * 50L) || this.currentTarget.hurtTime <= 3) {
                    int rod = this.findRod();
                    if (rod != -1) {
                        this.startRodUse(rod);
                    }
                }
            } else if (this.recastTimer.hasTimeElapsed(this.maxRecastDelay.getValue() * 50L) || this.currentTarget.hurtTime >= 9) {
                this.reset();
            }
        } else if (this.recastTimer.hasTimeElapsed(this.maxRecastDelay.getValue() * 50L)) {
            this.reset();
        }
    }

    @EventTarget
    public void onLeftClick(LeftClickMouseEvent event) {
        if (this.isEnabled() && !this.delayTimer.hasTimeElapsed(50L)) {
            event.setCancelled(true);
        }
    }

    private void updateRotations(UpdateEvent event) {
        if (this.onlyOnKillAura.getValue() && !this.isKillAuraEnabled()) {
            this.rotating = false;
            return;
        }

        double range = this.getDistanceToEntityBox(this.currentTarget);
        ItemStack currentItem = mc.thePlayer.inventory.getCurrentItem();
        this.rotating = this.rotate.getValue()
                && this.currentTarget != null
                && (this.overrideAuraRots.getValue() || !this.isKillAuraEnabled())
                && range > this.minRange.getValue()
                && range <= this.maxRange.getValue()
                && currentItem != null
                && currentItem.getItem() instanceof ItemFishingRod;
        if (this.rotating) {
            float[] rotations = this.faceTrajectory(this.currentTarget, (float) this.predictionSize, 0.03F, 2.0F);
            event.setRotation(rotations[0], rotations[1], 2);
            event.setPervRotation(rotations[0], 2);
        }
    }

    private EntityLivingBase getTarget(double distance) {
        EntityLivingBase target = null;
        double closestRayDistance = Double.MAX_VALUE;
        Vec3 mouseOver = this.getMouseOverHitVec(distance + 1.5D);

        for (Object object : mc.theWorld.loadedEntityList) {
            if (!(object instanceof EntityLivingBase)) {
                continue;
            }
            EntityLivingBase entity = (EntityLivingBase) object;
            if (!this.isValidTarget(entity, distance)) {
                continue;
            }

            double rayDistance = this.getCustomDistanceToEntityBox(mouseOver, entity);
            if (rayDistance < closestRayDistance) {
                target = entity;
                closestRayDistance = rayDistance;
            }
        }

        return target;
    }

    private boolean isValidTarget(EntityLivingBase entity, double distance) {
        if (entity == mc.thePlayer || entity == mc.thePlayer.ridingEntity || entity.deathTime > 0 || entity.isDead) {
            return false;
        }
        if (!(entity instanceof EntityPlayer)) {
            return false;
        }
        if (this.getDistanceToEntityBox(entity) > distance) {
            return false;
        }
        EntityPlayer player = (EntityPlayer) entity;
        if (TeamUtil.isFriend(player)) {
            return false;
        }
        if (TeamUtil.shouldBlockTarget(player)) {
            return false;
        }
        return true;
    }

    private void reset() {
        if (!this.switchTimer.hasTimeElapsed(this.switchBackDelay.getValue() * 50L)) {
            return;
        }

        if (!this.resetSpoofing) {
            this.restoreSlot();
        }

        this.recastTimer.reset();
        this.oldSlot = mc.thePlayer.inventory.currentItem;
        this.usingRod = false;
        this.resetSpoofing = true;
        this.rotating = false;
        this.rodState = 0;
        this.rodSlot = -1;
    }

    private void restoreSlot() {
        if (mc.thePlayer == null || mc.playerController == null) {
            return;
        }
        if (this.oldSlot >= 0 && this.oldSlot < 9) {
            mc.thePlayer.inventory.currentItem = this.oldSlot;
        }
        ((IAccessorPlayerControllerMP) mc.playerController).callSyncCurrentPlayItem();
    }

    private int findRod() {
        for (int slot = 36; slot < 45; slot++) {
            ItemStack stack = mc.thePlayer.inventoryContainer.getSlot(slot).getStack();
            if (stack != null && stack.getItem() instanceof ItemFishingRod) {
                return slot;
            }
        }
        return -1;
    }

    private void startRodUse(int rod) {
        if (this.oldSlot < 0 || this.oldSlot > 8) {
            this.oldSlot = mc.thePlayer.inventory.currentItem;
        }
        this.rodSlot = rod - 36;
        this.rodState = 1;
    }

    private void handleRodState(UpdateEvent event) {
        switch (this.rodState) {
            case 1:
                mc.thePlayer.inventory.currentItem = this.rodSlot;
                if (this.resetSpoofing) {
                    this.useTimer.reset();
                    this.resetSpoofing = false;
                }
                this.rodState = 2;
                break;
            case 2:
                mc.thePlayer.inventory.currentItem = this.rodSlot;
                if (!this.useTimer.hasTimeElapsed(this.useDelayOnSwitch.getValue() * 50L)) {
                    break;
                }
                if (this.rotate.getValue()) {
                    float[] rotations = this.faceTrajectory(this.currentTarget, (float) this.predictionSize, 0.03F, 2.0F);
                    event.setRotation(rotations[0], rotations[1], 2);
                    event.setPervRotation(rotations[0], 2);
                    this.rotating = true;
                }
                this.rodState = 3;
                break;
            case 3:
                mc.thePlayer.inventory.currentItem = this.rodSlot;
                this.castRod();
                this.rodState = 0;
                this.rodSlot = -1;
                break;
            default:
                this.rodState = 0;
                this.rodSlot = -1;
        }
    }

    private void castRod() {
        ItemStack stack = mc.thePlayer.inventory.getCurrentItem();
        if (stack == null || !(stack.getItem() instanceof ItemFishingRod)) {
            this.reset();
            return;
        }
        ((IAccessorPlayerControllerMP) mc.playerController).callSyncCurrentPlayItem();
        PacketUtil.sendPacket(new C08PacketPlayerBlockPlacement(stack));
        this.usingRod = true;
        this.recastTimer.reset();
        this.switchTimer.reset();
        this.delayTimer.reset();
    }

    private boolean isKillAuraEnabled() {
        Module killAura = Unfair.moduleManager != null ? Unfair.moduleManager.modules.get(KillAura.class) : null;
        return killAura != null && killAura.isEnabled();
    }

    private double getDistanceToEntityBox(Entity entity) {
        return this.getCustomDistanceToEntityBox(mc.thePlayer.getPositionEyes(1.0F), entity);
    }

    private double getCustomDistanceToEntityBox(Vec3 eyes, Entity entity) {
        return this.distance(eyes, this.getBestHitVec(entity.getEntityBoundingBox().expand(
                entity.getCollisionBorderSize(),
                entity.getCollisionBorderSize(),
                entity.getCollisionBorderSize()
        ), eyes));
    }

    private double getCustomDistanceToEntityBox(Vec3 eyes, Vec3 entityEyes, Entity entity) {
        AxisAlignedBB box = entity.getEntityBoundingBox().expand(
                entity.getCollisionBorderSize(),
                entity.getCollisionBorderSize(),
                entity.getCollisionBorderSize()
        );
        AxisAlignedBB predictedBox = box.offset(entityEyes.xCoord - entity.getPositionEyes(1.0F).xCoord,
                entityEyes.yCoord - entity.getPositionEyes(1.0F).yCoord,
                entityEyes.zCoord - entity.getPositionEyes(1.0F).zCoord);
        return this.distance(eyes, this.getBestHitVec(predictedBox, eyes));
    }

    private Vec3 getBestHitVec(AxisAlignedBB boundingBox, Vec3 eyes) {
        return new Vec3(
                MathHelper.clamp_double(eyes.xCoord, boundingBox.minX, boundingBox.maxX),
                MathHelper.clamp_double(eyes.yCoord, boundingBox.minY, boundingBox.maxY),
                MathHelper.clamp_double(eyes.zCoord, boundingBox.minZ, boundingBox.maxZ)
        );
    }

    private Vec3 getFlatMoveDelta(Entity entity, double multiplier) {
        return new Vec3(
                (entity.posX - entity.prevPosX) * multiplier,
                0.0D,
                (entity.posZ - entity.prevPosZ) * multiplier
        );
    }

    private double distance(Vec3 first, Vec3 second) {
        double x = first.xCoord - second.xCoord;
        double y = first.yCoord - second.yCoord;
        double z = first.zCoord - second.zCoord;
        return Math.sqrt(x * x + y * y + z * z);
    }

    private float getRotationDifference(Entity entity) {
        float[] target = RotationUtil.getRotations(
                entity.posX,
                entity.posY + entity.getEyeHeight(),
                entity.posZ,
                mc.thePlayer.posX,
                mc.thePlayer.posY + mc.thePlayer.getEyeHeight(),
                mc.thePlayer.posZ
        );
        float yawDiff = MathHelper.wrapAngleTo180_float(target[0] - mc.thePlayer.rotationYaw);
        float pitchDiff = target[1] - mc.thePlayer.rotationPitch;
        return (float) Math.hypot(Math.abs(yawDiff), Math.abs(pitchDiff));
    }

    private float[] faceTrajectory(Entity target, float predictSize, float gravity, float velocity) {
        double posX = target.posX + (target.posX - target.prevPosX) * predictSize
                - (mc.thePlayer.posX + (mc.thePlayer.posX - mc.thePlayer.prevPosX));
        double posY = target.getEntityBoundingBox().minY
                + (target.getEntityBoundingBox().minY - target.prevPosY) * predictSize
                + target.getEyeHeight()
                - 0.15D
                - (mc.thePlayer.getEntityBoundingBox().minY + (mc.thePlayer.posY - mc.thePlayer.prevPosY))
                - mc.thePlayer.getEyeHeight();
        double posZ = target.posZ + (target.posZ - target.prevPosZ) * predictSize
                - (mc.thePlayer.posZ + (mc.thePlayer.posZ - mc.thePlayer.prevPosZ));
        double horizontalDistance = Math.sqrt(posX * posX + posZ * posZ);

        velocity = Math.min((velocity * velocity + velocity * 2.0F) / 3.0F, 1.0F);
        float gravityModifier = 0.12F * gravity;
        double root = velocity * velocity * velocity * velocity
                - gravityModifier * (gravityModifier * horizontalDistance * horizontalDistance + 2.0D * posY * velocity * velocity);

        if (root < 0.0D) {
            return RotationUtil.getRotationsTo(posX, posY, posZ, mc.thePlayer.rotationYaw, mc.thePlayer.rotationPitch);
        }

        return new float[]{
                (float) Math.toDegrees(Math.atan2(posZ, posX)) - 90.0F,
                (float) -Math.toDegrees(Math.atan((velocity * velocity - Math.sqrt(root)) / (gravityModifier * horizontalDistance)))
        };
    }

    private Vec3 getMouseOverHitVec(double reach) {
        Vec3 eyes = mc.thePlayer.getPositionEyes(1.0F);
        Vec3 look = mc.thePlayer.getLook(1.0F);
        Vec3 end = eyes.addVector(look.xCoord * reach, look.yCoord * reach, look.zCoord * reach);
        MovingObjectPosition result = mc.theWorld.rayTraceBlocks(eyes, end, false, false, false);
        return result != null && result.hitVec != null ? result.hitVec : end;
    }
}
