package cn.unfair.util.rotationadvanced;

import cn.unfair.util.MoveUtil;
import cn.unfair.util.RandomUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.util.MathHelper;

import java.util.ArrayList;
import java.util.List;

public class AdvancedRotationLimiter {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private double previousDeltaYaw;
    private double previousDeltaPitch;
    private double accelerationYaw;
    private double accelerationPitch;
    private final List<Float> yawDiffList = new ArrayList<>();
    private final List<Float> pitchDiffList = new ArrayList<>();
    private final List<Double> speedList = new ArrayList<>();
    private float avgYawDiff;
    private float avgPitchDiff;
    private float prevYaw;
    private float prevPitch;
    private float stamina = 0.8F;
    private float lastYawDiff;

    public void reset(float yaw, float pitch) {
        previousDeltaYaw = 0.0D;
        previousDeltaPitch = 0.0D;
        accelerationYaw = 0.0D;
        accelerationPitch = 0.0D;
        yawDiffList.clear();
        pitchDiffList.clear();
        speedList.clear();
        avgYawDiff = 0.0F;
        avgPitchDiff = 0.0F;
        prevYaw = yaw;
        prevPitch = pitch;
        stamina = 0.8F;
        lastYawDiff = yaw;
    }

    public float[] limit(float currentYaw, float currentPitch, float targetYaw, float targetPitch, String limiterMode, int maxDeltaHistorySize, String averageMode, float maxAverageYawDelta, float minYawMultiplier, float maxYawMultiplier, float yawSpeed, float pitchSpeed) {
        updateHistory(currentYaw, currentPitch, maxDeltaHistorySize);

        double[] delta = limitRotations(currentYaw, currentPitch, targetYaw, targetPitch, limiterMode, yawSpeed, pitchSpeed);
        if (!"NONE".equalsIgnoreCase(averageMode)) {
            delta[0] = reduceYaw(delta[0], averageMode, maxDeltaHistorySize, maxAverageYawDelta, minYawMultiplier, maxYawMultiplier);
        }

        return new float[]{
                currentYaw + (float) delta[0],
                MathHelper.clamp_float(currentPitch - (float) delta[1], -90.0F, 90.0F)
        };
    }

    private void updateHistory(float currentYaw, float currentPitch, int maxDeltaHistorySize) {
        float yawDiff = prevYaw - currentYaw;
        float pitchDiff = prevPitch - currentPitch;

        if (maxDeltaHistorySize == 0) {
            avgYawDiff = 0.0F;
            avgPitchDiff = 0.0F;
        } else {
            yawDiffList.add(yawDiff);
            pitchDiffList.add(pitchDiff);
            speedList.add(MoveUtil.getSpeed());

            float yawSum = 0.0F;
            for (float diff : yawDiffList) {
                yawSum += diff;
            }
            avgYawDiff = yawSum / yawDiffList.size();

            float pitchSum = 0.0F;
            for (float diff : pitchDiffList) {
                pitchSum += diff;
            }
            avgPitchDiff = pitchSum / pitchDiffList.size();

            while (yawDiffList.size() > maxDeltaHistorySize) {
                yawDiffList.remove(0);
            }
            while (pitchDiffList.size() > maxDeltaHistorySize) {
                pitchDiffList.remove(0);
            }
            while (speedList.size() > 20) {
                speedList.remove(0);
            }

            avgYawDiff = MathHelper.clamp_float(avgYawDiff, -180.0F, 180.0F);
            avgPitchDiff = MathHelper.clamp_float(avgPitchDiff, -90.0F, 90.0F);
        }

        prevYaw = currentYaw;
        prevPitch = currentPitch;
    }

