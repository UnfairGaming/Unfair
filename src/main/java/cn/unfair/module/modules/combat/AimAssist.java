package cn.unfair.module.modules.combat;

import cn.unfair.Unfair;
import cn.unfair.event.EventTarget;
import cn.unfair.event.types.EventType;
import cn.unfair.events.*;
import cn.unfair.management.RotationState;
import cn.unfair.module.Module;
import cn.unfair.module.modules.player.Reach;
import cn.unfair.property.properties.*;
import cn.unfair.util.client.TeamUtil;
import cn.unfair.util.player.ItemUtil;
import cn.unfair.util.player.MoveUtil;
import cn.unfair.util.rotation.RayCastUtil;
import cn.unfair.util.rotation.RotationUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.network.play.client.C02PacketUseEntity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MathHelper;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class AimAssist extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private static final int ROTATION_PRIORITY = 0;

    public final ModeProperty mode = new ModeProperty("Mode", 0, new String[]{"Normal", "Silent"});
    public final IntProperty speed = new IntProperty("Speed", 10, 1, 30);
    public final PercentProperty horizontalMultipoint = new PercentProperty("Horizontal Multipoint", 0);
    public final PercentProperty verticalMultipoint = new PercentProperty("Vertical Multipoint", 0);
    public final PercentProperty randomization = new PercentProperty("Randomization", 50);
    public final IntProperty fov = new IntProperty("Fov", 90, 15, 360);
    public final FloatProperty range = new FloatProperty("Range", 4.5F, 0.0F, 5.0F);
    public final ModeProperty sort = new ModeProperty(
            "Sort", 1, new String[]{"Health", "Angle", "Hurt Time", "Distance"}
    );
    public final BooleanProperty throughWalls = new BooleanProperty("Through Walls", true);
    public final BooleanProperty throughEntities = new BooleanProperty("Through Entities", true);
    public final BooleanProperty invisibles = new BooleanProperty("Invisibles", false);
    public final BooleanProperty clickAim = new BooleanProperty("Click Aim", true);
    public final BooleanProperty stopWhenBreaking = new BooleanProperty("Stop When Breaking", false);
    public final BooleanProperty keepMoveDirection = new BooleanProperty(
            "Keep Move Direction", true, () -> this.mode.getValue() == 1
    );
    public final IntProperty hoverDelay = new IntProperty(
            "Hover Delay", 100, 0, 500, this.stopWhenBreaking::getValue
    );
    public final BooleanProperty weaponsOnly = new BooleanProperty("Weapons Only", false);
    public final BooleanProperty allowTools = new BooleanProperty("Allow Tools", false, this.weaponsOnly::getValue);

    private long miningStartTime = -1L;
    private boolean controllingAim;
    private EntityPlayer target;
    private float serverYaw;
    private float serverPitch;
    private int targetTicks;

    public AimAssist() {
        super("AimAssist", false);
    }

    @Override
    public void onDisabled() {
        this.miningStartTime = -1L;
        this.controllingAim = false;
        this.target = null;
        this.targetTicks = 0;
    }

    @EventTarget
    public void onUpdate(UpdateEvent event) {
        if (!this.isEnabled()
                || event.getType() != EventType.PRE
                || this.mode.getValue() != 1) {
            return;
        }
        this.controllingAim = false;
        if (!this.isReady() || this.hasAuraTarget()) {
            this.target = null;
            this.targetTicks = 0;
            return;
        }

        float baseYaw = event.getYaw();
        float basePitch = event.getPitch();
        EntityPlayer target = this.getTarget(baseYaw);
        if (target == null) {
            this.target = null;
            this.targetTicks = 0;
            return;
        }

        float[] rotations = this.getRotations(target, baseYaw, basePitch);
        if (rotations == null) {
            return;
        }

        event.setRotation(rotations[0], rotations[1], ROTATION_PRIORITY);
        event.setPervRotation(rotations[0], ROTATION_PRIORITY);
        if (this.target != target) {
            this.targetTicks = 0;
        } else {
            this.targetTicks++;
        }
        this.target = target;
        this.serverYaw = rotations[0];
        this.serverPitch = rotations[1];
        this.controllingAim = true;
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (!this.isEnabled()
                || event.type() != EventType.POST
                || this.mode.getValue() != 0) {
            return;
        }
        this.controllingAim = false;
        this.target = null;
        if (!this.isReady() || this.hasAuraTarget()) {
            return;
        }

        EntityPlayer target = this.getTarget(mc.thePlayer.rotationYaw);
        if (target == null) {
            return;
        }
        float[] rotations = this.getRotations(target, mc.thePlayer.rotationYaw, mc.thePlayer.rotationPitch);
        if (rotations != null) {
            this.target = target;
            mc.thePlayer.rotationYaw = rotations[0];
            mc.thePlayer.rotationPitch = rotations[1];
            this.controllingAim = true;
        }
    }

    @EventTarget
    public void onHitBlock(HitBlockEvent event) {
        if (this.controllingAim
                && mc.objectMouseOver != null
                && mc.objectMouseOver.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK) {
            event.setCancelled(true);
        }
    }

    @EventTarget
    public void onLeftClick(LeftClickMouseEvent event) {
        if (this.controllingAim
                && this.mode.getValue() == 1
                && this.target != null
                && !this.target.isDead
                && this.targetTicks > 0
                && this.isInAttackRange(this.target)) {
            if (!this.canRayTraceTarget(this.target)) {
                event.setCancelled(true);
                return;
            }
            mc.objectMouseOver = new MovingObjectPosition(this.target);
            return;
        }
        if (this.controllingAim
                && mc.objectMouseOver != null
                && mc.objectMouseOver.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK) {
            event.setCancelled(true);
        }
    }

    @EventTarget
    public void onPacket(PacketEvent event) {
        if (!this.isEnabled()
                || event.getType() != EventType.SEND
                || event.isCancelled()
                || this.mode.getValue() != 1
                || !(event.getPacket() instanceof C02PacketUseEntity packet)
                || packet.getAction() != C02PacketUseEntity.Action.ATTACK) {
            return;
        }
        Entity attackedEntity = mc.theWorld == null ? null : packet.getEntityFromWorld(mc.theWorld);
        if (!this.controllingAim
                || this.target == null
                || this.target.isDead
                || this.targetTicks <= 0
                || attackedEntity != this.target
                || !this.isInAttackRange(this.target)
                || !this.canRayTraceTarget(this.target)) {
            event.setCancelled(true);
        }
    }

    @EventTarget
    public void onMove(MoveInputEvent event) {
        if (this.isEnabled()
                && this.mode.getValue() == 1
                && this.keepMoveDirection.getValue()
                && RotationState.isActived()
                && RotationState.getPriority() == ROTATION_PRIORITY
                && (mc.thePlayer.movementInput.moveForward != 0.0F
                || mc.thePlayer.movementInput.moveStrafe != 0.0F)) {
            MoveUtil.fixStrafe(RotationState.getSmoothedYaw());
        }
    }

    private boolean hasAuraTarget() {
        KillAura killAura = (KillAura) Unfair.moduleManager.getModule(KillAura.class);
        return killAura != null && killAura.isEnabled() && killAura.getTarget() != null;
    }

    private boolean isInAttackRange(EntityPlayer player) {
        return RotationUtil.distanceToEntity(player) <= this.getAttackRange();
    }

    private double getAttackRange() {
        Reach reach = (Reach) Unfair.moduleManager.getModule(Reach.class);
        return reach != null && reach.isEnabled() ? reach.range.getValue() : 3.0D;
    }

    private boolean canRayTraceTarget(EntityPlayer player) {
        MovingObjectPosition hit = RayCastUtil.rayTrace(
                this.getExpandedBox(player), this.serverYaw, this.serverPitch, this.getAttackRange()
        );
        return hit != null;
    }

    private boolean isReady() {
        if (mc.thePlayer == null || mc.theWorld == null || mc.currentScreen != null || !mc.inGameHasFocus) {
            this.miningStartTime = -1L;
            return false;
        }
        if (this.weaponsOnly.getValue()
                && !ItemUtil.isHoldingSword()
                && (!this.allowTools.getValue() || !ItemUtil.isHoldingTool())) {
            return false;
        }
        if (this.clickAim.getValue() && !Mouse.isButtonDown(0)) {
            return false;
        }
        if (this.stopWhenBreaking.getValue() && this.isMining()) {
            if (this.miningStartTime == -1L) {
                this.miningStartTime = System.currentTimeMillis();
            }
            return System.currentTimeMillis() - this.miningStartTime < this.hoverDelay.getValue();
        }
        this.miningStartTime = -1L;
        return true;
    }

    private boolean isMining() {
        int keyCode = mc.gameSettings.keyBindAttack.getKeyCode();
        if (keyCode == 0) {
            return false;
        }
        boolean attackDown = keyCode < 0 ? Mouse.isButtonDown(keyCode + 100) : Keyboard.isKeyDown(keyCode);
        if (!attackDown) {
            return false;
        }
        RayCastUtil.RayCastResult hit = RayCastUtil.rayCast(
                new RotationUtil.RotationVec(mc.thePlayer.rotationYaw, mc.thePlayer.rotationPitch),
                mc.playerController.getBlockReachDistance()
        );
        return hit != null && hit.typeOfHit == RayCastUtil.RayCastResult.Type.BLOCK;
    }

    private EntityPlayer getTarget(float viewYaw) {
        double rangeSquared = this.range.getValue() * this.range.getValue();
        List<EntityPlayer> candidates = new ArrayList<>();

        for (EntityPlayer player : mc.theWorld.playerEntities) {
            if (player == mc.thePlayer || player.deathTime != 0 || player.isDead) {
                continue;
            }
            if (TeamUtil.isFriend(player)) {
                continue;
            }
            if (TeamUtil.shouldBlockTarget(player)) {
                continue;
            }
            if (!this.invisibles.getValue() && player.isInvisible()) {
                continue;
            }
            if (this.distanceSquaredToBox(player) > rangeSquared) {
                continue;
            }
            if (this.fov.getValue() != 360 && !this.isInFov(viewYaw, player)) {
                continue;
            }
            candidates.add(player);
        }

        candidates.sort(this.getTargetComparator().thenComparingDouble(mc.thePlayer::getDistanceSqToEntity));
        if (!this.throughWalls.getValue() || !this.throughEntities.getValue()) {
            for (EntityPlayer candidate : candidates) {
                if (this.findValidAimPoint(candidate) != null) {
                    return candidate;
                }
            }
            return null;
        }
        return candidates.isEmpty() ? null : candidates.get(0);
    }

    private Comparator<EntityPlayer> getTargetComparator() {
        switch (this.sort.getValue()) {
            case 0:
                return Comparator.comparingDouble(player -> player.getHealth() + player.getAbsorptionAmount());
            case 2:
                return Comparator.comparingInt(player -> player.hurtTime);
            case 3:
                return Comparator.comparingDouble(mc.thePlayer::getDistanceSqToEntity);
            default:
                return Comparator.comparingDouble(this::getAngleDifference);
        }
    }

    private double getAngleDifference(EntityPlayer player) {
        Vec3 aimPoint = this.getAimPoint(player);
        float[] rotations = this.getRotationsToPoint(
                aimPoint, mc.thePlayer.rotationYaw, mc.thePlayer.rotationPitch
        );
        return Math.abs(MathHelper.wrapAngleTo180_float(rotations[0] - mc.thePlayer.rotationYaw))
                + Math.abs(rotations[1] - mc.thePlayer.rotationPitch);
    }

    private boolean isInFov(float viewYaw, EntityPlayer player) {
        float targetYaw = (float) Math.toDegrees(Math.atan2(
                player.posZ - mc.thePlayer.posZ, player.posX - mc.thePlayer.posX
        )) - 90.0F;
        return Math.abs(MathHelper.wrapAngleTo180_float(viewYaw - targetYaw)) < this.fov.getValue() * 0.5F;
    }

    private float[] getRotations(EntityPlayer target, float baseYaw, float basePitch) {
        Vec3 point = !this.throughWalls.getValue() || !this.throughEntities.getValue()
                ? this.findValidAimPoint(target)
                : this.getAimPoint(target);
        if (point == null) {
            return null;
        }
        float[] targetRotations = this.getRotationsToPoint(point, baseYaw, basePitch);
        return RotationUtil.smoothRotation(baseYaw, basePitch, targetRotations[0], targetRotations[1], this.speed.getValue(), this.randomization.getValue());
    }

    private Vec3 getAimPoint(Entity target) {
        AxisAlignedBB box = this.getExpandedBox(target);
        Vec3 eye = mc.thePlayer.getPositionEyes(1.0F);
        return RotationUtil.getAimPoint(
                box, eye, target.posY + target.getEyeHeight(),
                this.horizontalMultipoint.getValue() / 100.0D,
                this.verticalMultipoint.getValue() / 100.0D
        );
    }

    private Vec3 findValidAimPoint(Entity target) {
        Vec3 eye = mc.thePlayer.getPositionEyes(1.0F);
        return RotationUtil.getBestAimPoint(
                target, this.getExpandedBox(target), eye, target.posY + target.getEyeHeight(),
                this.range.getValue(),
                this.horizontalMultipoint.getValue() / 100.0D,
                this.verticalMultipoint.getValue() / 100.0D,
                this.throughWalls.getValue(), this.throughEntities.getValue()
        );
    }

    private float[] getRotationsToPoint(Vec3 point, float baseYaw, float basePitch) {
        return RotationUtil.getRotationsToPoint(
                point, mc.thePlayer.getPositionEyes(1.0F), baseYaw, basePitch, 3.0F
        );
    }

    private AxisAlignedBB getExpandedBox(Entity entity) {
        float border = entity.getCollisionBorderSize();
        return entity.getEntityBoundingBox().expand(border, border, border);
    }

    private double distanceSquaredToBox(Entity entity) {
        Vec3 eye = mc.thePlayer.getPositionEyes(1.0F);
        Vec3 closest = RotationUtil.getClosestPointOnBox(eye, this.getExpandedBox(entity));
        return eye.squareDistanceTo(closest);
    }

    @Override
    public String[] getSuffix() {
        return new String[]{this.mode.getModeString()};
    }
}
