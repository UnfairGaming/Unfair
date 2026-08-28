package cn.unfair.util.rotation;

import cn.unfair.util.client.RandomUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.BlockPos;
import net.minecraft.util.MathHelper;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class RotationUtil {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private static final double AIM_FACE_INSET = 0.05D;
    private static final int AIM_BACKUP_POINT_COUNT = 30;
    private static final int AIM_VERTICAL_SCAN_STEPS = 16;

    public static float wrapAngleDiff(float angle, float target) {
        return target + MathHelper.wrapAngleTo180_float(angle - target);
    }

    public static float angleDifference(float angle, float target) {
        return MathHelper.wrapAngleTo180_float(angle - target);
    }

    public static float absoluteAngleDifference(float angle, float target) {
        return Math.abs(angleDifference(angle, target));
    }

    public static float clampAngle(float angle, float maxAngle) {
        maxAngle = Math.clamp(maxAngle, 0.0f, 180.0f);
        if (angle > maxAngle) {
            angle = maxAngle;
        } else if (angle < -maxAngle) {
            angle = -maxAngle;
        }
        return angle;
    }

    public static float smoothAngle(float angle, float smoothFactor) {
        return angle * (0.5f + 0.5f * (1.0f - Math.clamp(smoothFactor + RandomUtil.nextFloat(-0.1f, 0.1f), 0.0f, 1.0f)));
    }

    public static float quantizeAngle(float angle) {
        return (float) ((double) angle - (double) angle % (double) 0.0096f);
    }

    public static float[] getRotationsToBox(AxisAlignedBB boundingBox, float yaw, float pitch, float maxAngle, float smoothFactor) {
        Vec3 eyePos = RotationUtil.mc.thePlayer.getPositionEyes(1.0f);
        double minTargetY = boundingBox.minY + 0.05 * (boundingBox.maxY - boundingBox.minY);
        double maxTargetY = boundingBox.minY + 0.75 * (boundingBox.maxY - boundingBox.minY);
        double deltaX = (boundingBox.minX + boundingBox.maxX) / 2.0 - eyePos.xCoord;
        double deltaY = eyePos.yCoord >= maxTargetY ? maxTargetY - eyePos.yCoord : (eyePos.yCoord <= minTargetY ? minTargetY - eyePos.yCoord : 0.0);
        double deltaZ = (boundingBox.minZ + boundingBox.maxZ) / 2.0 - eyePos.zCoord;
        return RotationUtil.getRotations(deltaX, deltaY, deltaZ, yaw, pitch, maxAngle, smoothFactor);
    }

    public static Vec3 getBestAimPoint(
            Entity target, AxisAlignedBB boundingBox, Vec3 eyePos, double preferredY, double range,
            double horizontalMultipoint, double verticalMultipoint,
            boolean throughWalls, boolean throughEntities
    ) {
        Vec3 mainPoint = getAimPoint(
                boundingBox, eyePos, preferredY, horizontalMultipoint, verticalMultipoint
        );
        if (throughWalls && throughEntities) {
            return mainPoint;
        }
        if (eyePos.squareDistanceTo(mainPoint) < 1.0E-6D) {
            return mainPoint;
        }
        if (!rayHitsBox(eyePos, mainPoint, boundingBox, range)) {
            return null;
        }
        if (canAimAtPoint(eyePos, mainPoint, target, boundingBox, range, throughWalls, throughEntities)) {
            return mainPoint;
        }

        List<Vec3> backupPoints = buildBackupAimPoints(boundingBox, eyePos);
        backupPoints.sort(
                Comparator.comparingDouble((Vec3 point) -> Math.abs(point.yCoord - preferredY))
                        .thenComparingDouble(eyePos::squareDistanceTo)
        );
        for (Vec3 point : backupPoints) {
            if (canAimAtPoint(eyePos, point, target, boundingBox, range, throughWalls, throughEntities)) {
                return point;
            }
        }
        return scanVerticalAimPoint(eyePos, boundingBox, preferredY, target, range, throughWalls, throughEntities);
    }

    public static Vec3 getAimPoint(
            AxisAlignedBB boundingBox, Vec3 eyePos, double preferredY,
            double horizontalMultipoint, double verticalMultipoint
    ) {
        double centerX = (boundingBox.minX + boundingBox.maxX) * 0.5D;
        double centerY = MathHelper.clamp_double(preferredY, boundingBox.minY, boundingBox.maxY);
        double centerZ = (boundingBox.minZ + boundingBox.maxZ) * 0.5D;
        if (boundingBox.isVecInside(eyePos)) {
            return new Vec3(centerX, eyePos.yCoord, centerZ);
        }

        Vec3 closest = getClosestPointOnBox(eyePos, boundingBox);
        double horizontal = MathHelper.clamp_double(horizontalMultipoint, 0.0D, 1.0D);
        double vertical = MathHelper.clamp_double(verticalMultipoint, 0.0D, 1.0D);
        return new Vec3(
                centerX + (closest.xCoord - centerX) * horizontal,
                centerY + (closest.yCoord - centerY) * vertical,
                centerZ + (closest.zCoord - centerZ) * horizontal
        );
    }

    public static float[] getRotationsToPoint(
            Vec3 point, Vec3 eyePos, float currentYaw, float currentPitch,
            float maxAngle, float smoothFactor
    ) {
        return getRotations(
                point.xCoord - eyePos.xCoord,
                point.yCoord - eyePos.yCoord,
                point.zCoord - eyePos.zCoord,
                currentYaw, currentPitch, maxAngle, smoothFactor
        );
    }

    public static float[] getRotationsToPoint(
            Vec3 point, Vec3 eyePos, float currentYaw, float currentPitch, float pitchOffset
    ) {
        double deltaX = point.xCoord - eyePos.xCoord;
        double deltaY = point.yCoord - eyePos.yCoord;
        double deltaZ = point.zCoord - eyePos.zCoord;
        double horizontalDistanceSquared = deltaX * deltaX + deltaZ * deltaZ;
        float targetYaw = horizontalDistanceSquared < 1.0E-12D
                ? currentYaw
                : (float) Math.toDegrees(Math.atan2(deltaZ, deltaX)) - 90.0F;
        float targetPitch = (float) -Math.toDegrees(
                Math.atan2(deltaY, Math.sqrt(horizontalDistanceSquared))
        );
        return new float[]{
                currentYaw + MathHelper.wrapAngleTo180_float(targetYaw - currentYaw),
                MathHelper.clamp_float(
                        currentPitch + MathHelper.wrapAngleTo180_float(targetPitch - currentPitch) + pitchOffset,
                        -90.0F, 90.0F
                )
        };
    }

    private static boolean canAimAtPoint(
            Vec3 eyePos, Vec3 point, Entity target, AxisAlignedBB targetBox, double range,
            boolean throughWalls, boolean throughEntities
    ) {
        Vec3 end = extendToRange(eyePos, point, range);
        if (end == null) {
            return false;
        }
        MovingObjectPosition targetHit = targetBox.calculateIntercept(eyePos, end);
        if (targetHit == null) {
            return false;
        }
        double targetDistanceSquared = eyePos.squareDistanceTo(targetHit.hitVec);
        if (!throughWalls) {
            MovingObjectPosition blockHit = mc.theWorld.rayTraceBlocks(eyePos, end, false, false, false);
            if (blockHit != null && eyePos.squareDistanceTo(blockHit.hitVec) < targetDistanceSquared) {
                return false;
            }
        }
        return throughEntities || !hasBlockingEntity(eyePos, end, target, targetDistanceSquared);
    }

    private static boolean hasBlockingEntity(Vec3 eyePos, Vec3 end, Entity target, double targetDistanceSquared) {
        for (Entity entity : mc.theWorld.loadedEntityList) {
            if (entity == mc.thePlayer || entity == target || entity.isDead || !entity.canBeCollidedWith()) {
                continue;
            }
            float border = entity.getCollisionBorderSize();
            AxisAlignedBB box = entity.getEntityBoundingBox().expand(border, border, border);
            if (box.isVecInside(eyePos)) {
                return true;
            }
            MovingObjectPosition hit = box.calculateIntercept(eyePos, end);
            if (hit != null && eyePos.squareDistanceTo(hit.hitVec) < targetDistanceSquared - 1.0E-7D) {
                return true;
            }
        }
        return false;
    }

    private static boolean rayHitsBox(Vec3 eyePos, Vec3 point, AxisAlignedBB box, double range) {
        Vec3 end = extendToRange(eyePos, point, range);
        return end != null && box.calculateIntercept(eyePos, end) != null;
    }

    private static Vec3 extendToRange(Vec3 eyePos, Vec3 point, double range) {
        Vec3 delta = point.subtract(eyePos);
        double length = delta.lengthVector();
        if (length < 1.0E-6D) {
            return null;
        }
        double scale = range / length;
        return eyePos.addVector(delta.xCoord * scale, delta.yCoord * scale, delta.zCoord * scale);
    }

    private static List<Vec3> buildBackupAimPoints(AxisAlignedBB box, Vec3 eyePos) {
        boolean positiveX = eyePos.xCoord > box.maxX;
        boolean negativeX = eyePos.xCoord < box.minX;
        boolean positiveY = eyePos.yCoord > box.maxY;
        boolean negativeY = eyePos.yCoord < box.minY;
        boolean positiveZ = eyePos.zCoord > box.maxZ;
        boolean negativeZ = eyePos.zCoord < box.minZ;
        int visibleFaces = (positiveX || negativeX ? 1 : 0)
                + (positiveY || negativeY ? 1 : 0)
                + (positiveZ || negativeZ ? 1 : 0);
        List<Vec3> points = new ArrayList<>();
        if (visibleFaces == 0) {
            return points;
        }

        int pointsPerFace = AIM_BACKUP_POINT_COUNT / visibleFaces;
        if (positiveX || negativeX) {
            addFaceGrid(points, 0, positiveX ? box.maxX - AIM_FACE_INSET : box.minX + AIM_FACE_INSET,
                    box.minY + AIM_FACE_INSET, box.maxY - AIM_FACE_INSET,
                    box.minZ + AIM_FACE_INSET, box.maxZ - AIM_FACE_INSET, pointsPerFace);
        }
        if (positiveY || negativeY) {
            addFaceGrid(points, 1, positiveY ? box.maxY - AIM_FACE_INSET : box.minY + AIM_FACE_INSET,
                    box.minX + AIM_FACE_INSET, box.maxX - AIM_FACE_INSET,
                    box.minZ + AIM_FACE_INSET, box.maxZ - AIM_FACE_INSET, pointsPerFace);
        }
        if (positiveZ || negativeZ) {
            addFaceGrid(points, 2, positiveZ ? box.maxZ - AIM_FACE_INSET : box.minZ + AIM_FACE_INSET,
                    box.minX + AIM_FACE_INSET, box.maxX - AIM_FACE_INSET,
                    box.minY + AIM_FACE_INSET, box.maxY - AIM_FACE_INSET, pointsPerFace);
        }
        return points;
    }

    private static void addFaceGrid(
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
                if (fixedAxis == 0) {
                    points.add(new Vec3(fixedValue, firstValue, secondValue));
                } else if (fixedAxis == 1) {
                    points.add(new Vec3(firstValue, fixedValue, secondValue));
                } else {
                    points.add(new Vec3(firstValue, secondValue, fixedValue));
                }
            }
        }
    }

    private static Vec3 scanVerticalAimPoint(Vec3 eyePos, AxisAlignedBB box, double preferredY,
                                             Entity target, double range, boolean throughWalls, boolean throughEntities) {
        double centerX = (box.minX + box.maxX) * 0.5D;
        double centerZ = (box.minZ + box.maxZ) * 0.5D;
        double faceX;
        double faceZ;
        if (Math.abs(eyePos.xCoord - centerX) >= Math.abs(eyePos.zCoord - centerZ)) {
            faceX = eyePos.xCoord >= centerX ? box.maxX : box.minX;
            faceZ = MathHelper.clamp_double(eyePos.zCoord, box.minZ, box.maxZ);
        } else {
            faceX = MathHelper.clamp_double(eyePos.xCoord, box.minX, box.maxX);
            faceZ = eyePos.zCoord >= centerZ ? box.maxZ : box.minZ;
        }

        double step = (box.maxY - box.minY) / AIM_VERTICAL_SCAN_STEPS;
        for (int i = 1; i <= AIM_VERTICAL_SCAN_STEPS; i++) {
            double upY = preferredY + step * i;
            if (upY <= box.maxY) {
                Vec3 up = new Vec3(faceX, upY, faceZ);
                if (canAimAtPoint(eyePos, up, target, box, range, throughWalls, throughEntities)) {
                    return up;
                }
            }

            double downY = preferredY - step * i;
            if (downY >= box.minY) {
                Vec3 down = new Vec3(faceX, downY, faceZ);
                if (canAimAtPoint(eyePos, down, target, box, range, throughWalls, throughEntities)) {
                    return down;
                }
            }
        }
        return null;
    }

    public static float[] getRotationsTo(double targetX, double targetY, double targetZ, float currentYaw, float currentPitch) {
        return RotationUtil.getRotations(targetX, targetY, targetZ, currentYaw, currentPitch, 180.0f, 0.0f);
    }

    public static float[] getRotations(BlockPos blockPos) {
        return getRotations(blockPos.getX() + 0.5, blockPos.getY() + 0.5, blockPos.getZ() + 0.5, mc.thePlayer.posX, mc.thePlayer.posY + (double) mc.thePlayer.getEyeHeight(), mc.thePlayer.posZ);
    }

    public static float[] getRotations(double rotX, double rotY, double rotZ, double startX, double startY, double startZ) {
        double x = rotX - startX;
        double y = rotY - startY;
        double z = rotZ - startZ;
        double dist = MathHelper.sqrt_double(x * x + z * z);
        float yaw = (float) (Math.atan2(z, x) * 180.0 / Math.PI) - 90.0F;
        float pitch = (float) (-(Math.atan2(y, dist) * 180.0 / Math.PI));
        return new float[]{yaw, pitch};
    }

    public static float[] getRotations(double targetX, double targetY, double targetZ, float currentYaw, float currentPitch, float maxAngle, float smoothFactor) {
        double horizontalDistance = Math.sqrt(targetX * targetX + targetZ * targetZ);
        float yawDelta = MathHelper.wrapAngleTo180_float((float) (Math.atan2(targetZ, targetX) * 180.0 / Math.PI) - 90.0f - currentYaw);
        float pitchDelta = MathHelper.wrapAngleTo180_float((float) (-Math.atan2(targetY, horizontalDistance) * 180.0 / Math.PI) - currentPitch);
        yawDelta = Math.abs(yawDelta) <= 1.0f ? 0.0f : RotationUtil.smoothAngle(RotationUtil.clampAngle(yawDelta, maxAngle), smoothFactor);
        pitchDelta = Math.abs(pitchDelta) <= 1.0f ? 0.0f : RotationUtil.smoothAngle(RotationUtil.clampAngle(pitchDelta, maxAngle), smoothFactor);
        return new float[]{RotationUtil.quantizeAngle(currentYaw + yawDelta), RotationUtil.quantizeAngle(currentPitch + pitchDelta)};
    }

    public static Vec3 getClosestPointOnBox(Vec3 point, AxisAlignedBB bb) {
        double x = MathHelper.clamp_double(point.xCoord, bb.minX, bb.maxX);
        double y = MathHelper.clamp_double(point.yCoord, bb.minY, bb.maxY);
        double z = MathHelper.clamp_double(point.zCoord, bb.minZ, bb.maxZ);
        return new Vec3(x, y, z);
    }

    public static double distanceToEntity(Entity entity) {
        float borderSize = entity.getCollisionBorderSize();
        AxisAlignedBB boundingBox = entity.getEntityBoundingBox().expand(borderSize, borderSize, borderSize);
        return RotationUtil.distanceToBox(boundingBox);
    }

    public static double distanceToBox(Entity entity, Vec3 point) {
        float borderSize = entity.getCollisionBorderSize();
        return RotationUtil.getDistanceToBox(entity.getEntityBoundingBox().expand(borderSize, borderSize, borderSize), point);
    }

    public static double distanceToBox(AxisAlignedBB boundingBox) {
        return RotationUtil.getDistanceToBox(boundingBox, RotationUtil.mc.thePlayer.getPositionEyes(1.0f));
    }

    public static double getDistanceToBox(AxisAlignedBB bb, Vec3 point) {
        if (bb.isVecInside(point)) {
            return 0.0;
        }
        Vec3 closestPoint = getClosestPointOnBox(point, bb);
        return point.distanceTo(closestPoint);
    }

    public static float angleToEntity(Entity entity) {
        Vec3 eyePos = RotationUtil.mc.thePlayer.getPositionEyes(1.0f);
        float borderSize = entity.getCollisionBorderSize();
        AxisAlignedBB boundingBox = entity.getEntityBoundingBox().expand(borderSize, borderSize, borderSize);
        if (boundingBox.isVecInside(eyePos)) {
            return 0.0f;
        }
        double deltaX = entity.posX - eyePos.xCoord;
        double deltaZ = entity.posZ - eyePos.zCoord;
        return Math.abs(MathHelper.wrapAngleTo180_float((float) (Math.atan2(deltaZ, deltaX) * 180.0 / Math.PI) - 90.0f - RotationUtil.mc.thePlayer.rotationYaw)) * 2.0f;
    }

    public static float getYawBetween(double x1, double z1, double x2, double z2) {
        return MathHelper.wrapAngleTo180_float((float) (Math.atan2(z2 - z1, x2 - x1) * 180.0 / Math.PI) - 90.0f - RotationUtil.mc.thePlayer.rotationYaw);
    }

    public static class RotationVec {
        public float x;
        public float y;

        public RotationVec(float x, float y) {
            this.x = x;
            this.y = y;
        }

        public RotationVec add(float x, float y) {
            return new RotationVec(this.x + x, this.y + y);
        }

        public float getX() {
            return this.x;
        }

        public void setX(float x) {
            this.x = x;
        }

        public float getY() {
            return this.y;
        }

        public void setY(float y) {
            this.y = y;
        }
    }
}
