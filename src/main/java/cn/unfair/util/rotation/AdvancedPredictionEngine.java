package cn.unfair.util.rotation;

import cn.unfair.util.RandomUtil;
import cn.unfair.util.TimerUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MathHelper;
import net.minecraft.util.Vec3;

import java.util.ArrayList;

public final class AdvancedPredictionEngine {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private static float currentTargetOffset;
    private static AxisAlignedBB lastTrackedAABB;
    private static int ticksExisted;
    public static int currentReactionTime;
    public static final ArrayList<Double> previousTargetMotions = new ArrayList<>();
    public static boolean lastReset;
    private static final TimerUtil updateTimer = new TimerUtil();
    private static Vec3 lastTrackedMoveDelta = new Vec3(0.0D, 0.0D, 0.0D);

    private AdvancedPredictionEngine() {
    }

    public static AxisAlignedBB simulatePredictions(EntityLivingBase currentTarget, float attackRange, boolean simulateReactionTime, float thresholdForDirectionConfidence, float thresholdToApplyReactionTime, float minReactionTime, float maxReactionTime, float extraPrediction) {
        lastReset = false;
        double dist = AdvancedRotationMath.getDistanceToEntityBox(currentTarget);
        Vec3 prediction;
        AxisAlignedBB hitbox = AdvancedRotationMath.getHitbox(currentTarget);
        boolean flag = false;
        double speed = AdvancedRotationMath.getSpeedPosBased(currentTarget);

        if (dist > attackRange) {
            currentTargetOffset = (float) Math.min(Math.max((dist - attackRange) * 3.0D, 0.0D), 8.0D);
            flag = true;
        }

        if (speed > 0.4D) {
            double extra = Math.random() * RandomUtil.nextFloat(0.9F, 1.1F);
            float target = (float) (-Math.min(Math.max(dist, 0.0D), 8.0D) + (Math.random() < 0.5D ? extra : -extra));
            currentTargetOffset = AdvancedRotationMath.interpolate(currentTargetOffset, target, 0.05F);
            flag = true;
        }

        if (mc.thePlayer.getEntityBoundingBox().intersectsWith(AdvancedRotationMath.getHitbox(currentTarget))) {
            currentTargetOffset = 3.0F;
            flag = true;
        }

        prediction = AdvancedRotationMath.flat(AdvancedRotationMath.multiply(AdvancedRotationMath.getMoveDeltaVector(currentTarget), flag ? currentTargetOffset : 0.0D));

        if (simulateReactionTime) {
            if (lastTrackedAABB == null) {
                lastTrackedAABB = hitbox;
            }

            if (mc.thePlayer.ticksExisted != ticksExisted) {
                previousTargetMotions.add(AdvancedRotationMath.getMoveDeltaVector(currentTarget).lengthVector());

                if (updateTimer.hasTimeElapsed((currentReactionTime + 1L) * 50L)) {
                    lastTrackedAABB = hitbox;
                    lastTrackedMoveDelta = AdvancedRotationMath.getMoveDeltaVector(currentTarget);

                    double averageMotion = 0.0D;
                    for (Double motion : previousTargetMotions) {
                        averageMotion += motion;
                    }
                    averageMotion /= previousTargetMotions.size();
                    double motionPercentage = MathHelper.clamp_double(averageMotion * 2.0D, 0.0D, 1.0D);
                    currentReactionTime = (int) AdvancedRotationMath.interpolate(minReactionTime, maxReactionTime, motionPercentage);
                    updateTimer.reset();
                } else {
                    lastTrackedAABB = lastTrackedAABB.offset(lastTrackedMoveDelta.xCoord, lastTrackedMoveDelta.yCoord, lastTrackedMoveDelta.zCoord);
                    hitbox = lastTrackedAABB;
                }

                while (previousTargetMotions.size() > 20) {
                    previousTargetMotions.remove(0);
                }
            }
        }

        ticksExisted = mc.thePlayer.ticksExisted;
        return hitbox.offset(prediction.xCoord * extraPrediction, prediction.yCoord * extraPrediction, prediction.zCoord * extraPrediction);
    }

    public static void reset() {
        lastTrackedAABB = null;
        currentReactionTime = 0;
        previousTargetMotions.clear();
        lastReset = true;
    }
}
