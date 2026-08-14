package cn.unfair.module.modules.render;

import cn.unfair.event.EventTarget;
import cn.unfair.events.Render3DEvent;
import cn.unfair.module.Module;
import cn.unfair.property.properties.*;
import cn.unfair.util.ColorUtil;
import cn.unfair.util.RenderUtil;
import net.minecraft.block.Block;
import net.minecraft.block.BlockStairs;
import net.minecraft.block.material.Material;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.MovingObjectPosition;
import org.lwjgl.opengl.GL11;

import java.awt.*;
import java.util.Arrays;
import java.util.List;

public class BlockOverlay extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private static final double PADDING = 0.0020000000949949026;
    private final List<Block> plants = Arrays.asList(
            Blocks.deadbush,
            Blocks.double_plant,
            Blocks.red_flower,
            Blocks.tallgrass,
            Blocks.yellow_flower
    );

    public final ModeProperty renderMode = new ModeProperty("Render Mode", 1, new String[]{"Hidden", "Vanilla", "Side", "Full"});
    public final BooleanProperty persistence = new BooleanProperty("Persistence", false);
    public final BooleanProperty depthless = new BooleanProperty("Depthless", false);
    public final BooleanProperty barriers = new BooleanProperty("Barriers", false);
    public final BooleanProperty hidePlants = new BooleanProperty("Hide Plants", false);
    public final FloatProperty thickness = new FloatProperty("Thickness", 2.0F, 1.0F, 10.0F);

    public final BooleanProperty overlayVisible = new BooleanProperty("Overlay Visible", true);
    public final ModeProperty overlayColorMode = new ModeProperty("Overlay Color Mode", 0, new String[]{"Static", "Gradient", "Fade", "Chroma"});
    public final ColorProperty overlayStaticColor = new ColorProperty("Overlay Static Color", Color.WHITE.getRGB(), () -> this.overlayColorMode.getValue() == 0);
    public final PercentProperty overlayStaticOpacity = new PercentProperty("Overlay Static Opacity", 100, () -> this.overlayColorMode.getValue() == 0);
    public final ColorProperty overlayGradientStartColor = new ColorProperty("Overlay Gradient Start Color", Color.WHITE.getRGB(), () -> this.overlayColorMode.getValue() == 1);
    public final PercentProperty overlayGradientStartOpacity = new PercentProperty("Overlay Gradient Start Opacity", 100, () -> this.overlayColorMode.getValue() == 1);
    public final ColorProperty overlayGradientEndColor = new ColorProperty("Overlay Gradient End Color", Color.WHITE.getRGB(), () -> this.overlayColorMode.getValue() == 1);
    public final PercentProperty overlayGradientEndOpacity = new PercentProperty("Overlay Gradient End Opacity", 100, () -> this.overlayColorMode.getValue() == 1);
    public final ColorProperty overlayFadeStartColor = new ColorProperty("Overlay Fade Start Color", Color.WHITE.getRGB(), () -> this.overlayColorMode.getValue() == 2);
    public final PercentProperty overlayFadeStartOpacity = new PercentProperty("Overlay Fade Start Opacity", 100, () -> this.overlayColorMode.getValue() == 2);
    public final ColorProperty overlayFadeEndColor = new ColorProperty("Overlay Fade End Color", Color.WHITE.getRGB(), () -> this.overlayColorMode.getValue() == 2);
    public final PercentProperty overlayFadeEndOpacity = new PercentProperty("Overlay Fade End Opacity", 100, () -> this.overlayColorMode.getValue() == 2);
    public final FloatProperty overlayFadeSpeed = new FloatProperty("Overlay Fade Speed", 5.5F, 1.0F, 10.0F, () -> this.overlayColorMode.getValue() == 2);
    public final PercentProperty overlayChromaOpacity = new PercentProperty("Overlay Chroma Opacity", 100, 7, 100, () -> this.overlayColorMode.getValue() == 3);
    public final FloatProperty overlayChromaSpeed = new FloatProperty("Overlay Chroma Speed", 5.5F, 1.0F, 10.0F, () -> this.overlayColorMode.getValue() == 3);

    public final BooleanProperty outlineVisible = new BooleanProperty("Outline Visible", true);
    public final ModeProperty outlineColorMode = new ModeProperty("Outline Color Mode", 0, new String[]{"Static", "Gradient", "Fade", "Chroma"});
    public final ColorProperty outlineStaticColor = new ColorProperty("Outline Static Color", Color.BLACK.getRGB(), () -> this.outlineColorMode.getValue() == 0);
    public final PercentProperty outlineStaticOpacity = new PercentProperty("Outline Static Opacity", 100, () -> this.outlineColorMode.getValue() == 0);
    public final ColorProperty outlineGradientStartColor = new ColorProperty("Outline Gradient Start Color", Color.BLACK.getRGB(), () -> this.outlineColorMode.getValue() == 1);
    public final PercentProperty outlineGradientStartOpacity = new PercentProperty("Outline Gradient Start Opacity", 100, () -> this.outlineColorMode.getValue() == 1);
    public final ColorProperty outlineGradientEndColor = new ColorProperty("Outline Gradient End Color", Color.BLACK.getRGB(), () -> this.outlineColorMode.getValue() == 1);
    public final PercentProperty outlineGradientEndOpacity = new PercentProperty("Outline Gradient End Opacity", 100, () -> this.outlineColorMode.getValue() == 1);
    public final ColorProperty outlineFadeStartColor = new ColorProperty("Outline Fade Start Color", Color.BLACK.getRGB(), () -> this.outlineColorMode.getValue() == 2);
    public final PercentProperty outlineFadeStartOpacity = new PercentProperty("Outline Fade Start Opacity", 100, () -> this.outlineColorMode.getValue() == 2);
    public final ColorProperty outlineFadeEndColor = new ColorProperty("Outline Fade End Color", Color.BLACK.getRGB(), () -> this.outlineColorMode.getValue() == 2);
    public final PercentProperty outlineFadeEndOpacity = new PercentProperty("Outline Fade End Opacity", 100, () -> this.outlineColorMode.getValue() == 2);
    public final FloatProperty outlineFadeSpeed = new FloatProperty("Outline Fade Speed", 5.5F, 1.0F, 10.0F, () -> this.outlineColorMode.getValue() == 2);
    public final PercentProperty outlineChromaOpacity = new PercentProperty("Outline Chroma Opacity", 100, 7, 100, () -> this.outlineColorMode.getValue() == 3);
    public final FloatProperty outlineChromaSpeed = new FloatProperty("Outline Chroma Speed", 5.5F, 1.0F, 10.0F, () -> this.outlineColorMode.getValue() == 3);

    public BlockOverlay() {
        super("BlockOverlay", false, true);
    }

    @EventTarget
    public void onRender3D(Render3DEvent event) {
        Entity entity = mc.getRenderViewEntity();
        if (entity == null || mc.theWorld == null || mc.thePlayer == null) {
            return;
        }
        if (mc.gameSettings.hideGUI) {
            return;
        }
        int renderMode = this.renderMode.getValue();
        if (renderMode != 2 && renderMode != 3) {
            return;
        }
        Block block = this.getFocusedBlock();
        if (block == null) {
            return;
        }
        if (mc.playerController.getCurrentGameType().isAdventure()
                && !this.persistence.getValue()
                && !this.canRenderBlockOverlay()) {
            return;
        }
        this.renderBlockOverlay(block, entity, event.partialTicks());
    }

    public boolean shouldCancelVanillaSelectionBox() {
        int renderMode = this.renderMode.getValue();
        return this.isEnabled() && (renderMode == 0 || renderMode == 2 || renderMode == 3);
    }

    private void renderBlockOverlay(Block block, Entity entity, float partialTicks) {
        double entityX = entity.lastTickPosX + (entity.posX - entity.lastTickPosX) * partialTicks;
        double entityY = entity.lastTickPosY + (entity.posY - entity.lastTickPosY) * partialTicks;
        double entityZ = entity.lastTickPosZ + (entity.posZ - entity.lastTickPosZ) * partialTicks;
        boolean overlay = this.overlayVisible.getValue();
        boolean outline = this.outlineVisible.getValue();
        int overlayStartColor = this.getOverlayStartColor();
        int overlayEndColor = this.getOverlayEndColor();
        int outlineStartColor = this.getOutlineStartColor();
        int outlineEndColor = this.getOutlineEndColor();
        MovingObjectPosition mouseOver = mc.objectMouseOver;
        if (mouseOver == null || mouseOver.typeOfHit != MovingObjectPosition.MovingObjectType.BLOCK) {
            return;
        }
        BlockPos blockPos = mouseOver.getBlockPos();
        AxisAlignedBB boundingBox = block.getSelectedBoundingBox(mc.theWorld, blockPos).expand(PADDING, PADDING, PADDING);
        EnumFacing side = this.renderMode.getValue() == 2 ? mouseOver.sideHit : null;

        GlState glState = GlState.capture();
        GL11.glPushMatrix();
        try {
            GlStateManager.disableAlpha();
            GlStateManager.disableLighting();
            GlStateManager.enableBlend();
            GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
            GlStateManager.disableTexture2D();
            GlStateManager.depthMask(false);
            if (this.depthless.getValue()) {
                GlStateManager.disableDepth();
            }
            GL11.glEnable(2848);
            GL11.glHint(3154, 4354);
            if (outline) {
                GL11.glLineWidth(this.thickness.getValue());
            }
            GL11.glShadeModel(7425);
            if (block instanceof BlockStairs) {
                RenderUtil.drawStairs(blockPos, mc.theWorld.getBlockState(blockPos), boundingBox.expand(PADDING, PADDING, PADDING), side, entityX, entityY, entityZ, overlayStartColor, overlayEndColor, outlineStartColor, outlineEndColor, overlay, outline);
            } else {
                RenderUtil.drawBlock(boundingBox.offset(-entityX, -entityY, -entityZ), side, overlayStartColor, overlayEndColor, outlineStartColor, outlineEndColor, overlay, outline);
            }
        } finally {
            glState.restore();
            GL11.glPopMatrix();
            RenderUtil.setColor(Color.WHITE.getRGB());
        }
    }

    private Block getFocusedBlock() {
        MovingObjectPosition mouseOver = mc.objectMouseOver;
        if (mouseOver == null || mouseOver.typeOfHit != MovingObjectPosition.MovingObjectType.BLOCK) {
            return null;
        }
        BlockPos blockPos = mouseOver.getBlockPos();
        if (!mc.theWorld.getWorldBorder().contains(blockPos)) {
            return null;
        }
        Block block = mc.theWorld.getBlockState(blockPos).getBlock();
        if (block.getMaterial() == Material.air) {
            return null;
        }
        if (this.hidePlants.getValue() && this.plants.contains(block)) {
            return null;
        }
        if (!this.barriers.getValue() && block == Blocks.barrier) {
            return null;
        }
        block.setBlockBoundsBasedOnState(mc.theWorld, blockPos);
        return block;
    }

    private boolean canRenderBlockOverlay() {
        Entity entity = mc.getRenderViewEntity();
        boolean flag = entity instanceof EntityPlayer;
        if (flag && !((EntityPlayer) entity).capabilities.allowEdit) {
            ItemStack heldItem = ((EntityPlayer) entity).getCurrentEquippedItem();
            if (mc.objectMouseOver != null && mc.objectMouseOver.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK) {
                BlockPos blockPos = mc.objectMouseOver.getBlockPos();
                Block block = mc.theWorld.getBlockState(blockPos).getBlock();
                if (mc.playerController.isSpectator()) {
                    flag = block.hasTileEntity() && mc.theWorld.getTileEntity(blockPos) instanceof net.minecraft.inventory.IInventory;
                } else {
                    flag = heldItem != null && (heldItem.canDestroy(block) || heldItem.canPlaceOn(block));
                }
            }
        }
        return flag;
    }

    private int getOverlayStartColor() {
        return switch (this.overlayColorMode.getValue()) {
            case 0 -> ColorUtil.setAlpha(this.overlayStaticColor.getValue(), this.overlayStaticOpacity.getValue() / 100.0);
            case 1 -> ColorUtil.setAlpha(this.overlayGradientStartColor.getValue(), this.overlayGradientStartOpacity.getValue() / 100.0);
            case 2 -> this.fadeColor(this.overlayFadeStartColor, this.overlayFadeStartOpacity, this.overlayFadeEndColor, this.overlayFadeEndOpacity, this.overlayFadeSpeed, 0L);
            case 3 -> ColorUtil.setAlpha(ColorUtil.getChroma(this.overlayChromaSpeed.getValue()), this.overlayChromaOpacity.getValue() / 100.0);
            default -> Color.WHITE.getRGB();
        };
    }

    private int getOverlayEndColor() {
        return switch (this.overlayColorMode.getValue()) {
            case 0, 3 -> this.getOverlayStartColor();
            case 1 -> ColorUtil.setAlpha(this.overlayGradientEndColor.getValue(), this.overlayGradientEndOpacity.getValue() / 100.0);
            case 2 -> this.fadeColor(this.overlayFadeStartColor, this.overlayFadeStartOpacity, this.overlayFadeEndColor, this.overlayFadeEndOpacity, this.overlayFadeSpeed, 500L);
            default -> Color.WHITE.getRGB();
        };
    }

    private int getOutlineStartColor() {
        return switch (this.outlineColorMode.getValue()) {
            case 0 -> ColorUtil.setAlpha(this.outlineStaticColor.getValue(), this.outlineStaticOpacity.getValue() / 100.0);
            case 1 -> ColorUtil.setAlpha(this.outlineGradientStartColor.getValue(), this.outlineGradientStartOpacity.getValue() / 100.0);
            case 2 -> this.fadeColor(this.outlineFadeStartColor, this.outlineFadeStartOpacity, this.outlineFadeEndColor, this.outlineFadeEndOpacity, this.outlineFadeSpeed, 0L);
            case 3 -> ColorUtil.setAlpha(ColorUtil.getChroma(this.outlineChromaSpeed.getValue()), this.outlineChromaOpacity.getValue() / 100.0);
            default -> Color.WHITE.getRGB();
        };
    }

    private int getOutlineEndColor() {
        return switch (this.outlineColorMode.getValue()) {
            case 0, 3 -> this.getOutlineStartColor();
            case 1 -> ColorUtil.setAlpha(this.outlineGradientEndColor.getValue(), this.outlineGradientEndOpacity.getValue() / 100.0);
            case 2 -> this.fadeColor(this.outlineFadeStartColor, this.outlineFadeStartOpacity, this.outlineFadeEndColor, this.outlineFadeEndOpacity, this.outlineFadeSpeed, 500L);
            default -> Color.WHITE.getRGB();
        };
    }

    private int fadeColor(ColorProperty startColor, PercentProperty startOpacity, ColorProperty endColor, PercentProperty endOpacity, FloatProperty speed, long offset) {
        double percent = Math.sin((System.currentTimeMillis() + offset) / (1100.0 - speed.getValue() * 100.0)) * 0.5 + 0.5;
        int start = ColorUtil.setAlpha(startColor.getValue(), startOpacity.getValue() / 100.0);
        int end = ColorUtil.setAlpha(endColor.getValue(), endOpacity.getValue() / 100.0);
        return ColorUtil.interpolate(start, end, percent);
    }

    private record GlState(boolean alpha,
                           boolean blend,
                           boolean texture2D,
                           boolean depth,
                           boolean lighting,
                           boolean cull,
                           boolean lineSmooth,
                           boolean depthMask,
                           int shadeModel,
                           float lineWidth) {
        private static GlState capture() {
            return new GlState(
                    GL11.glIsEnabled(GL11.GL_ALPHA_TEST),
                    GL11.glIsEnabled(GL11.GL_BLEND),
                    GL11.glIsEnabled(GL11.GL_TEXTURE_2D),
                    GL11.glIsEnabled(GL11.GL_DEPTH_TEST),
                    GL11.glIsEnabled(GL11.GL_LIGHTING),
                    GL11.glIsEnabled(GL11.GL_CULL_FACE),
                    GL11.glIsEnabled(GL11.GL_LINE_SMOOTH),
                    GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK),
                    GL11.glGetInteger(GL11.GL_SHADE_MODEL),
                    GL11.glGetFloat(GL11.GL_LINE_WIDTH)
            );
        }

        private void restore() {
            GL11.glShadeModel(this.shadeModel);
            GL11.glLineWidth(this.lineWidth);
            setState(GL11.GL_LINE_SMOOTH, this.lineSmooth);
            setGlState(this.lighting, GlStateManager::enableLighting, GlStateManager::disableLighting);
            setGlState(this.cull, GlStateManager::enableCull, GlStateManager::disableCull);
            setGlState(this.depth, GlStateManager::enableDepth, GlStateManager::disableDepth);
            setGlState(this.texture2D, GlStateManager::enableTexture2D, GlStateManager::disableTexture2D);
            setGlState(this.alpha, GlStateManager::enableAlpha, GlStateManager::disableAlpha);
            setGlState(this.blend, GlStateManager::enableBlend, GlStateManager::disableBlend);
            GlStateManager.depthMask(this.depthMask);
        }

        private static void setGlState(boolean enabled, Runnable enable, Runnable disable) {
            if (enabled) {
                enable.run();
            } else {
                disable.run();
            }
        }

        private static void setState(int cap, boolean enabled) {
            if (enabled) {
                GL11.glEnable(cap);
            } else {
                GL11.glDisable(cap);
            }
        }
    }
}
