package cn.unfair.util.postprocessing;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.shader.Framebuffer;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

import static org.lwjgl.opengl.GL11.GL_LINEAR;
import static org.lwjgl.opengl.GL11.GL_ONE;

public class BloomShader {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private static final ShaderUtils KAWASE_DOWN = new ShaderUtils("kawaseDownBloom");
    private static final ShaderUtils KAWASE_UP = new ShaderUtils("kawaseUpBloom");

    private static Framebuffer inputFramebuffer;
    private static int currentIterations;

    private static final List<Framebuffer> framebufferList = new ArrayList<>();

    private static Framebuffer ensureInputFramebuffer() {
        if (inputFramebuffer == null || inputFramebuffer.framebufferWidth != mc.displayWidth || inputFramebuffer.framebufferHeight != mc.displayHeight) {
            if (inputFramebuffer != null) {
                inputFramebuffer.deleteFramebuffer();
            }
            inputFramebuffer = new Framebuffer(mc.displayWidth, mc.displayHeight, false);
            inputFramebuffer.setFramebufferFilter(GL_LINEAR);
        }
        return inputFramebuffer;
    }

    public static Framebuffer beginFramebuffer() {
        Framebuffer fb = ensureInputFramebuffer();
        GL11.glClearColor(0, 0, 0, 0);
        fb.forceBind(true);
        fb.framebufferClearNoBinding();
        return fb;
    }

    private static void initFramebuffers(int iterations) {
        framebufferList.forEach(Framebuffer::deleteFramebuffer);
        framebufferList.clear();

        Framebuffer full = new Framebuffer(mc.displayWidth, mc.displayHeight, false);
        full.setFramebufferFilter(GL_LINEAR);
        framebufferList.add(full);

        for (int i = 1; i <= iterations; i++) {
            int width = Math.max(1, mc.displayWidth >> i);
            int height = Math.max(1, mc.displayHeight >> i);
            Framebuffer currentBuffer = new Framebuffer(width, height, false);
            currentBuffer.setFramebufferFilter(GL_LINEAR);
            GlStateManager.bindTexture(currentBuffer.framebufferTexture);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL14.GL_MIRRORED_REPEAT);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL14.GL_MIRRORED_REPEAT);
            GlStateManager.bindTexture(0);
            framebufferList.add(currentBuffer);
        }
    }

    public static void renderBloom(int framebufferTexture, int iterations, int offset, Color color) {
        iterations = Math.max(1, iterations);
        offset = Math.max(1, offset);

        if (framebufferList.isEmpty() || currentIterations != iterations || framebufferList.get(0).framebufferWidth != mc.displayWidth || framebufferList.get(0).framebufferHeight != mc.displayHeight) {
            initFramebuffers(iterations);
            currentIterations = iterations;
        }

        GlStateManager.enableBlend();
        GlStateManager.blendFunc(GL_ONE, GL_ONE);

        GL11.glClearColor(0, 0, 0, 0);
        renderDownFBO(framebufferList.get(1), framebufferTexture, offset);

        for (int i = 1; i < iterations; i++) {
            renderDownFBO(framebufferList.get(i + 1), framebufferList.get(i).framebufferTexture, offset);
        }

        for (int i = iterations; i > 1; i--) {
            renderUpFBO(framebufferList.get(i - 1), framebufferList.get(i).framebufferTexture, offset, color);
        }

        Framebuffer lastBuffer = framebufferList.get(0);
        lastBuffer.forceBind(true);
        lastBuffer.framebufferClearNoBinding();
        KAWASE_UP.init();
        KAWASE_UP.setUniformf("offset", offset, offset);
        KAWASE_UP.setUniformf("halfpixel", 1.0f / lastBuffer.framebufferWidth, 1.0f / lastBuffer.framebufferHeight);
        KAWASE_UP.setUniformf("iResolution", lastBuffer.framebufferWidth, lastBuffer.framebufferHeight);
        KAWASE_UP.setUniformi("inTexture", 0);
        KAWASE_UP.setUniformi("check", 1);
        KAWASE_UP.setUniformi("textureToCheck", 1);
        KAWASE_UP.setUniformf("color", color.getRed() / 255.0f, color.getGreen() / 255.0f, color.getBlue() / 255.0f);
        GlStateManager.setActiveTexture(GL13.GL_TEXTURE1);
        GlStateManager.bindTexture(framebufferTexture);
        GlStateManager.setActiveTexture(GL13.GL_TEXTURE0);
        GlStateManager.bindTexture(framebufferList.get(1).framebufferTexture);
        ShaderUtils.drawQuads();
        KAWASE_UP.unload();

        GL11.glClearColor(0, 0, 0, 0);
        mc.getFramebuffer().forceBind(true);
        GlStateManager.enableBlend();
        GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GlStateManager.bindTexture(framebufferList.get(0).framebufferTexture);
        ShaderUtils.drawFixedQuads();
        GlStateManager.bindTexture(0);
        GlStateManager.setActiveTexture(GL13.GL_TEXTURE0);
    }

    private static void renderDownFBO(Framebuffer fb, int texture, float offset) {
        fb.forceBind(true);
        fb.framebufferClearNoBinding();
        GlStateManager.setActiveTexture(GL13.GL_TEXTURE0);
        KAWASE_DOWN.init();
        KAWASE_DOWN.setUniformf("offset", offset, offset);
        KAWASE_DOWN.setUniformf("halfpixel", 1.0f / fb.framebufferWidth, 1.0f / fb.framebufferHeight);
        KAWASE_DOWN.setUniformf("iResolution", fb.framebufferWidth, fb.framebufferHeight);
        KAWASE_DOWN.setUniformi("inTexture", 0);
        GlStateManager.bindTexture(texture);
        ShaderUtils.drawQuads();
        KAWASE_DOWN.unload();
    }

    private static void renderUpFBO(Framebuffer fb, int texture, float offset, Color color) {
        fb.forceBind(true);
        fb.framebufferClearNoBinding();
        GlStateManager.setActiveTexture(GL13.GL_TEXTURE0);
        KAWASE_UP.init();
        KAWASE_UP.setUniformf("offset", offset, offset);
        KAWASE_UP.setUniformf("halfpixel", 1.0f / fb.framebufferWidth, 1.0f / fb.framebufferHeight);
        KAWASE_UP.setUniformf("iResolution", fb.framebufferWidth, fb.framebufferHeight);
        KAWASE_UP.setUniformi("inTexture", 0);
        KAWASE_UP.setUniformi("check", 0);
        KAWASE_UP.setUniformi("textureToCheck", 1);
        KAWASE_UP.setUniformf("color", color.getRed() / 255.0f, color.getGreen() / 255.0f, color.getBlue() / 255.0f);
        GlStateManager.bindTexture(texture);
        ShaderUtils.drawQuads();
        KAWASE_UP.unload();
    }
}
