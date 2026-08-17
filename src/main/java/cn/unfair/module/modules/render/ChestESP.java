package cn.unfair.module.modules.render;

import cn.unfair.Unfair;
import cn.unfair.event.EventTarget;
import cn.unfair.event.types.EventType;
import cn.unfair.events.LoadWorldEvent;
import cn.unfair.events.PacketEvent;
import cn.unfair.events.Render2DEvent;
import cn.unfair.events.Render3DEvent;
import cn.unfair.events.ResizeEvent;
import cn.unfair.module.Module;
import cn.unfair.property.properties.*;
import cn.unfair.util.AndroidUtil;
import cn.unfair.util.RenderUtil;
import cn.unfair.util.postprocessing.GlowESPBlurShader;
import cn.unfair.util.postprocessing.ShaderUtil;
import lombok.Getter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.tileentity.TileEntityRendererDispatcher;
import net.minecraft.client.shader.Framebuffer;
import net.minecraft.block.BlockChest;
import net.minecraft.init.Blocks;
import net.minecraft.network.play.server.S24PacketBlockAction;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntityChest;
import net.minecraft.tileentity.TileEntityEnderChest;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.BlockPos;
import net.minecraft.util.Vec3;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL20;

import java.awt.*;
import java.util.ArrayList;
import java.util.ConcurrentModificationException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

