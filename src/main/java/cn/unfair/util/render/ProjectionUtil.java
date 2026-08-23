package cn.unfair.util.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.ActiveRenderInfo;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.entity.Entity;
import net.minecraft.util.AxisAlignedBB;
import org.lwjgl.opengl.Display;
import org.lwjgl.util.glu.GLU;

import java.util.Arrays;
import java.util.List;

public final class ProjectionUtil {
    private static final Minecraft mc = Minecraft.getMinecraft();

    private ProjectionUtil() {
    }

    public static Projection projectEntity(Entity entity) {
        if (entity == null) {
            return null;
        }

        RenderManager renderManager = mc.getRenderManager();
        float partialTicks = mc.timer.renderPartialTicks;
        double x = entity.lastTickPosX + (entity.posX - entity.lastTickPosX) * partialTicks - renderManager.getRenderPosX();
        double y = entity.lastTickPosY + (entity.posY - entity.lastTickPosY) * partialTicks - renderManager.getRenderPosY();
        double z = entity.lastTickPosZ + (entity.posZ - entity.lastTickPosZ) * partialTicks - renderManager.getRenderPosZ();
        double width = (entity.width + 0.14D) / 2.0D;
        double height = entity.height + (entity.isSneaking() ? -0.1D : 0.2D) + 0.01D;
        AxisAlignedBB aabb = new AxisAlignedBB(x - width, y, z - width, x + width, y + height, z + width);
        List<double[]> vectors = Arrays.asList(
                new double[]{aabb.minX, aabb.minY, aabb.minZ},
                new double[]{aabb.minX, aabb.maxY, aabb.minZ},
                new double[]{aabb.maxX, aabb.minY, aabb.minZ},
                new double[]{aabb.maxX, aabb.maxY, aabb.minZ},
                new double[]{aabb.minX, aabb.minY, aabb.maxZ},
                new double[]{aabb.minX, aabb.maxY, aabb.maxZ},
                new double[]{aabb.maxX, aabb.minY, aabb.maxZ},
                new double[]{aabb.maxX, aabb.maxY, aabb.maxZ}
        );

        int scaleFactor = new ScaledResolution(mc).getScaleFactor();
        Projection projection = null;
        for (double[] vector : vectors) {
            Point projected = projectPoint(scaleFactor, vector[0], vector[1], vector[2]);
            if (projected == null || projected.z < 0.0F || projected.z >= 1.0F) {
                continue;
            }

            if (projection == null) {
                projection = new Projection(projected.x, projected.y, projected.x, projected.y);
            } else {
                projection = new Projection(
                        Math.min(projected.x, projection.left),
                        Math.min(projected.y, projection.top),
                        Math.max(projected.x, projection.right),
                        Math.max(projected.y, projection.bottom)
                );
            }
        }

        return projection;
    }

    private static Point projectPoint(int scaleFactor, double x, double y, double z) {
        if (!GLU.gluProject(
                (float) x,
                (float) y,
                (float) z,
                ActiveRenderInfo.getModelView(),
                ActiveRenderInfo.getProjection(),
                ActiveRenderInfo.getViewport(),
                ActiveRenderInfo.getObjectCoords()
        )) {
            return null;
        }

        java.nio.FloatBuffer objectCoords = ActiveRenderInfo.getObjectCoords();
        return new Point(
                objectCoords.get(0) / scaleFactor,
                (Display.getHeight() - objectCoords.get(1)) / scaleFactor,
                objectCoords.get(2)
        );
    }

    public record Projection(float left, float top, float right, float bottom) {
        public float width() {
            return this.right - this.left;
        }

        public float height() {
            return this.bottom - this.top;
        }

        public float centerX() {
            return (this.left + this.right) / 2.0F;
        }

        public float centerY() {
            return (this.top + this.bottom) / 2.0F;
        }
    }

    private record Point(float x, float y, float z) {
    }
}