    private double reduceYaw(double deltaYaw, String averageMode, int maxDeltaHistorySize, float maxAverageYawDelta, float minYawMultiplier, float maxYawMultiplier) {
        if (maxDeltaHistorySize <= 0) {
            return deltaYaw;
        }

        boolean shouldLimit;
        if ("NCP".equalsIgnoreCase(averageMode)) {
            double avgSpeed = 0.0D;
            for (double speed : speedList) {
                avgSpeed += speed;
            }
            avgSpeed = speedList.isEmpty() ? 0.0D : avgSpeed / speedList.size();
            double vl = 0.0D;
            float avgYawAbs = Math.abs(avgYawDiff);
            if (avgYawAbs > 50.0F) {
                vl += 30.0D * avgYawAbs / 180.0D;
            }
            if (avgSpeed >= 0.0D && avgSpeed < 0.2D) {
                vl += 20.0D * (0.2D - avgSpeed) / 0.2D;
            }
            vl += 30.0D * (150.0D - RandomUtil.nextDouble(37.5D, 112.5D)) / 150.0D;
            shouldLimit = vl > 70.0D;
        } else {
            shouldLimit = Math.abs(avgYawDiff) > maxAverageYawDelta;
        }

        if (!shouldLimit) {
            return deltaYaw;
        }

        if ("NCP".equalsIgnoreCase(averageMode)) {
            return 0.0D;
        }

        float percentageYaw = 1.0F - MathHelper.clamp_float((Math.abs(avgYawDiff) - maxAverageYawDelta) / maxAverageYawDelta, 0.0F, 1.0F);
        float reduce = (float) AdvancedRotationMath.interpolate(minYawMultiplier, maxYawMultiplier, percentageYaw);
        return deltaYaw * reduce;
    }

