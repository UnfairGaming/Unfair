package cn.unfair.module.modules.combat;

import cn.unfair.Unfair;
import cn.unfair.event.EventTarget;
import cn.unfair.event.types.EventType;
import cn.unfair.events.*;
import cn.unfair.module.Module;
import cn.unfair.module.modules.player.Reach;
import cn.unfair.property.properties.BooleanProperty;
import cn.unfair.property.properties.FloatProperty;
import cn.unfair.property.properties.IntProperty;
import cn.unfair.property.properties.ModeProperty;
import cn.unfair.property.properties.PercentProperty;
import cn.unfair.util.client.KeyBindUtil;
import cn.unfair.util.client.TeamUtil;
import cn.unfair.util.client.TimerUtil;
import cn.unfair.util.player.ItemUtil;
import cn.unfair.util.player.PlayerUtil;
import cn.unfair.util.rotation.AimNoise;
import cn.unfair.util.rotation.RayCastUtil;
import cn.unfair.util.rotation.RotationUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityOtherPlayerMP;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MathHelper;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public class AimAssist extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();

    private static final int MODE_LOCK_ON = 0;
    private static final int MODE_NORMAL = 1;

    public final ModeProperty mode = new ModeProperty("Mode", MODE_LOCK_ON, new String[]{"Lock-on", "Normal"});
    public final BooleanProperty lockOnVertical = new BooleanProperty("LockOnVertical", false, () -> this.mode.getValue() == MODE_LOCK_ON);
    public final PercentProperty predict = new PercentProperty("Predict", 0, () -> this.mode.getValue() == MODE_LOCK_ON);
    public final PercentProperty randomization = new PercentProperty("Randomization", 50, () -> this.mode.getValue() == MODE_LOCK_ON);
    public final PercentProperty horizontalHitbox = new PercentProperty("HorizontalHitbox", 0, () -> this.mode.getValue() == MODE_LOCK_ON);
    public final PercentProperty verticalHitbox = new PercentProperty("VerticalHitbox", 100, () -> this.mode.getValue() == MODE_LOCK_ON);
    public final ModeProperty modeTarget = new ModeProperty("ModeTarget", 0, new String[]{"Single", "Switch"}, () -> this.mode.getValue() == MODE_LOCK_ON);
    public final ModeProperty sortBy = new ModeProperty("SortBy", 0, new String[]{"Distance", "Health", "Hurt time", "Aim angle"}, () -> this.mode.getValue() == MODE_LOCK_ON && this.modeTarget.getValue() == 1);
    public final BooleanProperty allowExceedFov = new BooleanProperty("AllowExceedFov", true, () -> this.mode.getValue() == MODE_LOCK_ON && this.modeTarget.getValue() == 0);
    public final FloatProperty rangeMin = new FloatProperty("RangeMin", 1.0F, 0.0F, 10.0F, () -> this.mode.getValue() == MODE_LOCK_ON);
    public final FloatProperty rangeMax = new FloatProperty("RangeMax", 4.0F, 0.0F, 10.0F, () -> this.mode.getValue() == MODE_LOCK_ON);
    public final FloatProperty fovMin = new FloatProperty("FovMin", 4.0F, 0.0F, 180.0F, () -> this.mode.getValue() == MODE_LOCK_ON);
    public final FloatProperty fovMax = new FloatProperty("FovMax", 60.0F, 0.0F, 180.0F, () -> this.mode.getValue() == MODE_LOCK_ON);
    public final BooleanProperty requireSprint = new BooleanProperty("RequireSprint", false, () -> this.mode.getValue() == MODE_LOCK_ON);
    public final BooleanProperty requireMousePressed = new BooleanProperty("RequireMousePressed", false, () -> this.mode.getValue() == MODE_LOCK_ON);
    public final BooleanProperty requireMouseMoved = new BooleanProperty("RequireMouseMoved", false, () -> this.mode.getValue() == MODE_LOCK_ON);
    public final BooleanProperty disableOnBlockBreak = new BooleanProperty("DisableOnBlockBreak", false, () -> this.mode.getValue() == MODE_LOCK_ON);
    public final BooleanProperty requireWeapon = new BooleanProperty("RequireWeapon", false, () -> this.mode.getValue() == MODE_LOCK_ON);
    public final BooleanProperty ignoreInvis = new BooleanProperty("IgnoreInvis", true, () -> this.mode.getValue() == MODE_LOCK_ON);
    public final BooleanProperty botCheck = new BooleanProperty("BotCheck", true);
    public final BooleanProperty requireLineOfSight = new BooleanProperty("RequireLineOfSight", true, () -> this.mode.getValue() == MODE_LOCK_ON);
    public final FloatProperty normalHorizontalSpeed = new FloatProperty("NormalHorizontalSpeed", 3.0F, 0.0F, 10.0F, () -> this.mode.getValue() == MODE_NORMAL);
    public final FloatProperty normalVerticalSpeed = new FloatProperty("NormalVerticalSpeed", 0.0F, 0.0F, 10.0F, () -> this.mode.getValue() == MODE_NORMAL);
    public final PercentProperty normalSmoothing = new PercentProperty("NormalSmoothing", 50, () -> this.mode.getValue() == MODE_NORMAL);
    public final PercentProperty normalMultipointHorizontal = new PercentProperty("NormalMultipointHorizontal", 0, () -> this.mode.getValue() == MODE_NORMAL);
    public final PercentProperty normalMultipointVertical = new PercentProperty("NormalMultipointVertical", 0, () -> this.mode.getValue() == MODE_NORMAL);
    public final FloatProperty normalRange = new FloatProperty("NormalRange", 4.5F, 3.0F, 8.0F, () -> this.mode.getValue() == MODE_NORMAL);
    public final IntProperty normalFov = new IntProperty("NormalFov", 90, 30, 360, () -> this.mode.getValue() == MODE_NORMAL);
    public final BooleanProperty normalWeaponsOnly = new BooleanProperty("NormalWeaponsOnly", true, () -> this.mode.getValue() == MODE_NORMAL);
    public final BooleanProperty normalAllowTools = new BooleanProperty("NormalAllowTools", false, () -> this.mode.getValue() == MODE_NORMAL && this.normalWeaponsOnly.getValue());
    public final BooleanProperty normalTeams = new BooleanProperty("NormalTeams", true, () -> this.mode.getValue() == MODE_NORMAL);

    private final TimerUtil normalTimer = new TimerUtil();
    private EntityPlayer target;
    private boolean targetActive;
    private Vec3 previousAim = new Vec3(0.0, 0.0, 0.0);
    private Vec3 currentAim = this.previousAim;
    private long tieCounter;
    private double previousDisplaySeconds;
    private float displayYaw = Float.NaN;
    private float displayPitch = Float.NaN;
    private float manualYaw;
    private float manualPitch;
    private float pendingYaw;
    private float pendingPitch;
    private int deltaAge;
    private float quantum;

    public AimAssist() {
        super("AimAssist", false);
    }

    public EntityPlayer getCurrentTarget() {
        return this.target;
    }

    @Override
    public String[] getSuffix() {
        return new String[]{this.mode.getModeString()};
    }

    @Override
    public void verifyValue(String name) {
        if (this.rangeMin.getName().equals(name)) {
            if (this.rangeMin.getValue() > this.rangeMax.getValue()) {
                this.rangeMax.setValue(this.rangeMin.getValue());
            }
        } else if (this.rangeMax.getName().equals(name)) {
            if (this.rangeMin.getValue() > this.rangeMax.getValue()) {
                this.rangeMin.setValue(this.rangeMax.getValue());
            }
        } else if (this.fovMin.getName().equals(name)) {
            if (this.fovMin.getValue() > this.fovMax.getValue()) {
                this.fovMax.setValue(this.fovMin.getValue());
            }
        } else if (this.fovMax.getName().equals(name)) {
            if (this.fovMin.getValue() > this.fovMax.getValue()) {
                this.fovMin.setValue(this.fovMax.getValue());
            }
        }
    }

    @Override
    public void onDisabled() {
        this.clearTarget();
        this.resetRotationState();
    }

    @EventTarget
    public void onLoadWorld(LoadWorldEvent event) {
        this.clearTarget();
        this.previousDisplaySeconds = 0.0;
        this.resetRotationState();
    }

    private boolean focused() {
        return mc.thePlayer != null && mc.theWorld != null && mc.inGameHasFocus && mc.currentScreen == null;
    }

    private void clearTarget() {
        this.target = null;
        this.targetActive = false;
    }

    private void clearManual() {
        this.manualYaw = 0.0F;
        this.manualPitch = 0.0F;
    }

    private void resetRotationState() {
        this.clearManual();
        this.displayYaw = Float.NaN;
        this.displayPitch = Float.NaN;
        this.pendingYaw = 0.0F;
        this.pendingPitch = 0.0F;
        this.deltaAge = 0;
        this.quantum = 0.0F;
    }

    @EventTarget
    public void onRender3D(Render3DEvent event) {
        if (!this.isEnabled() || mc.thePlayer == null || mc.theWorld == null) {
            return;
        }
        if (this.mode.getValue() == MODE_NORMAL) {
            return;
        }
        this.beforeDisplay();
        double now = (double) System.nanoTime() / 1.0E9;
        if (this.previousDisplaySeconds == 0.0) {
            this.previousDisplaySeconds = now;
        }
        double elapsed = now - this.previousDisplaySeconds;
        if (!(0.002 > elapsed)) {
            this.rotate(event.partialTicks());
            this.applyPending(false);
            this.previousDisplaySeconds = now;
            this.afterDisplay();
        }
    }

    private void rotate(float partialTicks) {
        if (!this.focused() || !this.targetActive) {
            return;
        }
        float currentYaw = mc.thePlayer.rotationYaw + this.pendingYaw;
        float currentPitch = mc.thePlayer.rotationPitch + this.pendingPitch;
        double fraction = (double) partialTicks;
        Vec3 previous = new Vec3(mc.thePlayer.prevPosX, mc.thePlayer.prevPosY, mc.thePlayer.prevPosZ);
        Vec3 current = new Vec3(mc.thePlayer.posX, mc.thePlayer.posY, mc.thePlayer.posZ);
        Vec3 eye = interpolate(previous, current, fraction).addVector(0.0, 1.62, 0.0);
        Vec3 aim = interpolate(this.previousAim, this.currentAim, fraction);
        float[] desired = RotationUtil.getRotations(
                aim.xCoord, aim.yCoord, aim.zCoord,
                eye.xCoord, eye.yCoord, eye.zCoord
        );
        float yaw = MathHelper.wrapAngleTo180_float(desired[0] - currentYaw);
        float pitch = desired[1] - currentPitch;
        this.applyStep(yaw, pitch);
    }

    private void applyStep(float yaw, float pitch) {
        float[] step = this.aimStep(yaw, pitch, this.manualYaw, this.manualPitch);
        if (this.requireMouseMoved.getValue()) {
            this.clearManual();
        }
        this.pendingYaw += step[0];
        this.pendingPitch += step[1];
        this.deltaAge = 0;
    }

    private void applyPending(boolean alsoArm) {
        float[] step = this.consumePending();
        if (step[0] == 0.0F && step[1] == 0.0F) {
            return;
        }
        float yaw = step[0] + mc.thePlayer.rotationYaw;
        float pitch = MathHelper.clamp_float(step[1] + mc.thePlayer.rotationPitch, -90.0F, 90.0F);
        mc.thePlayer.rotationYaw = yaw;
        mc.thePlayer.rotationPitch = pitch;
        mc.thePlayer.prevRotationYaw = yaw;
        mc.thePlayer.prevRotationPitch = pitch;
        if (alsoArm) {
            mc.thePlayer.prevRenderArmYaw = yaw;
            mc.thePlayer.prevRenderArmPitch = pitch;
            mc.thePlayer.renderArmYaw = yaw;
            mc.thePlayer.renderArmPitch = pitch;
        }
    }

    private float[] consumePending() {
        if (this.quantum == 0.0F || (this.pendingYaw == 0.0F && this.pendingPitch == 0.0F)) {
            return new float[]{0.0F, 0.0F};
        }
        float yawStep = this.quantize(this.pendingYaw);
        float pitchStep = this.quantize(this.pendingPitch);
        this.pendingYaw -= yawStep;
        this.pendingPitch -= pitchStep;
        if (yawStep == 0.0F && pitchStep == 0.0F) {
            return new float[]{0.0F, 0.0F};
        }
        return new float[]{yawStep, pitchStep};
    }

    private float quantize(float value) {
        if (this.quantum != 0.0F && (-this.quantum >= value || value >= this.quantum)) {
            return (float) (int) (value / this.quantum) * this.quantum;
        }
        return 0.0F;
    }

    private void beforeDisplay() {
        if (Float.isNaN(this.displayYaw)) {
            this.displayYaw = mc.thePlayer.rotationYaw;
            this.displayPitch = mc.thePlayer.rotationPitch;
            return;
        }
        this.manualYaw += mc.thePlayer.rotationYaw - this.displayYaw;
        this.manualPitch += mc.thePlayer.rotationPitch - this.displayPitch;
    }

    private void afterDisplay() {
        this.displayYaw = mc.thePlayer.rotationYaw;
        this.displayPitch = mc.thePlayer.rotationPitch;
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (!this.isEnabled()) {
            return;
        }
        if (event.type() == EventType.PRE) {
            if (mc.thePlayer != null && mc.theWorld != null) {
                this.quantum = RotationUtil.getRotationGcd();
                int previousAge = this.deltaAge++;
                if (previousAge > 0) {
                    this.pendingYaw = 0.0F;
                    this.pendingPitch = 0.0F;
                }
            }
        } else if (event.type() == EventType.POST) {
            if (mc.thePlayer == null || mc.theWorld == null) {
                this.clearTarget();
                return;
            }
            if (this.mode.getValue() == MODE_NORMAL) {
                this.clearTarget();
                this.normalAim();
                return;
            }
            this.updateTarget();
            this.applyPending(true);
        }
    }

    public float[] aimStep(float yaw, float pitch, float manualYaw, float manualPitch) {
        float deadZone = this.fovMin.getValue();
        yaw = yaw > 0.0F ? Math.max(yaw - deadZone, 0.0F) : Math.min(yaw + deadZone, 0.0F);
        pitch = pitch > 0.0F ? Math.max(pitch - deadZone, 0.0F) : Math.min(deadZone + pitch, 0.0F);
        if (!this.lockOnVertical.getValue()) {
            pitch = 0.0F;
        }
        if (this.requireMouseMoved.getValue()) {
            float yawMagnitude = Math.abs(yaw);
            float pitchMagnitude = Math.abs(pitch);
            float manualYawMagnitude = Math.abs(manualYaw);
            float manualPitchMagnitude = Math.abs(manualPitch);
            boolean adjustYaw = manualYawMagnitude < yawMagnitude && yaw != 0.0F;
            float ratio = manualYawMagnitude / yawMagnitude;
            float scaledYaw = ratio * yaw;
            if (manualPitchMagnitude < pitchMagnitude && pitch != 0.0F) {
                float pitchRatio = manualPitchMagnitude / pitchMagnitude;
                pitch *= pitchRatio;
            }
            if (adjustYaw) {
                yaw = scaledYaw;
            }
        }
        return new float[]{yaw, pitch};
    }

    private void updateTarget() {
        this.clearManual();
        if (!this.focused()) {
            this.clearTarget();
        } else if (this.requireSprint.getValue() && !mc.thePlayer.isSprinting()) {
            this.targetActive = false;
        } else {
            boolean attack = KeyBindUtil.isKeyDown(mc.gameSettings.keyBindAttack.getKeyCode());
            if (this.requireMousePressed.getValue()) {
                if (!attack) {
                    this.clearTarget();
                    return;
                }
            }

            MovingObjectPosition hit = mc.objectMouseOver;
            if (this.disableOnBlockBreak.getValue() && attack && hit != null && hit.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK) {
                this.targetActive = false;
            } else if (this.requireWeapon.getValue() && !this.isHoldingWeapon()) {
                this.clearTarget();
            } else {
                Vec3 eye = new Vec3(mc.thePlayer.posX, mc.thePlayer.posY + 1.62, mc.thePlayer.posZ);
                float viewYaw = mc.thePlayer.rotationYaw;
                Score best = new Score((double) this.rangeMax.getValue(), this.fovMax.getValue());
                Candidate selected = new Candidate();
                if (this.modeTarget.getValue() == 0 && this.target != null) {
                    if (this.consider(this.target, eye, viewYaw, true, best, selected)) {
                        this.previousAim = this.currentAim;
                        this.currentAim = selected.point;
                        return;
                    }

                    this.clearTarget();
                }

                EntityPlayer chosen = null;

                for (int i = 0; i < mc.theWorld.playerEntities.size(); i++) {
                    EntityPlayer candidate = (EntityPlayer) mc.theWorld.playerEntities.get(i);
                    if (candidate != mc.thePlayer) {
                        Candidate result = new Candidate();
                        if (this.consider(candidate, eye, viewYaw, false, best, result)) {
                            chosen = candidate;
                            selected = result;
                        }
                    }
                }

                if (chosen == null) {
                    this.clearTarget();
                } else {
                    this.previousAim = this.target == chosen ? this.currentAim : selected.point;
                    this.currentAim = selected.point;
                    this.targetActive = selected.enabled;
                    this.target = chosen;
                }
            }
        }
    }

    private boolean consider(
            EntityPlayer candidate, Vec3 eye, float viewYaw, boolean locked, Score best, Candidate result
    ) {
        if (candidate.isDead || candidate.isPlayerSleeping()) {
            return false;
        } else if (this.ignoreInvis.getValue() && candidate.isInvisibleToPlayer(mc.thePlayer)) {
            return false;
        } else if (TeamUtil.isFriend(candidate) || TeamUtil.isSameTeam(candidate)
                || (this.botCheck.getValue() && TeamUtil.isBot(candidate))) {
            return false;
        } else {
            Vec3 position = new Vec3(candidate.posX, candidate.posY, candidate.posZ);
            float prediction = this.predict.getValue() / 100.0F;
            if (prediction > 0.0F && candidate instanceof EntityOtherPlayerMP) {
                position = interpolate(position, serverPosition((EntityOtherPlayerMP) candidate), (double) prediction);
            }

            AxisAlignedBB box = candidate.getEntityBoundingBox().expand(0.1, 0.1, 0.1);
            Vec3 nearest = new Vec3(
                    MathHelper.clamp_double(eye.xCoord, box.minX, box.maxX),
                    MathHelper.clamp_double(eye.yCoord, box.minY, box.maxY),
                    MathHelper.clamp_double(eye.zCoord, box.minZ, box.maxZ)
            );
            double distance = eye.distanceTo(nearest);
            boolean enabled = !((double) this.rangeMin.getValue() > distance);
            if (distance > best.distance) {
                return false;
            } else {
                Vec3 point = aimPoint(
                        eye, position, nearest, distance,
                        this.horizontalHitbox.getValue() / 100.0F, this.verticalHitbox.getValue() / 100.0F,
                        this.randomization.getValue() / 100.0F
                );
                float angle = Math.abs(MathHelper.wrapAngleTo180_float(RotationUtil.getRotations(
                        point.xCoord, point.yCoord, point.zCoord,
                        eye.xCoord, eye.yCoord, eye.zCoord
                )[0] - viewYaw));
                if (angle > best.angle) {
                    if (!locked) {
                        return false;
                    }

                    enabled = this.allowExceedFov.getValue();
                }

                float health = candidate.getHealth();
                if (Float.isNaN(health)) {
                    health = 20.0F;
                }

                if (!(health > best.health) && candidate.hurtTime <= best.hurtTime) {
                    if (this.requireLineOfSight.getValue() && mc.theWorld.rayTraceBlocks(eye, point, false, true, true) != null) {
                        return false;
                    } else {
                        if (this.modeTarget.getValue() != 0) {
                            switch (this.sortBy.getValue()) {
                                case 0:
                                    best.distance = distance;
                                    break;
                                case 1:
                                    best.health = health;
                                    break;
                                case 2:
                                    int hash = tieHash(candidate.getEntityId(), this.tieCounter++);
                                    if (candidate.hurtTime == best.hurtTime && Integer.compareUnsigned(hash, best.tieBreak) < 0) {
                                        return false;
                                    }

                                    best.hurtTime = candidate.hurtTime;
                                    best.tieBreak = hash;
                                    break;
                                case 3:
                                    best.angle = angle;
                                    break;
                                default:
                                    throw new IllegalStateException("target sort");
                            }
                        } else {
                            best.angle = angle;
                        }

                        result.point = point;
                        result.enabled = enabled;
                        if (locked) {
                            this.targetActive = enabled;
                        }

                        return true;
                    }
                } else {
                    return false;
                }
            }
        }
    }

    private boolean isHoldingWeapon() {
        return ItemUtil.hasRawUnbreakingEnchant();
    }

    public static Vec3 aimPoint(Vec3 localEye, Vec3 predicted, Vec3 nearest, double distance, float horizontal, float vertical, float randomization) {
        Vec3 targetEye = new Vec3(predicted.xCoord, predicted.yCoord + 1.62, predicted.zCoord);
        double x = targetEye.xCoord;
        double z = targetEye.zCoord;
        if (horizontal > 0.0F) {
            float fraction = distance < 3.5 ? (float) ((double) horizontal * (distance / 3.5)) : horizontal;
            Vec3 point = interpolate(targetEye, nearest, (double) fraction);
            x = point.xCoord;
            z = point.zCoord;
        }

        double y = targetEye.yCoord + (nearest.yCoord - targetEye.yCoord) * (double) vertical;
        double sum = localEye.xCoord + localEye.yCoord;
        sum += localEye.zCoord;
        sum += x;
        sum += y;
        sum += z;
        float input = (float) (sum * 0.1);
        float noiseX = AimNoise.sample(input);
        float noiseY = AimNoise.sample(10000.0F + input);
        double halfX = (double) noiseX * 0.5;
        double halfY = (double) noiseY * 0.5;
        float noiseZ = AimNoise.sample(20000.0F + input);
        double halfZ = (double) noiseZ * 0.5;
        float randomScale = randomization;
        if (distance < 3.5) {
            randomScale = (float) ((double) randomization * (distance / 3.5 * 0.9 + 0.1));
        }

        double scale = (double) randomScale;
        return new Vec3(x + scale * halfX, y + scale * halfY, z + scale * halfZ);
    }

    private boolean isValidNormalTarget(EntityPlayer entityPlayer) {
        if (entityPlayer != mc.thePlayer && entityPlayer != mc.thePlayer.ridingEntity) {
            if (entityPlayer == mc.getRenderViewEntity() || entityPlayer == mc.getRenderViewEntity().ridingEntity) {
                return false;
            } else if (entityPlayer.deathTime > 0) {
                return false;
            } else if (RotationUtil.distanceToEntity(entityPlayer) > (double) this.normalRange.getValue()) {
                return false;
            } else if (RotationUtil.angleToEntity(entityPlayer) > (float) this.normalFov.getValue()) {
                return false;
            } else if (RayCastUtil.rayTrace(entityPlayer) != null) {
                return false;
            } else if (TeamUtil.isFriend(entityPlayer)) {
                return false;
            } else {
                return (!this.normalTeams.getValue() || !TeamUtil.isSameTeam(entityPlayer))
                        && (!this.botCheck.getValue() || !TeamUtil.isBot(entityPlayer));
            }
        } else {
            return false;
        }
    }

    private boolean isInNormalReach(EntityPlayer entityPlayer) {
        Reach reach = (Reach) Unfair.moduleManager.getModule(Reach.class);
        double distance = reach != null && reach.isEnabled() ? (double) reach.range.getValue() : 3.0;
        return RotationUtil.distanceToEntity(entityPlayer) <= distance;
    }

    private boolean isLookingAtBlock() {
        return mc.objectMouseOver != null && mc.objectMouseOver.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK;
    }

    private void normalAim() {
        if (mc.currentScreen != null) {
            return;
        }
        if (this.normalWeaponsOnly.getValue()
                && !ItemUtil.hasRawUnbreakingEnchant()
                && !(this.normalAllowTools.getValue() && ItemUtil.isHoldingTool())) {
            return;
        }
        boolean attacking = PlayerUtil.isAttacking();
        if (attacking && this.isLookingAtBlock()) {
            return;
        }
        if (!attacking && this.normalTimer.hasTimeElapsed(350L)) {
            return;
        }
        List<EntityPlayer> inRange = mc.theWorld
                .loadedEntityList
                .stream()
                .filter(entity -> entity instanceof EntityPlayer)
                .map(entity -> (EntityPlayer) entity)
                .filter(this::isValidNormalTarget)
                .sorted(Comparator.comparingDouble(RotationUtil::distanceToEntity))
                .collect(Collectors.toList());
        if (inRange.isEmpty()) {
            return;
        }
        if (inRange.stream().anyMatch(this::isInNormalReach)) {
            inRange.removeIf(entityPlayer -> !this.isInNormalReach(entityPlayer));
        }
        EntityPlayer player = inRange.get(0);
        if (RotationUtil.distanceToEntity(player) <= 0.0) {
            return;
        }
        AxisAlignedBB axisAlignedBB = player.getEntityBoundingBox();
        double collisionBorderSize = player.getCollisionBorderSize();
        Vec3 eye = mc.thePlayer.getPositionEyes(1.0F);
        Vec3 point = RotationUtil.getAimPoint(
                axisAlignedBB.expand(collisionBorderSize, collisionBorderSize, collisionBorderSize),
                eye,
                (axisAlignedBB.minY + axisAlignedBB.maxY) / 2.0,
                this.normalMultipointHorizontal.getValue() / 100.0,
                this.normalMultipointVertical.getValue() / 100.0
        );
        float[] rotation = RotationUtil.getRotationsToPoint(
                point,
                eye,
                mc.thePlayer.rotationYaw,
                mc.thePlayer.rotationPitch,
                180.0F,
                this.normalSmoothing.getValue() / 100.0F
        );
        float yaw = Math.min(Math.abs(this.normalHorizontalSpeed.getValue()), 10.0F);
        float pitch = Math.min(Math.abs(this.normalVerticalSpeed.getValue()), 10.0F);
        Unfair.rotationManager.setRotation(
                mc.thePlayer.rotationYaw + (rotation[0] - mc.thePlayer.rotationYaw) * 0.1F * yaw,
                mc.thePlayer.rotationPitch + (rotation[1] - mc.thePlayer.rotationPitch) * 0.1F * pitch,
                0,
                false
        );
    }

    @EventTarget
    public void onPress(KeyEvent event) {
        AutoClicker autoClicker = (AutoClicker) Unfair.moduleManager.getModule(AutoClicker.class);
        if (event.getKey() == mc.gameSettings.keyBindAttack.getKeyCode()
                && (autoClicker == null || !autoClicker.isEnabled())) {
            this.normalTimer.reset();
        }
    }

    private static Vec3 serverPosition(EntityOtherPlayerMP player) {
        return new Vec3(player.serverPosX / 32.0, player.serverPosY / 32.0, player.serverPosZ / 32.0);
    }

    private static int tieHash(int entityId, long counter) {
        int x = entityId + (int) counter;
        x ^= x >>> 16;
        x *= 73244475;
        x ^= x >>> 16;
        x *= 73244475;
        return x ^ x >>> 16;
    }

    private static Vec3 interpolate(Vec3 from, Vec3 to, double fraction) {
        return new Vec3(
                fraction * (to.xCoord - from.xCoord) + from.xCoord,
                fraction * (to.yCoord - from.yCoord) + from.yCoord,
                (to.zCoord - from.zCoord) * fraction + from.zCoord
        );
    }

    private static final class Candidate {
        Vec3 point;
        boolean enabled;
    }

    public static final class Score {
        public double distance;
        public float health = Float.MAX_VALUE;
        public float angle;
        public int hurtTime = Integer.MAX_VALUE;
        public int tieBreak;

        public Score(double range, float fov) {
            this.distance = range;
            this.angle = fov;
        }
    }
}
