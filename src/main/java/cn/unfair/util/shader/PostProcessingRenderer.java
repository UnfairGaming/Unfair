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
import org.lwjgl.opengl.GL13;

import java.awt.*;

public class PostProcessingRenderer {
    private static final String BLIT_FRAG = "#version 120\n" +
            "uniform sampler2D tex;\n" +
            "void main() {\n" +
            "    gl_FragColor = texture2D(tex, gl_TexCoord[0].st);\n" +
            "}";
    private static final Minecraft mc = Minecraft.getMinecraft();
    private static Framebuffer stencilFramebuffer = new Framebuffer(1, 1, false);
    private static Framebuffer bloomFramebuffer = new Framebuffer(1, 1, false);
    private static Framebuffer blurSourceFramebuffer;
    private static boolean blurSourceActive;
    private static ShaderUtil blitShader;

    public static void render2D(float partialTicks) {
        flushPostProcessing();
    }

    public static void captureBlurSource() {
        if (AndroidUtil.isAndroid()) {
            return;
        }
        blurSourceFramebuffer = RenderUtil.createFrameBuffer(blurSourceFramebuffer);
        blurSourceFramebuffer.forceBind(true);
        blurSourceFramebuffer.framebufferClearNoBinding();
        if (blitShader == null) {
            blitShader = new ShaderUtil(BLIT_FRAG, true);
        }
        blitShader.init();
        GlStateManager.setActiveTexture(GL13.GL_TEXTURE0);
        RenderUtil.bindTexture(mc.getFramebuffer().framebufferTexture);
        blitShader.setUniformi("tex", 0);
        ShaderUtil.drawQuads();
        blitShader.unload();
        RenderUtil.bindTexture(0);
        mc.getFramebuffer().forceBind(true);
        blurSourceActive = true;
    }

    public static void flushPostProcessing() {
        if (AndroidUtil.isAndroid() || Unfair.moduleManager == null) {
            ShaderElement.getTasks().clear();
            ShaderElement.getBloomTasks().clear();
            ShaderElement.getPostBlurTasks().clear();
            blurSourceActive = false;
            return;
        }
        PostProcessing pp = (PostProcessing) Unfair.moduleManager.getModule(PostProcessing.class);
        if (pp == null || !pp.isEnabled()) {
            ShaderElement.getTasks().clear();
            ShaderElement.getBloomTasks().clear();
            ShaderElement.getPostBlurTasks().clear();
            blurSourceActive = false;
            return;
        }

        if (pp.blur.getValue() && !ShaderElement.getTasks().isEmpty()) {
            drawBlur(pp.blurIterations.getValue(), pp.blurOffset.getValue());
            blurSourceActive = false;
            for (Runnable runnable : ShaderElement.getPostBlurTasks()) {
                runnable.run();
            }
            ShaderElement.getPostBlurTasks().clear();
        } else {
            ShaderElement.getTasks().clear();
            ShaderElement.getPostBlurTasks().clear();
            blurSourceActive = false;
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
        int sourceTexture = blurSourceActive
                ? blurSourceFramebuffer.framebufferTexture
                : mc.getFramebuffer().framebufferTexture;
        KawaseBlur.renderBlur(stencilFramebuffer.framebufferTexture, sourceTexture, iterations, (int) offset);
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