package cn.unfair.util.anticheat.impl;

import cn.unfair.util.anticheat.ACPlayerData;
import cn.unfair.util.anticheat.AntiCheatCheck;
import cn.unfair.util.anticheat.AnticheatManager;
import net.minecraft.entity.player.EntityPlayer;

public class EagleCheck extends AntiCheatCheck {
    public EagleCheck() {
        super("Eagle", "Detects mechanical eagle patterns.");
    }

    @Override
    public void onTick(AnticheatManager manager, ACPlayerData data) {
        EntityPlayer player = data.getPlayer();
        if (player == null || player == net.minecraft.client.Minecraft.getMinecraft().thePlayer
                || data.lastPosition == null || !data.isHoldingBlock()) {
            return;
        }

        double dx = player.posX - data.lastPosition.xCoord;
        double dz = player.posZ - data.lastPosition.zCoord;
        double moveAngle = relativeMoveAngle(dx, dz, player.rotationYaw);
        long crouchDuration = data.lastCrouchEndTick - data.lastCrouchStartTick;
        boolean quickCrouch = crouchDuration >= 1L && crouchDuration <= 2L;
        boolean swungOnCrouchTick = data.lastSwingTick >= data.lastCrouchEndTick
                && data.lastSwingTick <= data.lastCrouchEndTick + 1L;
        boolean steep = player.rotationPitch >= 70.0F;
        boolean verySteep = player.rotationPitch >= 85.0F;
        boolean sideMove = Math.abs(moveAngle) >= 90.0D;
        boolean backwards = Math.abs(moveAngle) >= 160.0D;

        if (!steep || !player.isSwingInProgress && !swungOnCrouchTick) {
            if (data.currentTick - data.lastEaglePatternTick > 15L) {
                data.eaglePatternCount = 0;
            }
            if (data.eagleConsecutiveViolations > 0) data.eagleConsecutiveViolations--;
            return;
        }

        boolean consistent = data.crouchDurations.size() >= 3
                && data.crouchDurations.stream().limit(3).allMatch(duration -> duration <= 2);
        if (quickCrouch && swungOnCrouchTick) {
            data.crouchDurations.stream().limit(3).mapToInt(Integer::intValue).average().ifPresent(avg -> {
                double variance = data.crouchDurations.stream().limit(3)
                        .mapToDouble(duration -> Math.pow(duration - avg, 2.0D)).average().orElse(0.0D);
                if (consistent && variance < 4.0D) {
                    recordPattern(manager, data, "consistent-pattern", moveAngle, crouchDuration);
                } else if (backwards || sideMove) {
                    recordPattern(manager, data, backwards ? "backwards-bridging" : "mechanical-pattern",
                            moveAngle, crouchDuration);
                }
            });
        }
    }

    private void recordPattern(AnticheatManager manager, ACPlayerData data, String type,
                               double angle, long crouchDuration) {
        long tick = data.currentTick;
        if (tick - data.lastEaglePatternTick > 15L) data.eaglePatternCount = 0;
        data.eaglePatternCount++;
        data.lastEaglePatternTick = tick;

        if (data.eaglePatternCount >= 2) {
            data.eagleConsecutiveViolations++;
            double amount = 1.0D + Math.min(5, data.eagleConsecutiveViolations) * 0.15D;
            manager.flag(data, this, String.format("type: %s, angle: %.1f, crouch: %dt, consistency: %.2f",
                    type, angle, crouchDuration, 1.0D), amount);
            data.eaglePatternCount = 0;
        }
    }

    private double relativeMoveAngle(double dx, double dz, float yaw) {
        double angle = Math.toDegrees(Math.atan2(-dx, dz)) - yaw;
        angle %= 360.0D;
        if (angle > 180.0D) angle -= 360.0D;
        if (angle < -180.0D) angle += 360.0D;
        return angle;
    }
}
