package cn.unfair.util.rotation.advanced;

import cn.unfair.util.client.RandomUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MathHelper;
import net.minecraft.util.Vec3;

import java.util.ArrayList;
import java.util.List;

public final class AdvancedRotationMath {
    private static final Minecraft mc = Minecraft.getMinecraft();

    private AdvancedRotationMath() {
    }

    public static float getAngleDifference(float a, float b) {
        return MathHelper.wrapAngleTo180_float(a - b);
    }

    public static float[] getRotations(Vec3 vec, Vec3 playerPos) {
        double x = vec.xCoord - playerPos.xCoord;
        double y = vec.yCoord - playerPos.yCoord;
        double z = vec.zCoord - playerPos.zCoord;
        double dist = MathHelper.sqrt_double(x * x + z * z);
        float yaw = (float) (Math.atan2(z, x) * 180.0 / Math.PI) - 90.0F;
        float pitch = (float) (-(Math.atan2(y, dist) * 180.0 / Math.PI));
        return new float[]{yaw, pitch};
    }

    public static float[] getRotations(Vec3 vec) {
        return getRotations(vec, mc.thePlayer.getPositionEyes(1.0F));
    }

    public static float getRotationDifference(float[] a, float[] b) {
        return (float) Math.hypot(Math.abs(getAngleDifference(a[0], b[0])), Math.abs(a[1] - b[1]));
    }

    public static AxisAlignedBB getHitbox(Entity entity) {
        float border = entity.getCollisionBorderSize();
        return entity.getEntityBoundingBox().expand(border, border, border);
    }

    public static Vec3 getPrevPositionVector(Entity entity) {
        return new Vec3(entity.prevPosX, entity.prevPosY, entity.prevPosZ);
    }

    public static Vec3 getMoveDeltaVector(Entity entity) {
        return new Vec3(entity.posX - entity.prevPosX, entity.posY - entity.prevPosY, entity.posZ - entity.prevPosZ);
    }

    public static Vec3 multiply(Vec3 vec, double value) {
        return new Vec3(vec.xCoord * value, vec.yCoord * value, vec.zCoord * value);
    }

    public static Vec3 flat(Vec3 vec) {
        return new Vec3(vec.xCoord, 0.0D, vec.zCoord);
    }

    public static Vec3 interpolate(Vec3 old, Vec3 now, float partialTicks) {
        if (old == null) {
            return now;
        }
        if (now == null) {
            return old;
        }
        return new Vec3(
                old.xCoord + (now.xCoord - old.xCoord) * partialTicks,
                old.yCoord + (now.yCoord - old.yCoord) * partialTicks,
                old.zCoord + (now.zCoord - old.zCoord) * partialTicks
        );
    }

    public static Vec3 interpolate(Vec3 old, Vec3 now, double amount) {
        if (old == null) {
            return now;
        }
        return new Vec3(
                old.xCoord + (now.xCoord - old.xCoord) * amount,
                old.yCoord + (now.yCoord - old.yCoord) * amount,
                old.zCoord + (now.zCoord - old.zCoord) * amount
        );
    }

    public static float interpolate(float old, float now, float amount) {
        return old + (now - old) * amount;
    }

    public static double interpolate(double old, double now, double amount) {
        return old + (now - old) * amount;
    }

    public static float randomizeAround(float range) {
        return RandomUtil.nextFloat(-range, range);
    }

    public static double getSpeedPosBased(Entity entity) {
        return Math.hypot(entity.posX - entity.prevPosX, entity.posZ - entity.prevPosZ);
    }

    public static double getDistanceToEntityBox(Entity entity) {
        Vec3 eyes = mc.thePlayer.getPositionEyes(1.0F);
        AxisAlignedBB bb = getHitbox(entity);
        if (bb.isVecInside(eyes)) {
            return 0.0D;
        }
        double x = MathHelper.clamp_double(eyes.xCoord, bb.minX, bb.maxX);
        double y = MathHelper.clamp_double(eyes.yCoord, bb.minY, bb.maxY);
        double z = MathHelper.clamp_double(eyes.zCoord, bb.minZ, bb.maxZ);
        return eyes.distanceTo(new Vec3(x, y, z));
    }

    public static Vec3 getCenter(AxisAlignedBB bb) {
        return new Vec3((bb.minX + bb.maxX) / 2.0D, (bb.minY + bb.maxY) / 2.0D, (bb.minZ + bb.maxZ) / 2.0D);
    }

    public static List<Vec3> getVertices(AxisAlignedBB bb) {
        ArrayList<Vec3> vertices = new ArrayList<>(8);
        vertices.add(new Vec3(bb.minX, bb.minY, bb.minZ));
        vertices.add(new Vec3(bb.minX, bb.minY, bb.maxZ));
        vertices.add(new Vec3(bb.minX, bb.maxY, bb.minZ));
        vertices.add(new Vec3(bb.minX, bb.maxY, bb.maxZ));
        vertices.add(new Vec3(bb.maxX, bb.minY, bb.minZ));
        vertices.add(new Vec3(bb.maxX, bb.minY, bb.maxZ));
        vertices.add(new Vec3(bb.maxX, bb.maxY, bb.minZ));
        vertices.add(new Vec3(bb.maxX, bb.maxY, bb.maxZ));
        return vertices;
    }

    public static boolean canPosBeSeen(Vec3 vec) {
        return mc.theWorld.rayTraceBlocks(mc.thePlayer.getPositionEyes(1.0F), vec, false, false, false) == null;
    }
}
