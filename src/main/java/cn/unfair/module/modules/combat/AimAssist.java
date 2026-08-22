package cn.unfair.module.modules.combat;

import cn.unfair.Unfair;
import cn.unfair.event.EventTarget;
import cn.unfair.event.types.EventType;
import cn.unfair.events.*;
import cn.unfair.management.RotationState;
import cn.unfair.module.Module;
import cn.unfair.module.modules.player.Reach;
import cn.unfair.property.properties.*;
import cn.unfair.util.*;
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
    private static final double BACKUP_FACE_INSET = 0.05D;
    private static final int BACKUP_POINT_COUNT = 30;

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
        return this.smoothRotation(baseYaw, basePitch, targetRotations[0], targetRotations[1]);
    }

    private Vec3 getAimPoint(Entity target) {
        AxisAlignedBB box = this.getExpandedBox(target);
        Vec3 eye = mc.thePlayer.getPositionEyes(1.0F);
        double centerX = (box.minX + box.maxX) * 0.5D;
        double centerY = target.posY + target.getEyeHeight();
        double centerZ = (box.minZ + box.maxZ) * 0.5D;
        if (box.isVecInside(eye)) {
            return new Vec3(centerX, eye.yCoord, centerZ);
        }
        Vec3 closest = RotationUtil.getClosestPointOnBox(eye, box);
        double horizontal = this.horizontalMultipoint.getValue() / 100.0D;
        double vertical = this.verticalMultipoint.getValue() / 100.0D;
        return new Vec3(
                centerX + (closest.xCoord - centerX) * horizontal,
                centerY + (closest.yCoord - centerY) * vertical,
                centerZ + (closest.zCoord - centerZ) * horizontal
        );
    }

    private Vec3 findValidAimPoint(Entity target) {
        Vec3 eye = mc.thePlayer.getPositionEyes(1.0F);
        Vec3 mainPoint = this.getAimPoint(target);
        if (eye.squareDistanceTo(mainPoint) < 1.0E-6D) {
            return mainPoint;
        }
        if (!this.rayHitsTarget(eye, mainPoint, target)) {
            return null;
        }
        if (this.canAimAtPoint(eye, mainPoint, target)) {
            return mainPoint;
        }

        List<Vec3> backupPoints = this.buildBackupPoints(target, eye);
        backupPoints.sort(Comparator.comparingDouble(eye::squareDistanceTo));
        for (Vec3 point : backupPoints) {
            if (this.canAimAtPoint(eye, point, target)) {
                return point;
            }
        }
        return null;
    }

    private boolean canAimAtPoint(Vec3 eye, Vec3 point, Entity target) {
        Vec3 delta = point.subtract(eye);
        double length = delta.lengthVector();
        if (length < 1.0E-6D) {
            return false;
        }
        double scale = this.range.getValue() / length;
        Vec3 end = eye.addVector(delta.xCoord * scale, delta.yCoord * scale, delta.zCoord * scale);
        MovingObjectPosition targetHit = this.getExpandedBox(target).calculateIntercept(eye, end);
        if (targetHit == null) {
            return false;
        }
        double targetDistanceSquared = eye.squareDistanceTo(targetHit.hitVec);

        if (!this.throughWalls.getValue()) {
            MovingObjectPosition blockHit = mc.theWorld.rayTraceBlocks(eye, end, false, false, false);
            if (blockHit != null && eye.squareDistanceTo(blockHit.hitVec) < targetDistanceSquared) {
                return false;
            }
        }
        return this.throughEntities.getValue()
                || !this.hasBlockingEntity(eye, end, target, targetDistanceSquared);
    }

    private boolean hasBlockingEntity(Vec3 eye, Vec3 end, Entity target, double targetDistanceSquared) {
        for (Entity entity : mc.theWorld.loadedEntityList) {
            if (entity == mc.thePlayer || entity == target || entity.isDead || !entity.canBeCollidedWith()) {
                continue;
            }
            AxisAlignedBB box = this.getExpandedBox(entity);
            if (box.isVecInside(eye)) {
                return true;
            }
            MovingObjectPosition hit = box.calculateIntercept(eye, end);
            if (hit != null && eye.squareDistanceTo(hit.hitVec) < targetDistanceSquared - 1.0E-7D) {
                return true;
            }
        }
        return false;
    }

    private boolean rayHitsTarget(Vec3 eye, Vec3 point, Entity target) {
        Vec3 delta = point.subtract(eye);
        double length = delta.lengthVector();
        if (length < 1.0E-6D) {
            return false;
        }
        double scale = this.range.getValue() / length;
        Vec3 end = eye.addVector(delta.xCoord * scale, delta.yCoord * scale, delta.zCoord * scale);
        return this.getExpandedBox(target).calculateIntercept(eye, end) != null;
    }

    private List<Vec3> buildBackupPoints(Entity target, Vec3 eye) {
        AxisAlignedBB box = this.getExpandedBox(target);
        boolean positiveX = eye.xCoord > box.maxX;
        boolean negativeX = eye.xCoord < box.minX;
        boolean positiveY = eye.yCoord > box.maxY;
        boolean negativeY = eye.yCoord < box.minY;
        boolean positiveZ = eye.zCoord > box.maxZ;
        boolean negativeZ = eye.zCoord < box.minZ;
        int visibleFaces = (positiveX || negativeX ? 1 : 0)
                + (positiveY || negativeY ? 1 : 0)
                + (positiveZ || negativeZ ? 1 : 0);
        List<Vec3> points = new ArrayList<>();
        if (visibleFaces == 0) {
            return points;
        }

        int pointsPerFace = BACKUP_POINT_COUNT / visibleFaces;
        if (positiveX || negativeX) {
            this.addFaceGrid(points, 0, positiveX ? box.maxX - BACKUP_FACE_INSET : box.minX + BACKUP_FACE_INSET,
                    box.minY + BACKUP_FACE_INSET, box.maxY - BACKUP_FACE_INSET,
                    box.minZ + BACKUP_FACE_INSET, box.maxZ - BACKUP_FACE_INSET, pointsPerFace);
        }
        if (positiveY || negativeY) {
            this.addFaceGrid(points, 1, positiveY ? box.maxY - BACKUP_FACE_INSET : box.minY + BACKUP_FACE_INSET,
                    box.minX + BACKUP_FACE_INSET, box.maxX - BACKUP_FACE_INSET,
                    box.minZ + BACKUP_FACE_INSET, box.maxZ - BACKUP_FACE_INSET, pointsPerFace);
        }
        if (positiveZ || negativeZ) {
            this.addFaceGrid(points, 2, positiveZ ? box.maxZ - BACKUP_FACE_INSET : box.minZ + BACKUP_FACE_INSET,
                    box.minX + BACKUP_FACE_INSET, box.maxX - BACKUP_FACE_INSET,
                    box.minY + BACKUP_FACE_INSET, box.maxY - BACKUP_FACE_INSET, pointsPerFace);
        }
        return points;
    }

    private void addFaceGrid(
            List<Vec3> points, int fixedAxis, double fixedValue,
            double firstMin, double firstMax, double secondMin, double secondMax, int targetPoints
    ) {
        double firstSize = firstMax - firstMin;
        double secondSize = secondMax - secondMin;
        int firstCount = Math.max(2, (int) Math.round(Math.sqrt(targetPoints * firstSize / secondSize)));
        int secondCount = Math.max(2, (int) Math.round(Math.sqrt(targetPoints * secondSize / firstSize)));
        for (int first = 0; first < firstCount; first++) {
            double firstValue = firstMin + firstSize * first / (firstCount - 1);
            for (int second = 0; second < secondCount; second++) {
                double secondValue = secondMin + secondSize * second / (secondCount - 1);
                switch (fixedAxis) {
                    case 0:
                        points.add(new Vec3(fixedValue, firstValue, secondValue));
                        break;
                    case 1:
                        points.add(new Vec3(firstValue, fixedValue, secondValue));
                        break;
                    default:
                        points.add(new Vec3(firstValue, secondValue, fixedValue));
                }
            }
        }
    }

    private float[] getRotationsToPoint(Vec3 point, float baseYaw, float basePitch) {
        Vec3 eye = mc.thePlayer.getPositionEyes(1.0F);
        double deltaX = point.xCoord - eye.xCoord;
        double deltaY = point.yCoord - eye.yCoord;
        double deltaZ = point.zCoord - eye.zCoord;
        double horizontalDistanceSquared = deltaX * deltaX + deltaZ * deltaZ;
        float targetYaw = horizontalDistanceSquared < 1.0E-12D
                ? baseYaw
                : (float) Math.toDegrees(Math.atan2(deltaZ, deltaX)) - 90.0F;
        float targetPitch = (float) -Math.toDegrees(Math.atan2(deltaY, Math.sqrt(horizontalDistanceSquared)));
        return new float[]{
                baseYaw + MathHelper.wrapAngleTo180_float(targetYaw - baseYaw),
                MathHelper.clamp_float(
                        basePitch + MathHelper.wrapAngleTo180_float(targetPitch - basePitch) + 3.0F,
                        -90.0F, 90.0F
                )
        };
    }

    private float[] smoothRotation(float baseYaw, float basePitch, float targetYaw, float targetPitch) {
        int speedValue = this.speed.getValue();
        if (speedValue >= 30) {
            return new float[]{targetYaw, targetPitch};
        }
        float deltaYaw = MathHelper.wrapAngleTo180_float(targetYaw - baseYaw);
        float deltaPitch = targetPitch - basePitch;
        float magnitude = MathHelper.sqrt_float(deltaYaw * deltaYaw + deltaPitch * deltaPitch);
        if (magnitude < 0.001F) {
            return new float[]{targetYaw, targetPitch};
        }

        float speedFactor = speedValue / 30.0F;
        float stepSize = speedFactor * speedFactor * 180.0F;
        float randomRange = 0.6F * this.randomization.getValue() / 100.0F;
        if (randomRange > 0.001F) {
            stepSize *= 1.0F - randomRange * 0.5F + (float) Math.random() * randomRange;
        }
        float proximity = (float) Math.pow(Math.min(1.0F, magnitude / 180.0F), 0.7D);
        float slowdown = this.randomization.getValue() / 100.0F;
        stepSize *= Math.max(0.8F, 1.0F - slowdown * (1.0F - proximity));
        float scale = Math.min(stepSize, magnitude) / magnitude;
        return new float[]{
                baseYaw + deltaYaw * scale,
                MathHelper.clamp_float(basePitch + deltaPitch * scale, -90.0F, 90.0F)
        };
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
