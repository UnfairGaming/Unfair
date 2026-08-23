package cn.unfair.module.modules.combat;

import cn.unfair.Unfair;
import cn.unfair.event.EventTarget;
import cn.unfair.events.LoadWorldEvent;
import cn.unfair.events.MoveInputEvent;
import cn.unfair.events.PlayerUpdateEvent;
import cn.unfair.events.TimerManipulationEvent;
import cn.unfair.management.RotationState;
import cn.unfair.module.Module;
import cn.unfair.property.properties.BooleanProperty;
import cn.unfair.property.properties.FloatProperty;
import cn.unfair.property.properties.IntProperty;
import cn.unfair.util.client.ChatUtil;
import cn.unfair.util.rotation.RotationUtil;
import cn.unfair.util.client.TeamUtil;
import cn.unfair.util.client.TimerUtil;
import cn.unfair.util.player.SimulatedPlayer;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MathHelper;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class TickBase extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();

    public final IntProperty delay = new IntProperty("Delay", 50, 0, 1000);
    public final FloatProperty tickRange = new FloatProperty("Tick Range", 3.0F, 0.1F, 8.0F);
    public final FloatProperty minRange = new FloatProperty("Min Range", 2.5F, 0.1F, 8.0F);
    public final FloatProperty stopRange = new FloatProperty("Stop Range", 2.5F, 0.1F, 8.0F);
    public final FloatProperty searchRange = new FloatProperty("Search Range", 7.0F, 0.1F, 15.0F);
    public final IntProperty maxTicks = new IntProperty("Max Ticks", 4, 1, 20);
    public final BooleanProperty prioritiseCrits = new BooleanProperty("Prioritise Crits", false);
    public final BooleanProperty chatDebug = new BooleanProperty("Chat Debug", false);

    private final TimerUtil timer = new TimerUtil();
    private final List<PredictProcess> selfPrediction = new ArrayList<>();
    private long shifted;
    private long previousTime;
    private EntityLivingBase target;
    private int ticksToSkip;

    public TickBase() {
        super("TickBase", false);
    }

    private static double getDistanceToEntityBox(Entity entity) {
        return getCustomDistanceToEntityBox(mc.thePlayer.getPositionEyes(1.0F), entity);
    }

    private static double getCustomDistanceToEntityBox(Vec3 eyes, Entity entity) {
        return eyes.distanceTo(RotationUtil.getClosestPointOnBox(eyes, getHitbox(entity)));
    }

    private static double getCustomDistanceToEntityBox(Vec3 from, Vec3 to) {
        double xDist = Math.abs(to.xCoord - from.xCoord);
        double yDist = Math.abs(to.yCoord - from.yCoord);
        double zDist = Math.abs(to.zCoord - from.zCoord);
        return MathHelper.sqrt_double(xDist * xDist + yDist * yDist + zDist * zDist);
    }

    private static double getDistToTargetFromMouseOver(Vec3 eyes, Vec3 look, AxisAlignedBB targetBB) {
        Vec3 end = eyes.addVector(look.xCoord * 64.0D, look.yCoord * 64.0D, look.zCoord * 64.0D);
        Vec3 hitVec = null;
        MovingObjectPosition intercept = targetBB.calculateIntercept(eyes, end);

        if (targetBB.isVecInside(eyes)) {
            hitVec = intercept == null ? eyes : intercept.hitVec;
        } else if (intercept != null) {
            hitVec = intercept.hitVec;
        }

        return hitVec == null ? Double.MAX_VALUE : eyes.distanceTo(hitVec);
    }

    private static AxisAlignedBB getHitbox(Entity entity) {
        float border = entity.getCollisionBorderSize();
        return entity.getEntityBoundingBox().expand(border, border, border);
    }

    private static Vec3 getPositionVector(Entity entity) {
        return new Vec3(entity.posX, entity.posY, entity.posZ);
    }

    private static Vec3 getMoveDeltaVector(Entity entity) {
        return subtract(getPositionVector(entity), new Vec3(entity.prevPosX, entity.prevPosY, entity.prevPosZ));
    }

    private static Vec3 add(Vec3 vec, double x, double y, double z) {
        return new Vec3(vec.xCoord + x, vec.yCoord + y, vec.zCoord + z);
    }

    private static Vec3 add(Vec3 a, Vec3 b) {
        return new Vec3(a.xCoord + b.xCoord, a.yCoord + b.yCoord, a.zCoord + b.zCoord);
    }

    private static Vec3 subtract(Vec3 a, Vec3 b) {
        return new Vec3(a.xCoord - b.xCoord, a.yCoord - b.yCoord, a.zCoord - b.zCoord);
    }

    private static Vec3 multiply(Vec3 vec, double factor) {
        return new Vec3(vec.xCoord * factor, vec.yCoord * factor, vec.zCoord * factor);
    }

    private static AxisAlignedBB offset(AxisAlignedBB box, Vec3 vec) {
        return box.offset(vec.xCoord, vec.yCoord, vec.zCoord);
    }

    @Override
    public void onEnabled() {
        this.shifted = 0L;
        this.previousTime = 0L;
        this.ticksToSkip = 0;
        this.timer.reset();
    }

    @Override
    public void onDisabled() {
        this.target = null;
        this.selfPrediction.clear();
        this.shifted = 0L;
    }

    @Override
    public String[] getSuffix() {
        return new String[]{String.valueOf(this.ticksToSkip)};
    }

    @EventTarget
    public void onUpdate(PlayerUpdateEvent event) {
        if (!this.isEnabled() || mc.thePlayer == null || mc.theWorld == null) {
            return;
        }
        this.target = this.getTarget(this.searchRange.getValue());
    }

    @EventTarget
    public void onWorldChange(LoadWorldEvent event) {
        this.shifted = 0L;
        this.previousTime = 0L;
        this.selfPrediction.clear();
        this.target = null;
    }

    @EventTarget
    public void onTimerManipulation(TimerManipulationEvent event) {
        if (!this.isEnabled() || mc.thePlayer == null || mc.theWorld == null) {
            return;
        }

        if (this.target == null || this.selfPrediction.isEmpty()) {
            this.previousTime = event.getTime();
            event.setTime(event.getTime() - this.shifted);
            this.shifted = 0L;
            return;
        }

        if (this.shouldStart() && this.timer.hasTimeElapsed(this.delay.getValue())) {
            this.shifted += event.getTime() - this.previousTime;
        } else {
            long lastShifted = this.shifted;
            this.shifted = 0L;

            if (lastShifted > 0L && this.chatDebug.getValue()) {
                ChatUtil.sendFormatted("&7reset bal accum");
            }
        }

        if (this.shifted >= this.ticksToSkip * 50L) {
            this.shifted = 0L;
            this.timer.reset();
        }

        this.previousTime = event.getTime();
        event.setTime(event.getTime() - this.shifted);
    }

    @EventTarget
    public void onMove(MoveInputEvent event) {
        if (!this.isEnabled() || mc.thePlayer == null || mc.theWorld == null) {
            return;
        }

        this.selfPrediction.clear();

        SimulatedPlayer simulatedSelf = SimulatedPlayer.fromClientPlayer(mc.thePlayer.movementInput);
        simulatedSelf.rotationYaw = RotationState.isActived() ? RotationState.getSmoothedYaw() : mc.thePlayer.rotationYaw;

        for (int i = 0; i < this.maxTicks.getValue(); i++) {
            simulatedSelf.tick();

            PredictProcess predictProcess = new PredictProcess(
                    simulatedSelf.getPos(),
                    simulatedSelf.fallDistance,
                    simulatedSelf.onGround,
                    simulatedSelf.isCollidedHorizontally
            );
            predictProcess.tick = i;
            this.selfPrediction.add(predictProcess);
        }
    }

    private Vec3 getPredictedTargetPos(int tick) {
        return add(getPositionVector(this.target), multiply(getMoveDeltaVector(this.target), tick));
    }

    private boolean shouldStart() {
        if (mc.objectMouseOver != null && mc.objectMouseOver.entityHit == this.target) {
            return false;
        }

        boolean picked = false;
        double bestScore = Double.MAX_VALUE;
        int bestTick = -1;
        double bestDist = 27.6D;

        for (PredictProcess predictProcess : this.selfPrediction) {
            if (!this.criteria(predictProcess.tick)) {
                continue;
            }
            picked = true;

            AxisAlignedBB entityBoundingBox = offset(getHitbox(this.target), subtract(this.getPredictedTargetPos(this.ticksToSkip), getPositionVector(this.target)));
            double predictedSelfDistance = getDistToTargetFromMouseOver(
                    add(this.selfPrediction.get(predictProcess.tick).position, 0.0D, mc.thePlayer.getEyeHeight(), 0.0D),
                    mc.thePlayer.getLook(1.0F),
                    entityBoundingBox
            );
            double score = Math.abs(predictedSelfDistance - this.tickRange.getValue());

            if (this.prioritiseCrits.getValue() && predictProcess.fallDistance > 0.0F) {
                score -= 1.0D;
            }

            if (score < bestScore) {
                bestScore = score;
                bestTick = predictProcess.tick;
                bestDist = predictedSelfDistance;
            }
        }

        if (bestDist != 27.6D && this.chatDebug.getValue()) {
            ChatUtil.sendFormatted("&7tick dist is " + bestDist);
        }

        this.ticksToSkip = bestTick + 1;

        if (!picked) {
            this.ticksToSkip = this.maxTicks.getValue();
        }

        return this.criteria(this.ticksToSkip - 1);
    }

    private boolean criteria(int tick) {
        if (tick < 0 || tick >= this.selfPrediction.size() || this.target == null) {
            return false;
        }

        AxisAlignedBB entityBoundingBox = offset(getHitbox(this.target), subtract(this.getPredictedTargetPos(tick + 1), getPositionVector(this.target)));
        Vec3 predictedSelfEyes = add(this.selfPrediction.get(tick).position, 0.0D, mc.thePlayer.getEyeHeight(), 0.0D);
        Vec3 predictedTargetEyes = add(this.getPredictedTargetPos(tick + 1), 0.0D, this.target.getEyeHeight(), 0.0D);

        double predictedSelfDistanceMouseOver = getDistToTargetFromMouseOver(predictedSelfEyes, mc.thePlayer.getLook(1.0F), entityBoundingBox);
        double predictedSelfDistanceBHV = getCustomDistanceToEntityBox(predictedSelfEyes, predictedTargetEyes);

        double predictedSelfDistance = predictedSelfDistanceMouseOver > 8.0D ? predictedSelfDistanceBHV : predictedSelfDistanceMouseOver;
        double predictedTargetDistance = getCustomDistanceToEntityBox(predictedTargetEyes, mc.thePlayer);

        return this.target.hurtTime - tick <= 1
                && predictedSelfDistance < predictedTargetDistance
                && predictedSelfDistance <= this.tickRange.getValue()
                && predictedSelfDistance > this.minRange.getValue()
                && predictedSelfDistance <= this.searchRange.getValue()
                && getDistanceToEntityBox(this.target) >= this.stopRange.getValue()
                && mc.thePlayer.canEntityBeSeen(this.target)
                && !this.selfPrediction.get(tick).isCollidedHorizontally
                && !mc.thePlayer.isCollidedHorizontally;
    }

    private EntityLivingBase getTarget(double distance) {
        KillAura killAura = (KillAura) Unfair.moduleManager.modules.get(KillAura.class);
        if (killAura != null && killAura.isEnabled() && killAura.getTarget() != null) {
            return killAura.getTarget();
        }

        return mc.theWorld.loadedEntityList.stream()
                .filter(EntityLivingBase.class::isInstance)
                .map(EntityLivingBase.class::cast)
                .filter(this::isValidTarget)
                .filter(entity -> mc.thePlayer.getDistanceSqToEntity(entity) <= distance * distance)
                .filter(entity -> getDistanceToEntityBox(entity) <= distance)
                .min(Comparator.comparingDouble(TickBase::getDistanceToEntityBox))
                .orElse(null);
    }

    private boolean isValidTarget(EntityLivingBase entity) {
        return entity != mc.thePlayer
                && !entity.isDead
                && entity.getHealth() > 0.0F
                && TeamUtil.isEntityLoaded(entity)
                && entity instanceof EntityPlayer
                && !Unfair.friendManager.isFriend(entity.getName());
    }

    private static class PredictProcess {
        private final Vec3 position;
        private final float fallDistance;
        private final boolean isCollidedHorizontally;
        private int tick;

        private PredictProcess(Vec3 position, float fallDistance, boolean onGround, boolean isCollidedHorizontally) {
            this.position = position;
            this.fallDistance = fallDistance;
            this.isCollidedHorizontally = isCollidedHorizontally;
        }
    }
}
