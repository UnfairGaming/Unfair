package cn.unfair.util.rotationadvanced;

import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.Vec3;

import java.util.ArrayList;
import java.util.List;

public final class AdvancedPointFinder {
    public static final List<Vec3> hitboxPoints = new ArrayList<>();
    public static final List<Vec3> invalidHitboxPoints = new ArrayList<>();
    public static final List<Vec3> allHitboxPoints = new ArrayList<>();
    public static final int POINT_COUNT = 2048;
    private static double lastCbrt;
    private static double lastPointCount;

    private AdvancedPointFinder() {
    }

    public static void findPoints(AxisAlignedBB bb, int pointCount) {
        hitboxPoints.clear();
        invalidHitboxPoints.clear();
        allHitboxPoints.clear();

        double cbrt = pointCount == lastPointCount ? lastCbrt : Math.cbrt(pointCount);
        lastPointCount = pointCount;
        lastCbrt = cbrt;

        double width = bb.maxX - bb.minX;
        double height = bb.maxY - bb.minY;
        double depth = bb.maxZ - bb.minZ;
        double total = width + height + depth;
        int stepsX = Math.max(2, (int) (cbrt * (width / total) * 3));
        int stepsY = Math.max(2, (int) (cbrt * (height / total) * 3));
        int stepsZ = Math.max(2, (int) (cbrt * (depth / total) * 3));

        double stepX = width / (stepsX - 1);
        double stepY = height / (stepsY - 1);
        double stepZ = depth / (stepsZ - 1);

        for (int i = 0; i < stepsX; i++) {
            for (int j = 0; j < stepsY; j++) {
                double x = bb.minX + stepX * i;
                double y = bb.minY + stepY * j;
                add(new Vec3(x, y, bb.minZ));
                add(new Vec3(x, y, bb.maxZ));
            }
        }

        for (int i = 0; i < stepsX; i++) {
            for (int k = 0; k < stepsZ; k++) {
                double x = bb.minX + stepX * i;
                double z = bb.minZ + stepZ * k;
                add(new Vec3(x, bb.minY, z));
                add(new Vec3(x, bb.maxY, z));
            }
        }

        for (int j = 0; j < stepsY; j++) {
            for (int k = 0; k < stepsZ; k++) {
                double y = bb.minY + stepY * j;
                double z = bb.minZ + stepZ * k;
                add(new Vec3(bb.minX, y, z));
                add(new Vec3(bb.maxX, y, z));
            }
        }
    }

    private static void add(Vec3 point) {
        hitboxPoints.add(point);
        allHitboxPoints.add(point);
    }
}
