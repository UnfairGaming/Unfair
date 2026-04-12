package unfair.module.modules.render;

import net.minecraft.block.Block;
import net.minecraft.block.BlockBed;
import net.minecraft.block.BlockBed.EnumPartType;
import net.minecraft.block.BlockStainedGlass;
import net.minecraft.block.BlockGlass;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GLAllocation;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.init.Blocks;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.Vec3;
import org.lwjgl.opengl.GL11;
import org.lwjgl.util.glu.GLU;
import unfair.Unfair;
import unfair.event.EventTarget;
import unfair.events.Render2DEvent;
import unfair.mixin.IAccessorEntityRenderer;
import unfair.mixin.IAccessorRenderManager;
import unfair.module.Module;
import unfair.util.RenderUtil;
import unfair.util.shader.RoundedUtils;

import javax.vecmath.Vector4d;
import java.awt.*;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.*;
import java.util.List;

public class BedPlates extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();

    public BedPlates() {
        super("BedPlates", false);
    }

    @EventTarget
    public void onRender2D(Render2DEvent event) {
        if (!this.isEnabled() || mc.thePlayer == null || mc.theWorld == null) return;

        BedESP bedESP = (BedESP) Unfair.moduleManager.modules.get(BedESP.class);
        if (bedESP == null) return;

        ScaledResolution sr = new ScaledResolution(mc);
        double scaleFactor = sr.getScaleFactor();

        for (BlockPos bedPos : bedESP.beds) {
            IBlockState state = mc.theWorld.getBlockState(bedPos);
            if (!(state.getBlock() instanceof BlockBed)) continue;
            if (state.getValue(BlockBed.PART) != EnumPartType.HEAD) continue;

            BlockPos footPos = bedPos.offset(state.getValue(BlockBed.FACING).getOpposite());
            IBlockState footState = mc.theWorld.getBlockState(footPos);
            if (!(footState.getBlock() instanceof BlockBed)) continue;
            if (footState.getValue(BlockBed.PART) != EnumPartType.FOOT) continue;

            double minX = Math.min(bedPos.getX(), footPos.getX());
            double minY = bedPos.getY();
            double minZ = Math.min(bedPos.getZ(), footPos.getZ());
            double maxX = Math.max(bedPos.getX(), footPos.getX()) + 1.0;
            double maxY = bedPos.getY() + 1.0;
            double maxZ = Math.max(bedPos.getZ(), footPos.getZ()) + 1.0;

            ((IAccessorEntityRenderer) mc.entityRenderer).callSetupCameraTransform(event.getPartialTicks(), 0);

            double renderPosX = ((IAccessorRenderManager) mc.getRenderManager()).getRenderPosX();
            double renderPosY = ((IAccessorRenderManager) mc.getRenderManager()).getRenderPosY();
            double renderPosZ = ((IAccessorRenderManager) mc.getRenderManager()).getRenderPosZ();

            Vector4d pos = projectToScreen(minX, minY, minZ, maxX, maxY, maxZ, scaleFactor, renderPosX, renderPosY, renderPosZ);
            mc.entityRenderer.setupOverlayRendering();

            if (pos == null) continue;

            float screenX = (float) ((pos.x + pos.z) / 2.0);
            float screenY = (float) pos.y - 30;

            List<BlockEntry> blocks = collectProtectionBlocks(bedPos, footPos);
            if (blocks.isEmpty()) continue;

            blocks.sort((a, b) -> Float.compare(b.hardness, a.hardness));

            float itemSize = 16;
            float padding = 2;
            float bgRadius = 4;
            float totalWidth = blocks.size() * (itemSize + padding) + padding;
            float bgHeight = itemSize + padding * 2;

            float bgX = screenX - totalWidth / 2;
            float bgY = screenY - bgHeight / 2;

            GlStateManager.pushMatrix();
            GlStateManager.pushAttrib();

            GlStateManager.enableBlend();
            GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            GlStateManager.enableTexture2D();
            GlStateManager.enableAlpha();
            GlStateManager.disableDepth();
            GlStateManager.disableLighting();
            GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);

            RoundedUtils.drawRound(bgX, bgY, totalWidth, bgHeight, bgRadius, new Color(0, 0, 0, 100));

            GlStateManager.enableTexture2D();
            GlStateManager.enableBlend();
            GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            GlStateManager.enableAlpha();
            GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);

            float itemX = bgX + padding;
            float itemY = bgY + padding;

            for (BlockEntry entry : blocks) {
                ItemStack stack = new ItemStack(Item.getItemFromBlock(entry.block));
                RenderUtil.renderItemInGUI(stack, (int) itemX, (int) itemY);
                itemX += itemSize + padding;
            }

            GlStateManager.popAttrib();
            GlStateManager.popMatrix();
        }
    }

    private Vector4d projectToScreen(double minX, double minY, double minZ, double maxX, double maxY, double maxZ, double screenScale, double renderPosX, double renderPosY, double renderPosZ) {
        IntBuffer viewport = GLAllocation.createDirectIntBuffer(16);
        FloatBuffer modelView = GLAllocation.createDirectFloatBuffer(16);
        FloatBuffer projection = GLAllocation.createDirectFloatBuffer(16);
        FloatBuffer coords = GLAllocation.createDirectFloatBuffer(4);

        GL11.glGetFloat(GL11.GL_MODELVIEW_MATRIX, modelView);
        GL11.glGetFloat(GL11.GL_PROJECTION_MATRIX, projection);
        GL11.glGetInteger(GL11.GL_VIEWPORT, viewport);

        Vector4d result = null;

        for (double[] corner : new double[][]{
                {minX, minY, minZ},
                {minX, maxY, minZ},
                {maxX, minY, minZ},
                {maxX, maxY, minZ},
                {minX, minY, maxZ},
                {minX, maxY, maxZ},
                {maxX, minY, maxZ},
                {maxX, maxY, maxZ}
        }) {
            float x = (float) (corner[0] - renderPosX);
            float y = (float) (corner[1] - renderPosY);
            float z = (float) (corner[2] - renderPosZ);

            if (!GLU.gluProject(x, y, z, modelView, projection, viewport, coords)) continue;

            double screenX = coords.get(0) / screenScale;
            double screenY = (mc.displayHeight - coords.get(1)) / screenScale;
            double depth = coords.get(2);

            if (depth < 0.0 || depth >= 1.0) continue;

            if (result == null) {
                result = new Vector4d(screenX, screenY, screenX, screenY);
            }
            result.x = Math.min(screenX, result.x);
            result.y = Math.min(screenY, result.y);
            result.z = Math.max(screenX, result.z);
            result.w = Math.max(screenY, result.w);
        }

        return result;
    }

    private List<BlockEntry> collectProtectionBlocks(BlockPos head, BlockPos foot) {
        Map<Block, BlockEntry> blockMap = new LinkedHashMap<>();

        int centerX = (head.getX() + foot.getX()) / 2;
        int centerY = head.getY();
        int centerZ = (head.getZ() + foot.getZ()) / 2;

        for (int dx = -7; dx <= 7; dx++) {
            for (int dy = 0; dy <= 7; dy++) {
                for (int dz = -7; dz <= 7; dz++) {
                    BlockPos pos = new BlockPos(centerX + dx, centerY + dy, centerZ + dz);
                    Block block = mc.theWorld.getBlockState(pos).getBlock();

                    if (block == Blocks.air || block == Blocks.bedrock || block instanceof BlockBed) continue;

                    if (block instanceof BlockStainedGlass) {
                        block = Blocks.glass;
                    }

                    if (blockMap.containsKey(block)) continue;

                    float hardness = block.getBlockHardness(mc.theWorld, pos);
                    if (hardness < 0) hardness = Float.MAX_VALUE;

                    blockMap.put(block, new BlockEntry(block, hardness));
                }
            }
        }

        return new ArrayList<>(blockMap.values());
    }

    private static class BlockEntry {
        final Block block;
        final float hardness;

        BlockEntry(Block block, float hardness) {
            this.block = block;
            this.hardness = hardness;
        }
    }
}
