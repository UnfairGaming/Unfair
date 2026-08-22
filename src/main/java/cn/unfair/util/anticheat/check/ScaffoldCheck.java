package cn.unfair.util.anticheat.check;

import cn.unfair.util.anticheat.ACPlayerData;
import cn.unfair.util.anticheat.AntiCheatCheck;
import cn.unfair.util.anticheat.AnticheatManager;
import net.minecraft.entity.player.EntityPlayer;

public class ScaffoldCheck extends AntiCheatCheck {
    public ScaffoldCheck() {
        super("Scaffold", "Detects illegal bridging patterns.");
    }

    @Override
    public void onTick(AnticheatManager manager, ACPlayerData data) {
        EntityPlayer player = data.getPlayer();
        if (player == null || player == net.minecraft.client.Minecraft.getMinecraft().thePlayer
                || player.isRiding() || data.positionHistory.size() < 5 || !data.isHoldingBlock()) {
            return;
        }

        int size = data.positionHistory.size();
        ACPlayerData.PositionSample current = data.positionHistory.get(size - 1);
        ACPlayerData.PositionSample previous = data.positionHistory.get(size - 2);
        ACPlayerData.PositionSample previous2 = data.positionHistory.get(size - 3);
        double speedX = (current.pos().xCoord - previous.pos().xCoord) * 20.0D;
        double speedZ = (current.pos().zCoord - previous.pos().zCoord) * 20.0D;
        double speed = Math.sqrt(speedX * speedX + speedZ * speedZ);
        double accelY = ((previous.pos().yCoord - previous2.pos().yCoord) * 20.0D);
        double angle = relativeMoveAngle(speedX / 20.0D, speedZ / 20.0D, player.rotationYaw);

        if (!player.isSwingInProgress || player.hurtTime != 0
                || player.rotationPitch <= 50.0F || speed * speed >= 100.0D
                || Math.abs(angle) <= 165.0D || Math.abs(accelY) < 0.005D) {
            if (data.currentTick - data.lastScaffoldViolationTime > 60L) {
                data.scaffoldConsecutiveViolations = 0;
            }
            return;
        }

        String type;
        if (accelY >= 4.0D && accelY <= 15.0D) {
            type = "tower";
        } else if (accelY >= -1.0D && accelY <= 4.0D && speed * speed > 25.0D) {
            type = "horizontal";
        } else {
            return;
        }

        if (type.equals(data.lastScaffoldViolationType)
                && data.currentTick - data.lastScaffoldViolationTime < 2L) {
            data.scaffoldConsecutiveViolations++;
        } else {
            data.scaffoldConsecutiveViolations = 0;
        }
        data.lastScaffoldViolationType = type;
        data.lastScaffoldViolationTime = data.currentTick;

        double amount = 1.0D + Math.min(5, data.scaffoldConsecutiveViolations) * 0.2D;
        manager.flag(data, this, String.format("type: %s, angle: %.1f, speed: %.2f, accelY: %.2f",
                type, angle, speed, accelY), amount);
    }

    private double relativeMoveAngle(double dx, double dz, float yaw) {
        double angle = Math.toDegrees(Math.atan2(dz, dx)) - 90.0D - yaw;
        angle %= 360.0D;
        if (angle > 180.0D) angle -= 360.0D;
        if (angle < -180.0D) angle += 360.0D;
        return angle;
    }
}
