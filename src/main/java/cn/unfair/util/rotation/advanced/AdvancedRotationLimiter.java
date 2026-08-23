package cn.unfair.util.rotation.advanced;

import cn.unfair.module.modules.combat.KillAura;
import cn.unfair.util.player.MoveUtil;
import cn.unfair.util.client.RandomUtil;
import cn.unfair.util.client.TimerUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.MathHelper;

import java.util.ArrayList;
import java.util.List;

public class AdvancedRotationLimiter {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private static final float UPDATE_MS = 16.67F;

    private final TimerUtil updateTimer = new TimerUtil();
    private final List<Float> yawDiffList = new ArrayList<>();
    private final List<Float> pitchDiffList = new ArrayList<>();
    private final List<Double> speedList = new ArrayList<>();

    private double previousDeltaYaw;
    private double previousDeltaPitch;
    private double accelerationYaw;
    private double accelerationPitch;
    private float avgYawDiff;
    private float avgPitchDiff;
    private float prevYaw;
    private float prevPitch;
    private float yawDiff;
    private float pitchDiff;
    private float lastYawDiff;
    private double lastAccelDeltaYaw;
    private double lastAccelDeltaPitch;
    private float stamina = 0.8F;
    private boolean shouldLimit;
    private double averageSwitching;
    private double avgSpeed;

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
        yawDiff = 0.0F;
        pitchDiff = 0.0F;
        lastYawDiff = yaw;
        lastAccelDeltaYaw = 0.0F;
        lastAccelDeltaPitch = 0.0F;
        stamina = 0.8F;
        shouldLimit = false;
        averageSwitching = 0.0D;
        avgSpeed = 0.0D;
        updateTimer.reset();
    }

    public float[] limit(float currentYaw, float currentPitch, float targetYaw, float targetPitch, String limiterMode, int maxDeltaHistorySize, String averageMode, float maxAverageYawDelta, float minYawMultiplier, float maxYawMultiplier, float yawSpeed, float pitchSpeed) {
        updateHistory(currentYaw, currentPitch, maxDeltaHistorySize, averageMode, maxAverageYawDelta, limiterMode, yawSpeed);

        double[] delta = limitRotations(currentYaw, currentPitch, targetYaw, targetPitch, limiterMode, yawSpeed, pitchSpeed);
        if (!"NONE".equalsIgnoreCase(averageMode)) {
            delta[0] = reduceYaw(delta[0], averageMode, maxDeltaHistorySize, maxAverageYawDelta, minYawMultiplier, maxYawMultiplier);
        }

        return new float[]{
                currentYaw + (float) delta[0],
                MathHelper.clamp_float(currentPitch - (float) delta[1], -90.0F, 90.0F)
        };
    }

    private void updateHistory(float currentYaw, float currentPitch, int maxDeltaHistorySize, String averageMode, float maxAverageYawDelta, String limiterMode, float yawSpeed) {
        yawDiff = prevYaw - currentYaw;
        pitchDiff = prevPitch - currentPitch;

        if (maxDeltaHistorySize == 0) {
            avgYawDiff = 0.0F;
            avgPitchDiff = 0.0F;
            shouldLimit = false;
            averageSwitching = 0.0D;
            avgSpeed = 0.0D;
        } else {
            yawDiffList.add(yawDiff);
            pitchDiffList.add(pitchDiff);
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

            float pitchDiffListSum = 0.0F;
            for (float diffPitch : pitchDiffList) {
                pitchDiffListSum += diffPitch;
            }
            avgPitchDiff = pitchDiffListSum / pitchDiffList.size();

            double speedListSum = 0.0D;
            for (double speed : speedList) {
                speedListSum += speed;
            }
            avgSpeed = speedList.isEmpty() ? 0.0D : speedListSum / speedList.size();

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

        if ("ACCELERATED".equalsIgnoreCase(limiterMode)) {
            if (Math.abs(avgYawDiff) > yawSpeed / 4.0F) {
                stamina = Math.max(0.0F, stamina - (Math.abs(avgYawDiff) - (yawSpeed / 4.0F)) / 1980.0F);
            } else {
                stamina = Math.min(1.0F, stamina + 0.004F);
            }
        }
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

    private double[] limitRotations(float currentYaw, float currentPitch, float targetYaw, float targetPitch, String limiterMode, float baseYawSpeed, float basePitchSpeed) {
        float yawDifference = AdvancedRotationMath.getAngleDifference(targetYaw, currentYaw);
        float pitchDifference = AdvancedRotationMath.getAngleDifference(targetPitch, currentPitch);
        double rotationDifference = Math.max(1.0E-6D, Math.hypot(Math.abs(yawDifference), Math.abs(pitchDifference)));

        float hSpeed = baseYawSpeed + Math.abs(yawDiff * 0.4F) + Math.abs(avgYawDiff * RandomUtil.nextFloat(0.8F, 1.6F));
        float vSpeed = basePitchSpeed + Math.abs(pitchDiff * 0.4F) + Math.abs(avgPitchDiff * RandomUtil.nextFloat(0.8F, 1.6F));

        float unNormHSpeed = hSpeed;
        hSpeed /= 3.0F;

        float unNormVSpeed = vSpeed;
        vSpeed /= 3.0F;

        hSpeed = Math.min(hSpeed * 1.08F, 180.0F);
        vSpeed = Math.min(vSpeed * 1.08F, 180.0F);

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
                if (yawDifference > hSpeed * 1.2F && Math.abs(avgYawDiff) > 16.0F && Math.abs(avgPitchDiff) < 0.7F && RandomUtil.nextInt(0, 100) < 22) {
                    targetPitch += AdvancedRotationMath.randomizeAround(Math.abs(avgYawDiff) / 180.0F * 10.0F);
                    pitchDifference = AdvancedRotationMath.getAngleDifference(targetPitch, currentPitch);
                }

                float staminaMultiplier = MathHelper.clamp_float(0.84F + stamina * 0.16F, 0.84F, 1.0F);
                hSpeed *= staminaMultiplier;
                vSpeed *= staminaMultiplier;

                float decelStartYaw = unNormHSpeed / 4.0F;
                float decelStartPitch = unNormVSpeed / 4.0F;
                float percentageYawCurr = MathHelper.clamp_float((decelStartYaw - Math.abs(avgYawDiff)) / decelStartYaw, 0.0F, 1.0F);
                float percentageYawAvg = MathHelper.clamp_float((decelStartYaw - Math.abs(yawDifference)) / decelStartYaw, 0.0F, 1.0F);
                float percentageYaw = percentageYawCurr * percentageYawAvg;
                float percentagePitchCurr = MathHelper.clamp_float((decelStartPitch - Math.abs(avgPitchDiff)) / decelStartPitch, 0.0F, 1.0F);
                float percentagePitchAvg = MathHelper.clamp_float((decelStartPitch - Math.abs(pitchDifference)) / decelStartPitch, 0.0F, 1.0F);
                float percentagePitch = percentagePitchCurr * percentagePitchAvg;

                float multiYaw = Math.abs(yawDifference) / decelStartYaw;
                float multiPitch = Math.abs(pitchDifference) / decelStartPitch;
                multiYaw = MathHelper.clamp_float(multiYaw, 0.85F, 1.0F);
                multiPitch = MathHelper.clamp_float(multiPitch, 0.85F, 1.0F);

                float reducedHSpeed = hSpeed * multiYaw;
                float reducedVSpeed = vSpeed * multiPitch;
                hSpeed = MathHelper.clamp_float(AdvancedRotationMath.interpolate(hSpeed, reducedHSpeed, percentageYaw), 0.0F, 180.0F);
                vSpeed = MathHelper.clamp_float(AdvancedRotationMath.interpolate(vSpeed, reducedVSpeed, percentagePitch), 0.0F, 180.0F);

                float maxAccel = 1.8F;
                float percentage1 = MathHelper.clamp_float((Math.abs(currentYaw - prevYaw) - baseYawSpeed * maxAccel) / Math.max(baseYawSpeed * maxAccel, 1.0E-6F), 0.0F, 1.0F);
                float percentage2 = MathHelper.clamp_float((Math.abs(avgYawDiff) - baseYawSpeed) / baseYawSpeed, 0.0F, 1.0F);
                float reducedHSpeed2 = hSpeed * 0.78F;
                hSpeed = MathHelper.clamp_float(AdvancedRotationMath.interpolate(hSpeed, reducedHSpeed2, percentage1 * percentage2), 0.0F, 180.0F);

                double straightLineYaw = Math.abs(yawDifference / rotationDifference) * hSpeed;
                double straightLinePitch = Math.abs(pitchDifference / rotationDifference) * vSpeed;
                double deltaYaw = MathHelper.clamp_double(yawDifference, -straightLineYaw, straightLineYaw);
                double deltaPitch = MathHelper.clamp_double(pitchDifference, -straightLinePitch, straightLinePitch);

                if (updateTimer.hasTimeElapsed((long) UPDATE_MS)) {
                    double velocityYaw = deltaYaw - previousDeltaYaw;
                    double velocityPitch = deltaPitch - previousDeltaPitch;

                    float baseYaw = (Math.abs(yawDiff * 0.7F) + Math.abs(avgYawDiff * RandomUtil.nextFloat(0.8F, 1.6F))) / 180.0F;
                    baseYaw = MathHelper.clamp_float(baseYaw, 0.0F, 1.0F);

                    float basePitch = (Math.abs(pitchDiff * 0.7F) + Math.abs(avgPitchDiff * RandomUtil.nextFloat(0.8F, 1.6F))) / 180.0F;
                    basePitch = MathHelper.clamp_float(basePitch, 0.0F, 1.0F);

                    float factorYaw = MathHelper.clamp_float(AdvancedRotationMath.interpolate(0.14F, 0.35F, smooth(baseYaw, 2.0F)), 0.0F, 0.35F);
                    float factorPitch = MathHelper.clamp_float(AdvancedRotationMath.interpolate(0.14F, 0.35F, smooth(basePitch, 2.0F)), 0.0F, 0.35F);

                    float f = mc.gameSettings.mouseSensitivity * 0.5F + 0.2F;
                    float f1 = f * f * f * 8.0F;
                    if (Math.abs(lastAccelDeltaYaw) < f1 * 0.15F) {
                        float incYaw = MathHelper.clamp_float(0.15F * (Math.abs(yawDifference) / 50.0F), 0.0F, 1.0F);
                        factorYaw = RandomUtil.nextFloat(0.05F + incYaw, 0.15F + incYaw);
                    }

                    if (Math.abs(lastAccelDeltaPitch) < f1 * 0.15F) {
                        float incPitch = MathHelper.clamp_float(0.15F * (Math.abs(pitchDifference) / 50.0F), 0.0F, 1.0F);
                        factorPitch = RandomUtil.nextFloat(0.05F + incPitch, 0.15F + incPitch);
                    }

                    factorYaw = MathHelper.clamp_float(factorYaw, 0.0F, 1.0F);
                    factorPitch = MathHelper.clamp_float(factorPitch, 0.0F, 1.0F);

                    float distYaw = 1.0F - MathHelper.clamp_float(Math.abs(yawDifference) / Math.max(baseYawSpeed, 1.0F) * 2.0F, 0.0F, 1.0F);
                    float distPitch = 1.0F - MathHelper.clamp_float(Math.abs(pitchDifference) / Math.max(basePitchSpeed, 1.0F) * 2.0F, 0.0F, 1.0F);

                    float yawSpeedRatio = 1.0F - MathHelper.clamp_float(Math.abs(avgYawDiff) / Math.max(baseYawSpeed, 1.0F) / 2.0F, 0.0F, 1.0F);
                    float pitchSpeedRatio = 1.0F - MathHelper.clamp_float(Math.abs(avgPitchDiff) / Math.max(basePitchSpeed, 1.0F) / 2.0F, 0.0F, 1.0F);

                    float minDamping = 0.9F;
                    float yawProgress = AdvancedRotationMath.interpolate(minDamping, 1.0F, 1.0F - (distYaw * yawSpeedRatio));
                    float pitchProgress = AdvancedRotationMath.interpolate(minDamping, 1.0F, 1.0F - (distPitch * pitchSpeedRatio));
                    float yawDamping = MathHelper.clamp_float(smooth(yawProgress, 1.0F), minDamping, 1.0F);
                    float pitchDamping = MathHelper.clamp_float(smooth(pitchProgress, 1.0F), minDamping, 1.0F);

                    float dampFactor = Math.abs(targetYaw - lastYawDiff) / 4.7F;
                    yawDamping = MathHelper.clamp_float(AdvancedRotationMath.interpolate(yawDamping, 1.0F, dampFactor), minDamping, 1.0F);
                    pitchDamping = MathHelper.clamp_float(AdvancedRotationMath.interpolate(pitchDamping, 1.0F, dampFactor), minDamping, 1.0F);
                    lastYawDiff = targetYaw;

                    float minYaw = baseYawSpeed / 2.0F;
                    float minPitch = minYaw / 2.0F;
                    if (Math.signum(accelerationYaw) != Math.signum(velocityYaw) && Math.abs(avgYawDiff) > minYaw) {
                        float blendedDelta = (float) AdvancedRotationMath.interpolate(Math.abs(deltaYaw), Math.abs(avgYawDiff), 0.73F);
                        float avgYawRatio = MathHelper.clamp_float((blendedDelta - minYaw) / minYaw, 0.0F, 1.0F);
                        float multi = AdvancedRotationMath.interpolate(0.8F, 0.9F, avgYawRatio);
                        yawDamping *= multi;
                    }

                    if (Math.signum(accelerationPitch) != Math.signum(velocityPitch) && Math.abs(avgPitchDiff) > minPitch) {
                        float blendedDelta = (float) AdvancedRotationMath.interpolate(Math.abs(deltaPitch), Math.abs(avgYawDiff), 0.73F);
                        float avgPitchRatio = MathHelper.clamp_float((blendedDelta - minPitch) / minPitch, 0.0F, 1.0F);
                        float multi = AdvancedRotationMath.interpolate(0.8F, 0.9F, avgPitchRatio);
                        pitchDamping *= multi;
                    }

                    accelerationYaw = (float) AdvancedRotationMath.interpolate(previousDeltaYaw, deltaYaw, factorYaw);
                    accelerationPitch = (float) AdvancedRotationMath.interpolate(previousDeltaPitch, deltaPitch, factorPitch);
                    accelerationYaw *= yawDamping;
                    accelerationPitch *= pitchDamping;
                    float yawOvershootLimit = Math.abs(yawDifference) + Math.min(2.0F, Math.abs(yawDifference) * 0.06F);
                    float pitchOvershootLimit = Math.abs(pitchDifference) + Math.min(2.0F, Math.abs(pitchDifference) * 0.06F);
                    accelerationYaw = MathHelper.clamp_double(accelerationYaw, -yawOvershootLimit, yawOvershootLimit);
                    accelerationPitch = MathHelper.clamp_double(accelerationPitch, -pitchOvershootLimit, pitchOvershootLimit);
                    lastAccelDeltaYaw = accelerationYaw;
                    lastAccelDeltaPitch = accelerationPitch;
                    updateTimer.reset();
                }

                previousDeltaYaw = accelerationYaw;
                previousDeltaPitch = accelerationPitch;
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

    private float smooth(float x, float f) {
        return (x * x) * f;
    }
}
