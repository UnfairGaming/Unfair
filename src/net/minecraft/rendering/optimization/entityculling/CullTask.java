package net.minecraft.rendering.optimization.entityculling;

import net.minecraft.rendering.culling.OcclusionCullingInstance;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityArmorStand;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.BlockPos;
import net.minecraft.util.Vec3;
import net.minecraft.util.vector.Vec3d;

import java.util.ConcurrentModificationException;
import java.util.Iterator;
import java.util.Set;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class CullTask implements Runnable {

    private static final Logger LOGGER = LogManager.getLogger();
    public boolean requestCull = false;

    private final OcclusionCullingInstance culling;
    private final Set<String> unCullable;
    public long lastTime = 0;

    // reused preallocated vars
    private final Vec3d lastPos = new Vec3d(0, 0, 0);
    private final Vec3d aabbMin = new Vec3d(0, 0, 0);
    private final Vec3d aabbMax = new Vec3d(0, 0, 0);

    public CullTask(OcclusionCullingInstance culling, Set<String> unCullable) {
        this.culling = culling;
        this.unCullable = unCullable;
    }

    @Override
    public void run() {
        LOGGER.debug("[*] Culling task is running!");
        while (!Thread.currentThread().isInterrupted()) {
            try {
                Minecraft mc = Minecraft.getMinecraft();
                if (mc == null) {
                    Thread.sleep(100L);
                    continue;
                }
                int sleepDelay = 10;
                Thread.sleep(sleepDelay);

                if (mc.theWorld != null && mc.thePlayer != null && mc.thePlayer.ticksExisted > 10 && mc.getRenderViewEntity() != null) {
                    Vec3 cameraMC = getCameraPos();
                    if (requestCull || !(cameraMC.xCoord == lastPos.x && cameraMC.yCoord == lastPos.y && cameraMC.zCoord == lastPos.z)) {
                        long start = System.currentTimeMillis();
                        requestCull = false;
                        lastPos.set(cameraMC.xCoord, cameraMC.yCoord, cameraMC.zCoord);
                        Vec3d camera = lastPos;
                        culling.resetCache();
                        boolean noCulling = mc.thePlayer.isSpectator() || mc.gameSettings.thirdPersonView != 0;
                        Iterator<TileEntity> iterator = mc.theWorld.loadedTileEntityList.iterator();
                        TileEntity entry;
                        while(iterator.hasNext()) {
                            try {
                                entry = iterator.next();
                            }catch(NullPointerException | ConcurrentModificationException ex) {
                                break; // We are not synced to the main thread, so NPE's/CME are allowed here and way less
                                // overhead probably than trying to sync stuff up for no really good reason
                            }
                            if(unCullable.contains(entry.getBlockType().getUnlocalizedName())) {
                                continue;
                            }
                            Cullable cullable = entry;
                            if (!cullable.isForcedVisible()) {
                                if (noCulling) {
                                    cullable.setCulled(false);
                                    continue;
                                }
                                BlockPos pos = entry.getPos();
                                if(pos.distanceSq(cameraMC.xCoord, cameraMC.yCoord, cameraMC.zCoord) < 64*64) { // 64 is the fixed max tile view distance
                                    aabbMin.set(pos.getX(), pos.getY(), pos.getZ());
                                    aabbMax.set(pos.getX()+1d, pos.getY()+1d, pos.getZ()+1d);
                                    boolean visible = culling.isAABBVisible(aabbMin, aabbMax, camera);
                                    cullable.setCulled(!visible);
                                }

                            }
                        }
                        Entity entity;
                        Iterator<Entity> iterable = mc.theWorld.getLoadedEntityList().iterator();
                        while (iterable.hasNext()) {
                            try {
                                entity = iterable.next();
                            } catch (NullPointerException | ConcurrentModificationException ex) {
                                break; // We are not synced to the main thread, so NPE's/CME are allowed here and way less
                                // overhead probably than trying to sync stuff up for no really good reason
                            }
                            if(entity == null) {
                                continue; // Defensive guard for unsynchronized entity list snapshots.
                            }
                            if (!entity.isForcedVisible()) {
                                if (noCulling || isSkippableArmorstand(entity)) {
                                    entity.setCulled(false);
                                    continue;
                                }
                                if(entity.getPositionVector().squareDistanceTo(cameraMC) > 128 * 128) {
                                    entity.setCulled(false); // If your entity view distance is larger than tracingDistance just render it
                                    continue;
                                }
                                AxisAlignedBB boundingBox = entity.getEntityBoundingBox();
                                int hitboxLimit = 50;
                                if(boundingBox.maxX - boundingBox.minX > hitboxLimit || boundingBox.maxY - boundingBox.minY > hitboxLimit || boundingBox.maxZ - boundingBox.minZ > hitboxLimit) {
                                    entity.setCulled(false); // To big to bother to cull
                                    continue;
                                }
                                aabbMin.set(boundingBox.minX, boundingBox.minY, boundingBox.minZ);
                                aabbMax.set(boundingBox.maxX, boundingBox.maxY, boundingBox.maxZ);
                                boolean visible = culling.isAABBVisible(aabbMin, aabbMax, camera);
                                entity.setCulled(!visible);
                            }
                        }
                        lastTime = (System.currentTimeMillis()-start);
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        LOGGER.debug("[*] Shutting down culling task!");
    }

    // 1.8 doesnt know where the heck the camera is... what?!?
    private Vec3 getCameraPos() {
        Minecraft mc = Minecraft.getMinecraft();
        return mc.getRenderViewEntity().getPositionEyes(mc.timer.renderPartialTicks);
    }

    private boolean isSkippableArmorstand(Entity entity) {
        return entity instanceof EntityArmorStand && ((EntityArmorStand) entity).hasMarker();
    }
}
