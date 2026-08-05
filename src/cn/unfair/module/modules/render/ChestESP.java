package cn.unfair.module.modules.render;

import cn.unfair.Unfair;
import cn.unfair.event.EventTarget;
import cn.unfair.events.Render2DEvent;
import cn.unfair.events.Render3DEvent;
import cn.unfair.events.ResizeEvent;
import cn.unfair.module.Module;
import cn.unfair.property.properties.*;
import cn.unfair.util.RenderUtil;
import cn.unfair.util.postprocessing.GlowESPBlurShader;
import cn.unfair.util.postprocessing.ShaderUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.entity.RendererLivingEntity;
import net.minecraft.client.renderer.tileentity.TileEntityRendererDispatcher;
import net.minecraft.client.shader.Framebuffer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntityChest;
import net.minecraft.tileentity.TileEntityEnderChest;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.Vec3;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;

import java.awt.*;
import java.util.ArrayList;
import java.util.ConcurrentModificationException;
import java.util.List;
import java.util.stream.Collectors;

public class ChestESP extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private static final int MODE_DEFAULT = 0;
    private static final int MODE_GLOW = 1;
    public final ModeProperty mode;
    public final ColorProperty color;
    public final PercentProperty opacity;
    public final BooleanProperty tracers;
    public final FloatProperty glowExposure;
    public final IntProperty glowRadius;
    private final GlowESPBlurShader blurShader = new GlowESPBlurShader();
    private Framebuffer framebuffer = null;
    private Framebuffer glowFrameBuffer = null;
    private List<TileEntity> glowChests = new ArrayList<>();

    public ChestESP() {
        super("ChestESP", false, true);
        this.mode = new ModeProperty("mode", MODE_DEFAULT, new String[]{"DEFAULT", "GLOW"});
        this.color = new ColorProperty("color", new Color(255, 170, 0).getRGB());
        this.opacity = new PercentProperty("opacity", 100, () -> this.mode.getValue() == MODE_DEFAULT);
        this.tracers = new BooleanProperty("tracers", false);
        this.glowExposure = new FloatProperty("glow-exposure", 2.0F, 0.5F, 3.5F, () -> this.mode.getValue() == MODE_GLOW);
        this.glowRadius = new IntProperty("glow-radius", 5, 2, 30, () -> this.mode.getValue() == MODE_GLOW);
    }

    private Color getColor() {
        return new Color(this.color.getValue());
    }

    private List<TileEntity> getRenderedChests() {
        if (mc.theWorld == null) {
            return new ArrayList<>();
        }
        try {
            return mc.theWorld.loadedTileEntityList
                    .stream()
                    .filter(tileEntity -> tileEntity instanceof TileEntityChest || tileEntity instanceof TileEntityEnderChest)
                    .collect(Collectors.toList());
        } catch (ConcurrentModificationException ignored) {
            return new ArrayList<>();
        }
    }

    @EventTarget
    public void onResize(ResizeEvent event) {
        this.deleteGlowFramebuffers();
    }

    @Override
    public void onDisabled() {
        this.deleteGlowFramebuffers();
        this.glowChests.clear();
    }

    private void createGlowFramebuffers() {
        if (this.framebuffer != null
                && this.glowFrameBuffer != null
                && this.framebuffer.framebufferWidth == mc.displayWidth
                && this.framebuffer.framebufferHeight == mc.displayHeight
                && this.glowFrameBuffer.framebufferWidth == mc.displayWidth
                && this.glowFrameBuffer.framebufferHeight == mc.displayHeight) {
            return;
        }
        this.framebuffer = RenderUtil.createFrameBuffer(this.framebuffer, true);
        this.glowFrameBuffer = RenderUtil.createFrameBuffer(this.glowFrameBuffer, true);
    }

    private void deleteGlowFramebuffers() {
        if (this.framebuffer != null) {
            this.framebuffer.deleteFramebuffer();
            this.framebuffer = null;
        }
        if (this.glowFrameBuffer != null) {
            this.glowFrameBuffer.deleteFramebuffer();
            this.glowFrameBuffer = null;
        }
    }

    private void renderGlowChests(float partialTicks) {
        if (this.glowChests.isEmpty()) {
            return;
        }

        Color color = this.getColor();
        RendererLivingEntity.setShaderBrightness(color);
        try {
            for (TileEntity chest : this.glowChests) {
                TileEntityRendererDispatcher.instance.renderTileEntityAt(
                        chest,
                        (double) chest.getPos().getX() - TileEntityRendererDispatcher.staticPlayerX,
                        (double) chest.getPos().getY() - TileEntityRendererDispatcher.staticPlayerY,
                        (double) chest.getPos().getZ() - TileEntityRendererDispatcher.staticPlayerZ,
                        partialTicks
                );
            }
        } finally {
            RendererLivingEntity.unsetShaderBrightness();
        }
    }

    private void renderGlowPass() {
        if (this.framebuffer == null || this.glowFrameBuffer == null || this.glowChests.isEmpty()) {
            return;
        }

        GlStateManager.pushMatrix();
        GlStateManager.pushAttrib();
        GlStateManager.enableAlpha();
        GlStateManager.alphaFunc(GL11.GL_GREATER, 0.0F);
        GlStateManager.enableBlend();
        OpenGlHelper.glBlendFunc(GL11.GL_ONE, GL11.GL_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO);

        float radius = this.glowRadius.getValue();
        Color color = this.getColor();
        this.glowFrameBuffer.framebufferClear();
        this.glowFrameBuffer.bindFramebuffer(true);
        this.blurShader.use();
        this.blurShader.setup(2.0F, 0.0F, radius, this.glowExposure.getValue(), color);
        RenderUtil.bindTexture(this.framebuffer.framebufferTexture);
        ShaderUtils.drawQuads();
        this.blurShader.stop();
        this.glowFrameBuffer.unbindFramebuffer();

        mc.getFramebuffer().bindFramebuffer(true);
        OpenGlHelper.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO);
        this.blurShader.use();
        this.blurShader.setup(0.0F, 2.0F, radius, this.glowExposure.getValue(), color, true);
        RenderUtil.bindTexture(this.glowFrameBuffer.framebufferTexture);
        GL13.glActiveTexture(GL13.GL_TEXTURE16);
        RenderUtil.bindTexture(this.framebuffer.framebufferTexture);
        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        ShaderUtils.drawQuads();
        this.blurShader.stop();
        RenderUtil.bindTexture(0);
        GlStateManager.popAttrib();
        GlStateManager.popMatrix();
    }

    private AxisAlignedBB getChestBox(TileEntity chest) {
        return new AxisAlignedBB(
                (double) chest.getPos().getX() + 0.0625,
                chest.getPos().getY(),
                (double) chest.getPos().getZ() + 0.0625,
                (double) chest.getPos().getX() + 0.9375,
                (double) chest.getPos().getY() + 0.875,
                (double) chest.getPos().getZ() + 0.9375
        )
                .offset(
                        -mc.getRenderManager().getRenderPosX(),
                        -mc.getRenderManager().getRenderPosY(),
                        -mc.getRenderManager().getRenderPosZ()
                );
    }

    private void drawDefaultBox(TileEntity chest, Color color) {
        RenderUtil.drawBoundingBox(
                this.getChestBox(chest),
                color.getRed(),
                color.getGreen(),
                color.getBlue(),
                (int) ((float) this.opacity.getValue() / 100.0F * 255.0F),
                1.5F
        );
    }

    private void drawTracer(TileEntity chest, Color color) {
        Vec3 vec;
        if (mc.gameSettings.thirdPersonView == 0) {
            vec = new Vec3(0.0, 0.0, 1.0)
                    .rotatePitch((float) (-Math.toRadians(RenderUtil.lerpFloat(
                            mc.getRenderViewEntity().rotationPitch,
                            mc.getRenderViewEntity().prevRotationPitch,
                            mc.timer.renderPartialTicks
                    ))))
                    .rotateYaw((float) (-Math.toRadians(RenderUtil.lerpFloat(
                            mc.getRenderViewEntity().rotationYaw,
                            mc.getRenderViewEntity().prevRotationYaw,
                            mc.timer.renderPartialTicks
                    ))));
        } else {
            vec = new Vec3(0.0, 0.0, 0.0)
                    .rotatePitch((float) (-Math.toRadians(RenderUtil.lerpFloat(
                            mc.thePlayer.cameraPitch,
                            mc.thePlayer.prevCameraPitch,
                            mc.timer.renderPartialTicks
                    ))))
                    .rotateYaw((float) (-Math.toRadians(RenderUtil.lerpFloat(
                            mc.thePlayer.cameraYaw,
                            mc.thePlayer.prevCameraYaw,
                            mc.timer.renderPartialTicks
                    ))));
        }

        vec = new Vec3(vec.xCoord, vec.yCoord + (double) mc.getRenderViewEntity().getEyeHeight(), vec.zCoord);
        float opacity = (float) ((Tracers) Unfair.moduleManager.modules.get(Tracers.class)).opacity.getValue() / 100.0F;
        RenderUtil.drawLine3D(
                vec,
                (double) chest.getPos().getX() + 0.5,
                (double) chest.getPos().getY() + 0.5,
                (double) chest.getPos().getZ() + 0.5,
                (float) color.getRed() / 255.0F,
                (float) color.getGreen() / 255.0F,
                (float) color.getBlue() / 255.0F,
                opacity,
                1.5F
        );
    }

    @EventTarget
    public void onRender2D(Render2DEvent event) {
        if (this.isEnabled() && this.mode.getValue() == MODE_GLOW) {
            this.renderGlowPass();
        }
    }

    @EventTarget
    public void onRender(Render3DEvent event) {
        if (!this.isEnabled() || mc.theWorld == null) {
            return;
        }

        List<TileEntity> renderedChests = this.getRenderedChests();
        if (renderedChests.isEmpty() && !this.tracers.getValue()) {
            this.glowChests = renderedChests;
            return;
        }
        if (this.mode.getValue() == MODE_GLOW) {
            this.createGlowFramebuffers();
            this.glowChests = renderedChests;
            this.framebuffer.framebufferClear();
            this.framebuffer.bindFramebuffer(true);
            this.renderGlowChests(event.partialTicks());
            this.framebuffer.unbindFramebuffer();
            mc.getFramebuffer().bindFramebuffer(true);
            GlStateManager.disableLighting();
        }

        if (this.mode.getValue() == MODE_DEFAULT || this.tracers.getValue()) {
            RenderUtil.enableRenderState();
            Color color = this.getColor();
            for (TileEntity chest : renderedChests) {
                if (this.mode.getValue() == MODE_DEFAULT) {
                    this.drawDefaultBox(chest, color);
                }
                if (this.tracers.getValue()) {
                    this.drawTracer(chest, color);
                }
            }
            RenderUtil.disableRenderState();
        }
    }
}
