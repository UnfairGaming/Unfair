package cn.unfair.util.shader;

import cn.unfair.Unfair;
import cn.unfair.module.modules.render.PostProcessing;
import cn.unfair.util.client.AndroidUtil;
import cn.unfair.util.render.RenderUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.shader.Framebuffer;
import org.lwjgl.opengl.GL11;

import java.awt.*;

public class PostProcessingRenderer {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private static Framebuffer stencilFramebuffer = new Framebuffer(1, 1, false);
    private static Framebuffer bloomFramebuffer = new Framebuffer(1, 1, false);

    public static void render2D(float partialTicks) {
        if (AndroidUtil.isAndroid() || Unfair.moduleManager == null) {
            ShaderElement.getTasks().clear();
            ShaderElement.getBloomTasks().clear();
            return;
        }
        PostProcessing pp = (PostProcessing) Unfair.moduleManager.getModule(PostProcessing.class);
        if (pp == null || !pp.isEnabled()) {
            ShaderElement.getTasks().clear();
            ShaderElement.getBloomTasks().clear();
            return;
        }

        if (pp.blur.getValue() && !ShaderElement.getTasks().isEmpty()) {
            drawBlur(pp.blurIterations.getValue(), pp.blurOffset.getValue());
        } else {
            ShaderElement.getTasks().clear();
        }

        if (pp.bloom.getValue() && !ShaderElement.getBloomTasks().isEmpty()) {
            drawBloom(pp.bloomIterations.getValue(), pp.bloomOffset.getValue(), pp.getBloomColor(System.currentTimeMillis()));
        } else {
            ShaderElement.getBloomTasks().clear();
        }
    }

    private static void setupGuiProjection() {
        ScaledResolution sr = new ScaledResolution(mc);
        GlStateManager.matrixMode(GL11.GL_PROJECTION);
        GlStateManager.loadIdentity();
        GlStateManager.ortho(0.0D, sr.getScaledWidth_double(), sr.getScaledHeight_double(), 0.0D, 1000.0D, 3000.0D);
        GlStateManager.matrixMode(GL11.GL_MODELVIEW);
        GlStateManager.loadIdentity();
        GlStateManager.translate(0.0F, 0.0F, -2000.0F);
    }

    private static void drawBlur(int iterations, float offset) {
        setupGuiProjection();
        stencilFramebuffer = RenderUtil.createFrameBuffer(stencilFramebuffer);
        stencilFramebuffer.forceBind(true);
        stencilFramebuffer.framebufferClearNoBinding();
        for (Runnable runnable : ShaderElement.getTasks()) {
            runnable.run();
        }
        ShaderElement.getTasks().clear();
        stencilFramebuffer.unbindFramebuffer();
        KawaseBlur.renderBlur(stencilFramebuffer.framebufferTexture, iterations, (int) offset);
    }

    private static void drawBloom(int iterations, float offset, Color color) {
        setupGuiProjection();
        bloomFramebuffer = RenderUtil.createFrameBuffer(bloomFramebuffer);
        bloomFramebuffer.forceBind(true);
        bloomFramebuffer.framebufferClearNoBinding();
        for (Runnable runnable : ShaderElement.getBloomTasks()) {
            runnable.run();
        }
        ShaderElement.getBloomTasks().clear();
        bloomFramebuffer.unbindFramebuffer();
        KawaseBloom.renderBlur(bloomFramebuffer.framebufferTexture, iterations, (int) offset, color);
    }
}