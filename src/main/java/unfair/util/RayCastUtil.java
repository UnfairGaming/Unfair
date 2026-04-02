package unfair.util;

import com.google.common.base.Predicate;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.*;
import net.minecraft.util.MovingObjectPosition.MovingObjectType;

import java.util.Iterator;
import java.util.List;

public final class RayCastUtil {
    private static final Minecraft mc = Minecraft.getMinecraft();

    private static float wrapAngle(float angle) {
        angle %= 360.0F;
        if (angle >= 180.0F) {
            angle -= 360.0F;
        }

        if (angle < -180.0F) {
            angle += 360.0F;
        }

        return angle;
    }

    private static float wrappedDifference(float angle1, float angle2) {
        return Math.abs(wrapAngle(angle1 - angle2));
    }

    private static Vec3 getVectorForRotation(float pitch, float yaw) {
        float f = MathHelper.cos(-yaw * 0.017453292F - 3.1415927F);
        float f1 = MathHelper.sin(-yaw * 0.017453292F - 3.1415927F);
        float f2 = -MathHelper.cos(-pitch * 0.017453292F);
        float f3 = MathHelper.sin(-pitch * 0.017453292F);
        return new Vec3(f1 * f2, f3, f * f2);
    }

    public static RayCastUtil.RayCastResult rayCast(RotationUtil.RotationVec rotation, double distance) {
        return rayCast(rotation, distance, 0.0F);
    }

    public static boolean inView(Entity entity) {
        RotationUtil.RotationVec rotation = calculateRotationToEntity(entity);
        int renderDistance = 16 * mc.gameSettings.renderDistanceChunks;
        if (!((double) entity.getDistanceToEntity(mc.thePlayer) > 100.0D) && entity instanceof EntityPlayer) {
            AxisAlignedBB boundingBox = entity.getEntityBoundingBox();
            double var10000 = boundingBox.maxX - boundingBox.minX;

            for (double yOffset = 1.0D; yOffset >= -1.0D; yOffset -= 0.5D) {
                for (double xOffset = 1.0D; xOffset >= -1.0D; --xOffset) {
                    for (double zOffset = 1.0D; zOffset >= -1.0D; --zOffset) {
                        double scanX = entity.posX + (boundingBox.maxX - boundingBox.minX) * xOffset;
                        double scanY = entity.posY + (boundingBox.maxY - boundingBox.minY) * yOffset;
                        double scanZ = entity.posZ + (boundingBox.maxZ - boundingBox.minZ) * zOffset;
                        RotationUtil.RotationVec scanRotation = calculateRotationTo(scanX, scanY, scanZ);
                        RayCastUtil.RayCastResult result = rayCast(scanRotation, renderDistance, 0.2F);
                        if (result != null && result.typeOfHit == RayCastUtil.RayCastResult.Type.ENTITY) {
                            return true;
                        }
                    }
                }
            }

            return false;
        } else {
            RayCastUtil.RayCastResult result = rayCast(rotation, renderDistance, 0.3F);
            return result != null && result.typeOfHit == RayCastUtil.RayCastResult.Type.ENTITY;
        }
    }

    public static RayCastUtil.RayCastResult rayCast(RotationUtil.RotationVec rotation, double distance, float expandSize) {
        return rayCast(rotation, distance, expandSize, mc.thePlayer);
    }

    public static RayCastUtil.RayCastResult rayCast(RotationUtil.RotationVec rotation, double distance, float expandSize, Entity sourceEntity) {
        if (sourceEntity != null && mc.theWorld != null) {
            float partialTicks = 1.0F;
            MovingObjectPosition blockHit = rayTraceCustom(sourceEntity, rotation.x, rotation.y, distance);
            double maxDistance = distance;
            Vec3 eyePos = sourceEntity.getPositionEyes(partialTicks);
            if (blockHit != null && blockHit.typeOfHit == MovingObjectType.BLOCK) {
                maxDistance = blockHit.hitVec.distanceTo(eyePos);
            }

            Vec3 lookVec = getVectorForRotation(rotation.y, rotation.x);
            Vec3 endPos = eyePos.addVector(lookVec.xCoord * distance, lookVec.yCoord * distance, lookVec.zCoord * distance);
            Entity hitEntity = null;
            Vec3 hitVec = null;
            Predicate<Entity> predicate = Entity::canBeCollidedWith;
            List<Entity> entities = mc.theWorld.getEntitiesInAABBexcluding(sourceEntity, sourceEntity.getEntityBoundingBox().addCoord(lookVec.xCoord * distance, lookVec.yCoord * distance, lookVec.zCoord * distance).expand(1.0D, 1.0D, 1.0D), predicate);
            double currentDistance = maxDistance;
            Iterator var18 = entities.iterator();

            while (true) {
                if (!var18.hasNext()) {
                    if (hitEntity != null && (currentDistance < maxDistance || blockHit == null)) {
                        return new RayCastUtil.RayCastResult(hitEntity, hitVec);
                    }

                    if (blockHit != null) {
                        return new RayCastUtil.RayCastResult(blockHit.hitVec, blockHit.sideHit, blockHit.getBlockPos());
                    }
                    break;
                }

                Entity entity = (Entity) var18.next();
                float entityExpand = entity.getCollisionBorderSize() + expandSize;
                AxisAlignedBB expandedBB = entity.getEntityBoundingBox().expand(entityExpand, entityExpand, entityExpand);
                MovingObjectPosition entityHit = expandedBB.calculateIntercept(eyePos, endPos);
                if (expandedBB.isVecInside(eyePos)) {
                    if (currentDistance >= 0.0D) {
                        hitEntity = entity;
                        hitVec = entityHit == null ? eyePos : entityHit.hitVec;
                        currentDistance = 0.0D;
                    }
                } else if (entityHit != null) {
                    double distanceToHit = eyePos.distanceTo(entityHit.hitVec);
                    if (distanceToHit < currentDistance || currentDistance == 0.0D) {
                        hitEntity = entity;
                        hitVec = entityHit.hitVec;
                        currentDistance = distanceToHit;
                    }
                }
            }
        }

        return null;
    }

