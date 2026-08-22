package cn.unfair.util.shader;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.shader.Framebuffer;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL20;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

import static org.lwjgl.opengl.GL11.GL_LINEAR;
import static org.lwjgl.opengl.GL11.GL_ONE;

public class BloomShader {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private static final ShaderUtil KAWASE_DOWN = new ShaderUtil("kawaseDownBloom");
    private static final ShaderUtil KAWASE_UP = new ShaderUtil("kawaseUpBloom");
    private static final List<Framebuffer> framebufferList = new ArrayList<>();
    private static Framebuffer inputFramebuffer;
    private static int currentIterations;
    private static boolean uniformLocationsInitialized;
    private static int downOffsetLocation;
    private static int downHalfpixelLocation;
    private static int downResolutionLocation;
    private static int downTextureLocation;
    private static int upOffsetLocation;
    private static int upHalfpixelLocation;
    private static int upResolutionLocation;
    private static int upTextureLocation;
    private static int upCheckLocation;
    private static int upTextureToCheckLocation;
    private static int upColorLocation;

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

    public static void renderBloom(int framebufferTexture, int iterations, float offset, Color color) {
        iterations = Math.max(1, iterations);
        offset = Math.max(1.0F, offset);
        ensureUniformLocations();

        if (framebufferList.isEmpty() || currentIterations != iterations || framebufferList.get(0).framebufferWidth != mc.displayWidth || framebufferList.get(0).framebufferHeight != mc.displayHeight) {
            initFramebuffers(iterations);
            currentIterations = iterations;
        }

        GlStateManager.enableBlend();
        GlStateManager.blendFunc(GL_ONE, GL_ONE);

        GL11.glClearColor(0, 0, 0, 0);
        KAWASE_DOWN.init();
        renderDownFBO(framebufferList.get(1), framebufferTexture, offset);

        for (int i = 1; i < iterations; i++) {
            renderDownFBO(framebufferList.get(i + 1), framebufferList.get(i).framebufferTexture, offset);
        }
        KAWASE_DOWN.unload();

        KAWASE_UP.init();
        for (int i = iterations; i > 1; i--) {
            renderUpFBO(framebufferList.get(i - 1), framebufferList.get(i).framebufferTexture, offset, color);
        }

        Framebuffer lastBuffer = framebufferList.get(0);
        lastBuffer.forceBind(true);
        lastBuffer.framebufferClearNoBinding();
        setUpUniforms(lastBuffer, offset, color, 1);
        GlStateManager.setActiveTexture(GL13.GL_TEXTURE1);
        GlStateManager.bindTexture(framebufferTexture);
        GlStateManager.setActiveTexture(GL13.GL_TEXTURE0);
        GlStateManager.bindTexture(framebufferList.get(1).framebufferTexture);
        ShaderUtil.drawQuads();
        KAWASE_UP.unload();

        GL11.glClearColor(0, 0, 0, 0);
        mc.getFramebuffer().forceBind(true);
        GlStateManager.enableBlend();
        GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GlStateManager.bindTexture(framebufferList.get(0).framebufferTexture);
        ShaderUtil.drawFixedQuads();
        GlStateManager.bindTexture(0);
        GlStateManager.setActiveTexture(GL13.GL_TEXTURE0);
    }

    private static void renderDownFBO(Framebuffer fb, int texture, float offset) {
        fb.forceBind(true);
        fb.framebufferClearNoBinding();
        GlStateManager.setActiveTexture(GL13.GL_TEXTURE0);
        setDownUniforms(fb, offset);
        GlStateManager.bindTexture(texture);
        ShaderUtil.drawQuads();
    }

    private static void renderUpFBO(Framebuffer fb, int texture, float offset, Color color) {
        fb.forceBind(true);
        fb.framebufferClearNoBinding();
        GlStateManager.setActiveTexture(GL13.GL_TEXTURE0);
        setUpUniforms(fb, offset, color, 0);
        GlStateManager.bindTexture(texture);
        ShaderUtil.drawQuads();
    }

    private static void ensureUniformLocations() {
        if (uniformLocationsInitialized) {
            return;
        }
        downOffsetLocation = KAWASE_DOWN.getUniformLocation("offset");
        downHalfpixelLocation = KAWASE_DOWN.getUniformLocation("halfpixel");
        downResolutionLocation = KAWASE_DOWN.getUniformLocation("iResolution");
        downTextureLocation = KAWASE_DOWN.getUniformLocation("inTexture");
        upOffsetLocation = KAWASE_UP.getUniformLocation("offset");
        upHalfpixelLocation = KAWASE_UP.getUniformLocation("halfpixel");
        upResolutionLocation = KAWASE_UP.getUniformLocation("iResolution");
        upTextureLocation = KAWASE_UP.getUniformLocation("inTexture");
        upCheckLocation = KAWASE_UP.getUniformLocation("check");
        upTextureToCheckLocation = KAWASE_UP.getUniformLocation("textureToCheck");
        upColorLocation = KAWASE_UP.getUniformLocation("color");
        uniformLocationsInitialized = true;
    }

    private static void setDownUniforms(Framebuffer framebuffer, float offset) {
        if (downOffsetLocation >= 0) GL20.glUniform2f(downOffsetLocation, offset, offset);
        if (downHalfpixelLocation >= 0)
            GL20.glUniform2f(downHalfpixelLocation, 1.0F / framebuffer.framebufferWidth, 1.0F / framebuffer.framebufferHeight);
        if (downResolutionLocation >= 0)
            GL20.glUniform2f(downResolutionLocation, framebuffer.framebufferWidth, framebuffer.framebufferHeight);
        if (downTextureLocation >= 0) GL20.glUniform1i(downTextureLocation, 0);
    }

    private static void setUpUniforms(Framebuffer framebuffer, float offset, Color color, int check) {
        if (upOffsetLocation >= 0) GL20.glUniform2f(upOffsetLocation, offset, offset);
        if (upHalfpixelLocation >= 0)
            GL20.glUniform2f(upHalfpixelLocation, 1.0F / framebuffer.framebufferWidth, 1.0F / framebuffer.framebufferHeight);
        if (upResolutionLocation >= 0)
            GL20.glUniform2f(upResolutionLocation, framebuffer.framebufferWidth, framebuffer.framebufferHeight);
        if (upTextureLocation >= 0) GL20.glUniform1i(upTextureLocation, 0);
        if (upCheckLocation >= 0) GL20.glUniform1i(upCheckLocation, check);
        if (upTextureToCheckLocation >= 0) GL20.glUniform1i(upTextureToCheckLocation, 1);
        if (upColorLocation >= 0)
            GL20.glUniform3f(upColorLocation, color.getRed() / 255.0F, color.getGreen() / 255.0F, color.getBlue() / 255.0F);
    }
}