    private double[] limitRotations(float currentYaw, float currentPitch, float targetYaw, float targetPitch, String limiterMode, float baseYawSpeed, float basePitchSpeed) {
        float yawDifference = AdvancedRotationMath.getAngleDifference(targetYaw, currentYaw);
        float pitchDifference = AdvancedRotationMath.getAngleDifference(targetPitch, currentPitch);
        double rotationDifference = Math.max(1.0E-6D, Math.hypot(Math.abs(yawDifference), Math.abs(pitchDifference)));

        float hSpeed = baseYawSpeed + Math.abs((prevYaw - currentYaw) * 0.4F) + Math.abs(avgYawDiff * RandomUtil.nextFloat(0.8F, 1.6F));
        float vSpeed = basePitchSpeed + Math.abs((prevPitch - currentPitch) * 0.4F) + Math.abs(avgPitchDiff * RandomUtil.nextFloat(0.8F, 1.6F));
        float unNormHSpeed = hSpeed;
        float unNormVSpeed = vSpeed;
        hSpeed = Math.min(hSpeed / 3.0F, 180.0F);
        vSpeed = Math.min(vSpeed / 3.0F, 180.0F);

        switch (limiterMode.toUpperCase()) {
            case "NONE":
                return new double[]{yawDifference, -pitchDifference};
            case "INTERPOLATED": {
                double straightLineYaw = Math.abs(yawDifference / rotationDifference) * MathHelper.clamp_float(MathHelper.clamp_float(Math.abs(yawDifference) / 180.0F, 0.0F, 1.0F) * hSpeed, RandomUtil.nextFloat(4.0F, 6.0F), 180.0F);
                double straightLinePitch = Math.abs(pitchDifference / rotationDifference) * MathHelper.clamp_float(MathHelper.clamp_float(Math.abs(pitchDifference) / 180.0F, 0.0F, 1.0F) * vSpeed, RandomUtil.nextFloat(4.0F, 6.0F), 180.0F);
                return new double[]{
                        MathHelper.clamp_double(yawDifference, -straightLineYaw, straightLineYaw),
                        -MathHelper.clamp_double(pitchDifference, -straightLinePitch, straightLinePitch)
                };
            }
            case "ACCELERATED": {
                if (yawDifference > hSpeed && Math.abs(avgYawDiff) > 12.0F && Math.abs(avgPitchDiff) < 1.0F) {
                    targetPitch += AdvancedRotationMath.randomizeAround(Math.abs(avgYawDiff) / 90.0F * 50.0F);
                    pitchDifference = AdvancedRotationMath.getAngleDifference(targetPitch, currentPitch);
                }

                if (Math.abs(avgYawDiff) > baseYawSpeed / 4.0F) {
                    stamina = Math.max(0.0F, stamina - (Math.abs(avgYawDiff) - (baseYawSpeed / 4.0F)) / 1980.0F);
                } else {
                    stamina = Math.min(1.0F, stamina + 0.004F);
                }

                float staminaMultiplier = MathHelper.clamp_float(0.8F + stamina * 0.2F, 0.8F, 1.0F);
                hSpeed *= staminaMultiplier;
                vSpeed *= staminaMultiplier;

                float decelStartYaw = unNormHSpeed / 4.0F;
                float decelStartPitch = unNormVSpeed / 4.0F;
                float percentageYaw = MathHelper.clamp_float((decelStartYaw - Math.abs(avgYawDiff)) / decelStartYaw, 0.0F, 1.0F)
                        * MathHelper.clamp_float((decelStartYaw - Math.abs(yawDifference)) / decelStartYaw, 0.0F, 1.0F);
                float percentagePitch = MathHelper.clamp_float((decelStartPitch - Math.abs(avgPitchDiff)) / decelStartPitch, 0.0F, 1.0F)
                        * MathHelper.clamp_float((decelStartPitch - Math.abs(pitchDifference)) / decelStartPitch, 0.0F, 1.0F);
                hSpeed = (float) AdvancedRotationMath.interpolate(hSpeed, hSpeed * MathHelper.clamp_float(Math.abs(yawDifference) / decelStartYaw, 0.85F, 1.0F), percentageYaw);
                vSpeed = (float) AdvancedRotationMath.interpolate(vSpeed, vSpeed * MathHelper.clamp_float(Math.abs(pitchDifference) / decelStartPitch, 0.85F, 1.0F), percentagePitch);

                double straightLineYaw = Math.abs(yawDifference / rotationDifference) * Math.min(hSpeed, 180.0F);
                double straightLinePitch = Math.abs(pitchDifference / rotationDifference) * Math.min(vSpeed, 180.0F);
                double deltaYaw = MathHelper.clamp_double(yawDifference, -straightLineYaw, straightLineYaw);
                double deltaPitch = MathHelper.clamp_double(pitchDifference, -straightLinePitch, straightLinePitch);

                double velocityYaw = deltaYaw - previousDeltaYaw;
                double velocityPitch = deltaPitch - previousDeltaPitch;
                float baseYaw = MathHelper.clamp_float((Math.abs(prevYaw - currentYaw) * 0.7F + Math.abs(avgYawDiff * RandomUtil.nextFloat(0.8F, 1.6F))) / 180.0F, 0.0F, 1.0F);
                float basePitch = MathHelper.clamp_float((Math.abs(prevPitch - currentPitch) * 0.7F + Math.abs(avgPitchDiff * RandomUtil.nextFloat(0.8F, 1.6F))) / 180.0F, 0.0F, 1.0F);
                float factorYaw = MathHelper.clamp_float((float) AdvancedRotationMath.interpolate(0.15F, 0.35F, baseYaw * baseYaw * 2.0F), 0.0F, 0.35F);
                float factorPitch = MathHelper.clamp_float((float) AdvancedRotationMath.interpolate(0.15F, 0.35F, basePitch * basePitch * 2.0F), 0.0F, 0.35F);

                accelerationYaw = AdvancedRotationMath.interpolate(previousDeltaYaw, deltaYaw, factorYaw);
                accelerationPitch = AdvancedRotationMath.interpolate(previousDeltaPitch, deltaPitch, factorPitch);
                if (Math.signum(accelerationYaw) != Math.signum(velocityYaw) && Math.abs(avgYawDiff) > baseYawSpeed / 2.0F) {
                    accelerationYaw *= 0.85D;
                }
                if (Math.signum(accelerationPitch) != Math.signum(velocityPitch) && Math.abs(avgPitchDiff) > baseYawSpeed / 4.0F) {
                    accelerationPitch *= 0.85D;
                }
                previousDeltaYaw = accelerationYaw;
                previousDeltaPitch = accelerationPitch;
                lastYawDiff = targetYaw;
                return new double[]{accelerationYaw, -accelerationPitch};
            }
            case "LINEAR":
            default: {
                double straightLineYaw = Math.abs(yawDifference / rotationDifference) * hSpeed;
                double straightLinePitch = Math.abs(pitchDifference / rotationDifference) * vSpeed;
                return new double[]{
                        MathHelper.clamp_double(yawDifference, -straightLineYaw, straightLineYaw),
                        -MathHelper.clamp_double(pitchDifference, -straightLinePitch, straightLinePitch)
                };
            }
        }
    }
}
