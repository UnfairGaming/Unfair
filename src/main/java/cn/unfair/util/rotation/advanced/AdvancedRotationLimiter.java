package cn.unfair.util.rotation.advanced;

import cn.unfair.module.modules.combat.KillAura;
import cn.unfair.util.player.MoveUtil;
import cn.unfair.util.client.RandomUtil;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.MathHelper;

import java.util.ArrayList;
import java.util.List;

public class AdvancedRotationLimiter {
    private final List<Float> yawDiffList = new ArrayList<>();
    private final List<Double> speedList = new ArrayList<>();

    private float avgYawDiff;
    private float prevYaw;
    private float prevPitch;
    private float yawDiff;
    private boolean shouldLimit;
    private double averageSwitching;
    private double avgSpeed;

    public void reset(float yaw, float pitch) {
        yawDiffList.clear();
        speedList.clear();
        avgYawDiff = 0.0F;
        prevYaw = yaw;
        prevPitch = pitch;
        yawDiff = 0.0F;
        shouldLimit = false;
        averageSwitching = 0.0D;
        avgSpeed = 0.0D;
    }

    public float[] limit(float currentYaw, float currentPitch, float targetYaw, float targetPitch,
                         int maxDeltaHistorySize, String averageMode, float maxAverageYawDelta,
                         float minYawMultiplier, float maxYawMultiplier) {
        updateHistory(currentYaw, currentPitch, maxDeltaHistorySize, averageMode, maxAverageYawDelta);

        double deltaYaw = AdvancedRotationMath.getAngleDifference(targetYaw, currentYaw);
        double deltaPitch = AdvancedRotationMath.getAngleDifference(targetPitch, currentPitch);
        if (!"NONE".equalsIgnoreCase(averageMode)) {
            deltaYaw = reduceYaw(deltaYaw, averageMode, maxDeltaHistorySize, maxAverageYawDelta, minYawMultiplier, maxYawMultiplier);
        }

        return new float[]{
                currentYaw + (float) deltaYaw,
                MathHelper.clamp_float(currentPitch + (float) deltaPitch, -90.0F, 90.0F)
        };
    }

    private void updateHistory(float currentYaw, float currentPitch, int maxDeltaHistorySize, String averageMode, float maxAverageYawDelta) {
        yawDiff = prevYaw - currentYaw;

        if (maxDeltaHistorySize == 0) {
            avgYawDiff = 0.0F;
            shouldLimit = false;
            averageSwitching = 0.0D;
            avgSpeed = 0.0D;
        } else {
            yawDiffList.add(yawDiff);
            speedList.add(MoveUtil.getSpeed());

            int deltaSwitchTarget = 0;
            float yawDiffListSum = 0.0F;
            for (float diffYaw : yawDiffList) {
                yawDiffListSum += diffYaw;
                if (Math.abs(diffYaw) > 30.0F) {
                    deltaSwitchTarget++;
                }
            }
            avgYawDiff = yawDiffListSum / yawDiffList.size();
            averageSwitching = (double) deltaSwitchTarget / yawDiffList.size();

            double speedListSum = 0.0D;
            for (double speed : speedList) {
                speedListSum += speed;
            }
            avgSpeed = speedList.isEmpty() ? 0.0D : speedListSum / speedList.size();

            while (yawDiffList.size() > maxDeltaHistorySize) {
                yawDiffList.remove(0);
            }
            while (speedList.size() > 20) {
                speedList.remove(0);
            }

            avgYawDiff = MathHelper.clamp_float(avgYawDiff, -180.0F, 180.0F);

            if (!"NONE".equalsIgnoreCase(averageMode)) {
                float avgYawAbs = Math.abs(avgYawDiff);
                switch (averageMode.toUpperCase()) {
                    case "NCP":
                        EntityLivingBase target = KillAura.target == null ? null : KillAura.target.getEntity();
                        if (target == null || AdvancedRotationMath.getDistanceToEntityBox(target) > 1.0D) {
                            double vl = 0.0D;
                            if (avgYawAbs > 50.0F) {
                                vl += 30.0D * avgYawAbs / 180.0D;
                            }
                            if (avgSpeed >= 0.0D && avgSpeed < 0.2D) {
                                vl += 20.0D * (0.2D - avgSpeed) / 0.2D;
                            }
                            if (averageSwitching > 0.0D) {
                                vl += 20.0D * averageSwitching;
                            }
                            vl += 30.0D * (150.0D - RandomUtil.nextDouble(37.5D, 112.5D)) / 150.0D;
                            shouldLimit = vl > 70.0D;
                        } else {
                            shouldLimit = false;
                        }
                        break;
                    case "CUSTOM":
                        shouldLimit = avgYawAbs > maxAverageYawDelta;
                        break;
                    default:
                        shouldLimit = false;
                        break;
                }
            } else {
                shouldLimit = false;
            }
        }

        prevYaw = currentYaw;
        prevPitch = currentPitch;
    }

    private double reduceYaw(double deltaYaw, String averageMode, int maxDeltaHistorySize, float maxAverageYawDelta, float minYawMultiplier, float maxYawMultiplier) {
        if (maxDeltaHistorySize <= 0) {
            return deltaYaw;
        }

        if (!shouldLimit) {
            return deltaYaw;
        }

        if ("NCP".equalsIgnoreCase(averageMode)) {
            return 0.0D;
        }

        float percentageYaw = 1.0F - MathHelper.clamp_float((Math.abs(avgYawDiff) - maxAverageYawDelta) / maxAverageYawDelta, 0.0F, 1.0F);
        float reduce = AdvancedRotationMath.interpolate(minYawMultiplier, maxYawMultiplier, percentageYaw);
        return deltaYaw * reduce;
    }
}