    private static MovingObjectPosition rayTraceCustom(Entity entity, float yaw, float pitch, double distance) {
        Vec3 eyePos = entity.getPositionEyes(1.0F);
        Vec3 lookVec = getVectorForRotation(pitch, yaw);
        Vec3 targetPos = eyePos.addVector(lookVec.xCoord * distance, lookVec.yCoord * distance, lookVec.zCoord * distance);
        return entity.worldObj.rayTraceBlocks(eyePos, targetPos);
    }

    public static boolean overBlock(RotationUtil.RotationVec rotation, EnumFacing side, BlockPos pos, boolean checkSide) {
        RayCastUtil.RayCastResult hit = rayCast(rotation, 4.5D);
        if (hit != null && hit.hitVec != null) {
            return hit.getBlockPos() != null && hit.getBlockPos().equals(pos) && (!checkSide || hit.sideHit == side);
        } else {
            return false;
        }
    }

    public static RotationUtil.RotationVec calculateRotationToEntity(Entity entity) {
        Vec3 eyePos = mc.thePlayer.getPositionEyes(1.0F);
        Vec3 entityPos = new Vec3(entity.posX, entity.posY + (double) entity.getEyeHeight(), entity.posZ);
        double deltaX = entityPos.xCoord - eyePos.xCoord;
        double deltaY = entityPos.yCoord - eyePos.yCoord;
        double deltaZ = entityPos.zCoord - eyePos.zCoord;
        double horizontalDist = Math.sqrt(deltaX * deltaX + deltaZ * deltaZ);
        float yaw = (float) (Math.atan2(deltaZ, deltaX) * 180.0D / 3.141592653589793D) - 90.0F;
        float pitch = (float) (-(Math.atan2(deltaY, horizontalDist) * 180.0D / 3.141592653589793D));
        return new RotationUtil.RotationVec(yaw, pitch);
    }

    private static RotationUtil.RotationVec calculateRotationTo(double x, double y, double z) {
        Vec3 eyePos = mc.thePlayer.getPositionEyes(1.0F);
        double deltaX = x - eyePos.xCoord;
        double deltaY = y - eyePos.yCoord;
        double deltaZ = z - eyePos.zCoord;
        double horizontalDist = Math.sqrt(deltaX * deltaX + deltaZ * deltaZ);
        float yaw = (float) (Math.atan2(deltaZ, deltaX) * 180.0D / 3.141592653589793D) - 90.0F;
        float pitch = (float) (-(Math.atan2(deltaY, horizontalDist) * 180.0D / 3.141592653589793D));
        return new RotationUtil.RotationVec(yaw, pitch);
    }

    private static float getYawToEntity(Entity entity) {
        Vec3 eyePos = mc.thePlayer.getPositionEyes(1.0F);
        Vec3 entityPos = new Vec3(entity.posX, entity.posY + (double) entity.getEyeHeight(), entity.posZ);
        double deltaX = entityPos.xCoord - eyePos.xCoord;
        double deltaZ = entityPos.zCoord - eyePos.zCoord;
        return (float) (Math.atan2(deltaZ, deltaX) * 180.0D / 3.141592653589793D) - 90.0F;
    }

    public static class RayCastResult {
        public RayCastUtil.RayCastResult.Type typeOfHit;
        public Vec3 hitVec;
        public Entity entityHit;
        public EnumFacing sideHit;
        private BlockPos blockPos;

        public RayCastResult(Vec3 hitVec, RayCastUtil.RayCastResult.Type type) {
            this.hitVec = hitVec;
            this.typeOfHit = type;
        }

        public RayCastResult(Entity entity, Vec3 hitVec) {
            this.entityHit = entity;
            this.hitVec = hitVec;
            this.typeOfHit = RayCastUtil.RayCastResult.Type.ENTITY;
        }

        public RayCastResult(Vec3 hitVec, EnumFacing sideHit, BlockPos blockPos) {
            this.hitVec = hitVec;
            this.sideHit = sideHit;
            this.blockPos = blockPos;
            this.typeOfHit = RayCastUtil.RayCastResult.Type.BLOCK;
        }

        public RayCastResult(Vec3 hitVec, EnumFacing sideHit, RayCastUtil.RayCastResult.Type type) {
            this.hitVec = hitVec;
            this.sideHit = sideHit;
            this.typeOfHit = type;
        }

        public BlockPos getBlockPos() {
            return this.blockPos;
        }

        public void setBlockPos(BlockPos blockPos) {
            this.blockPos = blockPos;
        }

        public enum Type {
            MISS,
            BLOCK,
            ENTITY
        }
    }
}
