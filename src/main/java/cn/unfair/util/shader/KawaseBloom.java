package cn.unfair.util.shader;

import cn.unfair.util.render.RenderUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.shader.Framebuffer;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

import static org.lwjgl.opengl.GL11.GL_LINEAR;

public class KawaseBloom {

    private static final Minecraft mc = Minecraft.getMinecraft();
    public static ShaderUtil kawaseDown = new ShaderUtil("kawaseDownBloom");
    public static ShaderUtil kawaseUp = new ShaderUtil("kawaseUpBloom");
    public static Framebuffer framebuffer = new Framebuffer(1, 1, true);
    public static Framebuffer stencilFramebuffer = new Framebuffer(1, 1, false);
    private static int currentIterations;
    private static final List<Framebuffer> framebufferList = new ArrayList<>();

    private static void initFramebuffers(float iterations) {
        for (Framebuffer framebuffer : framebufferList) {
            framebuffer.deleteFramebuffer();
        }
        framebufferList.clear();
        framebuffer = RenderUtil.createFrameBuffer(null, true);
        framebufferList.add(framebuffer);
        for (int i = 1; i <= iterations; i++) {
            Framebuffer currentBuffer = new Framebuffer((int) (mc.displayWidth / Math.pow(2.0, i)), (int) (mc.displayHeight / Math.pow(2.0, i)), true);
            currentBuffer.setFramebufferFilter(GL_LINEAR);
            GlStateManager.bindTexture(currentBuffer.framebufferTexture);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
            GlStateManager.bindTexture(0);
            framebufferList.add(currentBuffer);
        }
    }

    public static void shadow2(Runnable drawMod) {
        stencilFramebuffer = RenderUtil.createFrameBuffer(stencilFramebuffer);
        stencilFramebuffer.forceBind(true);
        stencilFramebuffer.framebufferClearNoBinding();
        drawMod.run();
        stencilFramebuffer.unbindFramebuffer();
        KawaseBloom.renderBlur(stencilFramebuffer.framebufferTexture, 4, 4, null);
    }

    public static void shadow(Runnable drawMod) {
        stencilFramebuffer = RenderUtil.createFrameBuffer(stencilFramebuffer);
        stencilFramebuffer.forceBind(true);
        stencilFramebuffer.framebufferClearNoBinding();
        drawMod.run();
        stencilFramebuffer.unbindFramebuffer();
        KawaseBloom.renderBlur(stencilFramebuffer.framebufferTexture, 2, 3, null);
    }

    public static void renderBlur(int framebufferTexture, int iterations, int offset, Color color) {
        if (currentIterations != iterations || framebuffer.framebufferWidth != mc.displayWidth || framebuffer.framebufferHeight != mc.displayHeight) {
            initFramebuffers(iterations);
            currentIterations = iterations;
        }
        GlStateManager.disableBlend();
        GlStateManager.disableAlpha();
        GlStateManager.clearColor(0.0f, 0.0f, 0.0f, 0.0f);
        renderFBO(framebufferList.get(1), framebufferTexture, kawaseDown, offset);
        for (int i = 1; i < iterations; i++) {
            renderFBO(framebufferList.get(i + 1), framebufferList.get(i).framebufferTexture, kawaseDown, offset);
        }
        for (int i = iterations; i > 1; i--) {
            renderFBO(framebufferList.get(i - 1), framebufferList.get(i).framebufferTexture, kawaseUp, offset);
        }
        Framebuffer lastBuffer = framebufferList.get(0);
        lastBuffer.forceBind(true);
        lastBuffer.framebufferClearNoBinding();
        kawaseUp.init();
        kawaseUp.setUniformf("offset", offset, offset);
        kawaseUp.setUniformi("inTexture", 0);
        kawaseUp.setUniformi("check", 1);
        kawaseUp.setUniformi("textureToCheck", 16);
        kawaseUp.setUniformf("halfpixel", 1.0f / lastBuffer.framebufferWidth, 1.0f / lastBuffer.framebufferHeight);
        kawaseUp.setUniformf("iResolution", lastBuffer.framebufferWidth, lastBuffer.framebufferHeight);
        if (color != null) {
            kawaseUp.setUniformf("color", color.getRed() / 255.0F, color.getGreen() / 255.0F, color.getBlue() / 255.0F);
        }
        GlStateManager.setActiveTexture(GL13.GL_TEXTURE16);
        RenderUtil.bindTexture(framebufferTexture);
        GlStateManager.setActiveTexture(GL13.GL_TEXTURE0);
        RenderUtil.bindTexture(framebufferList.get(1).framebufferTexture);
        ShaderUtil.drawQuads();
        kawaseUp.unload();
        GlStateManager.clearColor(0.0f, 0.0f, 0.0f, 0.0f);
        mc.getFramebuffer().forceBind(true);
        GlStateManager.setActiveTexture(GL13.GL_TEXTURE0);
        RenderUtil.bindTexture(framebufferList.get(0).framebufferTexture);
        RenderUtil.setAlphaLimit(0.0f);
        GlStateManager.disableLighting();
        GlStateManager.enableTexture2D();
        GlStateManager.resetColor();
        GlStateManager.enableBlend();
        GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        ShaderUtil.drawQuads();
        GlStateManager.bindTexture(0);
        GlStateManager.enableAlpha();
        GlStateManager.disableBlend();
    }

    private static void renderFBO(Framebuffer framebuffer, int framebufferTexture, ShaderUtil shader, float offset) {
        framebuffer.forceBind(true);
        framebuffer.framebufferClearNoBinding();
        shader.init();
        GlStateManager.setActiveTexture(GL13.GL_TEXTURE0);
        RenderUtil.bindTexture(framebufferTexture);
        shader.setUniformf("offset", offset, offset);
        shader.setUniformi("inTexture", 0);
        shader.setUniformi("check", 0);
        shader.setUniformf("halfpixel", 1.0f / framebuffer.framebufferWidth, 1.0f / framebuffer.framebufferHeight);
        shader.setUniformf("iResolution", framebuffer.framebufferWidth, framebuffer.framebufferHeight);
        ShaderUtil.drawQuads();
        shader.unload();
    }
}