public class ChestESP extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    public final ModeProperty mode;
    public final ColorProperty color;
    public final PercentProperty opacity;
    public final BooleanProperty tracers;
    public final FloatProperty glowExposure;
    public final IntProperty glowRadius;
    private GlowESPBlurShader blurShader;
    private boolean glowAvailable;
    @Getter
    private boolean renderingGlowChests = false;
    private Framebuffer framebuffer = null;
    private Framebuffer glowFrameBuffer = null;
    private List<TileEntity> glowChests = new ArrayList<>();
    private final List<BlockPos> openedChests = new CopyOnWriteArrayList<>();

    public ChestESP() {
        super("ChestESP", false, true);
        this.mode = new ModeProperty("Mode", 2, new String[]{"Default", "Glow", "Naven"});
        this.color = new ColorProperty("Color", new Color(255, 170, 0).getRGB());
        this.opacity = new PercentProperty("Opacity", 100, () -> this.mode.getValue() == 0);
        this.tracers = new BooleanProperty("Tracers", false);
        this.glowExposure = new FloatProperty("Glow Exposure", 2.0F, 0.5F, 3.5F, () -> this.mode.getValue() == 2);
        this.glowRadius = new IntProperty("Glow Radius", 5, 2, 30, () -> this.mode.getValue() == 2);
        try {
            if (AndroidUtil.isAndroid()) {
                this.glowAvailable = false;
                return;
            }
            this.blurShader = new GlowESPBlurShader();
            this.glowAvailable = true;
        } catch (RuntimeException exception) {
            this.glowAvailable = false;
            System.err.println("ChestESP glow shader unavailable; falling back to default ESP.");
            exception.printStackTrace();
        }
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
        this.openedChests.clear();
    }

    @EventTarget
    public void onLoadWorld(LoadWorldEvent event) {
        this.openedChests.clear();
    }

    @EventTarget
    public void onPacket(PacketEvent event) {
        if (!this.isEnabled() || this.mode.getValue() != 2
                || event.getType() != EventType.RECEIVE || !(event.getPacket() instanceof S24PacketBlockAction packet)) {
            return;
        }

        if ((packet.getBlockType() == Blocks.chest || packet.getBlockType() == Blocks.trapped_chest)
                && packet.getData1() == 1 && packet.getData2() == 1
                && !this.openedChests.contains(packet.getBlockPosition())) {
            this.openedChests.add(packet.getBlockPosition());
        }
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
        this.renderingGlowChests = true;
        GlStateManager.color(
                color.getRed() / 255.0F,
                color.getGreen() / 255.0F,
                color.getBlue() / 255.0F,
                1.0F
        );
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
            GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
            this.renderingGlowChests = false;
        }
    }

    private void renderGlowPass() {
        if (!this.glowAvailable
                || this.blurShader == null
                || this.framebuffer == null
                || this.glowFrameBuffer == null
                || this.glowChests.isEmpty()) {
            return;
        }

        GlStateManager.pushMatrix();
        GlStateManager.pushAttrib();
        try {
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
            ShaderUtil.drawQuads();
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
            ShaderUtil.drawQuads();
            this.blurShader.stop();
        } finally {
            this.glowFrameBuffer.unbindFramebuffer();
            mc.getFramebuffer().bindFramebuffer(true);
            this.blurShader.stop();
            GL13.glActiveTexture(GL13.GL_TEXTURE0);
            GlStateManager.setActiveTexture(OpenGlHelper.defaultTexUnit);
            RenderUtil.bindTexture(0);
            GL20.glUseProgram(0);
            GlStateManager.resetColor();
            GlStateManager.enableTexture2D();
            GlStateManager.enableAlpha();
            GlStateManager.disableBlend();
            GlStateManager.popAttrib();
            GlStateManager.popMatrix();
        }
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

    private AxisAlignedBB getNavenChestBox(TileEntityChest chest) {
        chest.checkForAdjacentChests();
        if (chest.adjacentChestXNeg != null || chest.adjacentChestZNeg != null) {
            return null;
        }

        BlockPos position = chest.getPos();
        AxisAlignedBB box = new AxisAlignedBB(position, position.add(1, 1, 1));
        if (chest.adjacentChestXPos != null) {
            box = box.union(new AxisAlignedBB(chest.adjacentChestXPos.getPos(), chest.adjacentChestXPos.getPos().add(1, 1, 1)));
        }
        if (chest.adjacentChestZPos != null) {
            box = box.union(new AxisAlignedBB(chest.adjacentChestZPos.getPos(), chest.adjacentChestZPos.getPos().add(1, 1, 1)));
        }
        return box.offset(
                -mc.getRenderManager().getRenderPosX(),
                -mc.getRenderManager().getRenderPosY(),
                -mc.getRenderManager().getRenderPosZ()
        );
    }

    private void drawNavenBox(TileEntityChest chest) {
        if (!(chest.getBlockType() instanceof BlockChest)) {
            return;
        }

        AxisAlignedBB box = this.getNavenChestBox(chest);
        if (box == null) {
            return;
        }

        boolean opened = this.openedChests.contains(chest.getPos());
        RenderUtil.drawFilledBox(box, opened ? 255 : 0, opened ? 0 : 255, 0, 64);
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
        if (this.isEnabled() && this.mode.getValue() == 2 && this.glowAvailable) {
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
        if (this.mode.getValue() == 2 && this.glowAvailable) {
            this.createGlowFramebuffers();
            this.glowChests = renderedChests;
            this.framebuffer.framebufferClear();
            this.framebuffer.bindFramebuffer(true);
            this.renderGlowChests(event.partialTicks());
            this.framebuffer.unbindFramebuffer();
            mc.getFramebuffer().bindFramebuffer(true);
            GlStateManager.disableLighting();
        }

        if (this.mode.getValue() == 0
                || this.mode.getValue() == 2 && !this.glowAvailable
                || this.tracers.getValue()) {
            RenderUtil.enableRenderState();
            Color color = this.getColor();
            for (TileEntity chest : renderedChests) {
                if (this.mode.getValue() == 0 || this.mode.getValue() == 2 && !this.glowAvailable) {
                    this.drawDefaultBox(chest, color);
                }
                if (this.tracers.getValue()) {
                    this.drawTracer(chest, color);
                }
            }
            RenderUtil.disableRenderState();
        }

        if (this.mode.getValue() == 2) {
            RenderUtil.enableRenderState();
            for (TileEntity chest : renderedChests) {
                if (chest instanceof TileEntityChest tileEntityChest) {
                    this.drawNavenBox(tileEntityChest);
                }
            }
            RenderUtil.disableRenderState();
        }
    }
}
